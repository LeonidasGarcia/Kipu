import { createPlansHandler } from "./index.ts";

type RpcCall = {
  name: string;
  args: Record<string, unknown>;
  authorization: string;
};

type RpcReply = {
  data?: unknown;
  error?: { code: string; status: number; retryAfterSeconds?: number };
};

const validJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
  "eyJzdWIiOiIxMDAwMDAwMC0wMDAwLTQwMDAtODAwMC0wMDAwMDAwMDAwMDEiLCJyb2xlIjoiYXV0aGVudGljYXRlZCIsImV4cCI6NDEwMjQ0NDgwMH0.signature";

const selectionRequest = {
  contract_version: 1,
  operation_id: "5d92af34-c725-4a1a-a863-2c93fa214c86",
  selection_revision: "3",
  selection: "PREMIUM_INTENT",
  selected_at: "2026-09-14T15:03:12.123456Z",
};

const selectionResponse = {
  contract_version: 1,
  operation_id: selectionRequest.operation_id,
  result: "APPLIED",
  accepted_revision: "3",
  current_preference: {
    selection: "PREMIUM_INTENT",
    selected_at: selectionRequest.selected_at,
    updated_at: "2026-09-15T12:00:00.000000Z",
  },
  free_limits: {
    policy_version: 1,
    instruments: 4,
    custom_categories: 5,
    debts: 2,
    goals: 2,
    budgets: 2,
  },
  server_time: "2026-09-15T12:00:00.000000Z",
};

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

function assertEquals(
  actual: unknown,
  expected: unknown,
  message = "values differ",
) {
  const left = JSON.stringify(actual);
  const right = JSON.stringify(expected);
  if (left !== right) {
    throw new Error(`${message}: expected ${right}, got ${left}`);
  }
}

function request(path: string, init: RequestInit = {}) {
  return new Request(`http://localhost/functions/v1/plans${path}`, init);
}

function harness(reply: RpcReply) {
  const calls: RpcCall[] = [];
  const handler = createPlansHandler({
    rpc: (call: RpcCall) => {
      calls.push(call);
      return Promise.resolve(reply);
    },
  });
  return { handler, calls };
}

Deno.test("GET /plans/eligibility forwards the caller JWT and returns conservative UNKNOWN", async () => {
  const unknown = {
    status: "UNKNOWN",
    source: "UNAVAILABLE",
    verified_at: null,
    valid_until: null,
  };
  const { handler, calls } = harness({ data: unknown });
  const response = await handler(
    request("/eligibility", {
      headers: { authorization: `Bearer ${validJwt}` },
    }),
  );

  assertEquals(response.status, 200);
  assertEquals(
    response.headers.get("content-type"),
    "application/json; charset=utf-8",
  );
  assertEquals(await response.json(), unknown);
  assertEquals(calls, [{
    name: "get_trial_eligibility",
    args: {},
    authorization: `Bearer ${validJwt}`,
  }]);
});

Deno.test("POST /plans/selection delegates the exact typed payload and returns the contract", async () => {
  const { handler, calls } = harness({ data: selectionResponse });
  const response = await handler(request("/selection", {
    method: "POST",
    headers: {
      authorization: `Bearer ${validJwt}`,
      "content-type": "application/json",
    },
    body: JSON.stringify(selectionRequest),
  }));

  assertEquals(response.status, 200);
  assertEquals(await response.json(), selectionResponse);
  assertEquals(calls, [{
    name: "apply_plan_selection",
    args: selectionRequest,
    authorization: `Bearer ${validJwt}`,
  }]);
  assert(
    !("user_id" in calls[0].args),
    "user identity must come from the JWT, never the body",
  );
});

Deno.test("all APPLIED, DUPLICATE, STALE and CONFLICT outcomes remain HTTP 200", async () => {
  for (const result of ["APPLIED", "DUPLICATE", "STALE", "CONFLICT"]) {
    const { handler } = harness({ data: { ...selectionResponse, result } });
    const response = await handler(request("/selection", {
      method: "POST",
      headers: {
        authorization: `Bearer ${validJwt}`,
        "content-type": "application/json; charset=utf-8",
      },
      body: JSON.stringify(selectionRequest),
    }));
    assertEquals(
      response.status,
      200,
      `${result} must be a successful reconciliation response`,
    );
    assertEquals((await response.json()).result, result);
  }
});

Deno.test("missing, malformed and expired JWTs return retryable UNAUTHENTICATED without RPC calls", async () => {
  const expiredJwt =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMDAwMDAwMC0wMDAwLTQwMDAtODAwMC0wMDAwMDAwMDAwMDEiLCJleHAiOjF9.signature";
  for (
    const authorization of [
      undefined,
      "Basic credentials",
      "Bearer not-a-jwt",
      `Bearer ${expiredJwt}`,
    ]
  ) {
    const { handler, calls } = harness({ data: selectionResponse });
    const headers = authorization ? { authorization } : undefined;
    const response = await handler(request("/eligibility", { headers }));
    assertEquals(response.status, 401);
    assertEquals(await response.json(), {
      code: "UNAUTHENTICATED",
      retryable: true,
    });
    assertEquals(
      calls.length,
      0,
      "invalid JWT must be rejected before database access",
    );
  }
});

