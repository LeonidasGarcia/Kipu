# Mapeo de Migración Documental y Mockups UI/UX de Kipu

Este documento registra la trazabilidad completa de la migración y reorganización de assets visuales, referencias y documentación desde las carpetas no estandarizadas (`Mock/`, `redesign-references/`, `evidence/`, `evidence-final/` y raíz de `docs/`) hacia la estructura canónica y escalable de `docs/ux-ui/`.

---

## 1. Mapeo Inicial de Mockups y Assets Visuales

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

## 2. Incorporación de Mockups desde Stitch AI (Kipu V4.2 — 39 Pantallas)

Trazabilidad de las pantallas generadas en Stitch (`projects/8741755512585500557`) e incorporadas a `docs/ux-ui/mockups/`:

### A. Movements (Detalle de Movimiento y Filtros)
| ID Pantalla Stitch | Nombre Funcional en Canvas | Archivo Local | Tipo |
|:---|:---|:---|:---|
| `0cca9082707b47889586d2c73a57b897` | Detalle de Movimiento — Gasto | [`mockups/canonical/movements/movement-detail-expense.png`](./mockups/canonical/movements/movement-detail-expense.png) | Canonical |
| `be077b25af704351b33d7d7cc8f38a16` | Detalle de Movimiento — Ingreso | [`mockups/states/movements/movement-detail-income.png`](./mockups/states/movements/movement-detail-income.png) | State |
| `a66c996e8a264d64a0935240adeab057` | Detalle de Movimiento — Transferencia | [`mockups/states/movements/movement-detail-transfer.png`](./mockups/states/movements/movement-detail-transfer.png) | State |
| `b2aa111be4e84c188aa0173c20a88817` | Detalle de Movimiento — Gasto con Nota | [`mockups/states/movements/movement-detail-with-note.png`](./mockups/states/movements/movement-detail-with-note.png) | State |
| `92fd7e9328ae4eb6a0bb3e592b4e97e0` | Detalle de Movimiento — Anulado | [`mockups/states/movements/movement-detail-voided.png`](./mockups/states/movements/movement-detail-voided.png) | State |
| `c6c3475489404a82b604da7e5e72d365` | Detalle de Movimiento — Diálogo de Anulación | [`mockups/states/movements/movement-detail-void-dialog.png`](./mockups/states/movements/movement-detail-void-dialog.png) | State |
| `77ef9062928b40efa108f49f32ba0990` | Filtros — Estado: Filtro Activo por Mes y Gastos | [`mockups/canonical/movements/movement-filters-active.png`](./mockups/canonical/movements/movement-filters-active.png) | Canonical |
| `53daf8872fb44b6792e3b12b7c6bac8b` | Filtros — Estado: Sin Coincidencias (0 Movimientos) | [`mockups/states/movements/movement-filters-empty.png`](./mockups/states/movements/movement-filters-empty.png) | State |
| `b551065e529046029d4f3be8adfb5361` | Filtros — Estado: Modal Upsell Kipu Pro (Filtros Bloqueados) | [`mockups/states/movements/movement-filters-upsell-pro.png`](./mockups/states/movements/movement-filters-upsell-pro.png) | State |
| `ad8c2645e1e04aaa908f9670f403f912` | Filtros — Estado: Periodo y Fecha Colapsado | [`mockups/states/movements/movement-filters-collapsed.png`](./mockups/states/movements/movement-filters-collapsed.png) | State |
| `14484660fcbc49e7ad6b082909885a43` | Filtros — Estado: Sin Filtros Aplicados (Estado Inicial Limpio) | [`mockups/states/movements/movement-filters-clean.png`](./mockups/states/movements/movement-filters-clean.png) | State |
| `9c6506483af248408d9ea678aba35993` | Filtros — Estado: Rango de Fecha Personalizado (Desde / Hasta) | [`mockups/states/movements/movement-filters-custom-date.png`](./mockups/states/movements/movement-filters-custom-date.png) | State |
| `fb8fc65da79d41fbbafa31065f6152f7` | Filtros — Estado: Filtros Avanzados Desbloqueados (Plan Pro) | [`mockups/states/movements/movement-filters-pro-unlocked.png`](./mockups/states/movements/movement-filters-pro-unlocked.png) | State |

