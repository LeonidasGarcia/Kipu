import { verifyPubSubOidcToken } from "./google-oidc.ts";
import { createRtdnStore } from "./rtdn-store.ts";
import { createPurchaseStore } from "../verify-purchase/purchase-store.ts";
import { createGooglePlayApi, ProviderRejectedError, ProviderUnavailableError } from "../verify-purchase/google-play-api.ts";
import { createOfflineEntitlementGrantIssuer } from "../verify-purchase/offline-entitlement-grant.ts";
import { verifyCurrentPurchaseForOwner } from "../verify-purchase/index.ts";

export type RtdnOutcome = "VERIFIED" | "PENDING" | "REJECTED" | "RETRYABLE";

export type BeginEventInput = {
  eventIdentityHash: string;
  providerMessageId: string;
  purchaseTokenHash: string | null;
  notificationType: "SUBSCRIPTION" | "ONE_TIME_PRODUCT" | "VOIDED_PURCHASE" | "TEST";
};

export type BeginEventResult = {
  receiptId: string;
  duplicate: boolean;
  receiptStatus: "WAITING_FOR_TOKEN" | "PROCESSING" | "RETRYABLE" | "COMPLETED" | "REJECTED";
  userId: string | null;
  storeProductId: string | null;
  billingProductId?: string | null;
  jobId?: string | null;
  leaseOwner?: string | null;
  safeResultCode?: string | null;
};

export type PlayRtdnDependencies = {
  verifyPubSubIdentity: (
    token: string,
    audience: string,
    serviceAccount: string,
  ) => Promise<boolean>;
  beginEvent: (input: BeginEventInput) => Promise<BeginEventResult>;
  verifyCurrentPurchase: (input: {
    userId: string;
    storeProductId: string;
    billingProductId: string;
    purchaseToken: string;
    purchaseTokenHash: string;
    receiptId: string;
    jobId: string;
    leaseOwner: string;
    notificationType: BeginEventInput["notificationType"];
  }) => Promise<RtdnOutcome | {
    outcome: RtdnOutcome;
    finalized: boolean;
    safeResultCode?: string;
  }>;
  finishEvent: (input: {
    receiptId: string;
    jobId: string;
    leaseOwner: string;
    outcome: "RETRYABLE" | "COMPLETED" | "REJECTED";
    safeResultCode: string;
  }) => Promise<void>;
};

export type PlayRtdnConfig = {
  expectedAudience: string;
  expectedServiceAccount: string;
  packageName: string;
  subscriptionIdentity: string;
};

const MAX_BODY_BYTES = 65_536;
const MAX_MESSAGE_BYTES = 49_152;
const MAX_TOKEN_BYTES = 4_096;
const SUBSCRIPTION_NOTIFICATION_TYPES = new Set([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 16, 17, 18, 19]);
const SAFE_RESULT_CODES = new Set([
  "VERIFIED",
  "PENDING",
  "PURCHASE_REJECTED",
  "TOKEN_ACCOUNT_CONFLICT",
  "VERIFIER_UNAVAILABLE",
]);

type ParsedNotification = {
  notificationType: BeginEventInput["notificationType"];
  purchaseToken: string | null;
};

