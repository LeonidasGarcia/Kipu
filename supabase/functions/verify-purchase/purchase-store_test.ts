import { createPurchaseStore } from "./purchase-store.ts";

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("service role cannot substitute for the auth/catalog API key", () => {
  let rejected = false;
  try {
    createPurchaseStore({
      get: (name) =>
        ({
          SUPABASE_URL: "https://kipu.test",
          SUPABASE_SERVICE_ROLE_KEY: "server-only-service-key",
        } as Record<string, string>)[name],
    });
  } catch {
    rejected = true;
  }
  assert(rejected, "missing public API key must fail closed");
});

Deno.test("caller credentials stay on auth/catalog reads and server key stays on RPC writes", async () => {
  const requests: Array<{ url: string; headers: Headers; body: string }> = [];
  const fakeFetch: typeof fetch = async (input, init = {}) => {
    const headers = new Headers(init.headers);
    const body = typeof init.body === "string" ? init.body : "";
    const url = String(input);
    requests.push({ url, headers, body });
    if (url.endsWith("/auth/v1/user")) {
      return Response.json({ id: "73000000-0000-4000-8000-000000000001" });
    }
    if (url.includes("/rest/v1/billing_products?")) {
      return Response.json([{
        id: "kipu_monthly",
        store_product_id: "kipu_pro_monthly",
        base_plan_id: "monthly",
        plan_type: "PRO_MONTHLY",
      }]);
    }
    if (url.endsWith("/persist_verified_billing_purchase")) {
      return Response.json([{
        result: "VERIFIED",
        purchase_id: "purchase-id",
        effective_premium: true,
        effective_expires_at: "2026-10-26T12:00:00.000Z",
      }]);
    }
    if (url.endsWith("/claim_billing_purchase_acknowledgement")) {
      return Response.json(true);
    }
    return Response.json(true);
  };
  const store = createPurchaseStore({
    get: (name) =>
      ({
        SUPABASE_URL: "https://kipu.test",
        SUPABASE_ANON_KEY: "public-anon-key",
        SUPABASE_SERVICE_ROLE_KEY: "server-only-service-key",
      } as Record<string, string>)[name],
  }, fakeFetch);
  const callerRequest = new Request(
    "https://kipu.test/functions/v1/verify-purchase/billing/verify",
    {
      headers: { authorization: "Bearer user-jwt" },
    },
  );
  const userId = await store.authenticate(callerRequest);
  const product = await store.lookupProduct(
    "kipu_pro_monthly",
    "Bearer user-jwt",
  );
  const persisted = await store.persistPurchase({
    userId: userId!,
    storeProductId: "kipu_pro_monthly",
    purchaseTokenHash: "a".repeat(64),
    orderId: null,
    purchaseState: "PURCHASED",
    entitlementState: "ACTIVE",
    acknowledgementState: "PENDING",
    startsAt: "2026-09-26T12:00:00.000Z",
    expiresAt: "2026-10-26T12:00:00.000Z",
    sanitizedPayload: { provider: "GOOGLE_PLAY", purchaseState: "PURCHASED" },
  });

  assert(
    userId === "73000000-0000-4000-8000-000000000001",
    "auth owner comes from the verified Supabase user response",
  );
  assert(
    product?.storeProductId === "kipu_pro_monthly",
    "catalog data is resolved using the caller JWT",
  );
  assert(
    persisted.effectivePremium && persisted.effectiveExpiresAt !== null,
    "server writer result is returned to the handler",
  );
  const userReads = requests.filter((request) =>
    request.url.includes("/auth/v1/user") ||
    request.url.includes("/billing_products?")
  );
  assert(
    userReads.every((request) =>
      request.headers.get("authorization") === "Bearer user-jwt"
    ),
    "user-facing reads use only the user's JWT",
  );
  assert(
    userReads.every((request) =>
      request.headers.get("apikey") === "public-anon-key"
    ),
    "client-safe API key is used for auth/catalog reads",
  );
  const rpc = requests.find((request) =>
    request.url.endsWith("/persist_verified_billing_purchase")
  );
  assert(
    rpc?.headers.get("authorization") === "Bearer server-only-service-key",
    "only server RPCs use the service role credential",
  );
  assert(
    !rpc?.body.includes("purchaseToken"),
    "RPC persists no raw provider token",
  );
  assert(
    !requests.some((request) =>
      request.headers.get("authorization") ===
        "Bearer server-only-service-key" &&
      request.url.includes("/auth/v1/user")
    ),
    "service secret never verifies caller identity",
  );
});
