import type {
  Dependencies,
  PersistInput,
  PersistResult,
  ProductRecord,
} from "./index.ts";
import { ProviderUnavailableError } from "./google-play-api.ts";

type Env = { get(name: string): string | undefined };

export class PersistenceUnavailableError extends Error {}

function required(env: Env, name: string): string {
  const value = env.get(name);
  if (!value) {
    throw new PersistenceUnavailableError(
      `Missing server configuration: ${name}`,
    );
  }
  return value.replace(/\/$/, "");
}

async function readJson(response: Response): Promise<unknown> {
  return await response.json().catch(() => null);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export function createPurchaseStore(
  env: Env,
  fetchImpl: typeof fetch = fetch,
): Pick<
  Dependencies,
  | "authenticate"
  | "lookupProduct"
  | "persistPurchase"
  | "claimAcknowledgement"
  | "completeAcknowledgement"
  | "releaseAcknowledgement"
> {
  const supabaseUrl = required(env, "SUPABASE_URL");
  const apiKey = env.get("SUPABASE_ANON_KEY") ??
    env.get("SUPABASE_PUBLISHABLE_KEY");
  if (!apiKey) {
    throw new PersistenceUnavailableError(
      "Missing Supabase anon or publishable API key",
    );
  }
  const serviceRoleKey = required(env, "SUPABASE_SERVICE_ROLE_KEY");

  async function rpc<T>(
    name: string,
    body: Record<string, unknown>,
  ): Promise<T> {
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
      throw new PersistenceUnavailableError(
        "Supabase billing persistence is unavailable",
      );
    }
    if (!response.ok) {
      throw new PersistenceUnavailableError(
        "Supabase billing persistence rejected the server request",
      );
    }
    return await readJson(response) as T;
  }

  async function persistPurchase(input: PersistInput): Promise<PersistResult> {
    const result = await rpc<unknown>("persist_verified_billing_purchase", {
      p_user_id: input.userId,
      p_store_product_id: input.storeProductId,
      p_purchase_token_hash: input.purchaseTokenHash,
      p_order_id: input.orderId,
      p_purchase_state: input.purchaseState,
      p_entitlement_state: input.entitlementState,
      p_acknowledgement_state: input.acknowledgementState,
      p_starts_at: input.startsAt,
      p_expires_at: input.expiresAt,
      p_event_payload: input.sanitizedPayload,
    });
    const row = Array.isArray(result) ? result[0] : result;
    if (!isRecord(row)) {
      throw new PersistenceUnavailableError(
        "Supabase returned an invalid billing persistence result",
      );
    }
    return {
      result: row.result === "TOKEN_ACCOUNT_CONFLICT"
        ? "TOKEN_ACCOUNT_CONFLICT"
        : row.result === "PENDING"
        ? "PENDING"
        : "VERIFIED",
      purchaseId: typeof row.purchase_id === "string" ? row.purchase_id : null,
      effectivePremium: row.effective_premium === true,
      effectiveExpiresAt: typeof row.effective_expires_at === "string"
        ? row.effective_expires_at
        : null,
    };
  }

  async function acknowledgeRpc(
    name: string,
    purchaseTokenHash: string,
  ): Promise<unknown> {
    return await rpc(name, { p_purchase_token_hash: purchaseTokenHash });
  }

  return {
    authenticate: async (request) => {
      const authorization = request.headers.get("authorization");
      if (!authorization) return null;
      let response: Response;
      try {
        response = await fetchImpl(`${supabaseUrl}/auth/v1/user`, {
          signal: AbortSignal.timeout(8_000),
          headers: {
            apikey: apiKey,
            authorization,
            accept: "application/json",
          },
        });
      } catch {
        throw new ProviderUnavailableError("Supabase Auth is unavailable");
      }
      if (response.status === 401 || response.status === 403) return null;
      if (!response.ok) {
        throw new ProviderUnavailableError(
          "Supabase Auth could not verify the session",
        );
      }
      const payload = await readJson(response);
      return isRecord(payload) && typeof payload.id === "string"
        ? payload.id
        : null;
    },
    lookupProduct: async (productId, authorization) => {
      const url = new URL(`${supabaseUrl}/rest/v1/billing_products`);
      url.searchParams.set(
        "select",
        "id,store_product_id,base_plan_id,plan_type",
      );
      url.searchParams.set("store_product_id", `eq.${productId}`);
      url.searchParams.set("is_active", "eq.true");
      url.searchParams.set("limit", "1");
      let response: Response;
      try {
        response = await fetchImpl(url, {
          signal: AbortSignal.timeout(8_000),
          headers: {
            apikey: apiKey,
            authorization,
            accept: "application/json",
          },
        });
      } catch {
        throw new PersistenceUnavailableError(
          "Supabase product catalog is unavailable",
        );
      }
      if (!response.ok) {
        throw new PersistenceUnavailableError(
          "Supabase product catalog lookup failed",
        );
      }
      const rows = await readJson(response);
      if (!Array.isArray(rows) || !isRecord(rows[0])) return null;
      const row = rows[0];
      if (
        typeof row.id !== "string" ||
        typeof row.store_product_id !== "string" ||
        typeof row.plan_type !== "string"
      ) return null;
      return {
        id: row.id,
        storeProductId: row.store_product_id,
        basePlanId: typeof row.base_plan_id === "string"
          ? row.base_plan_id
          : null,
        planType: row.plan_type,
      } satisfies ProductRecord;
    },
    persistPurchase,
    claimAcknowledgement: async (purchaseTokenHash) => {
      const result = await acknowledgeRpc(
        "claim_billing_purchase_acknowledgement",
        purchaseTokenHash,
      );
      return result === true || (Array.isArray(result) && result[0] === true);
    },
    completeAcknowledgement: async (purchaseTokenHash) => {
      await acknowledgeRpc(
        "complete_billing_purchase_acknowledgement",
        purchaseTokenHash,
      );
    },
    releaseAcknowledgement: async (purchaseTokenHash) => {
      await acknowledgeRpc(
        "release_billing_purchase_acknowledgement",
        purchaseTokenHash,
      );
    },
  };
}
