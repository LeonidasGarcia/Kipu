# Mapeo de Migración Documental y Mockups UI/UX de Kipu

Este documento registra la trazabilidad completa de la migración y reorganización de assets visuales, referencias y documentación desde las carpetas no estandarizadas (`Mock/`, `redesign-references/`, `evidence/`, `evidence-final/` y raíz de `docs/`) hacia la estructura canónica y escalable de `docs/ux-ui/`.

---

## 1. Mapeo Completo de Mockups y Assets Visuales

| Archivo anterior | Contenido identificado tras inspección visual | Nueva ruta | Categoría |
|:---|:---|:---|:---|
| `Mock/image copy 3.png` | **Mi dinero (Dashboard normal):** Balance general `S/ 5,210.00`, subtítulo de activos líquidos, desglose de cuentas/efectivo, tarjeta BBVA Visa Signature y barra flotante con FAB `+`. | [`docs/ux-ui/mockups/canonical/dashboard/dashboard.png`](./mockups/canonical/dashboard/dashboard.png) | Canonical |
| `Mock/image.png` | **Registrar movimiento (Gasto):** Modal bottom sheet con pestaña Gasto seleccionada (`- S/ 45.50`), selector PEN/USD, cuenta de cargo Débito BCP, categoría Alimentación, Cafetería Miraflores y botón "Guardar gasto". | [`docs/ux-ui/mockups/canonical/quick-movement/register-movement.png`](./mockups/canonical/quick-movement/register-movement.png) | Canonical |
| `Mock/image copy 8.png` | **Movimientos (Historial con datos):** Ledger cronológico con balance neto del mes (`+S/ 1,420.50`), buscador, chips de filtro rápido y transacciones agrupadas (Hoy, Ayer 25 de setiembre, 29 de setiembre). | [`docs/ux-ui/mockups/canonical/movements/movements.png`](./mockups/canonical/movements/movements.png) | Canonical |
| `docs/ux-ui/redesign-references/add-instrument.png` | **Agregar a Mi dinero:** Flujo de entrada con opciones en lenguaje natural (Cuenta bancaria, Tarjeta de crédito, Billetera digital, Efectivo) y nota de privacidad. | [`docs/ux-ui/mockups/canonical/add-money/add-to-my-money.png`](./mockups/canonical/add-money/add-to-my-money.png) | Canonical |
| `Mock/image copy 10.png` | **¿Qué tarjeta tienes? (Paso 2):** Catálogo de tarjetas de crédito BCP con filtros ("Todas", "Clásica & Oro", "Signature / Black"), tarjeta Visa Signature Qore seleccionada y sticky CTA. | [`docs/ux-ui/mockups/canonical/cards/select-card.png`](./mockups/canonical/cards/select-card.png) | Canonical |
| `Mock/image copy 14.png` | **Configura tu tarjeta (Paso 3):** Formulario contractual con tarjeta superior seleccionada, línea de crédito `S/ 8,000.00`, día de corte 15, día de pago 5, últimos 4 dígitos `•••• 8989` y alertas de uso (80%, 100%). | [`docs/ux-ui/mockups/canonical/cards/configure-card.png`](./mockups/canonical/cards/configure-card.png) | Canonical |
| `Mock/image copy 6.png` | **Dashboard en Modo Privacidad:** Cabecera con banner informativo verde oscuro ("Modo privacidad activado"), icono de ojo tachado y saldos enmascarados con puntos (`S/ • • • • • •`). | [`docs/ux-ui/mockups/states/dashboard/dashboard-privacy.png`](./mockups/states/dashboard/dashboard-privacy.png) | State |
| `Mock/image copy 7.png` | **Dashboard con Límite Free Alcanzado:** Indicador "Plan Básico - 4 de 4 cuentas usadas (100%)", botón "Mejorar a PRO", banner de aviso accesible y botón bloqueado con candado. | [`docs/ux-ui/mockups/states/dashboard/dashboard-free-limit.png`](./mockups/states/dashboard/dashboard-free-limit.png) | State |
| `Mock/image copy 4.png` | **Registrar movimiento con Errores de Validación:** Cabecera con distintivo "2 errores", banner de saldo insuficiente en Débito BCP, monto en rojo (`- S/ 250.00`), selector de categoría en alerta y botón "Corregir datos para guardar". | [`docs/ux-ui/mockups/states/quick-movement/register-movement-errors.png`](./mockups/states/quick-movement/register-movement-errors.png) | State |
| `Mock/image copy.png` | **Registrar movimiento (Ingreso):** Pestaña Ingreso seleccionada (`+ S/ 3,850.00`), cuenta destino Cuenta Sueldo BCP, categoría Sueldo / Salario y pagador Empresa Tech SAC. | [`docs/ux-ui/mockups/states/quick-movement/register-income.png`](./mockups/states/quick-movement/register-income.png) | State |
| `Mock/image copy 2.png` | **Registrar movimiento (Transferencia):** Pestaña Transferencia seleccionada (`S/ 500.00`), cuenta origen Sueldo BCP conectada verticalmente a destino BBVA Ahorros y comisión gratuita. | [`docs/ux-ui/mockups/states/quick-movement/register-transfer.png`](./mockups/states/quick-movement/register-transfer.png) | State |
| `Mock/image copy 9.png` | **Historial Vacío (Empty State):** Estado cuando el mes seleccionado no tiene operaciones (`S/ 0.00`, 0 movimientos), ilustración semántica y botones "+ Registrar primer movimiento" e "Importar extracto". | [`docs/ux-ui/mockups/states/movements/movements-empty.png`](./mockups/states/movements/movements-empty.png) | State |
| `Mock/image copy 11.png` | **Seleccionar Entidad Emisora:** Hoja modal con buscador, grilla 2x2 de bancos peruanos (BCP, BBVA, Interbank, Scotiabank) y tarjetas retail (CMR Falabella, Cencosud, Ripley). | [`docs/ux-ui/mockups/states/cards/select-bank.png`](./mockups/states/cards/select-bank.png) | State |
| `Mock/image copy 12.png` | **Búsqueda Dinámica de Tarjeta:** Filtro en tiempo real con término `"latam"`, indicador "1 tarjeta encontrada para 'latam'" y selección de Visa LATAM Pass con corte sugerido. | [`docs/ux-ui/mockups/states/cards/card-search.png`](./mockups/states/cards/card-search.png) | State |
| `Mock/image copy 13.png` | **Búsqueda de Tarjeta sin Resultados:** Mensaje empty "No encontramos «Amex Platinum Corp» en BCP" con sugerencias y fallback explícito a "Registrar tarjeta personalizada" / "Crear tarjeta manual". | [`docs/ux-ui/mockups/states/cards/card-not-found.png`](./mockups/states/cards/card-not-found.png) | State |
| `Mock/image copy 16.png` | **Configurar Tarjeta con TEA Expandida:** Acordeón desplegado con progressive disclosure: campo de TEA Anual contractual (`89.90%`) y tasa referencial de mercado BCP (`84.50%`) con botón de aplicar. | [`docs/ux-ui/mockups/states/cards/card-tea-expanded.png`](./mockups/states/cards/card-tea-expanded.png) | State |
| `Mock/image copy 17.png` | **Tarjeta Configurada con Éxito:** Hoja modal de confirmación con check circular verde, badge de la tarjeta creada, grid 2x2 (línea total, alertas, corte, pago) y CTAs de navegación. | [`docs/ux-ui/mockups/states/cards/card-created-success.png`](./mockups/states/cards/card-created-success.png) | State |
| `Mock/image copy 18.png` | **Board Design System Calm Emerald Fintech:** Especificación visual de paleta (Primary `#0B5F51`, Secondary `#22C79A`, Tertiary `#BCEBDD`, Neutral `#17211E`), escala tipográfica Inter y botones. | [`docs/ux-ui/mockups/inspiration/design-system/calm-emerald-fintech.png`](./mockups/inspiration/design-system/calm-emerald-fintech.png) | Inspiration |
| `Mock/image copy 19.png` | **Estudio Gradient Builder - Emerald Night (Dark):** Referencia interactiva de preset "Emerald night · Aurora" con gradiente celeste a esmeralda profundo sobre negro azulado (`#132B45`). | [`docs/ux-ui/mockups/inspiration/gradients/emerald-night.png`](./mockups/inspiration/gradients/emerald-night.png) | Inspiration |
| `Mock/image copy 20.png` | **Estudio Gradient Builder - Emerald Flow (Light):** Referencia interactiva de preset "Emerald · Flow" con gradiente fluido de verdes menta (`#F0FBEF`, `#22C79A`, `#0B5F51`). | [`docs/ux-ui/mockups/inspiration/gradients/emerald-flow.png`](./mockups/inspiration/gradients/emerald-flow.png) | Inspiration |