### B. Debts (Cierre Contable, Liquidación y Deudas)
| ID Pantalla Stitch | Nombre Funcional en Canvas | Archivo Local | Tipo |
|:---|:---|:---|:---|
| `5a55d83fe4ee49149f1b49dba51c53eb` | Detalle de Deuda — Resumen y Acciones de Gestión | [`mockups/canonical/debts/debt-detail.png`](./mockups/canonical/debts/debt-detail.png) | Canonical |
| `4b44920178794ca0b7954899fb59eb6d` | Ajustes y Cierre Contable — Saldo Pendiente y Condonación | [`mockups/canonical/debts/debt-settlement-adjustment.png`](./mockups/canonical/debts/debt-settlement-adjustment.png) | Canonical |
| `f0b61f9dfab5448588959e505be9ccb1` | Cuotas y Cronograma — Configuración y Proyección de Pagos | [`mockups/canonical/debts/debt-payment-schedule.png`](./mockups/canonical/debts/debt-payment-schedule.png) | Canonical |
| `4b92743224414c1580de0392228f9409` | Ajustes y Cierre Contable — Saldo Cero (Listo para Liquidar) | [`mockups/states/debts/debt-settlement-zero-balance.png`](./mockups/states/debts/debt-settlement-zero-balance.png) | State |
| `e885da329e2744cc86dfe43d3d4843d7` | Detalle de Liquidación — Obligación Liquidada con Éxito | [`mockups/states/debts/debt-settlement-success.png`](./mockups/states/debts/debt-settlement-success.png) | State |
| `fc81e0a8d39d44fc93e8efdf963fd15f` | Ajustes y Cierre Contable — Error de Validación y Límite Excedido | [`mockups/states/debts/debt-settlement-validation-error.png`](./mockups/states/debts/debt-settlement-validation-error.png) | State |

### C. Cards (Tarjetas de Crédito y Tasas TEA)
| ID Pantalla Stitch | Nombre Funcional en Canvas | Archivo Local | Tipo |
|:---|:---|:---|:---|
| `e838b11bf002412b8e6a445eac784af2` | Detalle de Tarjeta — Consumos Activos (Utilización Moderada) | [`mockups/canonical/cards/card-detail-active.png`](./mockups/canonical/cards/card-detail-active.png) | Canonical |
| `5608f03b9490463a8a29ee3e3e64faac` | Detalle de Tarjeta — Saldo Cero y Utilización Óptima | [`mockups/states/cards/card-detail-zero-balance.png`](./mockups/states/cards/card-detail-zero-balance.png) | State |
| `088822b35c854898a96a080165b055fc` | Detalle de Tarjeta — Alta Utilización y Alerta de Riesgo | [`mockups/states/cards/card-detail-high-utilization.png`](./mockups/states/cards/card-detail-high-utilization.png) | State |
| `a9635ec5c9a84016baf4bb7dc1ab3874` | Detalle de Tarjeta — Estado Archivada e Inactiva | [`mockups/states/cards/card-detail-archived.png`](./mockups/states/cards/card-detail-archived.png) | State |
| `974f502337444017b9711b9cc018fe5a` | Editar Tarjeta de Crédito — Modal de Configuración | [`mockups/states/cards/card-edit-modal.png`](./mockups/states/cards/card-edit-modal.png) | State |
| `bd4cb6a6f4164cd8876084d687f222de` | Tasas y TEA — Simulador con Comparador Visual Tarifario | [`mockups/canonical/cards/card-tea-simulator.png`](./mockups/canonical/cards/card-tea-simulator.png) | Canonical |
| `3dcca7960d164f7abce51114a2e492c6` | Tasas y TEA — Vista Referencial y TEA Personalizada | [`mockups/states/cards/card-tea-custom.png`](./mockups/states/cards/card-tea-custom.png) | State |
| `2901b24eefd1440086497318deb9348b` | Tasas y TEA — Alerta Crítica (Tasa Fuera de Rango) | [`mockups/states/cards/card-tea-alert-out-of-range.png`](./mockups/states/cards/card-tea-alert-out-of-range.png) | State |
| `abaead4b70d941b38b2117c41a5d86a4` | Tasas y TEA — Confirmación de Tasa Actualizada con Éxito | [`mockups/states/cards/card-tea-updated-success.png`](./mockups/states/cards/card-tea-updated-success.png) | State |
| `5e9e24c878d74039996e9d93476f3fbc` | Tasas y TEA — Estado Inicial Sin Personalizar (Tarifario Medio) | [`mockups/states/cards/card-tea-unconfigured.png`](./mockups/states/cards/card-tea-unconfigured.png) | State |
| `dc84fa1d71cc4eedb3eb15f553f34f1c` | Tasas y TEA — Verificación SBS y Auditoría Contractual | [`mockups/states/cards/card-tea-sbs-verified.png`](./mockups/states/cards/card-tea-sbs-verified.png) | State |

