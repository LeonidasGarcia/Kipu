# Credit catalog regression fixture

`credit-products-2026-09-24.json` contains the 44 public BCP/BBVA/Interbank rows
from `supabase/migrations/20260925022130_s3_credit_canonical_purchase_payment.sql`,
the `s3_credit_product_seed` block. It preserves the exact published names,
networks, ranges, null values, source URLs and editorial status. `catalog_as_of`
is the migration's fixed date, 2026-09-24. IDs are deterministic test UUIDs,
not production IDs.

This fixture is packaged only in the instrumentation APK. It is supplied at the
API boundary; it does not seed the app database, perform a live API request or
update production data. The production catalog mapping, selection mapping,
registration repository and Room persistence execute normally in the tests.
