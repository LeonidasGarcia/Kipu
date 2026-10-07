-- Incremental migration: update merchant catalog logo_key for Listo!, Metropolitano, and Rutas de Lima
-- Preserves immutable migration history by supplying a clean, idempotent update for deployed databases.

UPDATE public.merchants
SET logo_key = 'ic_merchant_listo', updated_at = now()
WHERE id = '00000000-0000-0000-0001-000000000009'::uuid;

UPDATE public.merchants
SET logo_key = 'ic_merchant_metropolitano', updated_at = now()
WHERE id = '00000000-0000-0000-0001-000000000026'::uuid;

UPDATE public.merchants
SET logo_key = 'ic_merchant_rutas_de_lima', updated_at = now()
WHERE id = '00000000-0000-0000-0001-000000000028'::uuid;
