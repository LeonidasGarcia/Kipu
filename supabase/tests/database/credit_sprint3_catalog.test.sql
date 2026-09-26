BEGIN;
SELECT plan(13);

SELECT is(
    (SELECT count(*)::integer FROM public.credit_products WHERE is_catalog_listed AND institution_code IN ('BCP','BBVA','INTERBANK')),
    44,
    'Exactly 44 Sprint 3 catalog products are active'
);
SELECT is((SELECT count(*)::integer FROM public.credit_products WHERE is_catalog_listed AND institution_code='BCP'),18,'BCP has 18 listed products');
SELECT is((SELECT count(*)::integer FROM public.credit_products WHERE is_catalog_listed AND institution_code='BBVA'),10,'BBVA has 10 listed products');
SELECT is((SELECT count(*)::integer FROM public.credit_products WHERE is_catalog_listed AND institution_code='INTERBANK'),16,'Interbank has 16 listed products');
SELECT is(
    (SELECT count(DISTINCT institution_code || ':' || lower(product_name))::integer FROM public.credit_products WHERE is_catalog_listed AND institution_code IN ('BCP','BBVA','INTERBANK')),
    44,
    'Every listed institution/product identity is unique'
);
SELECT is(
    (SELECT count(*)::integer FROM public.credit_products WHERE is_catalog_listed AND catalog_as_of=DATE '2026-09-24'),
    44,
    'All listed products carry the fixed 2026-09-24 source cut'
);
SELECT is(
    (SELECT count(*)::integer FROM public.credit_products WHERE is_catalog_listed AND effective_to IS NULL),
    44,
    'No catalog product receives software auto-expiry'
);
SELECT is(
    (SELECT count(*)::integer FROM public.credit_products WHERE is_catalog_listed AND source_url IS NOT NULL AND published_tea_summary IS NOT NULL AND membership_condition IS NOT NULL),
    44,
    'Each product preserves source URL, TEA description, and membership condition/data state'
);
SELECT is(
    (SELECT count(*)::integer FROM public.credit_products WHERE is_catalog_listed AND institution_code IN ('BCP','BBVA') AND reference_tea_pen_min_bps IS NOT NULL AND reference_tea_usd_min_bps IS NOT NULL),
    26,
    'Currency-specific PEN and USD rates are stored where the source assigns them'
);
SELECT is(
    (SELECT count(*)::integer FROM public.credit_products WHERE is_catalog_listed AND institution_code='INTERBANK' AND reference_tea_pen_min_bps IS NULL AND reference_tea_usd_min_bps IS NULL),
    16,
    'Interbank rates with no unambiguous product/currency assignment remain null instead of being inferred'
);
SELECT is(
    (SELECT reference_tea_bps FROM public.credit_products WHERE is_catalog_listed AND institution_code='BBVA' AND product_name='Visa Signature'),
    7999,
    'A unique same-currency BBVA Signature TEA remains available in the legacy reference field'
);
SELECT is(
    (SELECT membership_fee_pen_minor FROM public.credit_products WHERE is_catalog_listed AND institution_code='BCP' AND product_name='Visa Light'),
    0::bigint,
    'Visa Light preserves its explicitly stated zero membership fee'
);
SELECT ok(
    NOT has_table_privilege('anon','public.credit_products','SELECT'),
    'Anonymous clients cannot read the catalog through direct table grants'
);

SELECT * FROM finish();
ROLLBACK;
