-- Migration: 20260921000003_tea_catalog.sql
-- Description: Referential TEA catalog and personal TEA persistence

-- 1. Referential Rate Catalog
CREATE TABLE IF NOT EXISTS public.referential_rate_catalog (
    id TEXT PRIMARY KEY,
    institution TEXT NOT NULL,
    product_name TEXT NOT NULL,
    currency TEXT NOT NULL REFERENCES public.currencies(code),
    min_tea_bps INTEGER NOT NULL CHECK (min_tea_bps >= 0),
    max_tea_bps INTEGER NOT NULL CHECK (max_tea_bps >= min_tea_bps),
    verified_at DATE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    disclaimer TEXT NOT NULL DEFAULT 'Las tasas mostradas son referenciales y provienen de tarifarios públicos de entidades reguladas por la SBS. La tasa real depende del contrato de cada cliente.',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.referential_rate_catalog ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.referential_rate_catalog FORCE ROW LEVEL SECURITY;

CREATE POLICY select_referential_rate_catalog ON public.referential_rate_catalog
    FOR SELECT TO authenticated
    USING (is_active = true);

-- 2. Card Personal TEA
CREATE TABLE IF NOT EXISTS public.card_personal_teas (
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    card_id TEXT NOT NULL,
    tea_bps INTEGER NOT NULL CHECK (tea_bps >= 0 AND tea_bps <= 100000),
    effective_from TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, card_id)
);

ALTER TABLE public.card_personal_teas ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.card_personal_teas FORCE ROW LEVEL SECURITY;

CREATE POLICY manage_own_card_personal_teas ON public.card_personal_teas
    FOR ALL TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- 3. Seed initial referential catalog
INSERT INTO public.referential_rate_catalog (id, institution, product_name, currency, min_tea_bps, max_tea_bps, verified_at)
VALUES 
    ('bcp-classic-pen', 'BCP', 'Visa Clásica', 'PEN', 2990, 8990, '2026-01-15'),
    ('bcp-gold-pen', 'BCP', 'Visa Oro', 'PEN', 2690, 7990, '2026-01-15'),
    ('bbva-zero-pen', 'BBVA', 'Visa Cero', 'PEN', 3500, 8490, '2026-02-01'),
    ('bbva-signature-pen', 'BBVA', 'Visa Signature', 'PEN', 2190, 6990, '2026-02-01'),
    ('ibk-benefit-pen', 'Interbank', 'Visa Benefit', 'PEN', 3100, 8600, '2026-01-20'),
    ('scotia-smart-pen', 'Scotiabank', 'Mastercard Smart', 'PEN', 2800, 8200, '2026-02-10'),
    ('bcp-classic-usd', 'BCP', 'Visa Clásica USD', 'USD', 1990, 4990, '2026-01-15'),
    ('bbva-gold-usd', 'BBVA', 'Visa Oro USD', 'USD', 1890, 4590, '2026-02-01')
ON CONFLICT (id) DO NOTHING;

GRANT SELECT ON public.referential_rate_catalog TO authenticated;
GRANT ALL ON public.card_personal_teas TO authenticated;