---

## 2. Duplicados Verificados (Conservados en `archive/duplicates/`)

| Archivo anterior | Archivo canónico retenido | Hash MD5 | Justificación y Nueva ruta |
|:---|:---|:---|:---|
| `Mock/image copy 5.png` | `Mock/image copy 3.png` (`dashboard.png`) | `014a00434fa4785348ae63347502f0c9` | Duplicado exacto bit a bit del Dashboard normal. Conservado en [`docs/ux-ui/archive/duplicates/dashboard-duplicate-image-copy-5.png`](./archive/duplicates/dashboard-duplicate-image-copy-5.png). |
| `Mock/image copy 15.png` | `Mock/image copy 14.png` (`configure-card.png`) | `bffad2909b9eda985afaa241af129dc9` | Duplicado exacto bit a bit del Paso 3 de configuración. Conservado en [`docs/ux-ui/archive/duplicates/configure-card-duplicate-image-copy-15.png`](./archive/duplicates/configure-card-duplicate-image-copy-15.png). |

---

## 3. Canvases de Referencia Anteriores (`archive/old-redesign-references/`)

Láminas compuestas extraídas de herramientas de diseño previas al desglose atómico:
- `docs/ux-ui/redesign-references/add-instrument.png` → [`docs/ux-ui/archive/old-redesign-references/add-instrument-canvas.png`](./archive/old-redesign-references/add-instrument-canvas.png)
- `docs/ux-ui/redesign-references/card-configuration.png` → [`docs/ux-ui/archive/old-redesign-references/card-configuration-canvas.png`](./archive/old-redesign-references/card-configuration-canvas.png)
- `docs/ux-ui/redesign-references/card-selection.png` → [`docs/ux-ui/archive/old-redesign-references/card-selection-canvas.png`](./archive/old-redesign-references/card-selection-canvas.png)
- `docs/ux-ui/redesign-references/dashboard.png` → [`docs/ux-ui/archive/old-redesign-references/dashboard-canvas.png`](./archive/old-redesign-references/dashboard-canvas.png)
- `docs/ux-ui/redesign-references/history.png` → [`docs/ux-ui/archive/old-redesign-references/history-canvas.png`](./archive/old-redesign-references/history-canvas.png)
- `docs/ux-ui/redesign-references/quick-movement.png` → [`docs/ux-ui/archive/old-redesign-references/quick-movement-canvas.png`](./archive/old-redesign-references/quick-movement-canvas.png)