Deno.test("method and media validation happen before RPC execution", async () => {
  const cases: Array<[Request, number]> = [
    [
      request("/eligibility", {
        method: "POST",
        headers: { authorization: `Bearer ${validJwt}` },
      }),
      405,
    ],
    [
      request("/selection", {
        method: "GET",
        headers: { authorization: `Bearer ${validJwt}` },
      }),
      405,
    ],
    [
      request("/selection", {
        method: "POST",
        headers: {
          authorization: `Bearer ${validJwt}`,
          "content-type": "text/plain",
        },
        body: "{}",
      }),
      415,
    ],
  ];
  for (const [input, status] of cases) {
    const { handler, calls } = harness({ data: selectionResponse });
    const response = await handler(input);
    assertEquals(response.status, status);
    assertEquals(calls.length, 0);
  }
});

Deno.test("invalid payload and unsupported version have distinct terminal bodies", async () => {
  const cases: Array<[Record<string, unknown>, string]> = [
    [{ ...selectionRequest, contract_version: 2 }, "UNSUPPORTED_VERSION"],
    [{ ...selectionRequest, selection_revision: 3 }, "INVALID_REQUEST"],
    [{ ...selectionRequest, selection_revision: "0" }, "INVALID_REQUEST"],
    [{ ...selectionRequest, selection: "PREMIUM" }, "INVALID_REQUEST"],
    [
      { ...selectionRequest, selected_at: "2026-09-14T15:03:12.1234567Z" },
      "INVALID_REQUEST",
    ],
    [
      { ...selectionRequest, user_id: "50000000-0000-4000-8000-000000000005" },
      "INVALID_REQUEST",
    ],
  ];
  for (const [body, code] of cases) {
    const { handler, calls } = harness({ data: selectionResponse });
    const response = await handler(request("/selection", {
      method: "POST",
      headers: {
        authorization: `Bearer ${validJwt}`,
        "content-type": "application/json",
      },
      body: JSON.stringify(body),
    }));
    assertEquals(response.status, 400);
    assertEquals(await response.json(), { code, retryable: false });
    assertEquals(calls.length, 0);
  }
});

Deno.test("RPC errors map to status-specific safe bodies", async () => {
  const cases: Array<[RpcReply["error"], number, Record<string, unknown>]> = [
    [{ code: "UNAUTHENTICATED", status: 401 }, 401, {
      code: "UNAUTHENTICATED",
      retryable: true,
    }],
    [{ code: "FORBIDDEN", status: 403 }, 403, {
      code: "FORBIDDEN",
      retryable: false,
    }],
    [{ code: "UNAVAILABLE", status: 500 }, 500, {
      code: "UNAVAILABLE",
      retryable: true,
    }],
    [{ code: "UNAVAILABLE", status: 503 }, 503, {
      code: "UNAVAILABLE",
      retryable: true,
    }],
  ];
  for (const [error, status, body] of cases) {
    const { handler } = harness({ error });
    const response = await handler(
      request("/eligibility", {
        headers: { authorization: `Bearer ${validJwt}` },
      }),
    );
    assertEquals(response.status, status);
    assertEquals(await response.json(), body);
  }
});

Deno.test("429 propagates Retry-After in both header and retryable body", async () => {
  const { handler } = harness({
    error: { code: "UNAVAILABLE", status: 429, retryAfterSeconds: 17 },
  });
  const response = await handler(
    request("/eligibility", {
      headers: { authorization: `Bearer ${validJwt}` },
    }),
  );
  assertEquals(response.status, 429);
  assertEquals(response.headers.get("retry-after"), "17");
  assertEquals(await response.json(), {
    code: "UNAVAILABLE",
    retryable: true,
    retry_after_seconds: 17,
  });
});

Deno.test("selection has no billing, purchase, subscription, entitlement or access-cache side effects", async () => {
  const { handler, calls } = harness({ data: selectionResponse });
  await handler(request("/selection", {
    method: "POST",
    headers: {
      authorization: `Bearer ${validJwt}`,
      "content-type": "application/json",
    },
    body: JSON.stringify(selectionRequest),
  }));

  assertEquals(calls.length, 1);
  assertEquals(calls[0].name, "apply_plan_selection");
  const serialized = JSON.stringify(calls).toLowerCase();
  for (
    const forbidden of [
      "billing",
      "purchase",
      "subscription",
      "entitlement",
      "feature_access_cache",
    ]
  ) {
    assert(
      !serialized.includes(forbidden),
      `selection boundary must not call ${forbidden}`,
    );
  }
});
