export type RateBucketResult = {
  allowed: boolean;
  retryAfterSeconds: number;
};

export type AuthSessionData = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
  userId: string;
};

export type Dependencies = {
  consumeRateBucket: (bucketKey: string) => Promise<RateBucketResult>;
  checkEmailExists: (email: string) => Promise<boolean>;
  validateCaptcha?: (token: string, clientIp: string) => Promise<boolean>;
  signUpWithAuth: (
    email: string,
    password: string,
  ) => Promise<{ error?: { message: string; status?: number } }>;
  signInWithAuth: (
    email: string,
    password: string,
  ) => Promise<{
    data?: {
      session?: {
        access_token: string;
        refresh_token: string;
        expires_in: number;
        token_type: string;
        user: { id: string };
      };
    };
    error?: { message: string; status?: number };
  }>;
  resetPasswordForEmail: (
    email: string,
  ) => Promise<{ error?: { message: string } }>;
  sleep?: (ms: number) => Promise<void>;
};

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
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
      ...corsHeaders,
      ...headers,
    },
  });

function validateEmail(email: unknown): boolean {
  if (typeof email !== "string") return false;
  const trimmed = email.trim();
  return /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(trimmed);
}

function validatePassword(password: unknown): boolean {
  if (typeof password !== "string") return false;
  if (password.length < 8 || password.length > 72) return false;
  const hasLetter = /[A-Za-z]/.test(password);
  const hasDigit = /[0-9]/.test(password);
  return hasLetter && hasDigit;
}

function getClientIp(request: Request): string {
  const cfConnectingIp = request.headers.get("cf-connecting-ip");
  if (cfConnectingIp) return cfConnectingIp.trim();
  const xRealIp = request.headers.get("x-real-ip");
  if (xRealIp) return xRealIp.trim();
  const forwardedFor = request.headers.get("x-forwarded-for");
  if (forwardedFor) return forwardedFor.split(",")[0].trim();
  return "127.0.0.1";
}