---

## 4. Reorganización de Documentación Markdown y Evidencias

| Archivo anterior | Nueva ruta | Razón del movimiento |
|:---|:---|:---|
| `docs/ux-ui/KIPU_UI_UX_AUDIT.md` | [`docs/ux-ui/audits/KIPU_UI_UX_AUDIT.md`](./audits/KIPU_UI_UX_AUDIT.md) | Clasificación en subcarpeta dedicada a diagnósticos y auditorías técnicas. |
| `docs/ux-ui/KIPU_UI_UX_IMPLEMENTATION.md` | [`docs/ux-ui/implementation/KIPU_UI_UX_IMPLEMENTATION.md`](./implementation/KIPU_UI_UX_IMPLEMENTATION.md) | Agrupación en subcarpeta de especificaciones técnicas y registros de implementación. |
| `docs/ux-ui/KIPU_CALM_EMERALD_REDESIGN.md` | [`docs/ux-ui/implementation/KIPU_CALM_EMERALD_REDESIGN.md`](./implementation/KIPU_CALM_EMERALD_REDESIGN.md) | Agrupación junto a las especificaciones activas de implementación Calm Emerald. |
| `docs/merchant-logo-assets.md` | [`docs/ux-ui/references/merchant-logo-assets.md`](./references/merchant-logo-assets.md) | Centralización de inventarios y activos de diseño en carpeta de referencias UI/UX. |
| `docs/KIPU_V4_2_STITCH_REDESIGN_CATALOG.md` | [`docs/ux-ui/references/stitch-redesign-catalog.md`](./references/stitch-redesign-catalog.md) | Renombrado en kebab-case y consolidación dentro del repositorio de referencias UI/UX. |
| `docs/ux-ui/evidence/*` | `docs/ux-ui/evidence/runtime/*` | Normalización obligatoria para separar capturas runtime iniciales de APK. |
| `docs/ux-ui/evidence-final/*` | `docs/ux-ui/evidence/final/*` | Normalización obligatoria para evidencia recapturada tras la versión instalada final. |
