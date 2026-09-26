-- EP-CTA Sprint 3: canonical credit catalog, purchases, installments, payments, and
-- utilization-crossing notifications. Forward-only; old rows are retained for audit.
BEGIN;

-- Preserve source truth for the credit-products catalog in its canonical table.
ALTER TABLE public.credit_products
    ADD COLUMN IF NOT EXISTS institution_name text,
    ADD COLUMN IF NOT EXISTS reference_tea_pen_min_bps integer,
    ADD COLUMN IF NOT EXISTS reference_tea_pen_max_bps integer,
    ADD COLUMN IF NOT EXISTS reference_tea_usd_min_bps integer,
    ADD COLUMN IF NOT EXISTS reference_tea_usd_max_bps integer,
    ADD COLUMN IF NOT EXISTS published_tea_summary text,
    ADD COLUMN IF NOT EXISTS published_tcea_summary text,
    ADD COLUMN IF NOT EXISTS membership_fee_pen_minor bigint,
    ADD COLUMN IF NOT EXISTS membership_fee_usd_minor bigint,
    ADD COLUMN IF NOT EXISTS membership_condition text,
    ADD COLUMN IF NOT EXISTS source_url text,
    ADD COLUMN IF NOT EXISTS verification_status text,
    ADD COLUMN IF NOT EXISTS catalog_as_of date,
    ADD COLUMN IF NOT EXISTS is_catalog_listed boolean NOT NULL DEFAULT true;

ALTER TABLE public.credit_products
    ALTER COLUMN effective_from DROP NOT NULL,
    ALTER COLUMN effective_from DROP DEFAULT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.credit_products'::regclass AND conname = 'credit_products_pen_tea_range_check') THEN
        ALTER TABLE public.credit_products ADD CONSTRAINT credit_products_pen_tea_range_check
            CHECK (reference_tea_pen_min_bps IS NULL OR reference_tea_pen_min_bps >= 0)
            NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.credit_products'::regclass AND conname = 'credit_products_pen_tea_order_check') THEN
        ALTER TABLE public.credit_products ADD CONSTRAINT credit_products_pen_tea_order_check
            CHECK (reference_tea_pen_max_bps IS NULL OR reference_tea_pen_min_bps IS NULL OR reference_tea_pen_max_bps >= reference_tea_pen_min_bps)
            NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.credit_products'::regclass AND conname = 'credit_products_usd_tea_range_check') THEN
        ALTER TABLE public.credit_products ADD CONSTRAINT credit_products_usd_tea_range_check
            CHECK (reference_tea_usd_min_bps IS NULL OR reference_tea_usd_min_bps >= 0)
            NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.credit_products'::regclass AND conname = 'credit_products_usd_tea_order_check') THEN
        ALTER TABLE public.credit_products ADD CONSTRAINT credit_products_usd_tea_order_check
            CHECK (reference_tea_usd_max_bps IS NULL OR reference_tea_usd_min_bps IS NULL OR reference_tea_usd_max_bps >= reference_tea_usd_min_bps)
            NOT VALID;
    END IF;
END;
$$;

-- The source explicitly says the catalog was reviewed at this fixed cut. Null
-- effective_to means no software expiry; effective_from stays null where the
-- source did not give a contractual start date.
CREATE TEMP TABLE s3_credit_product_seed (
    institution_code text NOT NULL,
    institution_name text NOT NULL,
    product_name text NOT NULL,
    card_network text,
    reference_tea_bps integer,
    pen_min integer,
    pen_max integer,
    usd_min integer,
    usd_max integer,
    tea_summary text NOT NULL,
    tcea_summary text,
    fee_pen bigint,
    fee_usd bigint,
    membership_condition text,
    source_url text NOT NULL,
    verification_status text NOT NULL
) ON COMMIT DROP;

