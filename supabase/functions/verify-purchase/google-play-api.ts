import type { ProviderPurchase } from "./index.ts";

export type GoogleProduct = {
  storeProductId: string;
  basePlanId: string | null;
  planType: string;
};

type Env = { get(name: string): string | undefined };

export class ProviderUnavailableError extends Error {}
export class ProviderRejectedError extends Error {}

const GOOGLE_SCOPE = "https://www.googleapis.com/auth/androidpublisher";
const TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
const PUBLISHER_ENDPOINT =
  "https://androidpublisher.googleapis.com/androidpublisher/v3";

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (let index = 0; index < bytes.length; index += 1) {
    binary += String.fromCharCode(bytes[index]);
  }
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll(
    "=",
    "",
  );
}

function encodeJson(value: unknown): string {
  return base64Url(new TextEncoder().encode(JSON.stringify(value)));
}

function parseServiceAccount(
  env: Env,
): { clientEmail: string; privateKey: string } {
  const raw = env.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON");
  if (raw) {
    try {
      const parsed = JSON.parse(raw) as {
        client_email?: string;
        private_key?: string;
      };
      if (parsed.client_email && parsed.private_key) {
        return {
          clientEmail: parsed.client_email,
          privateKey: parsed.private_key,
        };
      }
    } catch {
      throw new ProviderUnavailableError(
        "Google Play service account configuration is invalid",
      );
    }
  }
  const clientEmail = env.get("GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL");
  const privateKey = env.get("GOOGLE_PLAY_SERVICE_ACCOUNT_PRIVATE_KEY");
  if (!clientEmail || !privateKey) {
    throw new ProviderUnavailableError(
      "Google Play service account is not configured",
    );
  }
  return { clientEmail, privateKey };
}

async function importPrivateKey(value: string): Promise<CryptoKey> {
  const pem = value.replaceAll("\\n", "\n").replace(
    /-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g,
    "",
  );
  try {
    const binary = atob(pem);
    const der = Uint8Array.from(binary, (character) => character.charCodeAt(0));
    return await crypto.subtle.importKey(
      "pkcs8",
      der,
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["sign"],
    );
  } catch {
    throw new ProviderUnavailableError(
      "Google Play service account private key is invalid",
    );
  }
}

function dateOrNull(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const timestamp = Date.parse(value);
  return Number.isFinite(timestamp) ? new Date(timestamp).toISOString() : null;
}

export function mapSubscriptionLifecycle(
  state: string,
  expiryTime: string | null,
  nowEpochMillis = Date.now(),
): string {
  switch (state) {
    case "SUBSCRIPTION_STATE_ACTIVE":
    case "ACTIVE":
      return expiryTime && Date.parse(expiryTime) <= nowEpochMillis
        ? "EXPIRED"
        : "ACTIVE";
    case "SUBSCRIPTION_STATE_IN_GRACE_PERIOD":
    case "IN_GRACE_PERIOD":
      return "IN_GRACE_PERIOD";
    case "SUBSCRIPTION_STATE_ON_HOLD":
    case "ON_HOLD":
      return "ACCOUNT_HOLD";
    case "SUBSCRIPTION_STATE_PAUSED":
    case "PAUSED":
      return "PAUSED";
    case "SUBSCRIPTION_STATE_CANCELED":
    case "CANCELED":
      return expiryTime && Date.parse(expiryTime) > nowEpochMillis
        ? "CANCELED_ACTIVE"
        : "EXPIRED";
    case "SUBSCRIPTION_STATE_PENDING":
    case "SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED":
    case "PENDING":
      return "PENDING";
    case "SUBSCRIPTION_STATE_EXPIRED":
    case "EXPIRED":
      return "EXPIRED";
    default:
      return "REVOKED";
  }
}

export function mapOneTimeLifecycle(state: string): string {
  switch (state) {
    case "PENDING":
      return "PENDING";
    case "PURCHASED":
      return "ACTIVE";
    case "CANCELLED":
    default:
      return "REVOKED";
  }
}