function json(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

function errorResponse(code: string, retryable: boolean, status: number): Response {
  return json({ outcome: retryable ? "RETRYABLE" : "REJECTED", code, retryable }, status);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

async function sha256Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

function parseNotification(payload: Record<string, unknown>): ParsedNotification | null {
  const names = [
    "subscriptionNotification",
    "oneTimeProductNotification",
    "voidedPurchaseNotification",
    "testNotification",
  ];
  const present = names.filter((name) => payload[name] !== undefined);
  if (present.length !== 1) return null;
  const name = present[0];
  const event = payload[name];
  if (!isRecord(event)) return null;
  let notificationType: ParsedNotification["notificationType"];
  if (name === "subscriptionNotification") {
    if (typeof event.notificationType !== "number" ||
      !SUBSCRIPTION_NOTIFICATION_TYPES.has(event.notificationType)) return null;
    notificationType = "SUBSCRIPTION";
  } else if (name === "oneTimeProductNotification") {
    if (event.notificationType !== 1 && event.notificationType !== 2) return null;
    notificationType = "ONE_TIME_PRODUCT";
  } else if (name === "voidedPurchaseNotification") {
    if (event.productType !== 1 && event.productType !== 2) return null;
    notificationType = "VOIDED_PURCHASE";
  } else {
    if (typeof event.version !== "string" || event.version.length > 32) return null;
    notificationType = "TEST";
  }

  const rawToken = event.purchaseToken;
  if (rawToken === undefined || rawToken === null) {
    return { notificationType, purchaseToken: null };
  }
  if (typeof rawToken !== "string" || rawToken.length === 0 ||
    new TextEncoder().encode(rawToken).byteLength > MAX_TOKEN_BYTES) return null;
  return { notificationType, purchaseToken: rawToken };
}

function decodePubSubData(data: string): Record<string, unknown> | null {
  if (!data || data.length > MAX_MESSAGE_BYTES * 2) return null;
  try {
    const binary = atob(data);
    if (binary.length > MAX_MESSAGE_BYTES) return null;
    const jsonText = new TextDecoder("utf-8", { fatal: true }).decode(
      Uint8Array.from(binary, (character) => character.charCodeAt(0)),
    );
    const value: unknown = JSON.parse(jsonText);
    return isRecord(value) ? value : null;
  } catch {
    return null;
  }
}

function canonicalTerminalResult(result: BeginEventResult): Response {
  const outcome = result.receiptStatus === "REJECTED" ? "REJECTED" : "COMPLETED";
  return json({ outcome, duplicate: true, code: result.safeResultCode ?? outcome });
}

export function createPlayRtdnHandler(
  dependencies: PlayRtdnDependencies,
  config: PlayRtdnConfig,
) {
  return async (request: Request): Promise<Response> => {
    if (!new URL(request.url).pathname.endsWith("/play-rtdn")) {
      return errorResponse("NOT_FOUND", false, 404);
    }
    if (request.method !== "POST") return errorResponse("METHOD_NOT_ALLOWED", false, 405);

    const authorization = request.headers.get("authorization") ?? "";
    const token = /^Bearer\s+(\S+)$/i.exec(authorization)?.[1];
    if (!token) return errorResponse("UNAUTHENTICATED", false, 401);
    let identityValid = false;
    try {
      identityValid = await dependencies.verifyPubSubIdentity(
        token,
        config.expectedAudience,
        config.expectedServiceAccount,
      );
    } catch {
      identityValid = false;
    }
    if (!identityValid) return errorResponse("UNAUTHENTICATED", false, 401);

    const contentType = request.headers.get("content-type")?.split(";")[0].trim().toLowerCase();
    if (contentType !== "application/json") return errorResponse("INVALID_REQUEST", false, 400);
    const contentLength = Number(request.headers.get("content-length") ?? 0);
    if (contentLength > MAX_BODY_BYTES) return errorResponse("INVALID_REQUEST", false, 400);

    let rawBody: string;
    try {
      rawBody = await request.text();
    } catch {
      return errorResponse("INVALID_REQUEST", false, 400);
    }
    if (new TextEncoder().encode(rawBody).byteLength > MAX_BODY_BYTES) {
      return errorResponse("INVALID_REQUEST", false, 400);
    }
    let envelope: unknown;
    try {
      envelope = JSON.parse(rawBody);
    } catch {
      return errorResponse("INVALID_REQUEST", false, 400);
    }
    if (!isRecord(envelope) || !isRecord(envelope.message) ||
      typeof envelope.subscription !== "string" ||
      envelope.subscription !== config.subscriptionIdentity ||
      typeof envelope.message.messageId !== "string" ||
      envelope.message.messageId.length < 1 || envelope.message.messageId.length > 256 ||
      typeof envelope.message.data !== "string") {
      return errorResponse("INVALID_REQUEST", false, 400);
    }
    const payload = decodePubSubData(envelope.message.data);
    if (!payload || payload.packageName !== config.packageName) {
      return errorResponse("INVALID_NOTIFICATION", false, 400);
    }
    const notification = parseNotification(payload);
    if (!notification) return errorResponse("UNSUPPORTED_NOTIFICATION", false, 400);

    try {
      const purchaseTokenHash = notification.purchaseToken === null
        ? null
        : await sha256Hex(notification.purchaseToken);
      const eventIdentityHash = await sha256Hex(
        `${config.subscriptionIdentity}\n${envelope.message.messageId}`,
      );
      const receipt = await dependencies.beginEvent({
        eventIdentityHash,
        providerMessageId: envelope.message.messageId,
        purchaseTokenHash,
        notificationType: notification.notificationType,
      });

      if (receipt.receiptStatus === "COMPLETED" || receipt.receiptStatus === "REJECTED") {
        return canonicalTerminalResult(receipt);
      }
      if (receipt.receiptStatus === "WAITING_FOR_TOKEN") {
        return json({ outcome: "WAITING_FOR_TOKEN", retryable: true, code: receipt.safeResultCode ?? "TOKEN_UNAVAILABLE" });
      }
      if (
        !notification.purchaseToken || !purchaseTokenHash || !receipt.userId ||
        !receipt.storeProductId || !receipt.billingProductId || !receipt.jobId || !receipt.leaseOwner
      ) {
        return errorResponse("PROCESSING_UNAVAILABLE", true, 503);
      }
      if (receipt.receiptStatus !== "PROCESSING") {
        return errorResponse("PROCESSING_UNAVAILABLE", true, 503);
      }

      let verification: RtdnOutcome | {
        outcome: RtdnOutcome;
        finalized: boolean;
        safeResultCode?: string;
      };
      try {
        verification = await dependencies.verifyCurrentPurchase({
          userId: receipt.userId,
          storeProductId: receipt.storeProductId,
          billingProductId: receipt.billingProductId,
          purchaseToken: notification.purchaseToken,
          purchaseTokenHash,
          receiptId: receipt.receiptId,
          jobId: receipt.jobId,
          leaseOwner: receipt.leaseOwner,
          notificationType: notification.notificationType,
        });
      } catch {
        verification = "RETRYABLE";
      }

      const result = typeof verification === "string" ? verification : verification.outcome;
      const finalized = typeof verification !== "string" && verification.finalized;
      const outcome = result === "VERIFIED" || result === "PENDING"
        ? "COMPLETED"
        : result;
      const safeResultCode = typeof verification !== "string" && verification.safeResultCode
        ? verification.safeResultCode
        : SAFE_RESULT_CODES.has(result)
        ? result
        : "VERIFIER_UNAVAILABLE";
      if (!finalized) {
        await dependencies.finishEvent({
          receiptId: receipt.receiptId,
          jobId: receipt.jobId,
          leaseOwner: receipt.leaseOwner,
          outcome,
          safeResultCode,
        });
      }
      if (outcome === "RETRYABLE") return errorResponse(safeResultCode, true, 503);
      return json({ outcome, duplicate: receipt.duplicate, code: safeResultCode });
    } catch {
      return errorResponse("PERSISTENCE_UNAVAILABLE", true, 503);
    }
  };
}

if (import.meta.main) {
  const required = (name: string): string => {
    const value = Deno.env.get(name);
    if (!value) throw new Error(`Missing server configuration: ${name}`);
    return value;
  };
  const rtdnStore = createRtdnStore(Deno.env);
  const purchaseStore = createPurchaseStore(Deno.env);
  const googlePlay = createGooglePlayApi(Deno.env);
  const issueOfflineGrant = createOfflineEntitlementGrantIssuer(Deno.env);
  const handler = createPlayRtdnHandler({
    ...rtdnStore,
    verifyPubSubIdentity: async (token, audience, serviceAccount) =>
      await verifyPubSubOidcToken(token, { audience, serviceAccount }),
    verifyCurrentPurchase: async (input) => {
      try {
        const product = await purchaseStore.lookupProductById(input.billingProductId);
        if (!product || product.storeProductId !== input.storeProductId) {
          return { outcome: "REJECTED", finalized: false, safeResultCode: "UNKNOWN_PRODUCT" };
        }
        const result = await verifyCurrentPurchaseForOwner(
          { ...purchaseStore, ...googlePlay, issueOfflineGrant },
          {
            userId: input.userId,
            product,
            purchaseToken: input.purchaseToken,
            rtdnContext: {
              receiptId: input.receiptId,
              jobId: input.jobId,
              leaseOwner: input.leaseOwner,
            },
          },
        );
        return {
          outcome: result.outcome === "TOKEN_ACCOUNT_CONFLICT" ? "REJECTED" : result.outcome,
          finalized: result.rtdnFinalized,
          ...(result.outcome === "TOKEN_ACCOUNT_CONFLICT" ? { safeResultCode: "TOKEN_ACCOUNT_CONFLICT" } : {}),
        };
      } catch (error) {
        if (error instanceof ProviderRejectedError) {
          return { outcome: "REJECTED", finalized: false, safeResultCode: "PURCHASE_REJECTED" };
        }
        if (error instanceof ProviderUnavailableError) {
          return { outcome: "RETRYABLE", finalized: false, safeResultCode: "VERIFIER_UNAVAILABLE" };
        }
        return { outcome: "RETRYABLE", finalized: false, safeResultCode: "VERIFIER_UNAVAILABLE" };
      }
    },
  }, {
    expectedAudience: required("PUBSUB_OIDC_AUDIENCE"),
    expectedServiceAccount: required("PUBSUB_SERVICE_ACCOUNT_EMAIL"),
    packageName: required("GOOGLE_PLAY_PACKAGE_NAME"),
    subscriptionIdentity: required("PUBSUB_SUBSCRIPTION_IDENTITY"),
  });
  Deno.serve(handler);
}
