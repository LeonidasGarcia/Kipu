# HU-55 billing reconciliation operations

`reconcile-billing` runs a bounded database sweep every 15 minutes. It only reclaims expired leases and moves due hash-only work to `WAITING_FOR_TOKEN`; it never calls Google Play. A new Pub/Sub RTDN delivery or authenticated device restore must supply the current token before verification can resume.

Before scheduling, reconcile the live migration/function catalog and confirm the S3 unique token-owner index. Then enable and review `pg_cron`, `pg_net`, and Vault for that project. The live baseline observed on 2026-10-08 had none of these scheduling extensions enabled, so the cadence file was not applied.

Store these values in Vault without committing them:

- `project_url`: the Supabase project URL.
- `publishable_key`: the project publishable API key used by the Edge Function gateway.
- `reconcile_billing_secret`: a random secret with at least 32 bytes; set the same value as the Edge Function secret `RECONCILE_BILLING_SECRET`.

The Pub/Sub receiver has separate Edge Function secrets: `PUBSUB_OIDC_AUDIENCE`, `PUBSUB_SERVICE_ACCOUNT_EMAIL`, `PUBSUB_SUBSCRIPTION_IDENTITY`, and `GOOGLE_PLAY_PACKAGE_NAME`. Its Google OIDC identity is checked in the function before any message body is parsed. Google Play service account credentials remain the existing server-side verifier secrets.

After the remote gates are approved, apply `billing-reconciliation-cron.sql` once. It registers `kipu-billing-reconciliation-s5` on the 15-minute cadence and calls only `/functions/v1/reconcile-billing`. Review `cron.job_run_details` and Edge Function logs for status codes and sanitized counters. Do not include purchase tokens or raw RTDN payloads in logs or run metadata.

The local Supabase stack used for HU-55 validation does not have `pg_cron`, `pg_net`, or Vault enabled. The SQL schedule recipe is therefore operationally versioned but remains unexecuted pending the live baseline and P30 §4.3–4.4 alignment gates.
