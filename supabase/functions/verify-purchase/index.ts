import {
  createGooglePlayApi,
  type GoogleProduct,
  ProviderRejectedError,
  ProviderUnavailableError,
} from "./google-play-api.ts";
import {
  createPurchaseStore,
  PersistenceUnavailableError,
} from "./purchase-store.ts";
import {
  createOfflineEntitlementGrantIssuer,
  type OfflineEntitlementGrant,
} from "./offline-entitlement-grant.ts";

export { ProviderRejectedError, ProviderUnavailableError };

export type ProductRecord = GoogleProduct & {
  id: string;
};

export type ProviderPurchase = {
  purchaseState: "PURCHASED" | "PENDING" | "CANCELLED";
  entitlementState: string | null;
  acknowledgementState: "PENDING" | "ACKNOWLEDGED";
  startsAt: string | null;
  expiresAt: string | null;
  orderId: string | null;
  willRenew: boolean;
  sanitizedPayload: Record<string, unknown>;
};

export type PersistInput = {
  userId: string;
  storeProductId: string;
  purchaseTokenHash: string;
  orderId: string | null;
  purchaseState: ProviderPurchase["purchaseState"];
  entitlementState: string | null;
  acknowledgementState: ProviderPurchase["acknowledgementState"];
  startsAt: string | null;
  expiresAt: string | null;
  sanitizedPayload: Record<string, unknown>;
  restoreCandidate?: boolean;
  rtdnContext?: {
    receiptId: string;
    jobId: string;
    leaseOwner: string;
  };
};

export type PersistResult = {
  result: "VERIFIED" | "PENDING" | "TOKEN_ACCOUNT_CONFLICT";
  purchaseId: string | null;
  effectivePremium: boolean;
  effectiveExpiresAt: string | null;
};

export type Dependencies = {
  authenticate: (request: Request) => Promise<string | null>;
  lookupProduct: (
    productId: string,
    authorization: string,
  ) => Promise<ProductRecord | null>;
  verifyPurchase: (
    product: ProductRecord,
    purchaseToken: string,
  ) => Promise<ProviderPurchase>;
  persistPurchase: (input: PersistInput) => Promise<PersistResult>;
  claimAcknowledgement: (purchaseTokenHash: string) => Promise<boolean>;
  acknowledgePurchase: (
    product: ProductRecord,
    purchaseToken: string,
  ) => Promise<void>;
  completeAcknowledgement: (purchaseTokenHash: string) => Promise<void>;
  releaseAcknowledgement: (purchaseTokenHash: string) => Promise<void>;
  issueOfflineGrant?: (input: {
    userId: string;
    installationPublicKey?: string;
    effectivePremium: boolean;
    entitlementEndsAt: string | null;
  }) => Promise<OfflineEntitlementGrant | null>;
};

const MAX_BODY_BYTES = 8_192;
const SAFE_LIFECYCLE_STATES = new Set([
  "ACTIVE",
  "IN_GRACE_PERIOD",
  "ACCOUNT_HOLD",
  "CANCELED_ACTIVE",
  "EXPIRED",
  "REVOKED",
  "PAUSED",
  "PENDING",
]);

function json(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

function errorResponse(
  code: string,
  retryable: boolean,
  status: number,
): Response {
  return json({
    outcome: retryable ? "RETRYABLE" : "REJECTED",
    code,
    retryable,
  }, status);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function sanitizedEventPayload(
  value: Record<string, unknown>,
): Record<string, unknown> {
  const allowed = new Set([
    "provider",
    "productId",
    "basePlanId",
    "subscriptionState",
    "purchaseState",
    "acknowledgementState",
    "willRenew",
  ]);
  return Object.fromEntries(
    Object.entries(value).filter(([key, field]) =>
      allowed.has(key) &&
      (typeof field === "string" || typeof field === "boolean" ||
        field === null)
    ),
  );
}

async function sha256Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(value),
  );
  return Array.from(
    new Uint8Array(digest),
    (byte) => byte.toString(16).padStart(2, "0"),
  ).join("");
}

function effectiveResponse(
  product: ProductRecord,
  provider: ProviderPurchase,
  persisted: PersistResult,
  outcome: "VERIFIED" | "PENDING",
  offlineEntitlementGrant: OfflineEntitlementGrant | null = null,
) {
  return {
    outcome,
    purchaseId: persisted.purchaseId,
    productId: product.storeProductId,
    planType: product.planType,
    purchaseState: provider.purchaseState,
    entitlementState: provider.entitlementState,
    startsAt: provider.startsAt,
    expiresAt: provider.expiresAt,
    isLifetime: product.planType === "PRO_LIFETIME",
    willRenew: provider.willRenew,
    orderId: provider.orderId,
    effectivePremium: persisted.effectivePremium,
    effectiveExpiresAt: persisted.effectiveExpiresAt,
    offlineEntitlementGrant,
    acknowledgementState: provider.acknowledgementState,
  };
}

type CurrentPurchaseDependencies = Pick<
  Dependencies,
  | "verifyPurchase"
  | "persistPurchase"
  | "claimAcknowledgement"
  | "acknowledgePurchase"
  | "completeAcknowledgement"
  | "releaseAcknowledgement"
  | "issueOfflineGrant"
