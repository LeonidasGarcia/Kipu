-- EP-CCO: seed the Peru 2026 system taxonomy and curated Priority A merchants.
-- Merchant defaults remain suggestions; existing movement classifications are untouched.
BEGIN;

-- A system taxonomy with a strict root/subcategory shape. Existing root IDs 1-3
-- are retained so category references and user presentations keep their identity.
INSERT INTO public.categories (
    id, user_id, parent_id, name, icon_key, color_argb, origin, is_system,
    is_active, revision, remote_revision, category_type
)
VALUES
    ('00000000-0000-0000-0000-000000000001', NULL, NULL, 'Alimentación', 'restaurant', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000002', NULL, NULL, 'Transporte', 'directions_car', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000003', NULL, NULL, 'Servicios', 'receipt', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000004', NULL, NULL, 'Suscripciones', 'tv', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000005', NULL, NULL, 'Salud', 'medical_services', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000006', NULL, NULL, 'Compras', 'shopping_cart', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000007', NULL, NULL, 'Ocio y entretenimiento', 'movie', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000008', NULL, NULL, 'Viajes y alojamiento', 'flight', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000009', NULL, NULL, 'Finanzas y seguros', 'account_balance', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000010', NULL, NULL, 'Educación', 'school', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000011', NULL, NULL, 'Mascotas', 'pets', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000012', NULL, NULL, 'Estado y trámites', 'receipt', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL')
ON CONFLICT (id) DO UPDATE SET
    user_id = EXCLUDED.user_id,
    parent_id = NULL,
    name = EXCLUDED.name,
    icon_key = EXCLUDED.icon_key,
    origin = 'SYSTEM',
    is_system = true,
    is_active = true,
    category_type = 'GENERAL',
    remote_revision = GREATEST(public.categories.remote_revision, EXCLUDED.remote_revision),
    revision = GREATEST(public.categories.revision, EXCLUDED.revision),
    updated_at = now();

