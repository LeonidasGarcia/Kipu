type GoogleJwk = JsonWebKey & { kid?: string; alg?: string; use?: string };

export type PubSubOidcOptions = {
  audience: string;
  serviceAccount: string;
  fetchImpl?: typeof fetch;
  nowSeconds?: number;
};

type KeyCacheEntry = { keys: GoogleJwk[]; expiresAt: number };

const GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
const MAX_TOKEN_LENGTH = 8_192;
const keyCache = new WeakMap<typeof fetch, KeyCacheEntry>();

function decodeBase64Url(value: string): Uint8Array | null {
  if (!/^[A-Za-z0-9_-]+$/.test(value)) return null;
  try {
    const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
    const binary = atob(normalized + "=".repeat((4 - normalized.length % 4) % 4));
    return Uint8Array.from(binary, (character) => character.charCodeAt(0));
  } catch {
    return null;
  }
}

function parseJson(value: Uint8Array): Record<string, unknown> | null {
  try {
    const parsed: unknown = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(value));
    return typeof parsed === "object" && parsed !== null && !Array.isArray(parsed)
      ? parsed as Record<string, unknown>
      : null;
  } catch {
    return null;
  }
}

function cacheDuration(response: Response): number {
  const value = response.headers.get("cache-control") ?? "";
  const maxAge = /(?:^|,)\s*max-age=(\d+)/i.exec(value)?.[1];
  const seconds = maxAge ? Number(maxAge) : 300;
  return Math.max(60, Math.min(Number.isFinite(seconds) ? seconds : 300, 21_600));
}

async function loadGoogleKeys(fetchImpl: typeof fetch, nowMs: number): Promise<GoogleJwk[]> {
  const cached = keyCache.get(fetchImpl);
  if (cached && cached.expiresAt > nowMs) return cached.keys;
  const response = await fetchImpl(GOOGLE_JWKS_URL, {
    signal: AbortSignal.timeout(4_000),
    headers: { accept: "application/json" },
  });
  if (!response.ok) throw new Error("Google OIDC keys unavailable");
  const payload: unknown = await response.json();
  if (typeof payload !== "object" || payload === null || !Array.isArray((payload as { keys?: unknown }).keys)) {
    throw new Error("Google OIDC key set is invalid");
  }
  const keys = (payload as { keys: unknown[] }).keys.filter((key): key is GoogleJwk =>
    typeof key === "object" && key !== null &&
    typeof (key as { kid?: unknown }).kid === "string" &&
    (key as { kty?: unknown }).kty === "RSA" &&
    ((key as { alg?: unknown }).alg === undefined || (key as { alg?: unknown }).alg === "RS256")
  );
  if (keys.length === 0) throw new Error("Google OIDC key set is empty");
  keyCache.set(fetchImpl, {
    keys,
    expiresAt: nowMs + cacheDuration(response) * 1_000,
  });
  return keys;
}

export async function verifyPubSubOidcToken(
  token: string,
  options: PubSubOidcOptions,
): Promise<boolean> {
  if (!token || token.length > MAX_TOKEN_LENGTH || !options.audience || !options.serviceAccount) {
    return false;
  }
  const segments = token.split(".");
  if (segments.length !== 3) return false;
  const headerBytes = decodeBase64Url(segments[0]);
  const payloadBytes = decodeBase64Url(segments[1]);
  const signature = decodeBase64Url(segments[2]);
  if (!headerBytes || !payloadBytes || !signature) return false;
  const header = parseJson(headerBytes);
  const claims = parseJson(payloadBytes);
  if (!header || !claims || header.alg !== "RS256" || typeof header.kid !== "string") return false;

  const nowSeconds = options.nowSeconds ?? Math.floor(Date.now() / 1_000);
  const issuer = claims.iss;
  const audience = claims.aud;
  const emailVerified = claims.email_verified === true || claims.email_verified === "true";
  if (
    (issuer !== "https://accounts.google.com" && issuer !== "accounts.google.com") ||
    audience !== options.audience ||
    claims.email !== options.serviceAccount ||
    !emailVerified ||
    typeof claims.exp !== "number" || claims.exp <= nowSeconds ||
    typeof claims.iat !== "number" || claims.iat > nowSeconds + 60 ||
    (typeof claims.nbf === "number" && claims.nbf > nowSeconds)
  ) {
    return false;
  }

  try {
    const keys = await loadGoogleKeys(options.fetchImpl ?? fetch, nowSeconds * 1_000);
    const jwk = keys.find((key) => key.kid === header.kid);
    if (!jwk) {
      keyCache.delete(options.fetchImpl ?? fetch);
      return false;
    }
    const publicKey = await crypto.subtle.importKey(
      "jwk",
      jwk,
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["verify"],
    );
    const signatureBytes = new Uint8Array(signature);
    return await crypto.subtle.verify(
      "RSASSA-PKCS1-v1_5",
      publicKey,
      signatureBytes.buffer as ArrayBuffer,
      new TextEncoder().encode(`${segments[0]}.${segments[1]}`),
    );
  } catch {
    return false;
  }
}
