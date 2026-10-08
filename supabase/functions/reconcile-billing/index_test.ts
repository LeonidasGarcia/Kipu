import {
  createReconcileBillingHandler,
  type ReconcileBillingDependencies,
} from "./index.ts";

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

async function responseBody(response: Response) {
  return await response.json() as Record<string, unknown>;
}

function request(options: { method?: string; token?: string | null; body?: string } = {}) {
  const method = options.method ?? "POST";
  return new Request("http://localhost/functions/v1/reconcile-billing", {
    method,
    headers: {
      ...(options.token === null ? {} : { authorization: `Bearer ${options.token ?? "cron-secret-value-with-at-least-thirty-two-bytes"}` }),
      "content-type": "application/json",
    },
    body: method === "GET" ? undefined : options.body ?? "{}",
  });
}

function harness(options: {
  secret?: string;
  sweepError?: Error;
  result?: { reclaimedCount: number; waitingCount: number };
} = {}) {
  const calls = { sweep: 0 };
  const dependencies: ReconcileBillingDependencies = {
    sweepHashOnlyJobs: async () => {
      calls.sweep++;
      if (options.sweepError) throw options.sweepError;
      return options.result ?? { reclaimedCount: 1, waitingCount: 4 };
    },
  };
  return {
    handler: createReconcileBillingHandler(dependencies, {
      cronSecret: options.secret ?? "cron-secret-value-with-at-least-thirty-two-bytes",
      maxBatchSize: 100,
    }),
    calls,
  };
}

Deno.test("requires the server scheduler credential before sweeping", async () => {
  const h = harness();
  const missing = await h.handler(request({ token: null }));
  const wrong = await h.handler(request({ token: "wrong-secret" }));
  assert(missing.status === 401 && wrong.status === 401, "invalid scheduler credential is rejected");
  assert(h.calls.sweep === 0, "unauthorized invocation does not touch reconciliation state");
});

Deno.test("limits reconciliation to lease recovery and token waiting", async () => {
  const h = harness({ result: { reclaimedCount: 2, waitingCount: 7 } });
  const response = await h.handler(request());
  const result = await responseBody(response);
  assert(response.status === 200 && result.outcome === "COMPLETED", "authorized sweep completes");
  assert(result.providerCalls === 0, "hash-only scheduler sweep never calls Google Play");
  assert(result.reclaimedCount === 2 && result.waitingCount === 7, "safe reconciliation counts are returned");
  assert(h.calls.sweep === 1, "one request performs one bounded database sweep");
});

Deno.test("rejects methods and request bodies outside the fixed scheduler contract", async () => {
  const h = harness();
  const method = await h.handler(request({ method: "GET" }));
  const body = await h.handler(request({ body: "{\"purchaseToken\":\"secret\"}" }));
  assert(method.status === 405, "only POST is accepted");
  assert(body.status === 400, "scheduler caller cannot submit a purchase token");
  assert(h.calls.sweep === 0, "invalid requests do not start a sweep");
});

Deno.test("returns a generic retryable failure without exposing provider or database details", async () => {
  const h = harness({ sweepError: new Error("sensitive database detail") });
  const response = await h.handler(request());
  const text = await response.text();
  assert(response.status === 503, "database failure is retried by the scheduler");
  assert(text.includes("RETRYABLE") && !text.includes("sensitive"), "failure response is sanitized");
});