-- Subcategory IDs are stable. Every parent below is one of the roots seeded above,
-- so check_category_two_levels accepts the tree and no third level is introduced.
INSERT INTO public.categories (
    id, user_id, parent_id, name, icon_key, color_argb, origin, is_system,
    is_active, revision, remote_revision, category_type
)
VALUES
    ('00000000-0000-0000-0000-000000000101', NULL, '00000000-0000-0000-0000-000000000001', 'Supermercados', 'shopping_cart', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000102', NULL, '00000000-0000-0000-0000-000000000001', 'Tiendas de conveniencia', 'storefront', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000103', NULL, '00000000-0000-0000-0000-000000000001', 'Tiendas de descuento y proximidad', 'storefront', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000104', NULL, '00000000-0000-0000-0000-000000000001', 'Fast food', 'restaurant', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000105', NULL, '00000000-0000-0000-0000-000000000001', 'Cafeterías', 'local_cafe', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000106', NULL, '00000000-0000-0000-0000-000000000001', 'Delivery y agregadores', 'delivery_dining', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000201', NULL, '00000000-0000-0000-0000-000000000002', 'Taxi y movilidad por aplicativo', 'directions_car', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000202', NULL, '00000000-0000-0000-0000-000000000002', 'Combustibles y grifos', 'local_gas_station', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000203', NULL, '00000000-0000-0000-0000-000000000002', 'Transporte público urbano', 'directions_transit', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000204', NULL, '00000000-0000-0000-0000-000000000002', 'Peajes', 'toll', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000205', NULL, '00000000-0000-0000-0000-000000000002', 'Aerolíneas', 'flight', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000301', NULL, '00000000-0000-0000-0000-000000000003', 'Electricidad', 'electric_bolt', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000302', NULL, '00000000-0000-0000-0000-000000000003', 'Agua potable y saneamiento', 'water_drop', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000303', NULL, '00000000-0000-0000-0000-000000000003', 'Gas natural', 'local_fire_department', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000304', NULL, '00000000-0000-0000-0000-000000000003', 'Telecomunicaciones e internet', 'wifi', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000401', NULL, '00000000-0000-0000-0000-000000000004', 'Streaming de video', 'tv', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000402', NULL, '00000000-0000-0000-0000-000000000004', 'Streaming de música', 'music_note', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000403', NULL, '00000000-0000-0000-0000-000000000004', 'Almacenamiento en la nube', 'cloud', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000404', NULL, '00000000-0000-0000-0000-000000000004', 'Inteligencia artificial', 'smart_toy', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000405', NULL, '00000000-0000-0000-0000-000000000004', 'Productividad y software', 'terminal', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000406', NULL, '00000000-0000-0000-0000-000000000004', 'Videojuegos', 'sports_esports', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000501', NULL, '00000000-0000-0000-0000-000000000005', 'Farmacias y boticas', 'medical_services', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000502', NULL, '00000000-0000-0000-0000-000000000005', 'Gimnasios', 'fitness_center', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000601', NULL, '00000000-0000-0000-0000-000000000006', 'Tiendas por departamento', 'shopping_bag', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000602', NULL, '00000000-0000-0000-0000-000000000006', 'Mejoramiento del hogar', 'home', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000603', NULL, '00000000-0000-0000-0000-000000000006', 'Marketplaces y comercio electrónico', 'shopping_cart', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000701', NULL, '00000000-0000-0000-0000-000000000007', 'Cine', 'movie', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000702', NULL, '00000000-0000-0000-0000-000000000007', 'Eventos y entradas', 'confirmation_number', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000801', NULL, '00000000-0000-0000-0000-000000000008', 'Hoteles y alojamiento', 'hotel', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000901', NULL, '00000000-0000-0000-0000-000000000009', 'Bancos', 'account_balance', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000902', NULL, '00000000-0000-0000-0000-000000000009', 'Billeteras y medios de pago', 'account_balance_wallet', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000903', NULL, '00000000-0000-0000-0000-000000000009', 'Seguros', 'security', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000000904', NULL, '00000000-0000-0000-0000-000000000009', 'Pasarelas y servicios financieros', 'payments', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000001001', NULL, '00000000-0000-0000-0000-000000000010', 'Universidades e institutos', 'school', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000001002', NULL, '00000000-0000-0000-0000-000000000010', 'Cursos y formación', 'school', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000001101', NULL, '00000000-0000-0000-0000-000000000011', 'Veterinarias', 'pets', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000001102', NULL, '00000000-0000-0000-0000-000000000011', 'Tiendas de mascotas', 'pets', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000001201', NULL, '00000000-0000-0000-0000-000000000012', 'Impuestos y tasas', 'receipt', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL'),
    ('00000000-0000-0000-0000-000000001202', NULL, '00000000-0000-0000-0000-000000000012', 'Trámites y documentos', 'assignment', NULL, 'SYSTEM', true, true, 1, 1, 'GENERAL')
ON CONFLICT (id) DO UPDATE SET
    user_id = EXCLUDED.user_id,
    parent_id = EXCLUDED.parent_id,
    name = EXCLUDED.name,
    icon_key = EXCLUDED.icon_key,
    origin = 'SYSTEM',
    is_system = true,
    is_active = true,
    category_type = 'GENERAL',
    remote_revision = GREATEST(public.categories.remote_revision, EXCLUDED.remote_revision),
    revision = GREATEST(public.categories.revision, EXCLUDED.revision),
    updated_at = now();

-- Extend the existing system catalog without deleting or rebuilding rows.
ALTER TABLE public.merchant_services
    ADD COLUMN IF NOT EXISTS default_category_id uuid REFERENCES public.categories(id) ON DELETE RESTRICT;
ALTER TABLE public.merchant_services
    ADD COLUMN IF NOT EXISTS priority text NOT NULL DEFAULT 'B';
ALTER TABLE public.merchant_services
    ADD COLUMN IF NOT EXISTS logo_key text;
