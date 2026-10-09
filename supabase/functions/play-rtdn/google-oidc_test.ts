import { verifyPubSubOidcToken } from "./google-oidc.ts";

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

function base64Url(value: Uint8Array | string) {
  const bytes = typeof value === "string" ? new TextEncoder().encode(value) : value;
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

async function fixture() {
  const pair = await crypto.subtle.generateKey({
    name: "RSASSA-PKCS1-v1_5",
    modulusLength: 2048,
    publicExponent: new Uint8Array([1, 0, 1]),
    hash: "SHA-256",
  }, true, ["sign", "verify"]);
  const exported = await crypto.subtle.exportKey("jwk", pair.publicKey);
  const jwk = { ...exported, kid: "rtdn-test-key", alg: "RS256", use: "sig" };
  const fetchImpl: typeof fetch = async () => new Response(JSON.stringify({ keys: [jwk] }), {
    status: 200,
    headers: { "content-type": "application/json", "cache-control": "public, max-age=3600" },
  });
  const nowSeconds = 1_791_450_000;
  async function token(claims: Record<string, unknown> = {}, tamper = false) {
    const header = base64Url(JSON.stringify({ alg: "RS256", kid: "rtdn-test-key", typ: "JWT" }));
    const payload = base64Url(JSON.stringify({
      iss: "https://accounts.google.com",
      aud: "https://kipu.example/rtdn",
      email: "billing-push@kipu.iam.gserviceaccount.com",
      email_verified: true,
      exp: nowSeconds + 300,
      iat: nowSeconds,
      ...claims,
    }));
    const unsigned = `${header}.${payload}`;
    const signature = new Uint8Array(await crypto.subtle.sign(
      "RSASSA-PKCS1-v1_5",
      pair.privateKey,
      new TextEncoder().encode(unsigned),
    ));
    if (tamper) signature[0] ^= 1;
    return `${unsigned}.${base64Url(signature)}`;
  }
  return { fetchImpl, nowSeconds, token };
}

Deno.test("accepts a signed Pub/Sub token only for the configured audience and service account", async () => {
  const f = await fixture();
  const token = await f.token();
  const valid = await verifyPubSubOidcToken(token, {
    audience: "https://kipu.example/rtdn",
    serviceAccount: "billing-push@kipu.iam.gserviceaccount.com",
    fetchImpl: f.fetchImpl,
    nowSeconds: f.nowSeconds,
  });
  const wrongAudience = await verifyPubSubOidcToken(await f.token({ aud: "https://attacker.example" }), {
    audience: "https://kipu.example/rtdn",
    serviceAccount: "billing-push@kipu.iam.gserviceaccount.com",
    fetchImpl: f.fetchImpl,
    nowSeconds: f.nowSeconds,
  });
  const wrongEmail = await verifyPubSubOidcToken(await f.token({ email: "other@kipu.iam.gserviceaccount.com" }), {
    audience: "https://kipu.example/rtdn",
    serviceAccount: "billing-push@kipu.iam.gserviceaccount.com",
    fetchImpl: f.fetchImpl,
    nowSeconds: f.nowSeconds,
  });
  assert(valid, "valid Google OIDC token should pass");
  assert(!wrongAudience && !wrongEmail, "audience or caller mismatch must fail");
});

Deno.test("rejects bad signatures, unsupported issuers, unverified email and expired tokens", async () => {
  const f = await fixture();
  const options = {
    audience: "https://kipu.example/rtdn",
    serviceAccount: "billing-push@kipu.iam.gserviceaccount.com",
    fetchImpl: f.fetchImpl,
    nowSeconds: f.nowSeconds,
  };
  const badSignature = await verifyPubSubOidcToken(await f.token({}, true), options);
  const wrongIssuer = await verifyPubSubOidcToken(await f.token({ iss: "https://attacker.example" }), options);
  const unverifiedEmail = await verifyPubSubOidcToken(await f.token({ email_verified: false }), options);
  const expired = await verifyPubSubOidcToken(await f.token({ exp: f.nowSeconds }), options);
  const futureIssued = await verifyPubSubOidcToken(await f.token({ iat: f.nowSeconds + 120 }), options);
  assert(!badSignature, "invalid JWT signature must fail");
  assert(!wrongIssuer && !unverifiedEmail && !expired && !futureIssued, "claims outside policy must fail");
});

Deno.test("fails closed when the Google signing key endpoint is unavailable", async () => {
  const f = await fixture();
  const token = await f.token();
  const result = await verifyPubSubOidcToken(token, {
    audience: "https://kipu.example/rtdn",
    serviceAccount: "billing-push@kipu.iam.gserviceaccount.com",
    fetchImpl: async () => new Response("unavailable", { status: 503 }),
    nowSeconds: f.nowSeconds,
  });
  assert(!result, "key fetch failure must reject identity");
});
