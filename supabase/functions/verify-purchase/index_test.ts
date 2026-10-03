import {
  createVerifyPurchaseHandler,
  type Dependencies,
  type ProductRecord,
  type ProviderPurchase,
  ProviderUnavailableError,
} from "./index.ts";
import {
  mapOneTimeLifecycle,
  mapSubscriptionLifecycle,
} from "./google-play-api.ts";

const ownerA = "73000000-0000-4000-8000-000000000001";
const ownerB = "73000000-0000-4000-8000-000000000002";
const monthly: ProductRecord = {
  id: "kipu_monthly",
  storeProductId: "kipu_pro_monthly",
  basePlanId: "monthly",
  planType: "PRO_MONTHLY",
};
const lifetime: ProductRecord = {
  id: "kipu_lifetime",
  storeProductId: "kipu_pro_lifetime",
  basePlanId: null,
  planType: "PRO_LIFETIME",
};

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

async function body(response: Response) {
  return await response.json() as Record<string, unknown>;
}

function request(payload: unknown, init: RequestInit = {}) {
  return new Request(
    "http://localhost/functions/v1/verify-purchase/billing/verify",
    {
      method: "POST",
      headers: {
        authorization: "Bearer signed-user-jwt",
        "content-type": "application/json",
      },
      body: JSON.stringify(payload),
      ...init,
    },
  );
}

function harness(options: {
  owner?: string | null;
  product?: ProductRecord | null;
  provider?: ProviderPurchase;
  providerError?: Error;
  existingOwner?: string;
  issueOfflineGrant?: Dependencies["issueOfflineGrant"];
} = {}) {
  const rows = new Map<string, { owner: string; id: string; state: string }>();
  const eventPayloads: Record<string, unknown>[] = [];
  const calls = {
    verify: 0,
    persist: 0,
    acknowledge: 0,
    acknowledgementClaims: 0,
  };
  let acknowledgementState: "PENDING" | "ACKNOWLEDGING" | "ACKNOWLEDGED" =
    "PENDING";
  let provider = options.provider ?? {
    purchaseState: "PURCHASED",
    entitlementState: "ACTIVE",
    acknowledgementState: "PENDING",
    startsAt: "2026-09-26T12:00:00.000Z",
    expiresAt: "2026-10-26T12:00:00.000Z",
    orderId: "GPA.TEST-ORDER",
    willRenew: true,
    sanitizedPayload: { purchaseState: "PURCHASED" },
  } satisfies ProviderPurchase;
  const dependencies: Dependencies = {
    authenticate: async () =>
      options.owner === undefined ? ownerA : options.owner,
    lookupProduct: async (productId) =>
      productId === lifetime.storeProductId
        ? lifetime
        : productId === monthly.storeProductId
        ? monthly
        : options.product ?? null,
    verifyPurchase: async () => {
      calls.verify++;
      if (options.providerError) throw options.providerError;
      return provider;
    },
    persistPurchase: async (input) => {
      calls.persist++;
      eventPayloads.push(input.sanitizedPayload);
      const existing = rows.get(input.purchaseTokenHash);
      if (existing && existing.owner !== input.userId) {
        return {
          result: "TOKEN_ACCOUNT_CONFLICT",
          purchaseId: null,
          effectivePremium: false,
          effectiveExpiresAt: null,
        };
      }
      if (options.existingOwner && !existing) {
        rows.set(input.purchaseTokenHash, {
          owner: options.existingOwner,
          id: "existing-id",
          state: "ACTIVE",
        });
        return {
          result: "TOKEN_ACCOUNT_CONFLICT",
          purchaseId: null,
          effectivePremium: false,
          effectiveExpiresAt: null,
        };
      }
      const row = existing ??
        { owner: input.userId, id: "purchase-1", state: input.purchaseState };
      row.state = input.entitlementState ?? "NONE";
      rows.set(input.purchaseTokenHash, row);
      return {
        result: input.purchaseState === "PENDING" ? "PENDING" : "VERIFIED",
        purchaseId: row.id,
        effectivePremium: input.purchaseState === "PURCHASED" &&
          input.entitlementState === "ACTIVE",
        effectiveExpiresAt: input.expiresAt,
      };
    },
    claimAcknowledgement: async () => {
      calls.acknowledgementClaims++;
      if (acknowledgementState !== "PENDING") return false;
      acknowledgementState = "ACKNOWLEDGING";
      return true;
    },
    acknowledgePurchase: async () => {
      calls.acknowledge++;
      provider = { ...provider, acknowledgementState: "ACKNOWLEDGED" };
    },
    completeAcknowledgement: async () => {
      acknowledgementState = "ACKNOWLEDGED";
    },
    releaseAcknowledgement: async () => {
      if (acknowledgementState === "ACKNOWLEDGING") {
        acknowledgementState = "PENDING";
      }
    },
    issueOfflineGrant: options.issueOfflineGrant,
  };
  return {
    handler: createVerifyPurchaseHandler(dependencies),
    calls,
    rows,
    eventPayloads,
  };
}

