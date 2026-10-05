# Índice de Mockups UI/UX de Kipu (Calm Emerald)

Este directorio consolida los mockups visuales oficiales de Kipu organizados bajo el estándar **Calm Emerald Fintech**. Las referencias están divididas en diseños canónicos base (`canonical/`), variantes de estado interactivo (`states/`), referencias de inspiración y diseño (`inspiration/`), y assets archivados (`archive/`).

---

## Índice General de Pantallas y Estados

| Área | Mockup | Tipo | Archivo | Uso |
|:---|:---|:---|:---|:---|
| **Dashboard** | Mi dinero (Normal) | Canonical | [`canonical/dashboard/dashboard.png`](./canonical/dashboard/dashboard.png) | Implementación principal del balance general, desglose líquido (cuentas/efectivo) y tarjetas activas. |
| **Dashboard** | Modo privacidad activado | State | [`states/dashboard/dashboard-privacy.png`](./states/dashboard/dashboard-privacy.png) | Estado con saldos enmascarados (`S/ • • • • • •`), indicador visual y banner de aviso de privacidad. |
| **Dashboard** | Límite Free alcanzado (4/4) | State | [`states/dashboard/dashboard-free-limit.png`](./states/dashboard/dashboard-free-limit.png) | Estado con cupo completado, banner "Mejorar a PRO", botón bloqueado y advertencia accesible. |
| **Quick Movement** | Registrar movimiento (Gasto) | Canonical | [`canonical/quick-movement/register-movement.png`](./canonical/quick-movement/register-movement.png) | Implementación principal de la hoja modal para registrar gasto con comercio, categoría y fecha. |
| **Quick Movement** | Registro con validaciones / errores | State | [`states/quick-movement/register-movement-errors.png`](./states/quick-movement/register-movement-errors.png) | Estado de validación con aviso de saldo insuficiente, categoría requerida y CTA deshabilitado. |
| **Quick Movement** | Registrar ingreso | State | [`states/quick-movement/register-income.png`](./states/quick-movement/register-income.png) | Pestaña de Ingreso activa con cuenta de destino y campo para comercio o pagador. |
| **Quick Movement** | Registrar transferencia | State | [`states/quick-movement/register-transfer.png`](./states/quick-movement/register-transfer.png) | Pestaña de Transferencia activa con selección de cuenta origen y cuenta destino. |
| **Movements** | Historial de movimientos | Canonical | [`canonical/movements/movements.png`](./canonical/movements/movements.png) | Implementación principal del ledger cronológico, agrupación por fechas y tarjeta de balance neto. |
| **Movements** | Historial vacío (Empty state) | State | [`states/movements/movements-empty.png`](./states/movements/movements-empty.png) | Estado cuando no existen movimientos registrados para el mes seleccionado, con CTA de primer registro. |
| **Add Money** | Agregar a Mi dinero | Canonical | [`canonical/add-money/add-to-my-money.png`](./canonical/add-money/add-to-my-money.png) | Entrada unificada contextual para Cuenta bancaria, Tarjeta de crédito, Billetera digital y Efectivo. |
| **Cards** | Seleccionar tarjeta (Paso 2) | Canonical | [`canonical/cards/select-card.png`](./canonical/cards/select-card.png) | Paso 2 de 3: Selección de producto de crédito del catálogo BCP, filtros rápidos y sticky CTA. |
| **Cards** | Seleccionar entidad emisora | State | [`states/cards/select-bank.png`](./states/cards/select-bank.png) | Hoja modal con grilla 2x2 de bancos principales (BCP, BBVA, IBK, SCO) y tarjetas de retail. |
| **Cards** | Búsqueda de tarjeta con resultados | State | [`states/cards/card-search.png`](./states/cards/card-search.png) | Búsqueda dinámica dentro del catálogo (ej. query "latam") con chip de resultado. |
| **Cards** | Tarjeta no encontrada (Empty search) | State | [`states/cards/card-not-found.png`](./states/cards/card-not-found.png) | Búsqueda sin coincidencias con fallback para "Registrar tarjeta personalizada" / "Crear manual". |
| **Cards** | Configura tu tarjeta (Paso 3) | Canonical | [`canonical/cards/configure-card.png`](./canonical/cards/configure-card.png) | Paso 3 de 3: Datos contractuales (línea de crédito, día de cierre, día de pago, 4 dígitos y alertas). |
| **Cards** | Configurar tarjeta con TEA expandida | State | [`states/cards/card-tea-expanded.png`](./states/cards/card-tea-expanded.png) | Progressive disclosure con tasa contractual manual y tasa de mercado sugerida con botón `[ Aplicar ]`. |
| **Cards** | Tarjeta configurada con éxito | State | [`states/cards/card-created-success.png`](./states/cards/card-created-success.png) | Modal bottom sheet de confirmación tras el guardado real con grid 2x2 de métricas financieras. |
| **Design System** | Calm Emerald Fintech Board | Inspiration | [`inspiration/design-system/calm-emerald-fintech.png`](./inspiration/design-system/calm-emerald-fintech.png) | Board de referencia: paleta `#0B5F51`, `#22C79A`, `#BCEBDD`, `#17211E`, tipografía Inter y componentes. |
| **Gradients** | Emerald Night (Dark Mode) | Inspiration | [`inspiration/gradients/emerald-night.png`](./inspiration/gradients/emerald-night.png) | Estudio de gradiente "Emerald night · Aurora" en Gradient Builder para ambientación Dark Mode. |
| **Gradients** | Emerald Flow (Light Mode) | Inspiration | [`inspiration/gradients/emerald-flow.png`](./inspiration/gradients/emerald-flow.png) | Estudio de gradiente "Emerald · Flow" en Gradient Builder para fondos y transiciones fluidas. |

---

## Archivos Archivados

### Duplicados Verificados (`archive/duplicates/`)
Archivos idénticos bit a bit retirados de la raíz `Mock/`:
- [`dashboard-duplicate-image-copy-5.png`](../archive/duplicates/dashboard-duplicate-image-copy-5.png): Copia idéntica de `canonical/dashboard/dashboard.png` (MD5: `014a00434fa4785348ae63347502f0c9`).
- [`configure-card-duplicate-image-copy-15.png`](../archive/duplicates/configure-card-duplicate-image-copy-15.png): Copia idéntica de `canonical/cards/configure-card.png` (MD5: `bffad2909b9eda985afaa241af129dc9`).

### Canvases Históricos Multipantalla (`archive/old-redesign-references/`)
Laminas completas previas de Stitch antes de su desglose en pantallas atómicas:
- [`add-instrument-canvas.png`](../archive/old-redesign-references/add-instrument-canvas.png): Canvas de alta de instrumentos y límite Free.
- [`card-configuration-canvas.png`](../archive/old-redesign-references/card-configuration-canvas.png): Canvas del paso 3 con validaciones, TEA y modal de éxito.
- [`card-selection-canvas.png`](../archive/old-redesign-references/card-selection-canvas.png): Canvas del paso 2 con catálogo, banco emisor y búsqueda.
- [`dashboard-canvas.png`](../archive/old-redesign-references/dashboard-canvas.png): Canvas de Mi dinero con 4 estados (base, vacío, privacidad, límite).
- [`history-canvas.png`](../archive/old-redesign-references/history-canvas.png): Canvas de historial con vista con transacciones y estado vacío.
- [`quick-movement-canvas.png`](../archive/old-redesign-references/quick-movement-canvas.png): Canvas del modal de registro rápido en sus 4 variantes.