INSERT INTO s3_credit_product_seed VALUES
-- BCP: 18 products. Rates are kept as published; no range is collapsed to an average.
('BCP','Banco de Crédito del Perú','American Express Clásica LATAM Pass','AMEX',NULL,8340,9590,7690,7690,'Compras: PEN 83.40%–95.90%; USD 76.90%. Efectivo: PEN 107.00%; USD 85.90%','TCEA máxima: PEN 118.64%; USD 76.90%',8000,NULL,'S/80 anual; exoneración con consumo promedio mensual ≥S/1.','https://www.viabcp.com/tarjetas/american-express-clasica','VIGENTE_VERIFICADO'),
('BCP','Banco de Crédito del Perú','American Express Oro LATAM Pass','AMEX',NULL,6500,9590,6500,7690,'Compras: PEN 65.00%–95.90%; USD 65.00%–76.90%. Efectivo: PEN 107.00%; USD 85.90%','TCEA máxima: PEN 136.53%; USD 76.90%',17000,NULL,'S/170 anual; exoneración con consumo promedio mensual ≥S/1.','https://www.viabcp.com/tarjetas/american-express-gold','VIGENTE_VERIFICADO'),
('BCP','Banco de Crédito del Perú','American Express Platinum LATAM Pass','AMEX',NULL,5700,9590,5700,7690,'Compras: PEN 57.00%–95.90%; USD 57.00%–76.90%. Efectivo: PEN 107.00%; USD 85.90%','TCEA máxima: PEN 171.14%; USD 76.90%',35000,NULL,'S/350 anual; exoneración si consume ≥S/1,200 cada mes (venta nueva).','https://www.viabcp.com/tarjetas/american-express-platinum','VIGENTE_VERIFICADO'),
('BCP','Banco de Crédito del Perú','American Express Black LATAM Pass','AMEX',NULL,4900,9590,4900,6600,'Compras: PEN 49.00%–95.90%; USD 49.00%–66.00%. Efectivo: PEN 95.90%; USD 76.90%','TCEA máxima: PEN 180.53%; USD 76.90%',40000,NULL,'S/400 anual; exoneración con consumo promedio mensual ≥S/3,500. Ingreso mínimo publicado S/8,000.','https://www.viabcp.com/tarjetas/american-express-black','VIGENTE_VERIFICADO'),
('BCP','Banco de Crédito del Perú','Visa Clásica','VISA',NULL,8340,9590,7690,7690,'Compras: PEN 83.40%–95.90%; USD 76.90%. Efectivo: PEN 107.00%; USD 85.90%','TCEA máxima: PEN 118.64%; USD 76.90%',8000,NULL,'S/80 anual; exoneración con consumo mensual ≥S/50.','https://www.viabcp.com/tarjetas/credito-visa-clasica','VIGENTE_VERIFICADO'),
('BCP','Banco de Crédito del Perú','Visa Clásica LATAM Pass','VISA',NULL,8340,9590,7690,7690,'Compras: PEN 83.40%–95.90%; USD 76.90%. Efectivo: PEN 107.00%; USD 85.90%','TCEA máxima: PEN 118.64%; USD 76.90%',8000,NULL,'S/80 anual; exoneración con consumo promedio mensual ≥S/1.','https://www.viabcp.com/tarjetas/visa-latampass-clasica','VIGENTE_VERIFICADO'),
('BCP','Banco de Crédito del Perú','Visa Clásica Qore','VISA',NULL,8340,9590,7690,7690,'Compras: PEN 83.40%–95.90%; USD 76.90%. Efectivo: PEN 107.00%; USD 85.90%','TCEA máxima: PEN 118.64%; USD 76.90%',8000,NULL,'S/80 anual; ficha Qore indica exoneración con consumo mensual ≥S/1.','https://www.viabcp.com/tarjetas/visa-clasica-qore','VIGENTE_VERIFICADO'),
('BCP','Banco de Crédito del Perú','Visa Oro LATAM Pass','VISA',NULL,6500,9590,6500,7690,'Compras: PEN 65.00%–95.90%; USD 65.00%–76.90%. Efectivo: PEN 107.00%; USD 85.90%','TCEA máxima: PEN 136.53%; USD 76.90%',17000,NULL,'S/170 anual; exoneración con consumo promedio mensual ≥S/1.','https://www.viabcp.com/tarjetas/visa-latampass-oro','VIGENTE_VERIFICADO'),
('BCP','Banco de Crédito del Perú','Visa Oro Qore','VISA',NULL,5700,9590,5700,7690,'Compras: PEN 57.00%–95.90%; USD 57.00%–76.90%. Efectivo: PEN 107.00%; USD 85.90%','TCEA máxima: PEN 136.53%; USD 76.90%',17000,NULL,'S/170 anual; ficha Qore indica exoneración con consumo promedio mensual ≥S/1.','https://www.viabcp.com/tarjetas/visa-oro-qore','VIGENTE_VERIFICADO'),
('BCP','Banco de Crédito del Perú','Visa Platinum LATAM Pass','VISA',NULL,5700,9590,5700,7690,'Compras: PEN 57.00%–95.90%; USD 57.00%–76.90%. Efectivo: PEN 107.00%; USD 85.90%','TCEA máxima: PEN 171.14%; USD 76.90%',35000,NULL,'S/350 anual; exoneración con consumo mensual ≥S/1,200.','https://www.viabcp.com/tarjetas/visa-latampass-platinum','VIGENTE_VERIFICADO'),
('BCP','Banco de Crédito del Perú','Visa Platinum Qore','VISA',NULL,5700,9590,5700,7690,'Compras: PEN 57.00%–95.90%; USD 57.00%–76.90%. Efectivo: PEN 107.00%; USD 85.90%','TCEA máxima: PEN 171.14%; USD 76.90%',35000,NULL,'S/350 anual; discrepancia de exoneración: ficha indica promedio S/3,500; resumen S/1,200 mensual (D16).','https://www.viabcp.com/tarjetas/visa-platinum-qore','VIGENTE_VERIFICADO'),
('BCP','Banco de Crédito del Perú','Visa Signature LATAM Pass','VISA',NULL,4900,9590,4900,7690,'Compras: PEN 49.00%–95.90%; USD 49.00%–76.90%. Efectivo: PEN 95.90%; USD 76.90%','TCEA máxima: PEN 180.53%; USD 76.90%',40000,NULL,'S/400 anual; exoneración con consumo promedio mensual ≥S/3,500.','https://www.viabcp.com/tarjetas/visa-latampass-signature','VIGENTE_VERIFICADO'),
('BCP','Banco de Crédito del Perú','Visa Signature Qore','VISA',NULL,4900,9590,4900,7690,'Compras: PEN 49.00%–95.90%; USD 49.00%–76.90%. Efectivo: PEN 95.90%; USD 76.90%','TCEA máxima: PEN 180.53%; USD 76.90%',40000,NULL,'S/400 anual; exoneración con consumo promedio mensual ≥S/3,500.','https://www.viabcp.com/tarjetas/visa-signature-qore','VIGENTE_VERIFICADO'),
('BCP','Banco de Crédito del Perú','Visa Infinite Sapphire LATAM Pass','VISA',NULL,4900,9590,4900,7690,'Compras: PEN 49.00%–95.90%; USD 49.00%–76.90%. Efectivo: PEN 95.90%; USD 76.90%','TCEA máxima: PEN 189.80%; USD 76.90%',45000,NULL,'S/450 anual; exoneración con consumo ≥S/4,500 todos los meses de 12 ciclos. Ingreso mínimo S/10,000.','https://www.viabcp.com/tarjetas/sapphire','VIGENTE_VERIFICADO'),
('BCP','Banco de Crédito del Perú','Visa Infinite Iridium LATAM Pass','VISA',NULL,3300,6300,3300,6300,'Compras: PEN 33.00%–63.00%; USD 33.00%–63.00%. Efectivo: PEN 81.50%; USD 66.00%','TCEA máxima: PEN 161.74%; USD 63.00%',50000,NULL,'S/500 anual; exoneración por umbral anual y relación/inversiones BCP. Ingreso mínimo S/20,000.','https://www.viabcp.com/tarjetas/visa-latampass-infinite','VIGENTE_VERIFICADO'),
('BCP','Banco de Crédito del Perú','Visa Infinite Qore','VISA',NULL,3300,6300,3300,6300,'Compras: PEN 33.00%–63.00%; USD 33.00%–63.00%. Efectivo: PEN 81.50%; USD 66.00%','TCEA máxima: PEN 161.74%; USD 63.00%',50000,NULL,'S/500 anual; exoneración detallada difiere del resumen: umbrales anuales/relación frente a S/5,000 mensual (D16).','https://www.viabcp.com/tarjetas/visa-infinite-qore','VIGENTE_VERIFICADO'),
('BCP','Banco de Crédito del Perú','Visa Light','VISA',NULL,11290,11290,9490,9490,'Compras: PEN 112.90%; USD 94.90%. Efectivo: PEN 112.90%; USD 94.90%','TCEA máxima: PEN 112.90%; USD 94.90%',0,NULL,'Sin membresía. Seguro de desgravamen 0.34% solo para tarjetas emitidas hasta 2025-08-31, tope S/20.','https://www.viabcp.com/tarjetas/credito-visa-light','VIGENTE_VERIFICADO'),
('BCP','Banco de Crédito del Perú','Visa iO','VISA',NULL,NULL,NULL,NULL,NULL,'Conflicto no resuelto: BCP publica TEA compras 33.00%–95.90%; iO publica PEN 33.00%–111.90%, USD 33.00%–76.90%. No elegir valor canónico (D15).','TCEA conflictiva: BCP 101.70%; iO 119.20% PEN / 83.10% USD.',0,NULL,'Membresía cero reportada por iO; seguro 0.30% con tope S/20; condiciones oficiales parcialmente discrepantes.','https://www.viabcp.com/tarjeta-de-credito-visa-io','VIGENTE_TASA_PENDIENTE'),
-- BBVA: 10 products; exact rates only where the catalog assigns them by currency.
('BBVA','BBVA Perú','Bfree Visa','VISA',NULL,9999,9999,8399,8399,'Compras: PEN 99.99%; USD 83.99%.','No publicado en detalle canónico.',7500,NULL,'Membresía publicada S/75; condiciones de exoneración no especificadas en el catálogo.','https://www.bbva.pe/personas/productos/tarjetas/credito/visa-bfree-puntos-vida.html','VIGENTE_VERIFICADO'),
('BBVA','BBVA Perú','Bfree Mastercard','MASTERCARD',NULL,9999,9999,8399,8399,'Compras: PEN 99.99%; USD 83.99%.','No publicado en detalle canónico.',7500,NULL,'Membresía publicada S/75; condiciones de exoneración no especificadas en el catálogo.','https://www.bbva.pe/personas/productos/tarjetas/credito/mastercard-bfree-puntos-vida.html','VIGENTE_VERIFICADO'),
('BBVA','BBVA Perú','Cero Visa','VISA',NULL,10999,10999,8999,8999,'Compras: PEN 109.99%; USD 89.99%.','TCEA revolvente 118.06%; cuotas 120.39%.',0,NULL,'Sin membresía (S/0).','https://www.bbva.pe/personas/productos/tarjetas/credito/visa-cero.html','VIGENTE_VERIFICADO'),
('BBVA','BBVA Perú','Básica',NULL,NULL,10999,10999,8399,8399,'Compras: PEN 109.99%; USD 83.99%; sin seguro de desgravamen.','TCEA 117.85% / 122.32%.',5900,NULL,'Membresía publicada S/59; no incluye seguro de desgravamen.','https://www.bbva.pe/personas/productos/tarjetas/credito/tarjeta-basica.html','VIGENTE_VERIFICADO'),
('BBVA','BBVA Perú','Platinum Visa','VISA',NULL,8999,8999,8399,8399,'Compras fija: PEN 89.99%; USD 83.99%.','No publicado en detalle canónico.',28000,NULL,'Membresía publicada S/280.','https://www.bbva.pe/personas/productos/tarjetas/credito/tarjeta-platinum.html','VIGENTE_VERIFICADO'),
('BBVA','BBVA Perú','Platinum Mastercard','MASTERCARD',NULL,8999,8999,8399,8399,'Compras publicada para Platinum Visa/Mastercard: PEN 89.99%; USD 83.99%.','No publicado en detalle canónico.',28000,NULL,'Membresía publicada S/280.','https://www.bbva.pe/personas/productos/tarjetas/credito/tarjeta-platinum.html','VIGENTE_VERIFICADO'),
('BBVA','BBVA Perú','Visa Signature','VISA',7999,7999,7999,7999,7999,'Compras fija: PEN 79.99%; USD 79.99%.','TCEA revolvente 131.16%; cuotas 158.02%.',35000,NULL,'Membresía publicada S/350.','https://www.bbva.pe/personas/productos/tarjetas/credito/visa-signature-puntos-vida.html','VIGENTE_VERIFICADO'),
('BBVA','BBVA Perú','Visa Infinite','VISA',3999,3999,3999,3999,3999,'Compras fija: PEN 39.99%; USD 39.99%.','TCEA revolvente 104.09%; cuotas 139.47%.',50000,NULL,'Membresía publicada S/500.','https://www.bbva.pe/personas/productos/tarjetas/credito/visa-infinite-puntos.html','VIGENTE_VERIFICADO'),
('BBVA','BBVA Perú','Mastercard Black','MASTERCARD',NULL,3999,6999,3999,6999,'Compras: PEN 39.99%–69.99%; USD 39.99%–69.99%.','TCEA revolvente 126.10%; cuotas 155.93%.',39900,NULL,'Membresía publicada S/399.','https://www.bbva.pe/personas/productos/tarjetas/credito/mastercard-black-puntos-vida.html','VIGENTE_VERIFICADO'),
('BBVA','BBVA Perú','Start (con respaldo/garantía)',NULL,NULL,NULL,NULL,NULL,NULL,'La tasa no está disponible públicamente en la ficha de costos consultada.','No disponible públicamente.',NULL,NULL,'Producto con respaldo/garantía; el catálogo no publica membresía ni condición de exoneración.','https://www.bbva.pe/personas/productos/tarjetas/credito/tarjeta-credito-historial-crediticio.html','VIGENTE_VERIFICADO'),
-- Interbank: 16 products. Keep exact per-product source wording and leave numbers null
-- where the source only provides a range by profile/tariff or does not assign currency.
('INTERBANK','Banco Internacional del Perú S.A.A.','American Express Green','AMEX',NULL,NULL,NULL,NULL,NULL,'Rango por perfil; tarifario oficial. No se publica cifra individual PEN/USD en el catálogo fuente.','No publicado por producto en el dataset fuente.',NULL,NULL,'El catálogo señala que Amex tiene membresías por producto; no da una cifra verificable para esta fila.','https://content-us-2.content-cms.com/9b3f67ef-5a9f-4acc-8ce8-bcc27fa681c7/dxdam/76/76151830-0205-45bf-b9d9-f3a046db8853/TAR-0142.pdf?t=1696610001088','VIGENTE_VERIFICADO'),
('INTERBANK','Banco Internacional del Perú S.A.A.','American Express Gold','AMEX',NULL,NULL,NULL,NULL,NULL,'Rango por perfil; tarifario oficial. No se publica cifra individual PEN/USD en el catálogo fuente.','No publicado por producto en el dataset fuente.',NULL,NULL,'El catálogo señala que Amex tiene membresías por producto; no da una cifra verificable para esta fila.','https://content-us-2.content-cms.com/9b3f67ef-5a9f-4acc-8ce8-bcc27fa681c7/dxdam/76/76151830-0205-45bf-b9d9-f3a046db8853/TAR-0142.pdf?t=1696610001088','VIGENTE_VERIFICADO'),
('INTERBANK','Banco Internacional del Perú S.A.A.','American Express Platinum','AMEX',NULL,NULL,NULL,NULL,NULL,'Rango por perfil; tarifario oficial. No se publica cifra individual PEN/USD en el catálogo fuente.','No publicado por producto en el dataset fuente.',NULL,NULL,'El catálogo señala que Amex tiene membresías por producto; no da una cifra verificable para esta fila.','https://content-us-2.content-cms.com/9b3f67ef-5a9f-4acc-8ce8-bcc27fa681c7/dxdam/f4/f486b303-6c67-4da6-8f6f-42396cb930ea/TAR-0204.pdf?t=1696610001088','VIGENTE_VERIFICADO'),
('INTERBANK','Banco Internacional del Perú S.A.A.','American Express Black','AMEX',NULL,NULL,NULL,NULL,NULL,'Rango por perfil; tarifario oficial. No se publica cifra individual PEN/USD en el catálogo fuente.','No publicado por producto en el dataset fuente.',NULL,NULL,'El catálogo señala que Amex tiene membresías por producto; no da una cifra verificable para esta fila.','https://content-us-2.content-cms.com/9b3f67ef-5a9f-4acc-8ce8-bcc27fa681c7/dxdam/f4/f486b303-6c67-4da6-8f6f-42396cb930ea/TAR-0204.pdf?t=1696610001088','VIGENTE_VERIFICADO'),
('INTERBANK','Banco Internacional del Perú S.A.A.','The Platinum Card American Express','AMEX',NULL,NULL,NULL,NULL,NULL,'Rango por perfil; tarifario oficial. No se publica cifra individual PEN/USD en el catálogo fuente.','No publicado por producto en el dataset fuente.',NULL,NULL,'El catálogo señala que Amex tiene membresías por producto; no da una cifra verificable para esta fila.','https://content-us-2.content-cms.com/9b3f67ef-5a9f-4acc-8ce8-bcc27fa681c7/dxdam/76/76151830-0205-45bf-b9d9-f3a046db8853/TAR-0142.pdf?t=1696610001088','VIGENTE_VERIFICADO'),
('INTERBANK','Banco Internacional del Perú S.A.A.','Visa Clásica','VISA',NULL,NULL,NULL,NULL,NULL,'Rango por perfil; tarifario Visa mayo 2026. El catálogo no transcribe límites individualizados PEN/USD.','No publicado por producto en el dataset fuente.',NULL,NULL,'El catálogo indica que la membresía puede exonerarse con al menos una compra mensual elegible; importe individual no asignado.','https://content-us-2.content-cms.com/9b3f67ef-5a9f-4acc-8ce8-bcc27fa681c7/dxdam/11/11bd2a5e-728e-4ad6-8100-d12ec20034bf/TAR-0164.pdf?t=1696610001088','VIGENTE_VERIFICADO'),
('INTERBANK','Banco Internacional del Perú S.A.A.','Visa Oro','VISA',NULL,NULL,NULL,NULL,NULL,'Rango por perfil; tarifario Visa mayo 2026. El catálogo no transcribe límites individualizados PEN/USD.','No publicado por producto en el dataset fuente.',NULL,NULL,'El catálogo indica que la membresía puede exonerarse con al menos una compra mensual elegible; importe individual no asignado.','https://content-us-2.content-cms.com/9b3f67ef-5a9f-4acc-8ce8-bcc27fa681c7/dxdam/11/11bd2a5e-728e-4ad6-8100-d12ec20034bf/TAR-0164.pdf?t=1696610001088','VIGENTE_VERIFICADO'),
('INTERBANK','Banco Internacional del Perú S.A.A.','Visa Platinum','VISA',NULL,NULL,NULL,NULL,NULL,'Rango por perfil; tarifario Visa mayo 2026. El catálogo no transcribe límites individualizados PEN/USD.','No publicado por producto en el dataset fuente.',NULL,NULL,'El catálogo indica que la membresía puede exonerarse con al menos una compra mensual elegible; importe individual no asignado.','https://content-us-2.content-cms.com/9b3f67ef-5a9f-4acc-8ce8-bcc27fa681c7/dxdam/11/11bd2a5e-728e-4ad6-8100-d12ec20034bf/TAR-0164.pdf?t=1696610001088','VIGENTE_VERIFICADO'),
('INTERBANK','Banco Internacional del Perú S.A.A.','Visa Signature','VISA',NULL,NULL,NULL,NULL,NULL,'Rango por perfil; tarifario Visa mayo 2026. El catálogo no transcribe límites individualizados PEN/USD.','No publicado por producto en el dataset fuente.',NULL,NULL,'El catálogo indica que la membresía puede exonerarse con al menos una compra mensual elegible; importe individual no asignado.','https://content-us-2.content-cms.com/9b3f67ef-5a9f-4acc-8ce8-bcc27fa681c7/dxdam/11/11bd2a5e-728e-4ad6-8100-d12ec20034bf/TAR-0164.pdf?t=1696610001088','VIGENTE_VERIFICADO'),
('INTERBANK','Banco Internacional del Perú S.A.A.','Visa Infinite','VISA',NULL,NULL,NULL,NULL,NULL,'Rango por perfil; tarifario Visa mayo 2026. La página indica para Infinite membresía S/500 y desgravamen 0.40% tope S/25; tasa/membresía no se generalizan a otros productos.','TCEA máxima de ejemplo: 191.93%; no es TEA.',50000,NULL,'La página indica membresía S/500; exoneración condicionada según tarifario vigente.','https://content-us-2.content-cms.com/9b3f67ef-5a9f-4acc-8ce8-bcc27fa681c7/dxdam/11/11bd2a5e-728e-4ad6-8100-d12ec20034bf/TAR-0164.pdf?t=1696610001088','VIGENTE_VERIFICADO'),
('INTERBANK','Banco Internacional del Perú S.A.A.','Visa Access','VISA',NULL,NULL,NULL,NULL,NULL,'TEA 19.42%–109.81%; el tarifario no permite asignar inequívocamente la moneda a este rango.','TCEA máxima 109.81% (ejemplo S/1,000 a 12 meses).',0,NULL,'Membresía S/0.','https://content-us-2.content-cms.com/9b3f67ef-5a9f-4acc-8ce8-bcc27fa681c7/dxdam/a5/a547a3b0-3c03-4ef9-927e-f5dae50bd643/TAR-0225.pdf?t=1696610001016','VIGENTE_VERIFICADO'),
('INTERBANK','Banco Internacional del Perú S.A.A.','Visa Premia','VISA',NULL,NULL,NULL,NULL,NULL,'TEA 19.42%–99.86%; el tarifario no permite asignar inequívocamente la moneda a este rango.','No publicada en el dataset fuente.',NULL,NULL,'La membresía puede exonerarse con al menos una compra mensual elegible; importe individual no asignado.','https://content-us-2.content-cms.com/9b3f67ef-5a9f-4acc-8ce8-bcc27fa681c7/dxdam/36/3676c274-5ffc-43a6-af13-099c1655e95e/TAR-0186.pdf?t=1696610001088','VIGENTE_VERIFICADO'),
('INTERBANK','Banco Internacional del Perú S.A.A.','Mastercard Clásica','MASTERCARD',NULL,NULL,NULL,NULL,NULL,'Rango por perfil; tarifario Mastercard mayo 2026. El catálogo no transcribe límites individualizados PEN/USD.','No publicado por producto en el dataset fuente.',NULL,NULL,'El tarifario lista membresías por categoría; cifra individual no transcrita en el catálogo fuente.','https://content-us-2.content-cms.com/9b3f67ef-5a9f-4acc-8ce8-bcc27fa681c7/dxdam/ef/efc950f5-50e3-4884-b14c-cbb67b6bcea5/TAR-0144.pdf?t=1696610001088','VIGENTE_VERIFICADO'),
('INTERBANK','Banco Internacional del Perú S.A.A.','Mastercard Oro','MASTERCARD',NULL,NULL,NULL,NULL,NULL,'Rango por perfil; tarifario Mastercard mayo 2026. El catálogo no transcribe límites individualizados PEN/USD.','No publicado por producto en el dataset fuente.',NULL,NULL,'El tarifario lista membresías por categoría; cifra individual no transcrita en el catálogo fuente.','https://content-us-2.content-cms.com/9b3f67ef-5a9f-4acc-8ce8-bcc27fa681c7/dxdam/ef/efc950f5-50e3-4884-b14c-cbb67b6bcea5/TAR-0144.pdf?t=1696610001088','VIGENTE_VERIFICADO'),
('INTERBANK','Banco Internacional del Perú S.A.A.','Mastercard Platinum','MASTERCARD',NULL,NULL,NULL,NULL,NULL,'Rango por perfil; tarifario Mastercard mayo 2026. El catálogo no transcribe límites individualizados PEN/USD.','No publicado por producto en el dataset fuente.',NULL,NULL,'El tarifario lista membresías por categoría; cifra individual no transcrita en el catálogo fuente.','https://content-us-2.content-cms.com/9b3f67ef-5a9f-4acc-8ce8-bcc27fa681c7/dxdam/ef/efc950f5-50e3-4884-b14c-cbb67b6bcea5/TAR-0144.pdf?t=1696610001088','VIGENTE_VERIFICADO'),
('INTERBANK','Banco Internacional del Perú S.A.A.','Tarjeta de crédito con garantía líquida',NULL,NULL,NULL,NULL,NULL,NULL,'Tasa depende de la tarjeta elegida y evaluación; no se asigna tasa propia.','No publicado por producto en el dataset fuente.',NULL,NULL,'La fuente no publica membresía/condición propia de esta modalidad en el catálogo consultado.','https://interbank.pe/tarjetas/tarjetas-credito','VIGENTE_VERIFICADO');