Deno.test("requires a verified caller before product lookup or provider access", async () => {
  const h = harness({ owner: null });
  const response = await h.handler(
    request({ productId: monthly.storeProductId, purchaseToken: "raw-token" }),
  );
  assert(response.status === 401, "missing user identity must be rejected");
  assert(
    h.calls.verify === 0 && h.calls.persist === 0,
    "unauthenticated calls must not reach provider or persistence",
  );
});

Deno.test("validates method, JSON body, allowed keys, product and token", async () => {
  const h = harness();
  const method = await h.handler(
    new Request("http://localhost/billing/verify", {
      method: "GET",
      headers: { authorization: "Bearer jwt" },
    }),
  );
  assert(method.status === 405, "only POST is accepted");
  const invalid = await h.handler(
    request({
      productId: monthly.storeProductId,
      purchaseToken: "t",
      user_id: ownerB,
    }),
  );
  assert(
    invalid.status === 400,
    "client cannot supply user_id or unknown body fields",
  );
  const unknown = await h.handler(
    request({ productId: "unlisted", purchaseToken: "t" }),
  );
  assert(
    unknown.status === 400,
    "unlisted product must be rejected before Google lookup",
  );
});

Deno.test("maps Google subscription and one-time lifecycle states conservatively", () => {
  const now = Date.parse("2026-09-26T12:00:00.000Z");
  assert(
    mapSubscriptionLifecycle(
      "SUBSCRIPTION_STATE_CANCELED",
      "2026-10-26T12:00:00.000Z",
      now,
    ) === "CANCELED_ACTIVE",
    "cancellation before expiry retains the paid term",
  );
  assert(
    mapSubscriptionLifecycle(
      "SUBSCRIPTION_STATE_CANCELED",
      "2026-09-26T11:00:00.000Z",
      now,
    ) === "EXPIRED",
    "cancellation after expiry loses access",
  );
  assert(
    mapSubscriptionLifecycle(
      "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
      "2026-10-01T00:00:00.000Z",
      now,
    ) === "IN_GRACE_PERIOD",
    "Google grace period is preserved",
  );
  assert(
    mapSubscriptionLifecycle("SUBSCRIPTION_STATE_ON_HOLD", null, now) ===
      "ACCOUNT_HOLD",
    "hold denies access",
  );
  assert(
    mapOneTimeLifecycle("PENDING") === "PENDING",
    "one-time pending stays pending",
  );
  assert(
    mapOneTimeLifecycle("CANCELLED") === "REVOKED",
    "one-time cancellation/revocation denies access",
  );
});