>;

export type CurrentPurchaseVerification = {
  outcome: "VERIFIED" | "PENDING" | "TOKEN_ACCOUNT_CONFLICT";
  product: ProductRecord;
  provider: ProviderPurchase;
  persisted: PersistResult;
  offlineEntitlementGrant: OfflineEntitlementGrant | null;
  rtdnFinalized: boolean;
};

/** Shared provider, ownership, acknowledgement, projection and grant boundary. */
export async function verifyCurrentPurchaseForOwner(
  dependencies: CurrentPurchaseDependencies,
  input: {
    userId: string;
    product: ProductRecord;
    purchaseToken: string;
    installationPublicKey?: string;
    rtdnContext?: PersistInput["rtdnContext"];
    restoreCandidate?: boolean;
  },
): Promise<CurrentPurchaseVerification> {
  const { product, purchaseToken, userId, installationPublicKey, rtdnContext, restoreCandidate } = input;
  if (!["PRO_MONTHLY", "PRO_ANNUAL", "PRO_LIFETIME"].includes(product.planType)) {
    throw new ProviderRejectedError("UNKNOWN_PRODUCT");
  }
  const provider = await dependencies.verifyPurchase(product, purchaseToken);
  if (provider.entitlementState !== null && !SAFE_LIFECYCLE_STATES.has(provider.entitlementState)) {
    throw new ProviderRejectedError("PURCHASE_REJECTED");
  }
  if (provider.purchaseState === "PENDING") {
    provider.entitlementState = null;
    provider.acknowledgementState = "PENDING";
    provider.expiresAt = null;
  }
  if (product.planType === "PRO_LIFETIME" && provider.expiresAt !== null) {
    throw new ProviderRejectedError("PURCHASE_REJECTED");
  }

  const purchaseTokenHash = await sha256Hex(purchaseToken);
  const persistInput: PersistInput = {
    userId,
    storeProductId: product.storeProductId,
    purchaseTokenHash,
    orderId: provider.orderId,
    purchaseState: provider.purchaseState,
    entitlementState: provider.entitlementState,
    acknowledgementState: provider.acknowledgementState,
    startsAt: provider.startsAt,
    expiresAt: provider.expiresAt,
    sanitizedPayload: sanitizedEventPayload(provider.sanitizedPayload),
    ...(rtdnContext ? { rtdnContext } : {}),
    ...(restoreCandidate ? { restoreCandidate: true } : {}),
  };

  if (provider.purchaseState === "PENDING") {
    const persisted = await dependencies.persistPurchase(persistInput);
    return {
      outcome: persisted.result === "TOKEN_ACCOUNT_CONFLICT" ? "TOKEN_ACCOUNT_CONFLICT" : "PENDING",
      product,
      provider,
      persisted,
      offlineEntitlementGrant: null,
      rtdnFinalized: Boolean(rtdnContext),
    };
  }

  let persisted: PersistResult;
  if (rtdnContext) {
    // RTDN already resolved this token hash to an existing owner. Claim/acknowledge
    // before the single atomic projection + receipt commit.
    if (provider.purchaseState === "PURCHASED" && provider.acknowledgementState === "PENDING") {
      const claimed = await dependencies.claimAcknowledgement(purchaseTokenHash);
      if (claimed) {
        try {
          await dependencies.acknowledgePurchase(product, purchaseToken);
          await dependencies.completeAcknowledgement(purchaseTokenHash);
          provider.acknowledgementState = "ACKNOWLEDGED";
        } catch {
          await dependencies.releaseAcknowledgement(purchaseTokenHash).catch(() => undefined);
        }
      }
    }
    persisted = await dependencies.persistPurchase({
      ...persistInput,
      acknowledgementState: provider.acknowledgementState,
    });
    if (persisted.result === "TOKEN_ACCOUNT_CONFLICT") {
      return { outcome: "TOKEN_ACCOUNT_CONFLICT", product, provider, persisted, offlineEntitlementGrant: null, rtdnFinalized: true };
    }
  } else {
    persisted = await dependencies.persistPurchase(persistInput);
    if (persisted.result === "TOKEN_ACCOUNT_CONFLICT") {
      return { outcome: "TOKEN_ACCOUNT_CONFLICT", product, provider, persisted, offlineEntitlementGrant: null, rtdnFinalized: false };
    }

    if (provider.purchaseState === "PURCHASED" && provider.acknowledgementState === "PENDING") {
      const claimed = await dependencies.claimAcknowledgement(purchaseTokenHash);
      if (claimed) {
        try {
          await dependencies.acknowledgePurchase(product, purchaseToken);
          await dependencies.completeAcknowledgement(purchaseTokenHash);
          provider.acknowledgementState = "ACKNOWLEDGED";
          persisted = await dependencies.persistPurchase({
            ...persistInput,
            acknowledgementState: "ACKNOWLEDGED",
          });
          if (persisted.result === "TOKEN_ACCOUNT_CONFLICT") {
            return { outcome: "TOKEN_ACCOUNT_CONFLICT", product, provider, persisted, offlineEntitlementGrant: null, rtdnFinalized: false };
          }
        } catch {
          await dependencies.releaseAcknowledgement(purchaseTokenHash).catch(() => undefined);
        }
      }
    }
  }

  const offlineEntitlementGrant = !rtdnContext &&
      provider.purchaseState === "PURCHASED" && persisted.effectivePremium &&
      typeof installationPublicKey === "string" && dependencies.issueOfflineGrant
    ? await dependencies.issueOfflineGrant({
      userId,
      installationPublicKey,
      effectivePremium: true,
      entitlementEndsAt: persisted.effectiveExpiresAt,
    }).catch(() => null)
    : null;
  return { outcome: "VERIFIED", product, provider, persisted, offlineEntitlementGrant, rtdnFinalized: Boolean(rtdnContext) };
}