-- Retire any older top-three catalog rows from the active listing without deleting
-- referenced products. Then update one canonical row or insert one for each source row.
UPDATE public.credit_products
SET is_catalog_listed = false
WHERE institution_code IN ('BCP','BBVA','INTERBANK');

WITH ranked_existing AS (
    SELECT cp.id, s.institution_code, s.product_name,
           row_number() OVER (PARTITION BY s.institution_code, lower(s.product_name) ORDER BY cp.created_at, cp.id) AS rn
    FROM public.credit_products cp
    JOIN s3_credit_product_seed s
      ON cp.institution_code = s.institution_code
     AND lower(cp.product_name) = lower(s.product_name)
)
UPDATE public.credit_products cp
SET institution_name = s.institution_name,
    card_network = s.card_network,
    reference_tea_bps = s.reference_tea_bps,
    reference_tea_pen_min_bps = s.pen_min,
    reference_tea_pen_max_bps = s.pen_max,
    reference_tea_usd_min_bps = s.usd_min,
    reference_tea_usd_max_bps = s.usd_max,
    published_tea_summary = s.tea_summary,
    published_tcea_summary = s.tcea_summary,
    membership_fee_pen_minor = s.fee_pen,
    membership_fee_usd_minor = s.fee_usd,
    membership_condition = s.membership_condition,
    source_url = s.source_url,
    verification_status = s.verification_status,
    catalog_as_of = DATE '2026-09-24',
    effective_from = NULL,
    effective_to = NULL,
    is_catalog_listed = true
FROM ranked_existing r
JOIN s3_credit_product_seed s
  ON s.institution_code = r.institution_code
 AND lower(s.product_name) = lower(r.product_name)
WHERE cp.id = r.id AND r.rn = 1;

INSERT INTO public.credit_products (
    institution_code, institution_name, product_name, card_network, reference_tea_bps,
    reference_tea_pen_min_bps, reference_tea_pen_max_bps,
    reference_tea_usd_min_bps, reference_tea_usd_max_bps,
    published_tea_summary, published_tcea_summary,
    membership_fee_pen_minor, membership_fee_usd_minor, membership_condition,
    source_url, verification_status, catalog_as_of, effective_from, effective_to, is_catalog_listed
)
SELECT s.institution_code, s.institution_name, s.product_name, s.card_network, s.reference_tea_bps,
       s.pen_min, s.pen_max, s.usd_min, s.usd_max,
       s.tea_summary, s.tcea_summary,
       s.fee_pen, s.fee_usd, s.membership_condition,
       s.source_url, s.verification_status, DATE '2026-09-24', NULL, NULL, true
FROM s3_credit_product_seed s
WHERE NOT EXISTS (
    SELECT 1 FROM public.credit_products cp
    WHERE cp.institution_code = s.institution_code
      AND lower(cp.product_name) = lower(s.product_name)
      AND cp.is_catalog_listed
);

DO $$
DECLARE v_counts record;
BEGIN
    SELECT count(*)::integer AS total,
           count(*) FILTER (WHERE institution_code = 'BCP')::integer AS bcp,
           count(*) FILTER (WHERE institution_code = 'BBVA')::integer AS bbva,
           count(*) FILTER (WHERE institution_code = 'INTERBANK')::integer AS interbank
    INTO v_counts
    FROM public.credit_products
    WHERE is_catalog_listed AND institution_code IN ('BCP','BBVA','INTERBANK');
    IF v_counts.total <> 44 OR v_counts.bcp <> 18 OR v_counts.bbva <> 10 OR v_counts.interbank <> 16 THEN
        RAISE EXCEPTION 'S3 catalog seed count mismatch (total %, BCP %, BBVA %, Interbank %)',
            v_counts.total, v_counts.bcp, v_counts.bbva, v_counts.interbank;
    END IF;
END;
$$;

CREATE UNIQUE INDEX IF NOT EXISTS credit_products_listed_identity_uq
    ON public.credit_products (institution_code, lower(product_name))
    WHERE is_catalog_listed;

-- Match the documented 1..31 preferred day and preserve legacy aliases on remote
-- installations. Do not drop alias columns: old clients/data may still depend on them.
DO $$
DECLARE c record;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'public.cards'::regclass
               AND contype = 'c'
               AND pg_get_constraintdef(oid) ILIKE '%closing_day%'
    LOOP
        EXECUTE format('ALTER TABLE public.cards DROP CONSTRAINT %I', c.conname);
    END LOOP;
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'public.cards'::regclass
               AND contype = 'c'
               AND pg_get_constraintdef(oid) ILIKE '%due_day%'
    LOOP
        EXECUTE format('ALTER TABLE public.cards DROP CONSTRAINT %I', c.conname);
    END LOOP;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.cards'::regclass AND conname = 'cards_billing_close_day_1_31_check') THEN
        ALTER TABLE public.cards ADD CONSTRAINT cards_closing_day_1_31_check
            CHECK (closing_day IS NULL OR closing_day BETWEEN 1 AND 31) NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.cards'::regclass AND conname = 'cards_payment_due_day_1_31_check') THEN
        ALTER TABLE public.cards ADD CONSTRAINT cards_due_day_1_31_check
            CHECK (due_day IS NULL OR due_day BETWEEN 1 AND 31) NOT VALID;
    END IF;
END;
$$;

-- Remote T075 baseline has legacy aliases. Backfill canonical day fields if those
-- aliases exist, without removing or repurposing them.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='cards' AND column_name='billing_cycle_day') THEN
        EXECUTE 'UPDATE public.cards SET closing_day = COALESCE(closing_day, billing_cycle_day)';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='cards' AND column_name='payment_due_day')
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='cards' AND column_name='due_day') THEN
        EXECUTE 'UPDATE public.cards SET due_day = COALESCE(due_day, payment_due_day)';
    END IF;
