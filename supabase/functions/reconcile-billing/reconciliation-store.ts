import type { ReconcileBillingDependencies } from "./index.ts";

type Env = { get(name: string): string | undefined };

function required(env: Env, name: string): string {
  const value = env.get(name);
  if (!value) throw new Error(`Missing server configuration: ${name}`);
  return value.replace(/\/$/, "");
}

export function createReconciliationStore(
  env: Env,
  fetchImpl: typeof fetch = fetch,
): ReconcileBillingDependencies {
  const supabaseUrl = required(env, "SUPABASE_URL");
  const serviceRoleKey = required(env, "SUPABASE_SERVICE_ROLE_KEY");
  return {
    sweepHashOnlyJobs: async (limit) => {
      let response: Response;
      try {
        response = await fetchImpl(`${supabaseUrl}/rest/v1/rpc/sweep_billing_reconciliation_jobs`, {
          method: "POST",
          signal: AbortSignal.timeout(8_000),
          headers: {
            apikey: serviceRoleKey,
            authorization: `Bearer ${serviceRoleKey}`,
            "content-type": "application/json",
            accept: "application/json",
          },
          body: JSON.stringify({ p_limit: limit }),
        });
      } catch {
        throw new Error("Reconciliation database is unavailable");
      }
      if (!response.ok) throw new Error("Reconciliation database rejected the sweep");
      const raw: unknown = await response.json().catch(() => null);
      const row = Array.isArray(raw) ? raw[0] : raw;
      if (typeof row !== "object" || row === null || Array.isArray(row)) {
        throw new Error("Reconciliation result is invalid");
      }
      const result = row as Record<string, unknown>;
      if (!Number.isInteger(result.reclaimed_count) || !Number.isInteger(result.waiting_count)) {
        throw new Error("Reconciliation counters are invalid");
      }
      return {
        reclaimedCount: result.reclaimed_count as number,
        waitingCount: result.waiting_count as number,
      };
    },
  };
}