ALTER TABLE public.merchant_services
    ADD COLUMN IF NOT EXISTS brand_color text;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.merchant_services'::regclass
          AND conname = 'chk_merchant_services_priority'
    ) THEN
        ALTER TABLE public.merchant_services
            ADD CONSTRAINT chk_merchant_services_priority
            CHECK (priority IN ('A', 'B', 'C'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.merchant_services'::regclass
          AND conname = 'chk_merchant_services_brand_color'
    ) THEN
        ALTER TABLE public.merchant_services
            ADD CONSTRAINT chk_merchant_services_brand_color
            CHECK (brand_color IS NULL OR brand_color ~ '^#[0-9A-Fa-f]{6}$');
    END IF;
END;
$$;

CREATE INDEX IF NOT EXISTS idx_merchant_services_default_category
    ON public.merchant_services(default_category_id)
    WHERE is_active = true;

-- Bump the shared catalog version so already-installed clients fetch these fields.
WITH catalog_version AS (
    SELECT COALESCE(MAX(version), 0) + 1 AS value FROM public.merchant_services
)
INSERT INTO public.merchant_services (
    id, user_id, name, normalized_name, is_system, is_active, version,
    default_category_id, priority, logo_key, brand_color
)
SELECT seed.id, NULL, seed.name, seed.normalized_name, true, true, catalog_version.value,
       seed.default_category_id, 'A', seed.logo_key, seed.brand_color
FROM catalog_version
CROSS JOIN (VALUES
    -- Preserve the deployed IDs for these legacy rows so references remain stable.
    ('00000000-0000-0001-0000-000000000001'::uuid, 'Tambo', 'tambo', '00000000-0000-0000-0000-000000000102'::uuid, 'ic_merchant_tambo', '#652D90'),
    ('00000000-0000-0001-0000-000000000002'::uuid, 'Starbucks', 'starbucks', '00000000-0000-0000-0000-000000000105'::uuid, 'ic_merchant_starbucks', '#006241'),
    ('00000000-0000-0001-0000-000000000003'::uuid, 'Plaza Vea', 'plaza vea', '00000000-0000-0000-0000-000000000101'::uuid, 'ic_merchant_plaza_vea', '#C8102E'),
    ('00000000-0000-0000-0001-000000000004'::uuid, 'Tottus', 'tottus', '00000000-0000-0000-0000-000000000101'::uuid, 'ic_merchant_tottus', '#E2231A'),
    ('00000000-0000-0000-0001-000000000005'::uuid, 'Metro', 'metro', '00000000-0000-0000-0000-000000000101'::uuid, 'ic_merchant_metro', '#E5AB00'),
    ('00000000-0000-0000-0001-000000000006'::uuid, 'Wong', 'wong', '00000000-0000-0000-0000-000000000101'::uuid, 'ic_merchant_wong', '#D5001C'),
    ('00000000-0000-0000-0001-000000000007'::uuid, 'Mass', 'mass', '00000000-0000-0000-0000-000000000103'::uuid, 'ic_merchant_mass', '#FF5A00'),
    ('00000000-0000-0000-0001-000000000008'::uuid, 'Oxxo', 'oxxo', '00000000-0000-0000-0000-000000000102'::uuid, 'ic_merchant_oxxo', '#D71920'),
    ('00000000-0000-0000-0001-000000000009'::uuid, 'Listo!', 'listo', '00000000-0000-0000-0000-000000000102'::uuid, 'ic_merchant_listo', '#E2231A'),
    ('00000000-0000-0000-0001-000000000010'::uuid, 'KFC', 'kfc', '00000000-0000-0000-0000-000000000104'::uuid, 'ic_merchant_kfc', '#F40027'),
    ('00000000-0000-0000-0001-000000000011'::uuid, 'McDonald''s', 'mcdonalds', '00000000-0000-0000-0000-000000000104'::uuid, 'ic_merchant_mcdonalds', '#FFC72C'),
    ('00000000-0000-0000-0001-000000000012'::uuid, 'Bembos', 'bembos', '00000000-0000-0000-0000-000000000104'::uuid, 'ic_merchant_bembos', '#E84F28'),
    ('00000000-0000-0000-0001-000000000013'::uuid, 'Burger King', 'burger king', '00000000-0000-0000-0000-000000000104'::uuid, 'ic_merchant_burger_king', '#D62300'),
    ('00000000-0000-0000-0001-000000000014'::uuid, 'Pizza Hut', 'pizza hut', '00000000-0000-0000-0000-000000000104'::uuid, 'ic_merchant_pizza_hut', '#EE3124'),
    ('00000000-0000-0000-0001-000000000015'::uuid, 'Rappi', 'rappi', '00000000-0000-0000-0000-000000000106'::uuid, 'ic_merchant_rappi', '#FF441F'),
    ('00000000-0000-0000-0001-000000000016'::uuid, 'PedidosYa', 'pedidosya', '00000000-0000-0000-0000-000000000106'::uuid, 'ic_merchant_pedidosya', '#FA0050'),
    ('00000000-0000-0000-0001-000000000017'::uuid, 'Inkafarma', 'inkafarma', '00000000-0000-0000-0000-000000000501'::uuid, 'ic_merchant_inkafarma', '#EF3340'),
    ('00000000-0000-0000-0001-000000000018'::uuid, 'Mifarma', 'mifarma', '00000000-0000-0000-0000-000000000501'::uuid, 'ic_merchant_mifarma', '#FC5C7D'),
    ('00000000-0000-0000-0001-000000000019'::uuid, 'Uber', 'uber', '00000000-0000-0000-0000-000000000201'::uuid, 'ic_merchant_uber', '#000000'),
    ('00000000-0000-0000-0001-000000000020'::uuid, 'inDrive', 'indrive', '00000000-0000-0000-0000-000000000201'::uuid, 'ic_merchant_indrive', '#75C044'),
    ('00000000-0000-0000-0001-000000000021'::uuid, 'DiDi', 'didi', '00000000-0000-0000-0000-000000000201'::uuid, 'ic_merchant_didi', '#FF7E00'),
    ('00000000-0000-0000-0001-000000000022'::uuid, 'Cabify', 'cabify', '00000000-0000-0000-0000-000000000201'::uuid, 'ic_merchant_cabify', '#7145D6'),
    ('00000000-0000-0000-0001-000000000023'::uuid, 'Primax', 'primax', '00000000-0000-0000-0000-000000000202'::uuid, 'ic_merchant_primax', '#CE0037'),
    ('00000000-0000-0000-0001-000000000024'::uuid, 'Repsol', 'repsol', '00000000-0000-0000-0000-000000000202'::uuid, 'ic_merchant_repsol', '#FF6200'),
    ('00000000-0000-0000-0001-000000000025'::uuid, 'Petroperú', 'petroperu', '00000000-0000-0000-0000-000000000202'::uuid, 'ic_merchant_petroperu', '#E30613'),
    ('00000000-0000-0000-0001-000000000026'::uuid, 'Metropolitano', 'metropolitano', '00000000-0000-0000-0000-000000000203'::uuid, 'ic_merchant_metropolitano', '#00529B'),
    ('00000000-0000-0000-0001-000000000027'::uuid, 'Línea 1', 'linea 1', '00000000-0000-0000-0000-000000000203'::uuid, 'ic_merchant_linea_1', '#00A0DF'),
    ('00000000-0000-0000-0001-000000000028'::uuid, 'Rutas de Lima', 'rutas de lima', '00000000-0000-0000-0000-000000000204'::uuid, 'ic_merchant_rutas_de_lima', '#009FE3'),
    ('00000000-0000-0000-0001-000000000029'::uuid, 'Lima Expresa', 'lima expresa', '00000000-0000-0000-0000-000000000204'::uuid, 'ic_merchant_lima_expresa', '#EF7D00'),
    ('00000000-0000-0000-0001-000000000030'::uuid, 'LATAM Airlines', 'latam airlines', '00000000-0000-0000-0000-000000000205'::uuid, 'ic_merchant_latam', '#1B0088'),
    ('00000000-0000-0000-0001-000000000031'::uuid, 'Pluz Energía', 'pluz energia', '00000000-0000-0000-0000-000000000301'::uuid, 'ic_merchant_pluz_energia', '#FF7500'),
    ('00000000-0000-0000-0001-000000000032'::uuid, 'Luz del Sur', 'luz del sur', '00000000-0000-0000-0000-000000000301'::uuid, 'ic_merchant_luz_del_sur', '#EA1C24'),
    ('00000000-0000-0000-0001-000000000033'::uuid, 'Sedapal', 'sedapal', '00000000-0000-0000-0000-000000000302'::uuid, 'ic_merchant_sedapal', '#00529B'),
    ('00000000-0000-0000-0001-000000000034'::uuid, 'Cálidda', 'calidda', '00000000-0000-0000-0000-000000000303'::uuid, 'ic_merchant_calidda', '#00A3E0'),
    ('00000000-0000-0000-0001-000000000035'::uuid, 'Claro', 'claro', '00000000-0000-0000-0000-000000000304'::uuid, 'ic_merchant_claro', '#DA291C'),
    ('00000000-0000-0000-0001-000000000036'::uuid, 'Movistar', 'movistar', '00000000-0000-0000-0000-000000000304'::uuid, 'ic_merchant_movistar', '#019DF4'),
    ('00000000-0000-0000-0001-000000000037'::uuid, 'Entel', 'entel', '00000000-0000-0000-0000-000000000304'::uuid, 'ic_merchant_entel', '#FF3A00'),
    ('00000000-0000-0000-0001-000000000038'::uuid, 'Bitel', 'bitel', '00000000-0000-0000-0000-000000000304'::uuid, 'ic_merchant_bitel', '#F6CD46'),
    ('00000000-0000-0000-0001-000000000039'::uuid, 'Win', 'win', '00000000-0000-0000-0000-000000000304'::uuid, 'ic_merchant_win', '#EC028C'),
    ('00000000-0000-0000-0001-000000000040'::uuid, 'Netflix', 'netflix', '00000000-0000-0000-0000-000000000401'::uuid, 'ic_merchant_netflix', '#E50914'),
    ('00000000-0000-0000-0001-000000000041'::uuid, 'Disney+', 'disney plus', '00000000-0000-0000-0000-000000000401'::uuid, 'ic_merchant_disney_plus', '#113CCF'),
    ('00000000-0000-0000-0001-000000000042'::uuid, 'Prime Video', 'prime video', '00000000-0000-0000-0000-000000000401'::uuid, 'ic_merchant_prime_video', '#00A8E0'),
    ('00000000-0000-0000-0001-000000000043'::uuid, 'Spotify', 'spotify', '00000000-0000-0000-0000-000000000402'::uuid, 'ic_merchant_spotify', '#1DB954'),
    ('00000000-0000-0000-0001-000000000044'::uuid, 'Google One', 'google one', '00000000-0000-0000-0000-000000000403'::uuid, 'ic_merchant_google_one', '#4285F4'),
    ('00000000-0000-0000-0001-000000000045'::uuid, 'iCloud+', 'icloud', '00000000-0000-0000-0000-000000000403'::uuid, 'ic_merchant_icloud_plus', '#3693F3'),
    ('00000000-0000-0000-0001-000000000046'::uuid, 'Max', 'max', '00000000-0000-0000-0000-000000000401'::uuid, 'ic_merchant_max', '#002BE7'),
    ('00000000-0000-0000-0001-000000000047'::uuid, 'YouTube Premium', 'youtube premium', '00000000-0000-0000-0000-000000000401'::uuid, 'ic_merchant_youtube_premium', '#FF0000'),
    ('00000000-0000-0000-0001-000000000048'::uuid, 'Crunchyroll', 'crunchyroll', '00000000-0000-0000-0000-000000000401'::uuid, 'ic_merchant_crunchyroll', '#F47521'),
    ('00000000-0000-0000-0001-000000000049'::uuid, 'ChatGPT', 'chatgpt', '00000000-0000-0000-0000-000000000404'::uuid, 'ic_merchant_chatgpt', '#10A37F'),
    ('00000000-0000-0000-0001-000000000050'::uuid, 'Claude', 'claude', '00000000-0000-0000-0000-000000000404'::uuid, 'ic_merchant_claude', '#CC785C'),
    ('00000000-0000-0000-0001-000000000051'::uuid, 'Google AI Pro', 'google ai pro', '00000000-0000-0000-0000-000000000404'::uuid, 'ic_merchant_google_ai_pro', '#4285F4'),
    ('00000000-0000-0000-0001-000000000052'::uuid, 'Perplexity', 'perplexity', '00000000-0000-0000-0000-000000000404'::uuid, 'ic_merchant_perplexity', '#20B2AA'),
    ('00000000-0000-0000-0001-000000000053'::uuid, 'Canva', 'canva', '00000000-0000-0000-0000-000000000405'::uuid, 'ic_merchant_canva', '#00C4CC'),
    ('00000000-0000-0000-0001-000000000054'::uuid, 'Microsoft 365', 'microsoft 365', '00000000-0000-0000-0000-000000000405'::uuid, 'ic_merchant_microsoft_365', '#D83B01'),
    ('00000000-0000-0000-0001-000000000055'::uuid, 'Adobe', 'adobe', '00000000-0000-0000-0000-000000000405'::uuid, 'ic_merchant_adobe', '#FF0000'),
    ('00000000-0000-0000-0001-000000000056'::uuid, 'Notion', 'notion', '00000000-0000-0000-0000-000000000405'::uuid, 'ic_merchant_notion', '#000000'),
    ('00000000-0000-0000-0001-000000000057'::uuid, 'Steam', 'steam', '00000000-0000-0000-0000-000000000406'::uuid, 'ic_merchant_steam', '#171A21'),
    ('00000000-0000-0000-0001-000000000058'::uuid, 'PlayStation', 'playstation', '00000000-0000-0000-0000-000000000406'::uuid, 'ic_merchant_playstation', '#003791'),
    ('00000000-0000-0000-0001-000000000059'::uuid, 'Xbox', 'xbox', '00000000-0000-0000-0000-000000000406'::uuid, 'ic_merchant_xbox', '#107C10'),
    ('00000000-0000-0000-0001-000000000060'::uuid, 'Cineplanet', 'cineplanet', '00000000-0000-0000-0000-000000000701'::uuid, 'ic_merchant_cineplanet', '#004B93'),
    ('00000000-0000-0000-0001-000000000061'::uuid, 'Cinemark', 'cinemark', '00000000-0000-0000-0000-000000000701'::uuid, 'ic_merchant_cinemark', '#ED1C24'),
    ('00000000-0000-0000-0001-000000000062'::uuid, 'Teleticket', 'teleticket', '00000000-0000-0000-0000-000000000702'::uuid, 'ic_merchant_teleticket', '#E50014'),
    ('00000000-0000-0000-0001-000000000063'::uuid, 'Joinnus', 'joinnus', '00000000-0000-0000-0000-000000000702'::uuid, 'ic_merchant_joinnus', '#19E4A9')
) AS seed(id, name, normalized_name, default_category_id, logo_key, brand_color)
ON CONFLICT (id) DO UPDATE SET
    user_id = NULL,
    name = EXCLUDED.name,
    normalized_name = EXCLUDED.normalized_name,
    is_system = true,
    is_active = true,
    version = EXCLUDED.version,
    default_category_id = EXCLUDED.default_category_id,
    priority = 'A',
    logo_key = EXCLUDED.logo_key,
    brand_color = EXCLUDED.brand_color,
    updated_at = now();

-- Merchant logos are public, static brand assets. Public retrieval avoids an
-- authenticated request for an image; write operations remain protected by Storage RLS.
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('merchant-logos', 'merchant-logos', true, 524288, ARRAY['image/webp', 'image/png', 'image/jpeg'])
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    public = true,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

COMMIT;
