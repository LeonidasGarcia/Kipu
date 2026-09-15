export type RpcCall = {
  name: string;
  args: Record<string, unknown>;
  authorization: string;
};

type RpcError = {
  code: string;
  status: number;
  retryAfterSeconds?: number;
};

type RpcResult = { data?: unknown; error?: RpcError };

export type Dependencies = {
  rpc: (call: RpcCall) => Promise<RpcResult>;
};

const json = (
  body: unknown,
  status = 200,
  headers: Record<string, string> = {},
) =>
  new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      ...headers,
    },
  });

function authenticated(request: Request): string | null {
  const value = request.headers.get("authorization");
  if (!value?.startsWith("Bearer ")) return null;

  try {
    const encoded = value.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const payload = JSON.parse(atob(encoded));
    return payload.sub && (!payload.exp || payload.exp * 1000 > Date.now())
      ? value
      : null;
  } catch {
    return null;
  }
}

function validSelection(value: unknown): value is Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;

  const selection = value as Record<string, unknown>;
  const keys = Object.keys(selection).sort().join(",");
  return keys ===
      "contract_version,operation_id,selected_at,selection,selection_revision" &&
    selection.contract_version === 1 &&
    typeof selection.operation_id === "string" &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
      .test(selection.operation_id) &&
    typeof selection.selection_revision === "string" &&
    /^[1-9][0-9]*$/.test(selection.selection_revision) &&
    ["FREE", "TRIAL_INTENT", "PREMIUM_INTENT"].includes(
      String(selection.selection),
    ) &&
    typeof selection.selected_at === "string" &&
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?Z$/.test(
      selection.selected_at,
    ) &&
    !Number.isNaN(Date.parse(selection.selected_at));
}

export function createPlansHandler(deps: Dependencies) {
  return async (request: Request): Promise<Response> => {
    const authorization = authenticated(request);
    if (!authorization) {
      return json({ code: "UNAUTHENTICATED", retryable: true }, 401);
    }

    const path = new URL(request.url).pathname.split("/").pop();
    let name: string;
    let args: Record<string, unknown> = {};

    if (path === "eligibility") {
      if (request.method !== "GET") return new Response(null, { status: 405 });
      name = "get_trial_eligibility";
    } else if (path === "selection") {
      if (request.method !== "POST") return new Response(null, { status: 405 });
      if (
        !request.headers.get("content-type")?.toLowerCase().startsWith(
          "application/json",
        )
      ) {
        return new Response(null, { status: 415 });
      }

      let body: unknown;
      try {
        body = await request.json();
      } catch {
        return json({ code: "INVALID_REQUEST", retryable: false }, 400);
      }
      if ((body as Record<string, unknown>)?.contract_version !== 1) {
        return json({ code: "UNSUPPORTED_VERSION", retryable: false }, 400);
      }
      if (!validSelection(body)) {
        return json({ code: "INVALID_REQUEST", retryable: false }, 400);
      }

      name = "apply_plan_selection";
      args = body;
    } else {
      return new Response(null, { status: 404 });
    }

    let result: RpcResult;
    try {
      result = await deps.rpc({ name, args, authorization });
    } catch {
      return json({ code: "UNAVAILABLE", retryable: true }, 503);
    }

    if (result.error) {
      const retryable = result.error.code === "UNAUTHENTICATED" ||
        result.error.code === "UNAVAILABLE";
      const extra = result.error.retryAfterSeconds == null
        ? {}
        : { retry_after_seconds: result.error.retryAfterSeconds };
      const headers: Record<string, string> =
        result.error.retryAfterSeconds == null
          ? {}
          : { "retry-after": String(result.error.retryAfterSeconds) };
      return json(
        { code: result.error.code, retryable, ...extra },
        result.error.status,
        headers,
      );
    }
    return json(result.data);
  };
}

function createSupabaseRpc(): Dependencies["rpc"] {
  return async ({ name, args, authorization }) => {
    const baseUrl = Deno.env.get("SUPABASE_URL");
    const apiKey = Deno.env.get("SUPABASE_ANON_KEY") ??
      Deno.env.get("SUPABASE_PUBLISHABLE_KEY");
    if (!baseUrl || !apiKey) {
      return { error: { code: "UNAVAILABLE", status: 503 } };
    }

    const rpcArgs = name === "apply_plan_selection"
      ? Object.fromEntries(
        Object.entries(args).map(([key, value]) => [`p_${key}`, value]),
      )
      : args;
    const response = await fetch(`${baseUrl}/rest/v1/rpc/${name}`, {
      method: "POST",
      headers: {
        authorization,
        apikey: apiKey,
        "content-type": "application/json",
      },
      body: JSON.stringify(rpcArgs),
    });
    if (!response.ok) {
      const status = response.status;
      const code = status === 401
        ? "UNAUTHENTICATED"
        : status === 403
        ? "FORBIDDEN"
        : "UNAVAILABLE";
      const retryAfter = response.headers.get("retry-after");
      const retryAfterSeconds = retryAfter == null
        ? undefined
        : Number.parseInt(retryAfter, 10);
      return {
        error: {
          code,
          status,
          ...(Number.isNaN(retryAfterSeconds) ? {} : { retryAfterSeconds }),
        },
      };
    }

    const data = await response.json();
    return {
      data: name === "apply_plan_selection" && Array.isArray(data)
        ? data[0]
        : data,
    };
  };
}

if (import.meta.main) {
  Deno.serve(createPlansHandler({ rpc: createSupabaseRpc() }));
}
