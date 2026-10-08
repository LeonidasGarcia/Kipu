import type {
  BeginEventInput,
  BeginEventResult,
  PlayRtdnDependencies,
} from "./index.ts";

type Env = { get(name: string): string | undefined };

function required(env: Env, name: string): string {
  const value = env.get(name);
  if (!value) throw new Error(`Missing server configuration: ${name}`);
  return value.replace(/\/$/, "");
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export function createRtdnStore(
  env: Env,
  fetchImpl: typeof fetch = fetch,
): Pick<PlayRtdnDependencies, "beginEvent" | "finishEvent"> {
  const supabaseUrl = required(env, "SUPABASE_URL");
  const serviceRoleKey = required(env, "SUPABASE_SERVICE_ROLE_KEY");

  async function rpc<T>(name: string, body: Record<string, unknown>): Promise<T> {
    let response: Response;
    try {
      response = await fetchImpl(`${supabaseUrl}/rest/v1/rpc/${name}`, {
        method: "POST",
        signal: AbortSignal.timeout(8_000),
        headers: {
          apikey: serviceRoleKey,
          authorization: `Bearer ${serviceRoleKey}`,
          "content-type": "application/json",
          accept: "application/json",
        },
        body: JSON.stringify(body),
      });
    } catch {
      throw new Error("RTDN persistence is unavailable");
    }
    if (!response.ok) throw new Error("RTDN persistence rejected the request");
    return await response.json() as T;
  }

  return {
    beginEvent: async (input: BeginEventInput): Promise<BeginEventResult> => {
      const raw = await rpc<unknown>("begin_billing_rtdn_event", {
        p_event_identity_hash: input.eventIdentityHash,
        p_provider_message_id: input.providerMessageId,
        p_purchase_token_hash: input.purchaseTokenHash,
        p_notification_type: input.notificationType,
      });
      const row = Array.isArray(raw) ? raw[0] : raw;
      if (!isRecord(row) || typeof row.receipt_id !== "string") {
        throw new Error("RTDN receipt response is invalid");
      }
      const status = row.receipt_status;
      if (![
        "WAITING_FOR_TOKEN",
        "PROCESSING",
        "RETRYABLE",
        "COMPLETED",
        "REJECTED",
      ].includes(String(status))) {
        throw new Error("RTDN receipt status is invalid");
      }
      return {
        receiptId: row.receipt_id,
        duplicate: row.duplicate === true,
        receiptStatus: status as BeginEventResult["receiptStatus"],
        userId: typeof row.user_id === "string" ? row.user_id : null,
        storeProductId: typeof row.store_product_id === "string" ? row.store_product_id : null,
        billingProductId: typeof row.billing_product_id === "string" ? row.billing_product_id : null,
        jobId: typeof row.job_id === "string" ? row.job_id : null,
        leaseOwner: typeof row.lease_owner === "string" ? row.lease_owner : null,
        safeResultCode: typeof row.safe_result_code === "string" ? row.safe_result_code : null,
      };
    },
    finishEvent: async (input) => {
      await rpc("finish_billing_rtdn_event", {
        p_receipt_id: input.receiptId,
        p_job_id: input.jobId,
        p_lease_owner: input.leaseOwner,
        p_outcome: input.outcome,
        p_safe_result_code: input.safeResultCode,
      });
    },
  };
}
