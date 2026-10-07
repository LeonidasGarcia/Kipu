-- Incremental migration: update merchant catalog logo_key for Listo!, Metropolitano, and Rutas de Lima
-- Preserves immutable migration history by supplying a clean, idempotent update for deployed databases.

DO $$
DECLARE
    v_next_version bigint;
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.merchant_services
        WHERE (id = '00000000-0000-0000-0001-000000000009'::uuid AND logo_key IS DISTINCT FROM 'ic_merchant_listo')
           OR (id = '00000000-0000-0000-0001-000000000026'::uuid AND logo_key IS DISTINCT FROM 'ic_merchant_metropolitano')
           OR (id = '00000000-0000-0000-0001-000000000028'::uuid AND logo_key IS DISTINCT FROM 'ic_merchant_rutas_de_lima')
    ) THEN
        SELECT COALESCE(MAX(version), 0) + 1 INTO v_next_version FROM public.merchant_services;

        UPDATE public.merchant_services
        SET logo_key = 'ic_merchant_listo',
            version = v_next_version
        WHERE id = '00000000-0000-0000-0001-000000000009'::uuid
          AND logo_key IS DISTINCT FROM 'ic_merchant_listo';

        UPDATE public.merchant_services
        SET logo_key = 'ic_merchant_metropolitano',
            version = v_next_version
        WHERE id = '00000000-0000-0000-0001-000000000026'::uuid
          AND logo_key IS DISTINCT FROM 'ic_merchant_metropolitano';

        UPDATE public.merchant_services
        SET logo_key = 'ic_merchant_rutas_de_lima',
            version = v_next_version
        WHERE id = '00000000-0000-0000-0001-000000000028'::uuid
          AND logo_key IS DISTINCT FROM 'ic_merchant_rutas_de_lima';
    END IF;
END $$;