END;
$$;

-- Credit purchase transactions do not consume a liquid account. Existing remote
-- cards/transactions with their old aliases remain in place.
ALTER TABLE public.transactions ALTER COLUMN account_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_credit_installments_owner_due
    ON public.credit_installments (user_id, due_date, status);
CREATE INDEX IF NOT EXISTS idx_credit_installments_transaction_number
    ON public.credit_installments (user_id, transaction_id, installment_number);
CREATE INDEX IF NOT EXISTS idx_credit_allocations_payment
    ON public.credit_payment_allocations (user_id, payment_transaction_id);
CREATE INDEX IF NOT EXISTS idx_credit_allocations_installment
    ON public.credit_payment_allocations (user_id, installment_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_transactions_owner_id_uq
    ON public.transactions (user_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_credit_installments_owner_id_uq
    ON public.credit_installments (user_id, id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.credit_installments'::regclass AND conname='credit_installments_owner_transaction_fkey') THEN
        ALTER TABLE public.credit_installments ADD CONSTRAINT credit_installments_owner_transaction_fkey
            FOREIGN KEY (user_id, transaction_id) REFERENCES public.transactions(user_id, id) ON DELETE CASCADE NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.credit_payment_allocations'::regclass AND conname='credit_allocations_owner_payment_fkey') THEN
        ALTER TABLE public.credit_payment_allocations ADD CONSTRAINT credit_allocations_owner_payment_fkey
            FOREIGN KEY (user_id, payment_transaction_id) REFERENCES public.transactions(user_id, id) ON DELETE CASCADE NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.credit_payment_allocations'::regclass AND conname='credit_allocations_owner_installment_fkey') THEN
        ALTER TABLE public.credit_payment_allocations ADD CONSTRAINT credit_allocations_owner_installment_fkey
            FOREIGN KEY (user_id, installment_id) REFERENCES public.credit_installments(user_id, id) ON DELETE CASCADE NOT VALID;
    END IF;
END;
$$;

-- Shared EP-NOT/HU-42 contract. event_key includes the accepted operation, so a
-- retry dedupes while a later recross after re-arming creates a fresh event.
ALTER TABLE public.app_notifications
    ADD COLUMN IF NOT EXISTS event_key text,
    ADD COLUMN IF NOT EXISTS event_payload jsonb NOT NULL DEFAULT '{}'::jsonb;
CREATE UNIQUE INDEX IF NOT EXISTS app_notifications_owner_event_key_uq
    ON public.app_notifications (user_id, event_key)
    WHERE event_key IS NOT NULL;

CREATE OR REPLACE FUNCTION private.credit_cycle_installment_due_date(
    p_occurred_at timestamptz,
    p_closing_day smallint,
    p_due_day smallint,
    p_installment_number integer
)
RETURNS date
LANGUAGE plpgsql
IMMUTABLE
SET search_path = ''
AS $$
DECLARE
    v_purchase_date date;
    v_month_start date;
    v_cycle_close date;
    v_candidate_due date;
    v_first_due_month date;
    v_target_month date;
    v_days integer;
BEGIN
    IF p_installment_number < 1 OR p_installment_number > 36 OR p_closing_day NOT BETWEEN 1 AND 31 OR p_due_day NOT BETWEEN 1 AND 31 THEN
        RAISE EXCEPTION 'Invalid card cycle input' USING ERRCODE = '22023';
    END IF;
    v_purchase_date := (p_occurred_at AT TIME ZONE 'America/Lima')::date;
    v_month_start := date_trunc('month', v_purchase_date)::date;
    v_days := extract(day FROM (v_month_start + interval '1 month - 1 day'))::integer;
    v_cycle_close := v_month_start + (least(p_closing_day, v_days) - 1);
    IF v_purchase_date > v_cycle_close THEN
        v_month_start := (v_month_start + interval '1 month')::date;
        v_days := extract(day FROM (v_month_start + interval '1 month - 1 day'))::integer;
        v_cycle_close := v_month_start + (least(p_closing_day, v_days) - 1);
    END IF;
    v_days := extract(day FROM (date_trunc('month', v_cycle_close)::date + interval '1 month - 1 day'))::integer;
    v_candidate_due := date_trunc('month', v_cycle_close)::date + (least(p_due_day, v_days) - 1);
    IF v_candidate_due > v_cycle_close THEN
        v_first_due_month := date_trunc('month', v_cycle_close)::date;
    ELSE
        v_first_due_month := (date_trunc('month', v_cycle_close)::date + interval '1 month')::date;
    END IF;
    v_target_month := (v_first_due_month + make_interval(months => p_installment_number - 1))::date;
    v_days := extract(day FROM (v_target_month + interval '1 month - 1 day'))::integer;
    RETURN v_target_month + (least(p_due_day, v_days) - 1);
END;
$$;
REVOKE ALL ON FUNCTION private.credit_cycle_installment_due_date(timestamptz, smallint, smallint, integer) FROM PUBLIC, anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION private.emit_credit_utilization_crossings(
    p_user_id uuid,
    p_card_id uuid,
    p_operation_id uuid,
    p_previous_debt bigint,
    p_current_debt bigint,
    p_credit_limit bigint
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_threshold integer;
    v_event_key text;
    v_before_bps integer;
    v_after_bps integer;
BEGIN
    IF p_credit_limit IS NULL OR p_credit_limit <= 0 THEN RETURN; END IF;
    v_before_bps := floor((p_previous_debt::numeric * 10000) / p_credit_limit)::integer;
    v_after_bps := floor((p_current_debt::numeric * 10000) / p_credit_limit)::integer;
    FOREACH v_threshold IN ARRAY ARRAY[5000, 8000, 10000]
    LOOP
        IF p_previous_debt::numeric * 10000 < p_credit_limit::numeric * v_threshold
           AND p_current_debt::numeric * 10000 >= p_credit_limit::numeric * v_threshold THEN
            v_event_key := format('credit-utilization-v1:%s:%s:%s:%s', p_user_id, p_card_id, p_operation_id, v_threshold);
            INSERT INTO public.app_notifications (
                user_id, title, body, notification_type, reference_entity_type,
                reference_entity_id, is_read, created_at, event_key, event_payload
            ) VALUES (
                p_user_id,
                format('Uso de crédito: %s%%', v_threshold / 100),
                format('La utilización de tu tarjeta alcanzó el umbral de %s%%.', v_threshold / 100),
                'CREDIT_UTILIZATION_THRESHOLD_CROSSED', 'CARD', p_card_id, false, now(), v_event_key,
                jsonb_build_object(
                    'schema_version', 1,
                    'event_key', v_event_key,
                    'accepted_operation_id', p_operation_id,
                    'card_id', p_card_id,
                    'threshold_bps', v_threshold,
                    'previous_utilization_bps', v_before_bps,
                    'current_utilization_bps', v_after_bps,
                    'rearm_rule', 'A later accepted operation may emit a new event after utilization falls below this threshold.'
                )
            ) ON CONFLICT (user_id, event_key) WHERE event_key IS NOT NULL DO NOTHING;
        END IF;
    END LOOP;
END;
$$;
REVOKE ALL ON FUNCTION private.emit_credit_utilization_crossings(uuid, uuid, uuid, bigint, bigint, bigint) FROM PUBLIC, anon, authenticated, service_role;

-- Preserve the already checked/validated standard transaction RPC as an internal
-- delegate. The public entry point below extends it only for CARD_PURCHASE.
ALTER FUNCTION public.register_transaction_v1(jsonb) SET SCHEMA internal;
ALTER FUNCTION internal.register_transaction_v1(jsonb) RENAME TO register_transaction_v1_pre_credit_s3;
REVOKE ALL ON FUNCTION internal.register_transaction_v1_pre_credit_s3(jsonb) FROM PUBLIC, anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.register_transaction_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_tx jsonb := p_command->'transaction';
    v_type text;
    v_operation_kind text;
    v_idempotency_key text;
    v_request_hash text;
    v_client_hash text;
    v_tx_id uuid;
    v_card_id uuid;
    v_category_id uuid;
    v_merchant_id uuid;
    v_provisional_merchant text;
    v_amount bigint;
    v_currency char(3);
    v_installments integer;
    v_occurred_at timestamptz;
    v_interest_mode text;
    v_card record;
    v_existing record;
    v_receipt_id uuid;
    v_response jsonb;
    v_before_debt bigint;
    v_after_debt bigint;
    v_credit_limit bigint;
    v_base bigint;
    v_remainder integer;
    v_principal bigint;
    v_due_date date;
    v_installment_ids jsonb := '[]'::jsonb;
    v_now timestamptz := clock_timestamp();
    v_installment_number integer;
BEGIN
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','UNAUTHORIZED','field',NULL,'retryable',false));
    END IF;
    IF v_tx IS NULL THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_COMMAND','field','transaction','retryable',false));
    END IF;
    v_idempotency_key := NULLIF(p_command->>'idempotency_key','');
    v_client_hash := NULLIF(p_command->>'request_hash','');
    v_type := upper(COALESCE(v_tx->>'type',''));
    v_operation_kind := upper(COALESCE(v_tx->>'operation_kind','STANDARD'));

    -- Ordinary transaction behavior remains delegated to the last checked RPC.
    IF v_operation_kind = 'CARD_PAYMENT' THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','USE_ALLOCATE_CREDIT_PAYMENT','field','operation_kind','retryable',false));
    END IF;
    IF NOT (v_type = 'EXPENSE' AND v_operation_kind = 'CARD_PURCHASE') THEN
        RETURN internal.register_transaction_v1_pre_credit_s3(p_command);
    END IF;

    IF COALESCE((p_command->>'contract_version')::integer,1) <> 1
       OR v_idempotency_key IS NULL OR v_client_hash IS NULL THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_COMMAND','field','contract_version/idempotency_key/request_hash','retryable',false));
    END IF;
    IF NULLIF(v_tx->>'source_account_id','') IS NOT NULL THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','CARD_PURCHASE_CANNOT_DEBIT_CASH','field','source_account_id','retryable',false));
    END IF;
    BEGIN
        v_tx_id := (v_tx->>'id')::uuid;
        v_card_id := (v_tx->>'card_id')::uuid;
        v_category_id := NULLIF(v_tx->>'category_id','')::uuid;
        v_merchant_id := (v_tx->>'merchant_id')::uuid;
        v_amount := (v_tx->>'amount_minor')::bigint;
        v_currency := (v_tx->>'currency_code')::char(3);
        v_installments := COALESCE((v_tx->>'installment_count')::integer,1);
        v_occurred_at := COALESCE((v_tx->>'occurred_at')::timestamptz,v_now);
        v_interest_mode := upper(COALESCE(v_tx->>'interest_mode','NONE'));
    EXCEPTION WHEN invalid_text_representation OR datetime_field_overflow THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_COMMAND_FIELD','field',NULL,'retryable',false));
    END;
    v_provisional_merchant := NULLIF(trim(v_tx->>'merchant_provisional_text'),'');
    IF v_tx_id IS NULL OR v_card_id IS NULL OR v_amount IS NULL OR v_currency IS NULL THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','REQUIRED_FIELD_MISSING','field',NULL,'retryable',false));
    END IF;
    IF v_amount < 1 OR v_amount > 10000000 THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_AMOUNT','field','amount_minor','retryable',false));
    END IF;
    IF v_installments < 1 OR v_installments > 36 THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_INSTALLMENT_COUNT','field','installment_count','retryable',false));
    END IF;
    IF length(trim(v_currency::text))<>3 OR NOT EXISTS (SELECT 1 FROM public.currencies c WHERE c.code=v_currency) THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_CURRENCY','field','currency_code','retryable',false));
    END IF;
    IF v_interest_mode NOT IN ('NONE','INCLUDED','REFERENTIAL') THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_INTEREST_MODE','field','interest_mode','retryable',false));
    END IF;
    IF v_merchant_id IS NOT NULL AND v_provisional_merchant IS NOT NULL THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','MERCHANT_CONFLICT','field','merchant_id','retryable',false));
    END IF;
    IF v_category_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM public.categories c
        WHERE c.id=v_category_id AND c.is_active AND (c.user_id IS NULL OR c.user_id=v_user_id)
          AND (c.parent_id IS NULL OR EXISTS (SELECT 1 FROM public.categories p WHERE p.id=c.parent_id AND p.is_active))
    ) THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','CATEGORY_UNAVAILABLE','field','category_id','retryable',false));
    END IF;
    IF v_merchant_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM public.merchant_services m WHERE m.id=v_merchant_id AND m.is_active) THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','MERCHANT_UNAVAILABLE','field','merchant_id','retryable',false));
    END IF;

    -- Derive the idempotency hash from normalized PostgreSQL JSONB. The client
    -- hash remains an envelope field but is not trusted as the request identity.
    v_request_hash := encode(extensions.digest(convert_to(jsonb_build_object('contract_version',1,'transaction',v_tx)::text,'UTF8'),'sha256'),'hex');
    PERFORM pg_advisory_xact_lock(hashtextextended(v_user_id::text || ':' || v_idempotency_key,0));
    SELECT id,request_hash,response_payload INTO v_existing
    FROM internal.command_receipts WHERE user_id=v_user_id AND idempotency_key=v_idempotency_key;
    IF FOUND THEN
        IF v_existing.request_hash=v_request_hash THEN
            RETURN jsonb_set(v_existing.response_payload,'{status}','"DUPLICATE"'::jsonb);
        END IF;
        RETURN jsonb_build_object('status','CONFLICT','transaction_id',NULL,'receipt_id',v_existing.id,'server_updated_at',v_now,
            'error',jsonb_build_object('code','IDEMPOTENCY_CONFLICT','field','idempotency_key','retryable',false));
    END IF;
    IF EXISTS (SELECT 1 FROM public.transactions WHERE id=v_tx_id) THEN
        RETURN jsonb_build_object('status','CONFLICT','transaction_id',v_tx_id,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','TRANSACTION_ID_ALREADY_EXISTS','field','transaction.id','retryable',false));
    END IF;

    SELECT c.id,c.user_id,c.is_credit,c.is_archived,c.credit_limit_minor,c.closing_day,c.due_day,c.account_id,c.revision
    INTO v_card
    FROM public.cards c
    WHERE c.id=v_card_id AND c.user_id=v_user_id
    FOR UPDATE;
    IF NOT FOUND OR NOT v_card.is_credit OR v_card.is_archived THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','CREDIT_CARD_UNAVAILABLE','field','card_id','retryable',false));
    END IF;
    IF v_card.closing_day IS NULL OR v_card.due_day IS NULL THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','CARD_CYCLE_NOT_CONFIGURED','field','closing_day/due_day','retryable',false));
    END IF;
    SELECT COALESCE(sum(GREATEST(ci.principal_minor-COALESCE(a.allocated_minor,0),0)),0)::bigint
    INTO v_before_debt
    FROM public.credit_installments ci
    JOIN public.transactions purchase ON purchase.id=ci.transaction_id AND purchase.user_id=ci.user_id
    LEFT JOIN LATERAL (
        SELECT sum(x.allocated_minor)::bigint AS allocated_minor
        FROM public.credit_payment_allocations x
        WHERE x.user_id=ci.user_id AND x.installment_id=ci.id
    ) a ON true
    WHERE ci.user_id=v_user_id AND purchase.card_id=v_card_id AND purchase.currency_code=v_currency
      AND ci.status IN ('PENDING','PARTIAL') AND purchase.status='CONFIRMED' AND purchase.deleted_at IS NULL;
    v_credit_limit := v_card.credit_limit_minor;
    IF v_credit_limit IS NOT NULL AND v_before_debt + v_amount > v_credit_limit THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','CREDIT_LIMIT_EXCEEDED','field','amount_minor','retryable',false));
    END IF;

    INSERT INTO public.transactions (
        id,user_id,account_id,card_id,category_id,merchant_service_id,merchant_provisional_text,
        transaction_type,operation_kind,amount_minor,currency_code,occurred_at,installment_count,
        interest_mode,notes,status,revision,created_at,updated_at
    ) VALUES (
        v_tx_id,v_user_id,NULL,v_card_id,v_category_id,v_merchant_id,v_provisional_merchant,
        'EXPENSE','CARD_PURCHASE',v_amount,v_currency,v_occurred_at,v_installments,
        v_interest_mode,v_tx->>'note','CONFIRMED',1,v_now,v_now
    );

    v_base := v_amount / v_installments;
    v_remainder := (v_amount % v_installments)::integer;
    FOR v_installment_number IN 1..v_installments LOOP
        v_principal := v_base + CASE WHEN v_installment_number <= v_remainder THEN 1 ELSE 0 END;
        v_due_date := private.credit_cycle_installment_due_date(v_occurred_at,v_card.closing_day,v_card.due_day,v_installment_number);
        INSERT INTO public.credit_installments (
            user_id,transaction_id,installment_number,due_date,principal_minor,interest_minor,status,revision,created_at,updated_at
        ) VALUES (
            v_user_id,v_tx_id,v_installment_number,v_due_date,v_principal,0,'PENDING',1,v_now,v_now
        ) RETURNING id INTO STRICT v_receipt_id;
        v_installment_ids := v_installment_ids || jsonb_build_array(jsonb_build_object(
            'id',v_receipt_id,'installment_number',v_installment_number,'due_date',v_due_date,
            'principal_minor',v_principal,'interest_minor',0
        ));
    END LOOP;

    v_after_debt := v_before_debt + v_amount;
    PERFORM private.emit_credit_utilization_crossings(v_user_id,v_card_id,v_tx_id,v_before_debt,v_after_debt,v_credit_limit);

    UPDATE public.cards SET revision=revision+1,updated_at=v_now WHERE id=v_card_id AND user_id=v_user_id;
    v_receipt_id := extensions.gen_random_uuid();
    v_response := jsonb_build_object(
        'status','APPLIED','transaction_id',v_tx_id,'receipt_id',v_receipt_id,'server_updated_at',v_now,'error',NULL,
        'installments',v_installment_ids,'amount_minor',v_amount,'remaining_debt_minor',v_after_debt
    );
    INSERT INTO internal.command_receipts(id,user_id,idempotency_key,command_type,request_hash,response_payload,status)
    VALUES(v_receipt_id,v_user_id,v_idempotency_key,'REGISTER_CARD_PURCHASE_V1',v_request_hash,v_response,'APPLIED');
    INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload)
    VALUES(v_user_id,'TRANSACTION',v_tx_id,1,'UPSERT',jsonb_build_object(
        'id',v_tx_id,'type','EXPENSE','operation_kind','CARD_PURCHASE','card_id',v_card_id,
        'amount_minor',v_amount,'currency_code',v_currency,'category_id',v_category_id,
        'merchant_id',v_merchant_id,'merchant_provisional_text',v_provisional_merchant,
        'occurred_at',v_occurred_at,'installment_count',v_installments,'installments',v_installment_ids,
        'status','ACTIVE','ledger_entries','[]'::jsonb
    ));
    RETURN v_response;
END;
$$;
REVOKE ALL ON FUNCTION public.register_transaction_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.register_transaction_v1(jsonb) TO authenticated, service_role;

CREATE OR REPLACE FUNCTION public.allocate_credit_payment_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_tx jsonb := p_command->'transaction';
    v_idempotency_key text := NULLIF(p_command->>'idempotency_key','');
    v_request_hash text;
    v_client_hash text := NULLIF(p_command->>'request_hash','');
    v_tx_id uuid;
    v_card_id uuid;
    v_account_id uuid;
    v_amount bigint;
    v_currency char(3);
    v_occurred_at timestamptz;
    v_card record;
    v_account record;
    v_existing record;
    v_receipt_id uuid;
    v_response jsonb;
    v_debt bigint;
    v_funds bigint;
    v_left bigint;
    v_alloc bigint;
    v_outstanding bigint;
    v_total_allocated bigint := 0;
    v_remaining_debt bigint;
    v_allocations jsonb := '[]'::jsonb;
    v_installment record;
    v_now timestamptz := clock_timestamp();
BEGIN
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','UNAUTHORIZED','field',NULL,'retryable',false));
    END IF;
    IF COALESCE((p_command->>'contract_version')::integer,1) <> 1 OR v_tx IS NULL OR v_idempotency_key IS NULL OR v_client_hash IS NULL THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_COMMAND','field',NULL,'retryable',false));
    END IF;
    IF upper(COALESCE(v_tx->>'type','')) <> 'TRANSFER' OR upper(COALESCE(v_tx->>'operation_kind','')) <> 'CARD_PAYMENT' THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_PAYMENT_KIND','field','transaction.type/operation_kind','retryable',false));
    END IF;
    BEGIN
        v_tx_id := (v_tx->>'id')::uuid;
        v_card_id := (v_tx->>'card_id')::uuid;
        v_account_id := (v_tx->>'source_account_id')::uuid;
        v_amount := (v_tx->>'amount_minor')::bigint;
        v_currency := (v_tx->>'currency_code')::char(3);
        v_occurred_at := COALESCE((v_tx->>'occurred_at')::timestamptz,v_now);
    EXCEPTION WHEN invalid_text_representation OR datetime_field_overflow THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_COMMAND_FIELD','field',NULL,'retryable',false));
    END;
    IF v_tx_id IS NULL OR v_card_id IS NULL OR v_account_id IS NULL OR v_amount IS NULL OR v_currency IS NULL OR v_amount < 1 OR v_amount > 10000000 THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_PAYMENT_FIELDS','field',NULL,'retryable',false));
    END IF;
    IF length(trim(v_currency::text))<>3 OR NOT EXISTS (SELECT 1 FROM public.currencies c WHERE c.code=v_currency) THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_CURRENCY','field','currency_code','retryable',false));
    END IF;

    -- Use the server's normalized JSONB representation as the authoritative
    -- request identity. A caller cannot force an idempotency hit with a reused hash.
    v_request_hash := encode(extensions.digest(convert_to(jsonb_build_object('contract_version',1,'transaction',v_tx)::text,'UTF8'),'sha256'),'hex');

    PERFORM pg_advisory_xact_lock(hashtextextended(v_user_id::text || ':' || v_idempotency_key,0));
    SELECT id,request_hash,response_payload INTO v_existing
    FROM internal.command_receipts WHERE user_id=v_user_id AND idempotency_key=v_idempotency_key;
    IF FOUND THEN
        IF v_existing.request_hash=v_request_hash THEN
            RETURN jsonb_set(v_existing.response_payload,'{status}','"DUPLICATE"'::jsonb);
        END IF;
        RETURN jsonb_build_object('status','CONFLICT','transaction_id',NULL,'receipt_id',v_existing.id,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','IDEMPOTENCY_CONFLICT','field','idempotency_key','retryable',false));
    END IF;
    IF EXISTS (SELECT 1 FROM public.transactions WHERE id=v_tx_id) THEN
        RETURN jsonb_build_object('status','CONFLICT','transaction_id',v_tx_id,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','TRANSACTION_ID_ALREADY_EXISTS','field','transaction.id','retryable',false));
    END IF;

    -- Lock in a consistent order: card, then source account, then installments by FIFO.
    SELECT c.id,c.user_id,c.is_credit,c.is_archived,c.account_id,c.revision INTO v_card
    FROM public.cards c WHERE c.id=v_card_id AND c.user_id=v_user_id FOR UPDATE;
    IF NOT FOUND OR NOT v_card.is_credit OR v_card.is_archived THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','CREDIT_CARD_UNAVAILABLE','field','card_id','retryable',false));
    END IF;
    SELECT a.id,a.user_id,a.currency_code,a.account_type,a.is_archived,a.initial_balance_minor_units INTO v_account
    FROM public.accounts a WHERE a.id=v_account_id AND a.user_id=v_user_id FOR UPDATE;
    IF NOT FOUND OR v_account.is_archived OR v_account.account_type='CREDIT_LIABILITY' OR v_account.currency_code<>v_currency THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','SOURCE_ACCOUNT_UNAVAILABLE_OR_CURRENCY_MISMATCH','field','source_account_id/currency_code','retryable',false));
    END IF;
    SELECT COALESCE(v_account.initial_balance_minor_units,0)+COALESCE(sum(le.signed_amount_minor),0)::bigint INTO v_funds
    FROM public.accounts a
    LEFT JOIN internal.ledger_entries le ON le.account_id=a.id AND le.user_id=a.user_id
    LEFT JOIN public.transactions tx ON tx.id=le.transaction_id AND tx.user_id=le.user_id
    WHERE a.id=v_account_id AND a.user_id=v_user_id
      AND (le.id IS NULL OR (tx.status='CONFIRMED' AND tx.deleted_at IS NULL));
    IF v_funds < v_amount THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INSUFFICIENT_FUNDS','field','amount_minor','retryable',false));
    END IF;

    SELECT COALESCE(sum(GREATEST(ci.principal_minor-COALESCE(a.allocated_minor,0),0)),0)::bigint INTO v_debt
    FROM public.credit_installments ci
    JOIN public.transactions purchase ON purchase.id=ci.transaction_id AND purchase.user_id=ci.user_id
    LEFT JOIN LATERAL (SELECT sum(x.allocated_minor)::bigint AS allocated_minor FROM public.credit_payment_allocations x WHERE x.user_id=ci.user_id AND x.installment_id=ci.id) a ON true
    WHERE ci.user_id=v_user_id AND purchase.card_id=v_card_id AND purchase.currency_code=v_currency
      AND ci.status IN ('PENDING','PARTIAL') AND purchase.status='CONFIRMED' AND purchase.deleted_at IS NULL;
    IF v_debt=0 THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',0,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','NO_OUTSTANDING_DEBT','field','card_id','retryable',false));
    END IF;
    IF v_amount > v_debt THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',v_debt,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','PAYMENT_EXCEEDS_OUTSTANDING_DEBT','field','amount_minor','retryable',false));
    END IF;

    INSERT INTO public.transactions (
        id,user_id,account_id,card_id,transaction_type,operation_kind,amount_minor,currency_code,
        occurred_at,installment_count,interest_mode,notes,status,revision,created_at,updated_at
    ) VALUES (
        v_tx_id,v_user_id,v_account_id,v_card_id,'TRANSFER','CARD_PAYMENT',v_amount,v_currency,
        v_occurred_at,1,'NONE',v_tx->>'note','CONFIRMED',1,v_now,v_now
    );
    INSERT INTO internal.ledger_entries (
        transaction_id,user_id,account_id,signed_amount_minor,currency_code,entry_role,occurred_at
    ) VALUES (v_tx_id,v_user_id,v_account_id,-v_amount,v_currency,'SOURCE',v_occurred_at);

    v_left := v_amount;
    FOR v_installment IN
        SELECT ci.id,ci.principal_minor,ci.installment_number,ci.due_date,purchase.occurred_at,
               COALESCE((SELECT sum(x.allocated_minor)::bigint FROM public.credit_payment_allocations x
                         WHERE x.user_id=ci.user_id AND x.installment_id=ci.id),0) AS already_allocated
        FROM public.credit_installments ci
        JOIN public.transactions purchase ON purchase.id=ci.transaction_id AND purchase.user_id=ci.user_id
        WHERE ci.user_id=v_user_id AND purchase.card_id=v_card_id AND purchase.currency_code=v_currency
          AND ci.status IN ('PENDING','PARTIAL') AND purchase.status='CONFIRMED' AND purchase.deleted_at IS NULL
        ORDER BY ci.due_date,purchase.occurred_at,ci.installment_number,ci.id
        FOR UPDATE OF ci
    LOOP
        EXIT WHEN v_left=0;
        v_outstanding := GREATEST(v_installment.principal_minor-v_installment.already_allocated,0);
        IF v_outstanding=0 THEN CONTINUE; END IF;
        v_alloc := least(v_outstanding,v_left);
        INSERT INTO public.credit_payment_allocations(user_id,payment_transaction_id,installment_id,allocated_minor,created_at)
        VALUES(v_user_id,v_tx_id,v_installment.id,v_alloc,v_now);
        UPDATE public.credit_installments
        SET status=CASE WHEN v_installment.already_allocated+v_alloc=v_installment.principal_minor THEN 'PAID' ELSE 'PARTIAL' END,
            revision=revision+1,updated_at=v_now
        WHERE id=v_installment.id AND user_id=v_user_id;
        v_allocations := v_allocations || jsonb_build_array(jsonb_build_object('installment_id',v_installment.id,'amount_minor',v_alloc));
        v_total_allocated := v_total_allocated+v_alloc;
        v_left := v_left-v_alloc;
    END LOOP;
    IF v_left<>0 OR v_total_allocated<>v_amount THEN
        RAISE EXCEPTION 'Credit payment allocation invariant failed for transaction %',v_tx_id USING ERRCODE='23514';
    END IF;

    UPDATE public.cards SET revision=revision+1,updated_at=v_now WHERE id=v_card_id AND user_id=v_user_id;
    v_remaining_debt := v_debt-v_total_allocated;
    v_receipt_id := extensions.gen_random_uuid();
    v_response := jsonb_build_object(
        'status','APPLIED','transaction_id',v_tx_id,'receipt_id',v_receipt_id,
        'allocated_total_minor',v_total_allocated,'remaining_debt_minor',v_remaining_debt,
        'allocations',v_allocations,'server_updated_at',v_now,'error',NULL
    );
    INSERT INTO internal.command_receipts(id,user_id,idempotency_key,command_type,request_hash,response_payload,status)
    VALUES(v_receipt_id,v_user_id,v_idempotency_key,'ALLOCATE_CREDIT_PAYMENT_V1',v_request_hash,v_response,'APPLIED');
    INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload)
    VALUES(v_user_id,'TRANSACTION',v_tx_id,1,'UPSERT',jsonb_build_object(
        'id',v_tx_id,'type','TRANSFER','operation_kind','CARD_PAYMENT','card_id',v_card_id,
        'amount_minor',v_amount,'currency_code',v_currency,'source_account_id',v_account_id,
        'occurred_at',v_occurred_at,'status','ACTIVE','allocations',v_allocations,
        'ledger_entries',jsonb_build_array(jsonb_build_object('account_id',v_account_id,'role','SOURCE','signed_amount_minor',-v_amount,'currency_code',v_currency))
    ));
    RETURN v_response;