Deno.test("PENDING is persisted without entitlement and is never acknowledged", async () => {
  const h = harness({
    provider: {
      purchaseState: "PENDING",
      entitlementState: null,
      acknowledgementState: "PENDING",
      startsAt: null,
      expiresAt: null,
      orderId: null,
      willRenew: false,
      sanitizedPayload: { purchaseState: "PENDING" },
    },
  });
  const response = await h.handler(
    request({
      productId: monthly.storeProductId,
      purchaseToken: "token-pending",
    }),
  );
  const result = await body(response);
  assert(
    response.status === 200 && result.outcome === "PENDING",
    "provider pending maps to UI pending",
  );
  assert(
    result.entitlementState === null && result.effectivePremium === false,
    "pending grants no Premium",
  );
  assert(result.offlineEntitlementGrant === null, "pending must not receive an offline grant");
  assert(h.calls.acknowledge === 0, "pending must not be acknowledged");
});

Deno.test("issues a device-bound offline grant only after effective verified Premium", async () => {
  const issued: Record<string, unknown>[] = [];
  const h = harness({
    issueOfflineGrant: async (input) => {
      issued.push(input);
      return { payload: "signed-payload", signature: "signature", keyId: "test-key" };
    },
  });
  const response = await h.handler(request({
    productId: monthly.storeProductId,
    purchaseToken: "verified-token",
    installationPublicKey: "cHVibGljLWtleQ==",
  }));
  const result = await body(response);

  assert(response.status === 200 && result.outcome === "VERIFIED", "purchase is verified");
  assert(issued.length === 1, "effective Premium triggers grant issuance");
  assert(issued[0].userId === ownerA, "grant owner comes from authenticated session");
  assert(issued[0].installationPublicKey === "cHVibGljLWtleQ==", "grant binds the submitted public key");
  assert(issued[0].effectivePremium === true, "only effective Premium is passed to signer");
  assert(result.offlineEntitlementGrant !== null, "signed grant is included in response");
});

Deno.test("legacy clients without an installation key remain verifiable without a grant", async () => {
  let issuerCalls = 0;
  const h = harness({
    issueOfflineGrant: async () => {
      issuerCalls++;
      return { payload: "payload", signature: "signature", keyId: "test-key" };
    },
  });
  const response = await h.handler(request({
    productId: monthly.storeProductId,
    purchaseToken: "legacy-client-token",
  }));
  const result = await body(response);

  assert(response.status === 200 && result.outcome === "VERIFIED", "legacy verification remains compatible");
  assert(issuerCalls === 0, "the handler does not request a grant without the install key");
  assert(result.offlineEntitlementGrant === null, "legacy clients cannot receive a device-bound grant");
});

Deno.test("provider outage has a distinct retryable response and no grant", async () => {
  const h = harness({
    providerError: new ProviderUnavailableError("Google unavailable"),
  });
  const response = await h.handler(
    request({
      productId: monthly.storeProductId,
      purchaseToken: "token-transient",
    }),
  );
  const result = await body(response);
  assert(
    response.status === 503 && result.outcome === "RETRYABLE",
    "provider failure is retryable, not a payment pending state",
  );
  assert(
    h.calls.persist === 0 && result.retryable === true,
    "provider outage cannot create an entitlement",
  );
});

Deno.test("token hash owner conflict rejects without reassigning or acknowledging", async () => {
  const h = harness({ owner: ownerB, existingOwner: ownerA });
  const response = await h.handler(
    request({
      productId: monthly.storeProductId,
      purchaseToken: "same-purchase-token",
    }),
  );
  const result = await body(response);
  assert(
    response.status === 409 && result.code === "TOKEN_ACCOUNT_CONFLICT",
    "a token cannot move between accounts",
  );
  assert(h.calls.acknowledge === 0, "foreign tokens are not acknowledged");
});