### D. Purchases (Nueva Compra y Simulación de Cuotas)
| ID Pantalla Stitch | Nombre Funcional en Canvas | Archivo Local | Tipo |
|:---|:---|:---|:---|
| `cc50eb2c366f4cd190b33a85a390c830` | Nueva Compra — Simulación con Cuota Mensual Estimada | [`mockups/canonical/purchases/purchase-simulation.png`](./mockups/canonical/purchases/purchase-simulation.png) | Canonical |
| `762de919cc314467bafc27bbeb78a871` | Nueva Compra — Resultado de Simulación y Análisis de Impacto | [`mockups/canonical/purchases/purchase-simulation-result.png`](./mockups/canonical/purchases/purchase-simulation-result.png) | Canonical |
| `6a8b7a47f02841e6b3a3d2e8afb91006` | Nueva Compra — Formulario Inicial Vacío | [`mockups/states/purchases/purchase-initial-empty.png`](./mockups/states/purchases/purchase-initial-empty.png) | State |
| `649494b8fe46416382ef8f5b0afe3fcb` | Nueva Compra — Datos Completados (Comercio y Categoría) | [`mockups/states/purchases/purchase-form-completed.png`](./mockups/states/purchases/purchase-form-completed.png) | State |
| `778cc88ee0b1438facb3a25895b95225` | Nueva Compra — Selector de Cuotas (Modal Sheet) | [`mockups/states/purchases/purchase-select-installments.png`](./mockups/states/purchases/purchase-select-installments.png) | State |
| `df3361c8a38e457a97382d8c01b5cccb` | Nueva Compra — Cronograma Estimado de Cuotas y Confirmación | [`mockups/states/purchases/purchase-schedule-breakdown.png`](./mockups/states/purchases/purchase-schedule-breakdown.png) | State |
| `42a3d2f0d0e643e28ff71112b6feeb70` | Nueva Compra — Confirmación de Registro Exitoso | [`mockups/states/purchases/purchase-success-confirmation.png`](./mockups/states/purchases/purchase-success-confirmation.png) | State |
| `61bd2bb7a5e44ead895358461891f982` | Nueva Compra — Alerta: Importe Excede Línea de Crédito | [`mockups/states/purchases/purchase-alert-overlimit.png`](./mockups/states/purchases/purchase-alert-overlimit.png) | State |
| `85eeed907d604a8581070ff9950be8cd` | Nueva Compra — Validación: Monto Excede Límite Disponible | [`mockups/states/purchases/purchase-error-insufficient-line.png`](./mockups/states/purchases/purchase-error-insufficient-line.png) | State |

---

## 3. Reorganización de Documentación Markdown y Evidencias

| Archivo anterior | Nueva ruta | Razón del movimiento |
|:---|:---|:---|
| `docs/ux-ui/KIPU_UI_UX_AUDIT.md` | [`docs/ux-ui/audits/KIPU_UI_UX_AUDIT.md`](./audits/KIPU_UI_UX_AUDIT.md) | Clasificación en subcarpeta dedicada a diagnósticos y auditorías técnicas. |
| `docs/ux-ui/KIPU_UI_UX_IMPLEMENTATION.md` | [`docs/ux-ui/implementation/KIPU_UI_UX_IMPLEMENTATION.md`](./implementation/KIPU_UI_UX_IMPLEMENTATION.md) | Agrupación en subcarpeta de especificaciones técnicas y registros de implementación. |
| `docs/ux-ui/KIPU_CALM_EMERALD_REDESIGN.md` | [`docs/ux-ui/implementation/KIPU_CALM_EMERALD_REDESIGN.md`](./implementation/KIPU_CALM_EMERALD_REDESIGN.md) | Agrupación junto a las especificaciones activas de implementación Calm Emerald. |
| `docs/merchant-logo-assets.md` | [`docs/ux-ui/references/merchant-logo-assets.md`](./references/merchant-logo-assets.md) | Centralización de inventarios y activos de diseño en carpeta de referencias UI/UX. |
| `docs/KIPU_V4_2_STITCH_REDESIGN_CATALOG.md` | [`docs/ux-ui/references/stitch-redesign-catalog.md`](./references/stitch-redesign-catalog.md) | Renombrado en kebab-case y consolidación dentro del repositorio de referencias UI/UX. |

El índice visual consolidado para Codex y el equipo de desarrollo se mantiene en [`mockups/README.md`](./mockups/README.md).