export function createGooglePlayApi(env: Env) {
  const packageName = env.get("GOOGLE_PLAY_PACKAGE_NAME") ?? "com.kipu.app";
  let cachedAccessToken: { value: string; expiresAt: number } | null = null;

  async function accessToken(): Promise<string> {
    if (
      cachedAccessToken && cachedAccessToken.expiresAt > Date.now() + 60_000
    ) return cachedAccessToken.value;
    const account = parseServiceAccount(env);
    const now = Math.floor(Date.now() / 1000);
    const header = encodeJson({ alg: "RS256", typ: "JWT" });
    const claims = encodeJson({
      iss: account.clientEmail,
      scope: GOOGLE_SCOPE,
      aud: TOKEN_ENDPOINT,
      iat: now,
      exp: now + 3600,
    });
    const unsigned = `${header}.${claims}`;
    const signature = await crypto.subtle.sign(
      "RSASSA-PKCS1-v1_5",
      await importPrivateKey(account.privateKey),
      new TextEncoder().encode(unsigned),
    );
    const assertion = `${unsigned}.${base64Url(new Uint8Array(signature))}`;
    let response: Response;
    try {
      response = await fetch(TOKEN_ENDPOINT, {
        method: "POST",
        signal: AbortSignal.timeout(10_000),
        headers: { "content-type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
          grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
          assertion,
        }),
      });
    } catch {
      throw new ProviderUnavailableError(
        "Google OAuth endpoint is unavailable",
      );
    }
    if (!response.ok) {
      throw new ProviderUnavailableError("Google OAuth token exchange failed");
    }
    const tokenPayload = await response.json().catch(() => null) as {
      access_token?: string;
      expires_in?: number;
    } | null;
    if (!tokenPayload?.access_token) {
      throw new ProviderUnavailableError(
        "Google OAuth token response is invalid",
      );
    }
    cachedAccessToken = {
      value: tokenPayload.access_token,
      expiresAt: Date.now() +
        Math.max(60, tokenPayload.expires_in ?? 3600) * 1000,
    };
    return cachedAccessToken.value;
  }

  async function googleRequest(
    url: string,
    init: RequestInit = {},
  ): Promise<Record<string, unknown>> {
    let response: Response;
    try {
      response = await fetch(url, {
        ...init,
        signal: init.signal ?? AbortSignal.timeout(10_000),
        headers: {
          authorization: `Bearer ${await accessToken()}`,
          accept: "application/json",
          ...(init.body ? { "content-type": "application/json" } : {}),
          ...init.headers,
        },
      });
    } catch (error) {
      if (error instanceof ProviderUnavailableError) throw error;
      throw new ProviderUnavailableError("Google Play API is unavailable");
    }
    if (response.ok) {
      if (response.status === 204) return {};
      return await response.json().catch(() => ({})) as Record<string, unknown>;
    }
    if (
      response.status === 429 || response.status >= 500 ||
      response.status === 401 || response.status === 403
    ) {
      throw new ProviderUnavailableError(
        "Google Play API is temporarily unavailable",
      );
    }
    throw new ProviderRejectedError(
      "Google Play did not recognize this purchase",
    );
  }

  async function verifyPurchase(
    product: GoogleProduct,
    purchaseToken: string,
  ): Promise<ProviderPurchase> {
    const encodedToken = encodeURIComponent(purchaseToken);
    if (product.planType === "PRO_LIFETIME") {
      const details = await googleRequest(
        `${PUBLISHER_ENDPOINT}/applications/${
          encodeURIComponent(packageName)
        }/purchases/productsv2/tokens/${encodedToken}`,
      );
      const lineItems = Array.isArray(details.productLineItem)
        ? details.productLineItem as Record<string, unknown>[]
        : [];
      const matching = lineItems.find((item) =>
        item.productId === product.storeProductId
      );
      if (!matching) {
        throw new ProviderRejectedError(
          "Google Play product does not match the catalog item",
        );
      }
      const purchaseState = typeof details.purchaseState === "string"
        ? details.purchaseState
        : "";
      const normalized = mapOneTimeLifecycle(purchaseState);
      const acknowledgmentState =
        details.acknowledgementState === "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED"
          ? "ACKNOWLEDGED"
          : "PENDING";
      const pending = normalized === "PENDING";
      return {
        purchaseState: pending
          ? "PENDING"
          : purchaseState === "CANCELLED"
          ? "CANCELLED"
          : "PURCHASED",
        entitlementState: pending ? null : normalized,
        acknowledgementState: pending ? "PENDING" : acknowledgmentState,
        startsAt: dateOrNull(details.startTime) ?? new Date().toISOString(),
        expiresAt: null,
        orderId: typeof details.orderId === "string" ? details.orderId : null,
        willRenew: false,
        sanitizedPayload: {
          provider: "GOOGLE_PLAY",
          productId: product.storeProductId,
          purchaseState,
          acknowledgementState: acknowledgmentState,
        },
      };
    }

    const details = await googleRequest(
      `${PUBLISHER_ENDPOINT}/applications/${
        encodeURIComponent(packageName)
      }/purchases/subscriptionsv2/tokens/${encodedToken}`,
    );
    const state = typeof details.subscriptionState === "string"
      ? details.subscriptionState
      : "";
    const lineItems = Array.isArray(details.lineItems)
      ? details.lineItems as Record<string, unknown>[]
      : [];
    const matching = lineItems.find((item) => {
      const offer = item.offerDetails as Record<string, unknown> | undefined;
      return item.productId === product.storeProductId &&
        (!product.basePlanId || offer?.basePlanId === product.basePlanId);
    });
    if (!matching) {
      throw new ProviderRejectedError(
        "Google Play product or base plan does not match the catalog item",
      );
    }
    const expiresAt = dateOrNull(matching.expiryTime);
    const lifecycle = mapSubscriptionLifecycle(state, expiresAt);
    const pending = lifecycle === "PENDING";
    const autoRenewingPlan = matching.autoRenewingPlan as
      | Record<string, unknown>
      | undefined;
    const acknowledgmentState =
      details.acknowledgementState === "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED"
        ? "ACKNOWLEDGED"
        : "PENDING";
    return {
      purchaseState: pending
        ? "PENDING"
        : state === "SUBSCRIPTION_STATE_CANCELED"
        ? "CANCELLED"
        : "PURCHASED",
      entitlementState: pending ? null : lifecycle,
      acknowledgementState: pending ? "PENDING" : acknowledgmentState,
      startsAt: dateOrNull(matching.startTime) ?? dateOrNull(details.startTime),
      expiresAt: pending ? null : expiresAt,
      orderId: typeof details.latestOrderId === "string"
        ? details.latestOrderId
        : null,
      willRenew: autoRenewingPlan?.autoRenewEnabled === true,
      sanitizedPayload: {
        provider: "GOOGLE_PLAY",
        productId: product.storeProductId,
        basePlanId: product.basePlanId,
        subscriptionState: state,
        acknowledgementState: acknowledgmentState,
        willRenew: autoRenewingPlan?.autoRenewEnabled === true,
      },
    };
  }

  async function acknowledgePurchase(
    product: GoogleProduct,
    purchaseToken: string,
  ): Promise<void> {
    const encodedPackage = encodeURIComponent(packageName);
    const encodedToken = encodeURIComponent(purchaseToken);
    const path = product.planType === "PRO_LIFETIME"
      ? `purchases/products/${
        encodeURIComponent(product.storeProductId)
      }/tokens/${encodedToken}:acknowledge`
      : `purchases/subscriptions/${
        encodeURIComponent(product.storeProductId)
      }/tokens/${encodedToken}:acknowledge`;
    await googleRequest(
      `${PUBLISHER_ENDPOINT}/applications/${encodedPackage}/${path}`,
      {
        method: "POST",
        body: "{}",
      },
    );
  }

  return { verifyPurchase, acknowledgePurchase };
}