END;
$$;
REVOKE ALL ON FUNCTION public.allocate_credit_payment_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.allocate_credit_payment_v1(jsonb) TO authenticated, service_role;

-- HU-11 personal contract rate update. The referential catalog remains read-only;
-- this command changes only the authenticated owner's card row.
CREATE OR REPLACE FUNCTION public.update_card_personal_tea_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_card jsonb := p_command->'card';
    v_idempotency_key text := NULLIF(p_command->>'idempotency_key','');
    v_client_hash text := NULLIF(p_command->>'request_hash','');
    v_request_hash text;
    v_card_id uuid;
    v_bps integer;
    v_has_rate boolean;
    v_existing record;
    v_card_owner uuid;
    v_is_credit boolean;
    v_archived boolean;
    v_revision bigint;
    v_receipt_id uuid;
    v_response jsonb;
    v_now timestamptz := clock_timestamp();
BEGIN
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('status','REJECTED','card_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','UNAUTHORIZED','field',NULL,'retryable',false));
    END IF;
    IF COALESCE((p_command->>'contract_version')::integer,1) <> 1 OR v_card IS NULL
       OR v_idempotency_key IS NULL OR v_client_hash IS NULL OR NOT (v_card ? 'personal_tea_bps') THEN
        RETURN jsonb_build_object('status','REJECTED','card_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_COMMAND','field',NULL,'retryable',false));
    END IF;
    BEGIN
        v_card_id := (v_card->>'id')::uuid;
        v_has_rate := jsonb_typeof(v_card->'personal_tea_bps') <> 'null';
        IF v_has_rate THEN v_bps := (v_card->>'personal_tea_bps')::integer; END IF;
    EXCEPTION WHEN invalid_text_representation THEN
        RETURN jsonb_build_object('status','REJECTED','card_id',NULL,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_CARD_RATE','field','card.personal_tea_bps','retryable',false));
    END;
    IF v_card_id IS NULL OR (v_has_rate AND (v_bps < 0 OR v_bps > 100000)) THEN
        RETURN jsonb_build_object('status','REJECTED','card_id',v_card_id,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_CARD_RATE','field','card.personal_tea_bps','retryable',false));
    END IF;
    v_request_hash := encode(extensions.digest(convert_to(jsonb_build_object('contract_version',1,'card',v_card)::text,'UTF8'),'sha256'),'hex');
    PERFORM pg_advisory_xact_lock(hashtextextended(v_user_id::text || ':' || v_idempotency_key,0));
    SELECT id,request_hash,response_payload INTO v_existing
    FROM internal.command_receipts WHERE user_id=v_user_id AND idempotency_key=v_idempotency_key;
    IF FOUND THEN
        IF v_existing.request_hash=v_request_hash THEN
            RETURN jsonb_set(v_existing.response_payload,'{status}','"DUPLICATE"'::jsonb);
        END IF;
        RETURN jsonb_build_object('status','CONFLICT','card_id',NULL,'receipt_id',v_existing.id,'server_updated_at',v_now,
            'error',jsonb_build_object('code','IDEMPOTENCY_CONFLICT','field','idempotency_key','retryable',false));
    END IF;
    SELECT c.user_id,c.is_credit,c.is_archived,c.revision INTO v_card_owner,v_is_credit,v_archived,v_revision
    FROM public.cards c WHERE c.id=v_card_id FOR UPDATE;
    IF NOT FOUND OR v_card_owner<>v_user_id OR NOT v_is_credit OR v_archived THEN
        RETURN jsonb_build_object('status','REJECTED','card_id',v_card_id,'receipt_id',NULL,'server_updated_at',v_now,
            'error',jsonb_build_object('code','CREDIT_CARD_UNAVAILABLE','field','card.id','retryable',false));
    END IF;
    UPDATE public.cards SET personal_tea_bps=CASE WHEN v_has_rate THEN v_bps ELSE NULL END,
        revision=revision+1,updated_at=v_now WHERE id=v_card_id AND user_id=v_user_id
        RETURNING revision INTO v_revision;
    v_receipt_id := extensions.gen_random_uuid();
    v_response := jsonb_build_object('status','APPLIED','card_id',v_card_id,
        'personal_tea_bps',CASE WHEN v_has_rate THEN to_jsonb(v_bps) ELSE 'null'::jsonb END,
        'revision',v_revision,'receipt_id',v_receipt_id,'server_updated_at',v_now,'error',NULL);
    INSERT INTO internal.command_receipts(id,user_id,idempotency_key,command_type,request_hash,response_payload,status)
    VALUES(v_receipt_id,v_user_id,v_idempotency_key,'UPDATE_CARD_PERSONAL_TEA_V1',v_request_hash,v_response,'APPLIED');
    INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload)
    VALUES(v_user_id,'CARD',v_card_id,v_revision,'UPSERT',jsonb_build_object(
        'id',v_card_id,'personal_tea_bps',CASE WHEN v_has_rate THEN to_jsonb(v_bps) ELSE 'null'::jsonb END));
    RETURN v_response;
END;
$$;
REVOKE ALL ON FUNCTION public.update_card_personal_tea_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.update_card_personal_tea_v1(jsonb) TO authenticated;

-- Old app RPC names remain as compatibility adapters only. They never append a
-- movement row; purchase callers must now include an expense category ID.
CREATE OR REPLACE FUNCTION public.confirm_credit_purchase_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE v_result jsonb; v_op_id text; v_hash text; v_user_id uuid := auth.uid(); v_legacy record;
BEGIN
    v_op_id := p_command->>'operation_id';
    v_hash := COALESCE(p_command->>'payload_hash',p_command->>'request_hash');
    IF v_user_id IS NOT NULL AND v_op_id IS NOT NULL AND to_regclass('public.financial_movements') IS NOT NULL THEN
        SELECT m.id,m.card_id,m.amount_minor_units,m.currency,m.effective_at,tx.id AS transaction_id
        INTO v_legacy
        FROM public.financial_movements m
        LEFT JOIN public.transactions tx ON tx.id=m.id AND tx.user_id=m.user_id
        WHERE m.user_id=v_user_id AND m.operation_id::text=v_op_id AND m.kind='CREDIT_PURCHASE' AND m.status='POSTED'
        ORDER BY m.operation_sequence,m.id LIMIT 1;
        IF FOUND AND v_legacy.transaction_id IS NOT NULL THEN
            RETURN jsonb_build_object('status','DUPLICATE','transaction_id',v_legacy.transaction_id,'receipt_id',NULL,
                'amount_minor',v_legacy.amount_minor_units,'currency_code',v_legacy.currency::text,
                'installments',(SELECT COALESCE(jsonb_agg(jsonb_build_object('id',ci.id,'installment_number',ci.installment_number,'due_date',ci.due_date,'principal_minor',ci.principal_minor,'interest_minor',ci.interest_minor) ORDER BY ci.installment_number),'[]'::jsonb)
                    FROM public.credit_installments ci WHERE ci.user_id=v_user_id AND ci.transaction_id=v_legacy.transaction_id),
                'server_updated_at',v_legacy.effective_at,'error',NULL);
        END IF;
    END IF;
    v_result := public.register_transaction_v1(jsonb_build_object(
        'contract_version',1,'idempotency_key','compat-confirm-credit-purchase-v1:'||v_op_id,'request_hash',v_hash,
        'transaction',jsonb_build_object(
            'id',COALESCE(p_command->>'transaction_id',p_command->>'operation_id'),
            'type','EXPENSE','operation_kind','CARD_PURCHASE','card_id',p_command->>'card_id',
            'amount_minor',COALESCE(p_command->>'amount_minor_units',p_command->>'amount_minor'),
            'currency_code',COALESCE(p_command->>'currency',p_command->>'currency_code'),
            'category_id',COALESCE(p_command->>'category_id',p_command->>'expense_category_id'),
            'merchant_provisional_text',p_command->>'merchant',
            'occurred_at',COALESCE(p_command->>'effective_at',p_command->>'occurred_at'),
            'installment_count',COALESCE(p_command->>'installments',p_command->>'installment_count'),
            'interest_mode',COALESCE(p_command->>'interest_mode','NONE')
        )
    ));
    RETURN v_result;
END;
$$;
REVOKE ALL ON FUNCTION public.confirm_credit_purchase_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.confirm_credit_purchase_v1(jsonb) TO authenticated;

CREATE OR REPLACE FUNCTION public.pay_credit_card_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE v_result jsonb; v_op_id text; v_hash text; v_user_id uuid := auth.uid(); v_legacy record;
BEGIN
    v_op_id := p_command->>'operation_id';
    v_hash := COALESCE(p_command->>'payload_hash',p_command->>'request_hash');
    IF v_user_id IS NOT NULL AND v_op_id IS NOT NULL AND to_regclass('public.financial_movements') IS NOT NULL THEN
        SELECT cash.id,cash.account_id,liability.card_id,abs(cash.amount_minor_units)::bigint AS amount_minor,cash.currency,cash.effective_at,tx.id AS transaction_id
        INTO v_legacy
        FROM public.financial_movements cash
        JOIN public.financial_movements liability ON liability.user_id=cash.user_id AND liability.operation_id=cash.operation_id
            AND liability.kind='CARD_PAYMENT_LIABILITY' AND liability.status='POSTED'
        LEFT JOIN public.transactions tx ON tx.id=cash.id AND tx.user_id=cash.user_id
        WHERE cash.user_id=v_user_id AND cash.operation_id::text=v_op_id AND cash.kind='CARD_PAYMENT_CASH' AND cash.status='POSTED'
        ORDER BY cash.operation_sequence,cash.id LIMIT 1;
        IF FOUND AND v_legacy.transaction_id IS NOT NULL THEN
            RETURN jsonb_build_object('status','DUPLICATE','transaction_id',v_legacy.transaction_id,'receipt_id',NULL,
                'allocated_total_minor',(SELECT COALESCE(sum(cpa.allocated_minor),0)::bigint FROM public.credit_payment_allocations cpa WHERE cpa.user_id=v_user_id AND cpa.payment_transaction_id=v_legacy.transaction_id),
                'remaining_debt_minor',(SELECT COALESCE(sum(GREATEST(ci.principal_minor-COALESCE(a.allocated_minor,0),0)),0)::bigint
                    FROM public.credit_installments ci JOIN public.transactions purchase ON purchase.id=ci.transaction_id AND purchase.user_id=ci.user_id
                    LEFT JOIN LATERAL (SELECT sum(x.allocated_minor)::bigint AS allocated_minor FROM public.credit_payment_allocations x WHERE x.user_id=ci.user_id AND x.installment_id=ci.id) a ON true
                    WHERE ci.user_id=v_user_id AND purchase.card_id=v_legacy.card_id AND purchase.currency_code=v_legacy.currency AND ci.status IN ('PENDING','PARTIAL')),
                'allocations',(SELECT COALESCE(jsonb_agg(jsonb_build_object('installment_id',cpa.installment_id,'amount_minor',cpa.allocated_minor) ORDER BY cpa.created_at,cpa.id),'[]'::jsonb) FROM public.credit_payment_allocations cpa WHERE cpa.user_id=v_user_id AND cpa.payment_transaction_id=v_legacy.transaction_id),
                'server_updated_at',v_legacy.effective_at,'error',NULL);
        END IF;
    END IF;
    v_result := public.allocate_credit_payment_v1(jsonb_build_object(
        'contract_version',1,'idempotency_key','compat-pay-credit-card-v1:'||v_op_id,'request_hash',v_hash,
        'transaction',jsonb_build_object(
            'id',COALESCE(p_command->>'transaction_id',p_command->>'operation_id'),
            'type','TRANSFER','operation_kind','CARD_PAYMENT','card_id',p_command->>'card_id',
            'source_account_id',p_command->>'source_account_id',
            'amount_minor',COALESCE(p_command->>'amount_minor_units',p_command->>'amount_minor'),
            'currency_code',COALESCE(p_command->>'currency',p_command->>'currency_code'),
            'occurred_at',COALESCE(p_command->>'effective_at',p_command->>'occurred_at'),
            'note',p_command->>'note'
        )
    ));
    RETURN v_result;
END;
$$;
REVOKE ALL ON FUNCTION public.pay_credit_card_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.pay_credit_card_v1(jsonb) TO authenticated;

-- Backfill legacy posted movements into the canonical ledger/schedule once. The
-- original installment count and contractual due date are not stored on movement
-- rows, so each purchase becomes one explicitly documented aggregate balance
-- installment. Its effective local date is an ordering placeholder, not a claim
-- about the contractual due date. Payments retain their exact amount and timestamp.
-- Historical payments are accepted postings: their import must not revalidate
-- today's available funds, which may reflect later expenses. Ownership, currency,
-- accepted debt, FIFO allocations, and the equal asset/liability reduction remain checked.
CREATE OR REPLACE FUNCTION private.import_legacy_credit_payment_v1(
    p_transaction_id uuid,
    p_user_id uuid,
    p_source_account_id uuid,
    p_card_id uuid,
    p_amount_minor bigint,
    p_currency_code text,
    p_occurred_at timestamptz,
    p_created_at timestamptz
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_card record;
    v_account record;
    v_existing record;
    v_debt bigint;
    v_left bigint;
    v_outstanding bigint;
    v_allocate bigint;
    v_allocated bigint := 0;
    v_allocations jsonb := '[]'::jsonb;
    v_installment record;
    v_now timestamptz := clock_timestamp();
BEGIN
    IF p_transaction_id IS NULL OR p_user_id IS NULL OR p_source_account_id IS NULL
       OR p_card_id IS NULL OR p_amount_minor IS NULL OR p_amount_minor <= 0
       OR p_currency_code IS NULL OR length(p_currency_code) <> 3 OR p_occurred_at IS NULL THEN
        RAISE EXCEPTION 'Legacy card payment is missing a valid identity, amount, currency or timestamp';
    END IF;

    SELECT c.id,c.user_id,c.is_credit,c.is_archived,c.revision
    INTO v_card
    FROM public.cards c
    WHERE c.id=p_card_id AND c.user_id=p_user_id
    FOR UPDATE;
    IF NOT FOUND OR NOT v_card.is_credit OR v_card.is_archived THEN
        RAISE EXCEPTION 'Legacy card payment % references an unavailable owner card',p_transaction_id;
    END IF;

    SELECT a.id,a.user_id,a.currency_code,a.account_type,a.is_archived
    INTO v_account
    FROM public.accounts a
    WHERE a.id=p_source_account_id AND a.user_id=p_user_id
    FOR UPDATE;
    IF NOT FOUND OR v_account.is_archived OR v_account.account_type='CREDIT_LIABILITY'
       OR v_account.currency_code<>p_currency_code THEN
        RAISE EXCEPTION 'Legacy card payment % references an unavailable owner account or currency mismatch',p_transaction_id;
    END IF;

    SELECT tx.id,tx.user_id,tx.account_id,tx.card_id,tx.transaction_type,tx.operation_kind,tx.amount_minor,tx.currency_code
    INTO v_existing
    FROM public.transactions tx
    WHERE tx.id=p_transaction_id
    FOR UPDATE;
    IF FOUND THEN
        IF v_existing.user_id=p_user_id AND v_existing.account_id=p_source_account_id
           AND v_existing.card_id=p_card_id AND v_existing.transaction_type='TRANSFER'
           AND v_existing.operation_kind='CARD_PAYMENT' AND v_existing.amount_minor=p_amount_minor
           AND v_existing.currency_code=p_currency_code::char(3)
           AND (SELECT COALESCE(sum(cpa.allocated_minor),0)::bigint
                FROM public.credit_payment_allocations cpa
                WHERE cpa.user_id=p_user_id AND cpa.payment_transaction_id=p_transaction_id)=p_amount_minor
           AND (SELECT COALESCE(sum(le.signed_amount_minor),0)::bigint
                FROM internal.ledger_entries le
                WHERE le.user_id=p_user_id AND le.transaction_id=p_transaction_id
                  AND le.account_id=p_source_account_id AND le.entry_role='SOURCE')=-p_amount_minor THEN
            RETURN jsonb_build_object('status','DUPLICATE','transaction_id',p_transaction_id,
                'allocated_total_minor',p_amount_minor,'error',NULL);
        END IF;
        RAISE EXCEPTION 'Legacy card payment % conflicts with an existing canonical transaction',p_transaction_id;
    END IF;

    SELECT COALESCE(sum(GREATEST(ci.principal_minor-COALESCE(a.allocated_minor,0),0)),0)::bigint
    INTO v_debt
    FROM public.credit_installments ci
    JOIN public.transactions purchase ON purchase.id=ci.transaction_id AND purchase.user_id=ci.user_id
    LEFT JOIN LATERAL (
        SELECT sum(x.allocated_minor)::bigint AS allocated_minor
        FROM public.credit_payment_allocations x
        WHERE x.user_id=ci.user_id AND x.installment_id=ci.id
    ) a ON true
    WHERE ci.user_id=p_user_id AND purchase.card_id=p_card_id AND purchase.currency_code=p_currency_code::char(3)
      AND ci.status IN ('PENDING','PARTIAL') AND purchase.status='CONFIRMED' AND purchase.deleted_at IS NULL;
    IF v_debt < p_amount_minor THEN
        RAISE EXCEPTION 'Legacy card payment % exceeds reconciled posted credit debt (payment %, debt %)',
            p_transaction_id,p_amount_minor,v_debt;
    END IF;

    INSERT INTO public.transactions (
        id,user_id,account_id,card_id,transaction_type,operation_kind,amount_minor,currency_code,
        occurred_at,installment_count,interest_mode,notes,status,revision,created_at,updated_at
    ) VALUES (
        p_transaction_id,p_user_id,p_source_account_id,p_card_id,'TRANSFER','CARD_PAYMENT',p_amount_minor,
        p_currency_code::char(3),p_occurred_at,1,'NONE',
        'Imported from an accepted legacy card payment; current account funds were not revalidated.',
        'CONFIRMED',1,COALESCE(p_created_at,v_now),v_now
    );
    INSERT INTO internal.ledger_entries (
        transaction_id,user_id,account_id,signed_amount_minor,currency_code,entry_role,occurred_at
    ) VALUES (
        p_transaction_id,p_user_id,p_source_account_id,-p_amount_minor,p_currency_code::char(3),'SOURCE',p_occurred_at
    );

    v_left := p_amount_minor;
    FOR v_installment IN
        SELECT ci.id,ci.principal_minor,ci.installment_number,ci.due_date,purchase.occurred_at,
               COALESCE((SELECT sum(x.allocated_minor)::bigint FROM public.credit_payment_allocations x
                         WHERE x.user_id=ci.user_id AND x.installment_id=ci.id),0) AS already_allocated
        FROM public.credit_installments ci
        JOIN public.transactions purchase ON purchase.id=ci.transaction_id AND purchase.user_id=ci.user_id
        WHERE ci.user_id=p_user_id AND purchase.card_id=p_card_id AND purchase.currency_code=p_currency_code::char(3)
          AND ci.status IN ('PENDING','PARTIAL') AND purchase.status='CONFIRMED' AND purchase.deleted_at IS NULL
        ORDER BY ci.due_date,purchase.occurred_at,ci.installment_number,ci.id
        FOR UPDATE OF ci
    LOOP
        EXIT WHEN v_left=0;
        v_outstanding := GREATEST(v_installment.principal_minor-v_installment.already_allocated,0);
        IF v_outstanding=0 THEN CONTINUE; END IF;
        v_allocate := least(v_outstanding,v_left);
        INSERT INTO public.credit_payment_allocations(user_id,payment_transaction_id,installment_id,allocated_minor,created_at)
        VALUES(p_user_id,p_transaction_id,v_installment.id,v_allocate,v_now);
        UPDATE public.credit_installments
        SET status=CASE WHEN v_installment.already_allocated+v_allocate=v_installment.principal_minor THEN 'PAID' ELSE 'PARTIAL' END,
            revision=revision+1,updated_at=v_now
        WHERE id=v_installment.id AND user_id=p_user_id;
        v_allocations := v_allocations || jsonb_build_array(jsonb_build_object(
            'installment_id',v_installment.id,'amount_minor',v_allocate));
        v_allocated := v_allocated+v_allocate;
        v_left := v_left-v_allocate;
    END LOOP;
    IF v_left<>0 OR v_allocated<>p_amount_minor THEN
        RAISE EXCEPTION 'Legacy credit payment allocation invariant failed for transaction %',p_transaction_id;
    END IF;

    UPDATE public.cards SET revision=revision+1,updated_at=v_now WHERE id=p_card_id AND user_id=p_user_id;
    INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload)
    VALUES(p_user_id,'TRANSACTION',p_transaction_id,1,'UPSERT',jsonb_build_object(
        'id',p_transaction_id,'type','TRANSFER','operation_kind','CARD_PAYMENT','card_id',p_card_id,
        'amount_minor',p_amount_minor,'currency_code',p_currency_code,'source_account_id',p_source_account_id,
        'occurred_at',p_occurred_at,'status','ACTIVE','allocations',v_allocations,
        'ledger_entries',jsonb_build_array(jsonb_build_object('account_id',p_source_account_id,
            'role','SOURCE','signed_amount_minor',-p_amount_minor,'currency_code',p_currency_code))));
    RETURN jsonb_build_object('status','APPLIED','transaction_id',p_transaction_id,
        'allocated_total_minor',v_allocated,'remaining_debt_minor',v_debt-v_allocated,'allocations',v_allocations,'error',NULL);
END;
$$;
REVOKE ALL ON FUNCTION private.import_legacy_credit_payment_v1(uuid, uuid, uuid, uuid, bigint, text, timestamptz, timestamptz)
    FROM PUBLIC, anon, authenticated, service_role;

DO $$
DECLARE r record; v_result jsonb;
BEGIN
    IF to_regclass('public.financial_movements') IS NULL THEN RETURN; END IF;
    IF EXISTS (
        SELECT 1 FROM public.financial_movements m
        WHERE m.kind='CREDIT_PURCHASE' AND m.status='POSTED'
          AND (m.card_id IS NULL OR m.amount_minor_units<=0)
    ) THEN
        RAISE EXCEPTION 'Legacy credit purchase rows are missing card_id or positive amount; reconcile before applying S3 migration';
    END IF;

    INSERT INTO public.transactions (
        id,user_id,account_id,card_id,transaction_type,operation_kind,amount_minor,currency_code,
        occurred_at,installment_count,interest_mode,notes,status,revision,created_at,updated_at
    )
    SELECT m.id,m.user_id,NULL,m.card_id,'EXPENSE','CARD_PURCHASE',m.amount_minor_units,m.currency,
           m.effective_at,1,'NONE',
           'Legacy credit purchase imported as one aggregate balance installment; original installment count and due date were unavailable.',
           'CONFIRMED',1,m.created_at,m.created_at
    FROM public.financial_movements m
    WHERE m.kind='CREDIT_PURCHASE' AND m.status='POSTED'
      AND NOT EXISTS (SELECT 1 FROM public.transactions tx WHERE tx.id=m.id);

    INSERT INTO public.credit_installments (
        user_id,transaction_id,installment_number,due_date,principal_minor,interest_minor,status,revision,created_at,updated_at
    )
    SELECT m.user_id,m.id,1,(m.effective_at AT TIME ZONE 'America/Lima')::date,m.amount_minor_units,0,'PENDING',1,m.created_at,m.created_at
    FROM public.financial_movements m
    JOIN public.transactions tx ON tx.id=m.id AND tx.user_id=m.user_id AND tx.card_id=m.card_id AND tx.operation_kind='CARD_PURCHASE'
    WHERE m.kind='CREDIT_PURCHASE' AND m.status='POSTED'
      AND NOT EXISTS (SELECT 1 FROM public.credit_installments ci WHERE ci.user_id=m.user_id AND ci.transaction_id=tx.id);

    IF EXISTS (
        SELECT 1 FROM public.financial_movements liability
        LEFT JOIN public.financial_movements cash
          ON cash.user_id=liability.user_id AND cash.operation_id=liability.operation_id
         AND cash.kind='CARD_PAYMENT_CASH' AND cash.status='POSTED'
        WHERE liability.kind='CARD_PAYMENT_LIABILITY' AND liability.status='POSTED'
          AND (cash.id IS NULL OR cash.account_id IS NULL
               OR (cash.card_id IS NOT NULL AND cash.card_id IS DISTINCT FROM liability.card_id)
               OR cash.currency IS DISTINCT FROM liability.currency
               OR cash.amount_minor_units>=0 OR liability.amount_minor_units>=0
               OR abs(cash.amount_minor_units)<>abs(liability.amount_minor_units))
    ) THEN
        RAISE EXCEPTION 'Legacy card-payment pair is incomplete or inconsistent; reconcile before applying S3 migration';
    END IF;
    IF EXISTS (
        SELECT 1 FROM public.financial_movements cash
        LEFT JOIN public.financial_movements liability
          ON liability.user_id=cash.user_id AND liability.operation_id=cash.operation_id
         AND liability.kind='CARD_PAYMENT_LIABILITY' AND liability.status='POSTED'
        WHERE cash.kind='CARD_PAYMENT_CASH' AND cash.status='POSTED' AND liability.id IS NULL
    ) THEN
        RAISE EXCEPTION 'Legacy card-payment cash movement has no liability pair; reconcile before applying S3 migration';
    END IF;

    FOR r IN
        SELECT cash.id AS transaction_id,cash.user_id,cash.operation_id,cash.account_id,
               liability.card_id,abs(cash.amount_minor_units)::bigint AS amount_minor,
               cash.currency::text AS currency_code,cash.effective_at AS occurred_at,cash.created_at
        FROM public.financial_movements cash
        JOIN public.financial_movements liability
          ON liability.user_id=cash.user_id AND liability.operation_id=cash.operation_id
         AND liability.kind='CARD_PAYMENT_LIABILITY' AND liability.status='POSTED'
        WHERE cash.kind='CARD_PAYMENT_CASH' AND cash.status='POSTED'
        ORDER BY cash.effective_at,cash.operation_id,cash.id
    LOOP
        v_result := private.import_legacy_credit_payment_v1(
            r.transaction_id,r.user_id,r.account_id,r.card_id,r.amount_minor,
            r.currency_code,r.occurred_at,r.created_at
        );
        IF v_result->>'status' NOT IN ('APPLIED','DUPLICATE') THEN
            RAISE EXCEPTION 'Legacy card payment % could not be reconciled: %',r.operation_id,v_result;
        END IF;
    END LOOP;
END;
$$;

-- Least privilege for exposed catalog/schedule/notification data. The RPC owner
-- writes schedules and events; authenticated clients can read only their RLS rows.
REVOKE ALL ON TABLE public.credit_products, public.credit_installments, public.credit_payment_allocations FROM PUBLIC, anon, authenticated;
GRANT SELECT ON TABLE public.credit_products, public.credit_installments, public.credit_payment_allocations TO authenticated;
REVOKE ALL ON TABLE public.app_notifications FROM PUBLIC, anon, authenticated;
GRANT SELECT ON TABLE public.app_notifications TO authenticated;
GRANT UPDATE (is_read) ON TABLE public.app_notifications TO authenticated;

COMMIT;
