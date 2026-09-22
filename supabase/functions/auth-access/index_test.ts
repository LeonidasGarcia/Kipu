import { createAuthAccessHandler, Dependencies } from "./index.ts";

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

function assertEquals(actual: unknown, expected: unknown, message = "values differ") {
  const left = JSON.stringify(actual);
  const right = JSON.stringify(expected);
  if (left !== right) {
    throw new Error(`${message}: expected ${right}, got ${left}`);
  }
}

const defaultDeps: Dependencies = {
  consumeRateBucket: async () => ({ allowed: true, retryAfterSeconds: 0 }),
  checkEmailExists: async () => false,
  signUpWithAuth: async () => ({}),
  signInWithAuth: async () => ({
    data: {
      session: {
        access_token: "test_token",
        refresh_token: "test_refresh",
        expires_in: 3600,
        token_type: "bearer",
        user: { id: "user_uuid_1" },
      },
    },
  }),
  resetPasswordForEmail: async () => ({}),
  sleep: async () => {},
};

Deno.test("register - returns 201 for valid new account", async () => {
  const handler = createAuthAccessHandler(defaultDeps);
  const req = new Request("https://example.supabase.co/functions/v1/auth-access/register", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      email: "newuser@example.com",
      password: "Password123",
      captchaToken: "valid_captcha",
    }),
  });

  const res = await handler(req);
  assertEquals(res.status, 201);
  const data = await res.json();
  assertEquals(data.status, "ACCEPTED");
  assertEquals(data.confirmationRequired, true);
});

Deno.test("register - returns 409 for existing email per FR-051", async () => {
  const deps: Dependencies = {
    ...defaultDeps,
    checkEmailExists: async () => true,
  };
  const handler = createAuthAccessHandler(deps);
  const req = new Request("https://example.supabase.co/functions/v1/auth-access/register", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      email: "existing@example.com",
      password: "Password123",
      captchaToken: "valid_captcha",
    }),
  });

  const res = await handler(req);
  assertEquals(res.status, 409);
  const data = await res.json();
  assertEquals(data.title, "Account already exists");
});

Deno.test("register - returns 422 for invalid password policy", async () => {
  const handler = createAuthAccessHandler(defaultDeps);
  const req = new Request("https://example.supabase.co/functions/v1/auth-access/register", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      email: "user@example.com",
      password: "123",
      captchaToken: "valid_captcha",
    }),
  });

  const res = await handler(req);
  assertEquals(res.status, 422);
});

Deno.test("register - returns 429 when rate limited", async () => {
  const deps: Dependencies = {
    ...defaultDeps,
    consumeRateBucket: async () => ({ allowed: false, retryAfterSeconds: 30 }),
  };
  const handler = createAuthAccessHandler(deps);
  const req = new Request("https://example.supabase.co/functions/v1/auth-access/register", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      email: "user@example.com",
      password: "Password123",
      captchaToken: "valid_captcha",
    }),
  });

  const res = await handler(req);
  assertEquals(res.status, 429);
  assertEquals(res.headers.get("Retry-After"), "30");
});

Deno.test("login - returns 200 with session envelope for valid credentials", async () => {
  const handler = createAuthAccessHandler(defaultDeps);
  const req = new Request("https://example.supabase.co/functions/v1/auth-access/login", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      email: "user@example.com",
      password: "Password123",
      captchaToken: "valid_captcha",
    }),
  });

  const res = await handler(req);
  assertEquals(res.status, 200);
  assertEquals(res.headers.get("Cache-Control"), "private, no-store");
  const data = await res.json();
  assertEquals(data.accessToken, "test_token");
  assertEquals(data.userId, "user_uuid_1");
});

Deno.test("login - returns neutral 401 for invalid credentials per FR-004", async () => {
  const deps: Dependencies = {
    ...defaultDeps,
    signInWithAuth: async () => ({ error: { message: "Invalid login credentials", status: 400 } }),
  };
  const handler = createAuthAccessHandler(deps);
  const req = new Request("https://example.supabase.co/functions/v1/auth-access/login", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      email: "user@example.com",
      password: "WrongPassword1",
      captchaToken: "valid_captcha",
    }),
  });

  const res = await handler(req);
  assertEquals(res.status, 401);
  const data = await res.json();
  assertEquals(data.detail, "Correo o contraseña incorrectos.");
});

Deno.test("recovery - returns 202 for both existing and non-existing email per SC-002", async () => {
  const handler = createAuthAccessHandler(defaultDeps);

  // Existing email
  const reqExisting = new Request("https://example.supabase.co/functions/v1/auth-access/recovery", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email: "existing@example.com" }),
  });
  const resExisting = await handler(reqExisting);
  assertEquals(resExisting.status, 202);
  const dataExisting = await resExisting.json();
  assertEquals(dataExisting.status, "ACCEPTED");

  // Non-existing email
  const reqNonExisting = new Request("https://example.supabase.co/functions/v1/auth-access/recovery", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email: "nonexisting@example.com" }),
  });
  const resNonExisting = await handler(reqNonExisting);
  assertEquals(resNonExisting.status, 202);
  const dataNonExisting = await resNonExisting.json();
  assertEquals(dataNonExisting.status, "ACCEPTED");
});
