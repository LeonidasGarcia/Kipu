import { createOfflineEntitlementGrantIssuer } from "./offline-entitlement-grant.ts";

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

function base64(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes));
}

function base64Url(bytes: Uint8Array): string {
  return base64(bytes).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

async function createSignerFixture() {
  const keys = await crypto.subtle.generateKey(
    { name: "ECDSA", namedCurve: "P-256" },
    true,
    ["sign", "verify"],
  ) as CryptoKeyPair;
  const privateJwk = await crypto.subtle.exportKey("jwk", keys.privateKey);
  const publicDer = new Uint8Array(await crypto.subtle.exportKey("spki", keys.publicKey));
  const secrets = new Map([
    ["ENTITLEMENT_GRANT_PRIVATE_JWK", JSON.stringify(privateJwk)],
    ["ENTITLEMENT_GRANT_KEY_ID", "test-key-1"],
  ]);
  return {
    issuer: createOfflineEntitlementGrantIssuer(
      { get: (name: string) => secrets.get(name) },
      () => new Date("2026-10-02T12:00:00.000Z"),
    ),
    publicKey: base64(publicDer),
    verificationKey: keys.publicKey,
  };
}

const input = {
  userId: "73000000-0000-4000-8000-000000000001",
  installationPublicKey: "",
  effectivePremium: true,
  entitlementEndsAt: "2026-10-03T12:00:00.000Z",
};

Deno.test("signs the exact payload bytes and bounds grant by commercial end", async () => {
  const fixture = await createSignerFixture();
  const grant = await fixture.issuer({ ...input, installationPublicKey: fixture.publicKey });
  assert(grant !== null, "expected a signed grant");
  assert(grant.keyId === "test-key-1", "unexpected key id");
  const payloadBytes = Uint8Array.from(
    atob(grant.payload.replace(/-/g, "+").replace(/_/g, "/")),
    (char) => char.charCodeAt(0),
  );
  const signature = Uint8Array.from(
    atob(grant.signature.replace(/-/g, "+").replace(/_/g, "/")),
    (char) => char.charCodeAt(0),
  );
  assert(signature.length === 64, "ES256 must use fixed-width r||s bytes");
  assert(
    await crypto.subtle.verify(
      { name: "ECDSA", hash: "SHA-256" },
      fixture.verificationKey,
      signature,
      payloadBytes,
    ),
    "signature did not verify over the exact decoded payload",
  );
  const claims = JSON.parse(new TextDecoder().decode(payloadBytes));
  assert(claims.notAfter === "2026-10-03T12:00:00.000Z", "commercial end must be earlier than 72h");
  assert(claims.installationKeyThumbprint === base64Url(new Uint8Array(await crypto.subtle.digest("SHA-256", Uint8Array.from(atob(fixture.publicKey), (c) => c.charCodeAt(0))))), "grant must bind the public key thumbprint");
});

Deno.test("Lifetime grant is still capped at 72 hours", async () => {
  const fixture = await createSignerFixture();
  const grant = await fixture.issuer({
    ...input,
    installationPublicKey: fixture.publicKey,
    entitlementEndsAt: null,
  });
  assert(grant !== null, "expected a signed grant");
  const claims = JSON.parse(atob(grant.payload.replace(/-/g, "+").replace(/_/g, "/")));
  assert(claims.entitlementEndsAt === null, "Lifetime commercial end must stay null");
  assert(claims.notAfter === "2026-10-05T12:00:00.000Z", "Lifetime must expire at 72 hours");
});

Deno.test("does not issue a grant without effective Premium, install key, or signing secrets", async () => {
  const fixture = await createSignerFixture();
  assert(
    await fixture.issuer({ ...input, installationPublicKey: fixture.publicKey, effectivePremium: false }) === null,
    "Free must not receive a grant",
  );
  assert(await fixture.issuer(input) === null, "legacy callers without a public key must not receive a grant");
  const unconfigured = createOfflineEntitlementGrantIssuer({ get: () => undefined });
  assert(
    await unconfigured({ ...input, installationPublicKey: fixture.publicKey }) === null,
    "missing signing secrets must fail closed",
  );
});

Deno.test("rejects malformed installation public keys and expired commercial rights", async () => {
  const fixture = await createSignerFixture();
  assert(
    await fixture.issuer({ ...input, installationPublicKey: "not-a-key" }) === null,
    "malformed key must not produce a grant",
  );
  assert(
    await fixture.issuer({
      ...input,
      installationPublicKey: fixture.publicKey,
      entitlementEndsAt: "2026-10-02T11:59:59.000Z",
    }) === null,
    "already-ended entitlement must not produce a grant",
  );
});