Deno.test("same-account retries are idempotent and ack only after durable verified persistence", async () => {
  const h = harness();
  for (let attempt = 0; attempt < 2; attempt++) {
    const response = await h.handler(
      request({
        productId: monthly.storeProductId,
        purchaseToken: "same-token",
      }),
    );
    const result = await body(response);
    assert(
      response.status === 200 && result.outcome === "VERIFIED",
      "same-owner retries remain successful",
    );
  }
  assert(h.rows.size === 1, "retry does not create another purchase row");
  assert(
    h.calls.acknowledge === 1,
    "acknowledged purchases are not acknowledged again",
  );
  assert(
    h.calls.persist === 3,
    "first persistence precedes acknowledgement and acknowledgement is persisted once",
  );
});

Deno.test("Lifetime is acknowledged as a non-consumable and has no expiry", async () => {
  const h = harness({
    provider: {
      purchaseState: "PURCHASED",
      entitlementState: "ACTIVE",
      acknowledgementState: "PENDING",
      startsAt: "2026-09-26T12:00:00.000Z",
      expiresAt: null,
      orderId: "GPA.LIFETIME",
      willRenew: false,
      sanitizedPayload: { purchaseState: "PURCHASED", isLifetime: true },
    },
  });
  const response = await h.handler(
    request({
      productId: lifetime.storeProductId,
      purchaseToken: "lifetime-token",
    }),
  );
  const result = await body(response);
  assert(
    response.status === 200 && result.outcome === "VERIFIED",
    "Lifetime is verified",
  );
  assert(
    result.expiresAt === null && result.isLifetime === true,
    "Lifetime never expires commercially",
  );
  assert(h.calls.acknowledge === 1, "Lifetime is acknowledged once");
  assert(!("consumePurchase" in h.calls), "Lifetime has no consume path");
});

Deno.test("concurrent same-account retries claim only one acknowledgement lease", async () => {
  const h = harness();
  const [left, right] = await Promise.all([
    h.handler(
      request({
        productId: monthly.storeProductId,
        purchaseToken: "concurrent-token",
      }),
    ),
    h.handler(
      request({
        productId: monthly.storeProductId,
        purchaseToken: "concurrent-token",
      }),
    ),
  ]);
  assert(
    left.status === 200 && right.status === 200,
    "both idempotent verifications can complete",
  );
  assert(h.rows.size === 1, "concurrent retries bind a single purchase row");
  assert(
    h.calls.acknowledge === 1,
    "the acknowledgement lease allows one provider acknowledgement",
  );
});

Deno.test("raw purchase tokens are never echoed in responses", async () => {
  const h = harness({
    providerError: new ProviderUnavailableError("transient"),
  });
  const secret = "raw-super-secret-purchase-token";
  const response = await h.handler(
    request({ productId: monthly.storeProductId, purchaseToken: secret }),
  );
  assert(
    !(await response.text()).includes(secret),
    "response must not echo raw token",
  );
});

Deno.test("audit events retain only allowlisted provider metadata", async () => {
  const h = harness({
    provider: {
      purchaseState: "PURCHASED",
      entitlementState: "ACTIVE",
      acknowledgementState: "ACKNOWLEDGED",
      startsAt: "2026-09-26T12:00:00.000Z",
      expiresAt: "2026-10-26T12:00:00.000Z",
      orderId: "GPA.TEST-ORDER",
      willRenew: true,
      sanitizedPayload: {
        provider: "GOOGLE_PLAY",
        purchaseState: "PURCHASED",
        purchaseToken: "must-not-persist",
        nested: { accessToken: "must-not-persist" },
      },
    },
  });
  const response = await h.handler(
    request({
      productId: monthly.storeProductId,
      purchaseToken: "audit-test-token",
    }),
  );
  const stored = JSON.stringify(h.eventPayloads);
  assert(response.status === 200, "a sanitized verified purchase is accepted");
  assert(
    !stored.includes("must-not-persist"),
    "raw token and nested credentials are removed",
  );
  assert(
    stored.includes("GOOGLE_PLAY") && stored.includes("PURCHASED"),
    "safe provider metadata remains for audit",
  );
});