export function createVerifyPurchaseHandler(dependencies: Dependencies) {
  return async (request: Request): Promise<Response> => {
    const path = new URL(request.url).pathname;
    if (path !== "/billing/verify" && !path.endsWith("/billing/verify")) {
      return errorResponse("NOT_FOUND", false, 404);
    }
    if (request.method !== "POST") {
      return errorResponse("METHOD_NOT_ALLOWED", false, 405);
    }
    const authorization = request.headers.get("authorization") ?? "";
    if (!/^Bearer\s+\S+$/i.test(authorization)) {
      return errorResponse("UNAUTHENTICATED", false, 401);
    }
    let userId: string | null;
    try {
      userId = await dependencies.authenticate(request);
    } catch {
      return errorResponse("PROVIDER_UNAVAILABLE", true, 503);
    }
    if (!userId) {
      return errorResponse("UNAUTHENTICATED", false, 401);
    }

    const contentType = request.headers.get("content-type")?.split(";")[0]
      .trim().toLowerCase();
    if (contentType !== "application/json") {
      return errorResponse("INVALID_REQUEST", false, 400);
    }
    let rawBody: string;
    try {
      rawBody = await request.text();
    } catch {
      return errorResponse("INVALID_REQUEST", false, 400);
    }
    if (new TextEncoder().encode(rawBody).byteLength > MAX_BODY_BYTES) {
      return errorResponse("INVALID_REQUEST", false, 400);
    }
    let payload: unknown;
    try {
      payload = JSON.parse(rawBody);
    } catch {
      return errorResponse("INVALID_REQUEST", false, 400);
    }
    if (
      !isRecord(payload) ||
      Object.keys(payload).some((key) =>
        !["productId", "purchaseToken", "installationPublicKey", "restoreCandidate"].includes(key)
      )
    ) {
      return json({ code: "INVALID_REQUEST", retryable: false }, 400);
    }
    const productId = payload.productId;
    const purchaseToken = payload.purchaseToken;
    const installationPublicKey = payload.installationPublicKey;
    const restoreCandidate = payload.restoreCandidate;
    if (
      typeof productId !== "string" || productId.length < 1 ||
      productId.length > 200 ||
      typeof purchaseToken !== "string" || purchaseToken.length < 1 ||
      purchaseToken.length > 4096 ||
      (installationPublicKey !== undefined &&
        (typeof installationPublicKey !== "string" || installationPublicKey.length > 512)) ||
      (restoreCandidate !== undefined && typeof restoreCandidate !== "boolean")
    ) {
      return json({ code: "INVALID_REQUEST", retryable: false }, 400);
    }

    try {
      const product = await dependencies.lookupProduct(
        productId,
        authorization,
      );
      if (!product) {
        return errorResponse("UNKNOWN_PRODUCT", false, 400);
      }
      const verification = await verifyCurrentPurchaseForOwner(dependencies, {
        userId,
        product,
        purchaseToken,
        ...(typeof installationPublicKey === "string" ? { installationPublicKey } : {}),
        ...(restoreCandidate === true ? { restoreCandidate: true } : {}),
      });
      if (verification.outcome === "TOKEN_ACCOUNT_CONFLICT") {
        return errorResponse("TOKEN_ACCOUNT_CONFLICT", false, 409);
      }
      return json(effectiveResponse(
        verification.product,
        verification.provider,
        verification.persisted,
        verification.outcome,
        verification.offlineEntitlementGrant,
      ));
    } catch (error) {
      if (error instanceof ProviderRejectedError) {
        return errorResponse("PURCHASE_REJECTED", false, 400);
      }
      if (error instanceof ProviderUnavailableError) {
        return errorResponse("PROVIDER_UNAVAILABLE", true, 503);
      }
      if (error instanceof PersistenceUnavailableError) {
        return errorResponse("PERSISTENCE_UNAVAILABLE", true, 503);
      }
      return errorResponse("PERSISTENCE_UNAVAILABLE", true, 503);
    }
  };
}

if (import.meta.main) {
  const store = createPurchaseStore(Deno.env);
  const google = createGooglePlayApi(Deno.env);
  const issueOfflineGrant = createOfflineEntitlementGrantIssuer(Deno.env);
  const handler = createVerifyPurchaseHandler({ ...store, ...google, issueOfflineGrant });
  Deno.serve(handler);
}
