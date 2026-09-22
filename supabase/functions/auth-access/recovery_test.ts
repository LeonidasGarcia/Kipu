import { createAuthAccessHandler, Dependencies } from "./index.ts";

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
  signInWithAuth: async () => ({}),
  resetPasswordForEmail: async () => ({}),
  sleep: async () => {},
};

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
