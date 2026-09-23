-- Retain only the table privileges used by the rate limiter and catalog reader.
REVOKE TRUNCATE, REFERENCES, TRIGGER
ON TABLE private.registration_rate_buckets FROM service_role;

REVOKE TRUNCATE, REFERENCES, TRIGGER
ON TABLE public.merchant_services FROM authenticated;