export function createAuthAccessHandler(deps: Dependencies) {
  return async (request: Request): Promise<Response> => {
    if (request.method === "OPTIONS") {
      return new Response("ok", { headers: corsHeaders });
    }

    const url = new URL(request.url);
    const path = url.pathname.split("/").filter(Boolean).pop();

    if (request.method !== "POST") {
      return json({ status: 405, title: "Method Not Allowed" }, 405);
    }

    let body: Record<string, unknown>;
    try {
      body = await request.json();
    } catch {
      return json({ status: 400, title: "Invalid JSON body" }, 400);
    }

    const clientIp = getClientIp(request);
    const normalizedEmail = typeof body.email === "string" ? body.email.trim().toLowerCase() : "";

    async function checkRateLimits(operation: string): Promise<RateBucketResult> {
      // Partition by operation and client IP
      const ipRate = await deps.consumeRateBucket(`${operation}:ip:${clientIp}`);
      if (!ipRate.allowed) return ipRate;

      // Partition by operation and identity (email)
      if (normalizedEmail) {
        const idRate = await deps.consumeRateBucket(`${operation}:id:${normalizedEmail}`);
        if (!idRate.allowed) return idRate;
      }
      return { allowed: true, retryAfterSeconds: 0 };
    }

    async function checkCaptcha(): Promise<boolean> {
      if (!deps.validateCaptcha) return true;
      const token = body.captchaToken ?? body.captchaProof;
      if (typeof token !== "string" || !token.trim()) return false;
      return deps.validateCaptcha(token.trim(), clientIp);
    }

    if (path === "register") {
      const { email, password } = body;
      if (!validateEmail(email) || !validatePassword(password)) {
        return json(
          {
            status: 422,
            title: "Invalid email or password policy",
            detail: "Password must be 8-72 chars with letter and number.",
          },
          422,
        );
      }

      if (!(await checkCaptcha())) {
        return json(
          {
            status: 403,
            title: "CAPTCHA Verification Failed",
            detail: "La verificación de seguridad falló. Intente nuevamente.",
          },
          403,
        );
      }

      const rate = await checkRateLimits("register");
      if (!rate.allowed) {
        return json(
          {
            status: 429,
            title: "Too Many Requests",
            retry_after_seconds: rate.retryAfterSeconds,
          },
          429,
          { "Retry-After": rate.retryAfterSeconds.toString() },
        );
      }

      const exists = await deps.checkEmailExists(normalizedEmail);
      if (exists) {
        // FR-051: Disclosure limited to registration
        return json(
          {
            status: 409,
            title: "Account already exists",
            detail: "El correo ingresado ya está asociado a una cuenta.",
          },
          409,
        );
      }

      const { error } = await deps.signUpWithAuth(normalizedEmail, password as string);
      if (error) {
        return json(
          {
            status: 503,
            title: "Registration unavailable",
            detail: "No se pudo procesar el registro con el proveedor de identidad.",
          },
          503,
        );
      }

      return json({ status: "ACCEPTED", confirmationRequired: true }, 201);
    }

    if (path === "login") {
      const { email, password } = body;
      if (!validateEmail(email) || !validatePassword(password)) {
        // FR-004: Neutral response
        return json(
          {
            status: 401,
            title: "Authentication Rejected",
            detail: "Correo o contraseña incorrectos.",
          },
          401,
        );
      }

      if (!(await checkCaptcha())) {
        return json(
          {
            status: 403,
            title: "CAPTCHA Verification Failed",
            detail: "La verificación de seguridad falló. Intente nuevamente.",
          },
          403,
        );
      }

      const rate = await checkRateLimits("login");
      if (!rate.allowed) {
        return json(
          {
            status: 429,
            title: "Too Many Requests",
            retry_after_seconds: rate.retryAfterSeconds,
          },
          429,
          { "Retry-After": rate.retryAfterSeconds.toString() },
        );
      }

      const authResponse = await deps.signInWithAuth(normalizedEmail, password as string);

      if (authResponse.error || !authResponse.data?.session) {
        return json(
          {
            status: 401,
            title: "Authentication Rejected",
            detail: "Correo o contraseña incorrectos.",
          },
          401,
        );
      }

      const session = authResponse.data.session;
      return json(
        {
          accessToken: session.access_token,
          refreshToken: session.refresh_token,
          expiresIn: session.expires_in,
          tokenType: session.token_type,
          userId: session.user.id,
        },
        200,
        {
          "Cache-Control": "private, no-store",
          "Pragma": "no-cache",
        },
      );
    }

    if (path === "recovery") {
      const { email } = body;

      if (!(await checkCaptcha())) {
        return json(
          {
            status: 403,
            title: "CAPTCHA Verification Failed",
            detail: "La verificación de seguridad falló. Intente nuevamente.",
          },
          403,
        );
      }

      const rate = await checkRateLimits("recovery");
      if (!rate.allowed) {
        return json(
          {
            status: 429,
            title: "Too Many Requests",
            retry_after_seconds: rate.retryAfterSeconds,
          },
          429,
          { "Retry-After": rate.retryAfterSeconds.toString() },
        );
      }

      if (validateEmail(email)) {
        await deps.resetPasswordForEmail(normalizedEmail);
      }

      // Timing normalization to protect against timing enumeration (SC-002)
      if (deps.sleep) {
        await deps.sleep(50);
      }

      return json({ status: "ACCEPTED" }, 202);
    }

    return json({ status: 404, title: "Endpoint Not Found" }, 404);
  };
}

