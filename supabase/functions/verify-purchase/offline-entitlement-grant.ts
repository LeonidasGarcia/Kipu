export type OfflineEntitlementGrant = {
  payload: string;
  signature: string;
  keyId: string;
};

export type OfflineGrantIssueInput = {
  userId: string;
  installationPublicKey?: string;
  effectivePremium: boolean;
  entitlementEndsAt: string | null;
};

type Env = { get(name: string): string | undefined };
type Clock = () => Date;

const POLICY_VERSION = 1;
const GRANT_VERSION = 1;
const OFFLINE_CONCESSION_MILLIS = 72 * 60 * 60 * 1_000;

function encodeBase64Url(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  const buffer = new ArrayBuffer(bytes.byteLength);
  new Uint8Array(buffer).set(bytes);
  return buffer;
}

function decodeStandardBase64(value: string): Uint8Array | null {
  if (!/^[A-Za-z0-9+/]+={0,2}$/.test(value) || value.length > 512) return null;
  try {
    const binary = atob(value);
    const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
    const canonical = btoa(String.fromCharCode(...bytes));
    return canonical.replace(/=+$/g, "") === value.replace(/=+$/g, "")
      ? bytes
      : null;
  } catch {
    return null;
  }
}

function parseJwk(value: string): JsonWebKey | null {
  try {
    const parsed: unknown = JSON.parse(value);
    if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) return null;
    const jwk = parsed as JsonWebKey;
    if (jwk.kty !== "EC" || jwk.crv !== "P-256" || !jwk.d || !jwk.x || !jwk.y) return null;
    return jwk;
  } catch {
    return null;
  }
}

/** Signs a device-bound grant. Missing/malformed secrets or key material fail closed. */
export function createOfflineEntitlementGrantIssuer(
  env: Env,
  clock: Clock = () => new Date(),
): (input: OfflineGrantIssueInput) => Promise<OfflineEntitlementGrant | null> {
  let signingKeyPromise: Promise<CryptoKey | null> | null = null;

  return async (input) => {
    if (!input.effectivePremium || !input.installationPublicKey) return null;

    const keyId = env.get("ENTITLEMENT_GRANT_KEY_ID");
    const privateJwk = env.get("ENTITLEMENT_GRANT_PRIVATE_JWK");
    if (!keyId || !privateJwk) return null;

    const publicDer = decodeStandardBase64(input.installationPublicKey);
    if (!publicDer) return null;

    let installationPublicKey: CryptoKey;
    try {
      installationPublicKey = await crypto.subtle.importKey(
        "spki",
        toArrayBuffer(publicDer),
        { name: "ECDSA", namedCurve: "P-256" },
        false,
        ["verify"],
      );
      if (installationPublicKey.type !== "public") return null;
    } catch {
      return null;
    }

    const now = clock();
    const serverVerifiedAt = now.getTime();
    if (!Number.isFinite(serverVerifiedAt) || !input.userId) return null;
    let entitlementEndsAt: number | null = null;
    if (input.entitlementEndsAt !== null) {
      entitlementEndsAt = Date.parse(input.entitlementEndsAt);
      if (!Number.isFinite(entitlementEndsAt) || entitlementEndsAt <= serverVerifiedAt) return null;
    }
    const notAfter = Math.min(
      serverVerifiedAt + OFFLINE_CONCESSION_MILLIS,
      entitlementEndsAt ?? Number.POSITIVE_INFINITY,
    );
    if (!Number.isFinite(notAfter) || notAfter <= serverVerifiedAt) return null;

    const installationKeyThumbprint = encodeBase64Url(
      new Uint8Array(await crypto.subtle.digest("SHA-256", toArrayBuffer(publicDer))),
    );
    const payloadObject = {
      version: GRANT_VERSION,
      keyId,
      grantId: crypto.randomUUID(),
      userId: input.userId,
      installationKeyThumbprint,
      policyVersion: POLICY_VERSION,
      tier: "PREMIUM",
      serverVerifiedAt: new Date(serverVerifiedAt).toISOString(),
      entitlementEndsAt: entitlementEndsAt === null
        ? null
        : new Date(entitlementEndsAt).toISOString(),
      notAfter: new Date(notAfter).toISOString(),
    };
    const payloadBytes = new TextEncoder().encode(JSON.stringify(payloadObject));

    signingKeyPromise ??= (async () => {
      const jwk = parseJwk(privateJwk);
      if (!jwk) return null;
      try {
        return await crypto.subtle.importKey(
          "jwk",
          jwk,
          { name: "ECDSA", namedCurve: "P-256" },
          false,
          ["sign"],
        );
      } catch {
        return null;
      }
    })();
    const signingKey = await signingKeyPromise;
    if (!signingKey) return null;

    try {
      const signature = new Uint8Array(await crypto.subtle.sign(
        { name: "ECDSA", hash: "SHA-256" },
        signingKey,
        toArrayBuffer(payloadBytes),
      ));
      if (signature.length !== 64) return null;
      return {
        payload: encodeBase64Url(payloadBytes),
        signature: encodeBase64Url(signature),
        keyId,
      };
    } catch {
      return null;
    }
  };
}
