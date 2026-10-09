import { createReconciliationStore } from "./reconciliation-store.ts";

export type ReconcileBillingDependencies = {
  sweepHashOnlyJobs: (limit: number) => Promise<{
    reclaimedCount: number;
    waitingCount: number;
  }>;
};

export type ReconcileBillingConfig = {
  cronSecret: string;
  maxBatchSize: number;
};

function json(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

function constantTimeEquals(left: string, right: string): boolean {
  const leftBytes = new TextEncoder().encode(left);
  const rightBytes = new TextEncoder().encode(right);
  if (leftBytes.length !== rightBytes.length) return false;
  let mismatch = 0;
  for (let index = 0; index < leftBytes.length; index++) {
    mismatch |= leftBytes[index] ^ rightBytes[index];
  }
  return mismatch === 0;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export function createReconcileBillingHandler(
  dependencies: ReconcileBillingDependencies,
  config: ReconcileBillingConfig,
) {
  return async (request: Request): Promise<Response> => {
    if (!new URL(request.url).pathname.endsWith("/reconcile-billing")) {
      return json({ outcome: "REJECTED", code: "NOT_FOUND" }, 404);
    }
    if (request.method !== "POST") {
      return json({ outcome: "REJECTED", code: "METHOD_NOT_ALLOWED" }, 405);
    }
    if (config.cronSecret.length < 32 || config.maxBatchSize < 1 || config.maxBatchSize > 500) {
      return json({ outcome: "RETRYABLE", code: "SERVER_CONFIGURATION_INVALID" }, 503);
    }
    const presented = /^Bearer\s+(\S+)$/i.exec(request.headers.get("authorization") ?? "")?.[1];
    if (!presented || !constantTimeEquals(presented, config.cronSecret)) {
      return json({ outcome: "REJECTED", code: "UNAUTHENTICATED" }, 401);
    }

    const contentType = request.headers.get("content-type")?.split(";")[0].trim().toLowerCase();
    if (contentType !== "application/json") {
      return json({ outcome: "REJECTED", code: "INVALID_REQUEST" }, 400);
    }
    const contentLength = Number(request.headers.get("content-length") ?? 0);
    if (contentLength > 1_024) return json({ outcome: "REJECTED", code: "INVALID_REQUEST" }, 400);
    let payload: unknown;
    try {
      const raw = await request.text();
      if (new TextEncoder().encode(raw).byteLength > 1_024) {
        return json({ outcome: "REJECTED", code: "INVALID_REQUEST" }, 400);
      }
      payload = raw.length === 0 ? {} : JSON.parse(raw);
    } catch {
      return json({ outcome: "REJECTED", code: "INVALID_REQUEST" }, 400);
    }
    if (!isRecord(payload) || Object.keys(payload).length !== 0) {
      return json({ outcome: "REJECTED", code: "INVALID_REQUEST" }, 400);
    }

    try {
      const result = await dependencies.sweepHashOnlyJobs(config.maxBatchSize);
      if (!Number.isSafeInteger(result.reclaimedCount) || result.reclaimedCount < 0 ||
        !Number.isSafeInteger(result.waitingCount) || result.waitingCount < 0) {
        throw new Error("Invalid sweep counters");
      }
      return json({
        outcome: "COMPLETED",
        reclaimedCount: result.reclaimedCount,
        waitingCount: result.waitingCount,
        providerCalls: 0,
      });
    } catch {
      return json({ outcome: "RETRYABLE", code: "SWEEP_UNAVAILABLE" }, 503);
    }
  };
}

if (import.meta.main) {
  const required = (name: string): string => {
    const value = Deno.env.get(name);
    if (!value) throw new Error(`Missing server configuration: ${name}`);
    return value;
  };
  const dependencies = createReconciliationStore(Deno.env);
  const handler = createReconcileBillingHandler(dependencies, {
    cronSecret: required("RECONCILE_BILLING_SECRET"),
    maxBatchSize: 100,
  });
  Deno.serve(handler);
}
