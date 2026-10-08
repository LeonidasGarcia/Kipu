import {
  createPlayRtdnHandler,
  type PlayRtdnDependencies,
} from "./index.ts";
import { createRtdnStore } from "./rtdn-store.ts";

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

async function responseBody(response: Response) {
  return await response.json() as Record<string, unknown>;
}

function pushRequest(data: unknown, options: {
  method?: string;
  token?: string | null;
  subscription?: string;
} = {}) {
  const encoded = btoa(JSON.stringify(data));
  const method = options.method ?? "POST";
  return new Request("http://localhost/functions/v1/play-rtdn", {
    method,
    headers: options.token === null
      ? { "content-type": "application/json" }
      : {
        authorization: `Bearer ${options.token ?? "valid-pubsub-token"}`,
        "content-type": "application/json",
      },
    body: method === "GET" ? undefined : JSON.stringify({
      message: { messageId: "msg-100", data: encoded },
      subscription: options.subscription ??
        "projects/kipu/subscriptions/billing-rtdn",
    }),
  });
}

function harness(options: {
  authenticated?: boolean;
  begin?: Awaited<ReturnType<PlayRtdnDependencies["beginEvent"]>>;
  processResult?: "VERIFIED" | "PENDING" | "REJECTED" | "RETRYABLE";
  processError?: Error;
  finalized?: boolean;
} = {}) {
  const calls = {
    authenticate: 0,
    begin: 0,
    verify: 0,
    complete: 0,
    verifyTokens: [] as string[],
    persistedInputs: [] as Record<string, unknown>[],
    finishOutcomes: [] as string[],
  };
  const dependencies: PlayRtdnDependencies = {
    verifyPubSubIdentity: async (token, audience, serviceAccount) => {
      calls.authenticate++;
      return options.authenticated !== false && token === "valid-pubsub-token" &&
        audience === "https://kipu.example/rtdn" &&
        serviceAccount === "billing-push@kipu.iam.gserviceaccount.com";
    },
    beginEvent: async (input) => {
      calls.begin++;
      calls.persistedInputs.push(input as unknown as Record<string, unknown>);
      return options.begin ?? {
        receiptId: "receipt-1",
        duplicate: false,
        receiptStatus: "PROCESSING",
        userId: "73000000-0000-4000-8000-000000000001",
        storeProductId: "kipu_pro_monthly",
        billingProductId: "product-monthly",
        jobId: "job-1",
        leaseOwner: "lease-1",
      };
    },
    verifyCurrentPurchase: async (input) => {
      calls.verify++;
      calls.verifyTokens.push(input.purchaseToken);
      if (options.processError) throw options.processError;
      const outcome = options.processResult ?? "VERIFIED";
      return options.finalized ? { outcome, finalized: true } : outcome;
    },
    finishEvent: async (input) => {
      calls.complete++;
      calls.finishOutcomes.push(input.outcome);
    },
  };
  return {
    handler: createPlayRtdnHandler(dependencies, {
      expectedAudience: "https://kipu.example/rtdn",
      expectedServiceAccount: "billing-push@kipu.iam.gserviceaccount.com",
      packageName: "com.kipu.app",
      subscriptionIdentity: "projects/kipu/subscriptions/billing-rtdn",
    }),
    calls,
  };
}

const subscriptionEvent = {
  version: "1.0",
  packageName: "com.kipu.app",
  eventTimeMillis: "1791450000000",
  subscriptionNotification: {
    version: "1.0",
    notificationType: 2,
    purchaseToken: "raw-purchase-token",
    subscriptionId: "kipu_pro_monthly",
  },
};

Deno.test("rejects an invalid Pub/Sub OIDC identity before parsing or persistence", async () => {
  const h = harness({ authenticated: false });
  const response = await h.handler(pushRequest(subscriptionEvent));
  assert(response.status === 401, "invalid push identity must be rejected");
  assert(h.calls.authenticate === 1, "push identity must be checked once");
  assert(h.calls.begin === 0 && h.calls.verify === 0, "invalid push cannot reach billing state");
});