export function createDefaultDeps(): Dependencies {
  const getBaseUrl = () => Deno.env.get("SUPABASE_URL") ?? "";
  const getAnonKey = () =>
    Deno.env.get("SUPABASE_ANON_KEY") ??
    Deno.env.get("SUPABASE_PUBLISHABLE_KEY") ??
    "";
  const getServiceRoleKey = () =>
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ??
    getAnonKey();

  return {
    consumeRateBucket: async (bucketKey: string): Promise<RateBucketResult> => {
      const baseUrl = getBaseUrl();
      const serviceKey = getServiceRoleKey();
      if (!baseUrl || !serviceKey) {
        // FR-049/FR-050: Fail closed if rate limiting cannot be enforced
        return { allowed: false, retryAfterSeconds: 60 };
      }
      try {
        const res = await fetch(`${baseUrl}/rest/v1/rpc/consume_auth_rate_bucket`, {
          method: "POST",
          headers: {
            apikey: serviceKey,
            Authorization: `Bearer ${serviceKey}`,
            "content-type": "application/json",
          },
          body: JSON.stringify({ p_bucket_key: bucketKey }),
        });
        if (!res.ok) {
          return { allowed: false, retryAfterSeconds: 60 };
        }
        const data = await res.json();
        return {
          allowed: data?.allowed ?? false,
          retryAfterSeconds: data?.retry_after_seconds ?? 60,
        };
      } catch {
        return { allowed: false, retryAfterSeconds: 60 };
      }
    },

    checkEmailExists: async (email: string): Promise<boolean> => {
      const baseUrl = getBaseUrl();
      const serviceKey = getServiceRoleKey();
      if (!baseUrl || !serviceKey) {
        return false;
      }
      try {
        const res = await fetch(`${baseUrl}/rest/v1/rpc/check_email_exists`, {
          method: "POST",
          headers: {
            apikey: serviceKey,
            Authorization: `Bearer ${serviceKey}`,
            "content-type": "application/json",
          },
          body: JSON.stringify({ p_email: email }),
        });
        if (!res.ok) {
          return false;
        }
        const exists = await res.json();
        return Boolean(exists);
      } catch {
        return false;
      }
    },

    validateCaptcha: async (token: string): Promise<boolean> => {
      const captchaSecret = Deno.env.get("CAPTCHA_SECRET_KEY");
      if (!captchaSecret) {
        // Allow in development/test if no secret configured
        return true;
      }
      try {
        const res = await fetch("https://challenges.cloudflare.com/turnstile/v0/siteverify", {
          method: "POST",
          headers: { "content-type": "application/json" },
          body: JSON.stringify({
            secret: captchaSecret,
            response: token,
          }),
        });
        if (!res.ok) return false;
        const data = await res.json();
        return Boolean(data.success);
      } catch {
        return false;
      }
    },

    signUpWithAuth: async (email: string, password: string) => {
      const baseUrl = getBaseUrl();
      const anonKey = getAnonKey();
      const res = await fetch(`${baseUrl}/auth/v1/signup`, {
        method: "POST",
        headers: {
          apikey: anonKey,
          "content-type": "application/json",
        },
        body: JSON.stringify({ email, password }),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({ message: "Sign up error" }));
        return {
          error: {
            message: err.msg || err.message || err.error_description || "Sign up error",
            status: res.status,
          },
        };
      }
      return {};
    },

    signInWithAuth: async (email: string, password: string) => {
      const baseUrl = getBaseUrl();
      const anonKey = getAnonKey();
      const res = await fetch(`${baseUrl}/auth/v1/token?grant_type=password`, {
        method: "POST",
        headers: {
          apikey: anonKey,
          "content-type": "application/json",
        },
        body: JSON.stringify({ email, password }),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({ message: "Invalid credentials" }));
        return {
          error: {
            message: err.msg || err.error_description || "Invalid credentials",
            status: res.status,
          },
        };
      }
      const data = await res.json();
      return {
        data: {
          session: {
            access_token: data.access_token,
            refresh_token: data.refresh_token,
            expires_in: data.expires_in,
            token_type: data.token_type,
            user: { id: data.user.id },
          },
        },
      };
    },

    resetPasswordForEmail: async (email: string) => {
      const baseUrl = getBaseUrl();
      const anonKey = getAnonKey();
      const res = await fetch(`${baseUrl}/auth/v1/recover`, {
        method: "POST",
        headers: {
          apikey: anonKey,
          "content-type": "application/json",
        },
        body: JSON.stringify({ email }),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({ message: "Password recovery failed" }));
        return { error: { message: err.msg || err.message || "Password recovery failed" } };
      }
      return {};
    },

    sleep: (ms: number) => new Promise((resolve) => setTimeout(resolve, ms)),
  };
}

if (import.meta.main) {
  Deno.serve(createAuthAccessHandler(createDefaultDeps()));
}