Deno.test("validates method and malformed envelopes without creating receipts", async () => {
  const h = harness();
  const get = await h.handler(pushRequest(subscriptionEvent, { method: "GET" }));
  const malformed = await h.handler(
    new Request("http://localhost/functions/v1/play-rtdn", {
      method: "POST",
      headers: {
        authorization: "Bearer valid-pubsub-token",
        "content-type": "application/json",
      },
      body: "{",
    }),
  );
  const wrongPackage = await h.handler(pushRequest({
    ...subscriptionEvent,
    packageName: "com.other.app",
  }));
  assert(get.status === 405, "receiver only accepts POST");
  assert(malformed.status === 400, "malformed push envelope is rejected");
  assert(wrongPackage.status === 400, "wrong package is rejected");
  assert(h.calls.begin === 0 && h.calls.verify === 0, "invalid payloads cannot create billing state");
});

Deno.test("rejects unsupported RTDN notification types before receipt creation", async () => {
  const h = harness();
  const response = await h.handler(pushRequest({
    version: "1.0",
    packageName: "com.kipu.app",
    eventTimeMillis: "1791450000000",
    subscriptionNotification: {
      version: "1.0",
      notificationType: 99,
      purchaseToken: "raw-purchase-token",
      subscriptionId: "kipu_pro_monthly",
    },
  }));
  assert(response.status === 400, "unsupported notification types are rejected");
  assert(h.calls.begin === 0 && h.calls.verify === 0, "unsupported notifications cannot enter billing state");
});

Deno.test("hashes stable delivery identity and token, persisting no raw credentials", async () => {
  const h = harness();
  const response = await h.handler(pushRequest(subscriptionEvent));
  const result = await responseBody(response);
  const stored = h.calls.persistedInputs[0];
  assert(response.status === 200 && result.outcome === "COMPLETED", "known valid event is accepted");
  assert(h.calls.verifyTokens[0] === "raw-purchase-token", "current token reaches verifier in memory");
  assert(typeof stored.eventIdentityHash === "string" && /^[0-9a-f]{64}$/.test(stored.eventIdentityHash as string), "stable message identity is hashed");
  assert(typeof stored.purchaseTokenHash === "string" && /^[0-9a-f]{64}$/.test(stored.purchaseTokenHash as string), "purchase token is represented by its hash in persistence");
  assert(!JSON.stringify(stored).includes("raw-purchase-token"), "raw purchase token must not reach receipt persistence");
  assert(h.calls.complete === 1, "successful verifier result completes the receipt");
});

Deno.test("terminal redelivery returns the prior result without querying Google Play again", async () => {
  const h = harness({
    begin: {
      receiptId: "receipt-1",
      duplicate: true,
      receiptStatus: "COMPLETED",
      userId: "73000000-0000-4000-8000-000000000001",
      storeProductId: "kipu_pro_monthly",
      safeResultCode: "VERIFIED",
    },
  });
  const response = await h.handler(pushRequest(subscriptionEvent));
  const result = await responseBody(response);
  assert(response.status === 200 && result.outcome === "COMPLETED", "duplicate returns canonical completion");
  assert(h.calls.verify === 0 && h.calls.complete === 0, "terminal duplicate has no repeated effects");
});

Deno.test("unknown ownership and missing token wait without provider calls", async () => {
  const h = harness({
    begin: {
      receiptId: "receipt-waiting",
      duplicate: false,
      receiptStatus: "WAITING_FOR_TOKEN",
      userId: null,
      storeProductId: null,
    },
  });
  const response = await h.handler(pushRequest(subscriptionEvent));
  const result = await responseBody(response);
  assert(response.status === 200 && result.outcome === "WAITING_FOR_TOKEN", "unknown owner is safely queued");
  assert(h.calls.verify === 0 && h.calls.complete === 0, "unowned purchase cannot be queried or mutate entitlement");
});

Deno.test("transient verifier failures request Pub/Sub retry and do not complete the receipt", async () => {
  const h = harness({ processError: new Error("provider unavailable") });
  const response = await h.handler(pushRequest(subscriptionEvent));
  const result = await responseBody(response);
  assert(response.status === 503 && result.outcome === "RETRYABLE", "transient verification retries delivery");
  assert(h.calls.verify === 1 && h.calls.finishOutcomes[0] === "RETRYABLE", "retryable work stays nonterminal");
});

Deno.test("does not finalize a second time after the shared verifier atomically commits the receipt", async () => {
  const h = harness({ finalized: true });
  const response = await h.handler(pushRequest(subscriptionEvent));
  const result = await responseBody(response);
  assert(response.status === 200 && result.outcome === "COMPLETED", "atomic shared verification is acknowledged");
  assert(h.calls.verify === 1 && h.calls.complete === 0, "receipt and purchase are finalized in one database transaction");
});
