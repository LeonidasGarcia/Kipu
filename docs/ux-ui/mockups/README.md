# Índice de Mockups UI/UX de Kipu (Calm Emerald)

Este directorio consolida los mockups visuales oficiales de Kipu bajo el estándar **Calm Emerald Fintech**. Las referencias se dividen en diseños canónicos (`canonical/`), variantes interactivas (`states/`), flujos de autenticación (`Sesion/`) e inspiración (`inspiration/`). 

Todas las pantallas provienen del proyecto de diseño oficial en Stitch AI (**Kipu V4.2 Rediseño UI/UX**, `projects/8741755512585500557`) y sirven de especificación visual directa para la implementación en Jetpack Compose.

---

## Índice General de Pantallas y Estados

### 1. Dashboard (Mi Dinero)
| Área | Mockup | Tipo | Archivo | Uso |
|:---|:---|:---|:---|:---|
| **Dashboard** | Mi dinero (Normal) | Canonical | [`canonical/dashboard/dashboard.png`](./canonical/dashboard/dashboard.png) | Implementación principal del balance general, desglose líquido (cuentas/efectivo) y tarjetas activas. |
| **Dashboard** | Modo privacidad activado | State | [`states/dashboard/dashboard-privacy.png`](./states/dashboard/dashboard-privacy.png) | Estado con saldos enmascarados (`S/ • • • • • •`), indicador visual y banner de aviso de privacidad. |
| **Dashboard** | Límite Free alcanzado (4/4) | State | [`states/dashboard/dashboard-free-limit.png`](./states/dashboard/dashboard-free-limit.png) | Estado con cupo completado, banner "Mejorar a PRO", botón bloqueado y advertencia accesible. |

### 2. Quick Movement (Registro Rápido)
| Área | Mockup | Tipo | Archivo | Uso |
|:---|:---|:---|:---|:---|
| **Quick Movement** | Registrar movimiento (Gasto) | Canonical | [`canonical/quick-movement/register-movement.png`](./canonical/quick-movement/register-movement.png) | Implementación principal de la hoja modal para registrar gasto con comercio, categoría y fecha. |
| **Quick Movement** | Registro con validaciones / errores | State | [`states/quick-movement/register-movement-errors.png`](./states/quick-movement/register-movement-errors.png) | Estado de validación con aviso de saldo insuficiente, categoría requerida y CTA deshabilitado. |
| **Quick Movement** | Registrar ingreso | State | [`states/quick-movement/register-income.png`](./states/quick-movement/register-income.png) | Pestaña de Ingreso activa con cuenta de destino y campo para comercio o pagador. |
| **Quick Movement** | Registrar transferencia | State | [`states/quick-movement/register-transfer.png`](./states/quick-movement/register-transfer.png) | Pestaña de Transferencia activa con selección de cuenta origen y cuenta destino. |

### 3. Movements (Historial, Detalle de Transacciones y Filtros)
| Área | Mockup | Tipo | Archivo | Uso |
|:---|:---|:---|:---|:---|
| **Movements** | Historial de movimientos | Canonical | [`canonical/movements/movements.png`](./canonical/movements/movements.png) | Implementación principal del ledger cronológico, agrupación por fechas y tarjeta de balance neto. |
| **Movements** | Historial vacío (Empty state) | State | [`states/movements/movements-empty.png`](./states/movements/movements-empty.png) | Estado cuando no existen movimientos registrados para el mes seleccionado, con CTA de primer registro. |
| **Movements** | Detalle de Movimiento — Gasto | Canonical | [`canonical/movements/movement-detail-expense.png`](./canonical/movements/movement-detail-expense.png) | Ficha de detalle de gasto con comercio (Adobe -S/ 200.00), cuenta origen, categoría, comprobante y acciones. |
| **Movements** | Detalle de Movimiento — Ingreso | State | [`states/movements/movement-detail-income.png`](./states/movements/movement-detail-income.png) | Ficha de detalle para ingreso (+S/ 3,850.00 Sueldo BCP), empresa pagadora y estado conciliado. |
| **Movements** | Detalle de Movimiento — Transferencia | State | [`states/movements/movement-detail-transfer.png`](./states/movements/movement-detail-transfer.png) | Ficha de detalle de movimiento interno entre cuentas propias (origen Sueldo BCP a destino Ahorros BBVA). |
| **Movements** | Detalle de Movimiento — Gasto con Nota | State | [`states/movements/movement-detail-with-note.png`](./states/movements/movement-detail-with-note.png) | Ficha con comprobante PDF adjunto descargable y acordeón de notas descriptivas extendidas. |
| **Movements** | Detalle de Movimiento — Anulado | State | [`states/movements/movement-detail-voided.png`](./states/movements/movement-detail-voided.png) | Transacción anulada con indicación de restitución de fondos y trazabilidad de auditoría. |
| **Movements** | Detalle de Movimiento — Diálogo de Anulación | State | [`states/movements/movement-detail-void-dialog.png`](./states/movements/movement-detail-void-dialog.png) | Modal de confirmación para anulación contable con cálculo de saldo actual y saldo restituido proyectado. |
| **Movements** | Filtros — Filtro Activo por Mes y Gastos | Canonical | [`canonical/movements/movement-filters-active.png`](./canonical/movements/movement-filters-active.png) | Hoja modal de filtros con periodo mensual activo, filtro de gastos y botón con contador de resultados. |
| **Movements** | Filtros — Sin Coincidencias (0 Movimientos) | State | [`states/movements/movement-filters-empty.png`](./states/movements/movement-filters-empty.png) | Estado sin coincidencias con botón "Ver 0 movimientos". |
| **Movements** | Filtros — Modal Upsell Kipu Pro | State | [`states/movements/movement-filters-upsell-pro.png`](./states/movements/movement-filters-upsell-pro.png) | Modal de suscripción al intentar acceder a filtros avanzados bloqueados (cuentas, importes y multicategoría). |
| **Movements** | Filtros — Periodo y Fecha Colapsado | State | [`states/movements/movement-filters-collapsed.png`](./states/movements/movement-filters-collapsed.png) | Acordeón de fechas plegado para agilizar navegación con chips de rango rápido (Hoy, 7 días, Mes, Año). |
| **Movements** | Filtros — Sin Filtros (Estado Inicial Limpio) | State | [`states/movements/movement-filters-clean.png`](./states/movements/movement-filters-clean.png) | Estado limpio sin filtros activos ("Cualquier fecha - Historial completo", botón "Ver todos"). |
| **Movements** | Filtros — Rango de Fecha Personalizado | State | [`states/movements/movement-filters-custom-date.png`](./states/movements/movement-filters-custom-date.png) | Inputs de fecha "Desde" y "Hasta" con toggle para comparar contra periodo anterior. |
| **Movements** | Filtros — Filtros Avanzados Desbloqueados (Plan Pro) | State | [`states/movements/movement-filters-pro-unlocked.png`](./states/movements/movement-filters-pro-unlocked.png) | Filtros Pro activos por cuenta bancaria, categoría específica y rango numérico de importes. |

### 4. Cards (Catálogo, Configuración, Detalle y Tasas TEA)
| Área | Mockup | Tipo | Archivo | Uso |
|:---|:---|:---|:---|:---|
| **Cards** | Seleccionar tarjeta (Paso 2) | Canonical | [`canonical/cards/select-card.png`](./canonical/cards/select-card.png) | Paso 2 de 3: Selección de producto de crédito del catálogo BCP, filtros rápidos y sticky CTA. |
| **Cards** | Configura tu tarjeta (Paso 3) | Canonical | [`canonical/cards/configure-card.png`](./canonical/cards/configure-card.png) | Paso 3 de 3: Datos contractuales (línea de crédito, día de corte, día de pago, 4 dígitos y alertas). |
| **Cards** | Detalle de Tarjeta — Consumos Activos | Canonical | [`canonical/cards/card-detail-active.png`](./canonical/cards/card-detail-active.png) | Vista principal de tarjeta con consumos devengados (S/ 2,840.50), disponible y barra de utilización moderada. |
| **Cards** | Detalle de Tarjeta — Saldo Cero / Utilización Óptima | State | [`states/cards/card-detail-zero-balance.png`](./states/cards/card-detail-zero-balance.png) | Estado con deuda devengada en cero (S/ 0.00), línea total disponible y utilización baja (<10%). |
| **Cards** | Detalle de Tarjeta — Alta Utilización y Alerta de Riesgo | State | [`states/cards/card-detail-high-utilization.png`](./states/cards/card-detail-high-utilization.png) | Estado de alerta financiera por utilización del 84.5%, vencimiento inminente y CTA "Pagar tarjeta". |
| **Cards** | Detalle de Tarjeta — Archivada e Inactiva | State | [`states/cards/card-detail-archived.png`](./states/cards/card-detail-archived.png) | Tarjeta archivada con ciclos congelados, sin impacto en balance y opción para reactivar. |
| **Cards** | Editar Tarjeta de Crédito Modal | State | [`states/cards/card-edit-modal.png`](./states/cards/card-edit-modal.png) | Modal bottom sheet de edición de línea de crédito, día de corte y día de pago. |
| **Cards** | Seleccionar entidad emisora | State | [`states/cards/select-bank.png`](./states/cards/select-bank.png) | Hoja modal con grilla 2x2 de bancos principales (BCP, BBVA, IBK, SCO) y tarjetas de retail. |
| **Cards** | Búsqueda de tarjeta con resultados | State | [`states/cards/card-search.png`](./states/cards/card-search.png) | Búsqueda dinámica dentro del catálogo (ej. query "latam") con chip de resultado. |
| **Cards** | Tarjeta no encontrada (Empty search) | State | [`states/cards/card-not-found.png`](./states/cards/card-not-found.png) | Búsqueda sin coincidencias con fallback para "Registrar tarjeta personalizada" / "Crear manual". |
| **Cards** | Configurar tarjeta con TEA expandida | State | [`states/cards/card-tea-expanded.png`](./states/cards/card-tea-expanded.png) | Progressive disclosure con tasa contractual manual y tasa de mercado sugerida con botón `[ Aplicar ]`. |
| **Cards** | Tarjeta configurada con éxito | State | [`states/cards/card-created-success.png`](./states/cards/card-created-success.png) | Modal bottom sheet de confirmación tras el guardado real con grid 2x2 de métricas financieras. |
| **Cards** | Tasas y TEA — Simulador con Comparador Visual | Canonical | [`canonical/cards/card-tea-simulator.png`](./canonical/cards/card-tea-simulator.png) | Simulador de TEA acordada (42.00%) con termómetro visual vs rango del tarifario oficial BCP. |
| **Cards** | Tasas y TEA — Vista Referencial y TEA Personalizada | State | [`states/cards/card-tea-custom.png`](./cards/card-tea-custom.png) | Ficha contractual con TEA personalizada activa (38.50%) y comparativo porcentual. |
| **Cards** | Tasas y TEA — Alerta Crítica (Tasa Fuera de Rango) | State | [`states/cards/card-tea-alert-out-of-range.png`](./states/cards/card-tea-alert-out-of-range.png) | Alerta roja cuando la tasa ingresada (68.50%) excede el límite máximo del tarifario y la SBS. |
| **Cards** | Tasas y TEA — Confirmación de Tasa Actualizada | State | [`states/cards/card-tea-updated-success.png`](./states/cards/card-tea-updated-success.png) | Feedback de tasa guardada con recálculo automático en compras futuras. |
| **Cards** | Tasas y TEA — Estado Inicial Sin Personalizar | State | [`states/cards/card-tea-unconfigured.png`](./states/cards/card-tea-unconfigured.png) | Estado por defecto con uso de tarifario medio y card explicativa de consulta contractual. |
| **Cards** | Tasas y TEA — Verificación SBS y Auditoría Contractual | State | [`states/cards/card-tea-sbs-verified.png`](./states/cards/card-tea-sbs-verified.png) | Vista con validación oficial SBS, card educativa sobre TCEA y guardado contractual (42.5%). |

### 5. Purchases (Nueva Compra y Simulación de Cuotas)
| Área | Mockup | Tipo | Archivo | Uso |
|:---|:---|:---|:---|:---|
| **Purchases** | Nueva Compra — Simulación con Cuota Mensual | Canonical | [`canonical/purchases/purchase-simulation.png`](./canonical/purchases/purchase-simulation.png) | Formulario principal de compra en cuotas con cálculo automático de cuota mensual estimada (3 cuotas S/ 41.50). |
| **Purchases** | Nueva Compra — Resultado de Simulación y Análisis | Canonical | [`canonical/purchases/purchase-simulation-result.png`](./canonical/purchases/purchase-simulation-result.png) | Desglose integral: costo financiero total (S/ 154.00), cuota mensual, impacto en línea y presupuesto. |
| **Purchases** | Nueva Compra — Formulario Inicial Vacío | State | [`states/purchases/purchase-initial-empty.png`](./states/purchases/purchase-initial-empty.png) | Estado base sin datos ingresados con botón de acción inactivo. |
| **Purchases** | Nueva Compra — Datos Completados (Comercio y Categoría) | State | [`states/purchases/purchase-form-completed.png`](./states/purchases/purchase-form-completed.png) | Formulario con comercio (Bembos) y categoría (Alimentación) seleccionados listo para simular. |
| **Purchases** | Nueva Compra — Selector de Cuotas (Modal Sheet) | State | [`states/purchases/purchase-select-installments.png`](./states/purchases/purchase-select-installments.png) | Hoja modal para selección de cuotas (1 cuota, 3 cuotas sin intereses, 6, 12 o plan personalizado). |
| **Purchases** | Nueva Compra — Cronograma Estimado de Cuotas | State | [`states/purchases/purchase-schedule-breakdown.png`](./states/purchases/purchase-schedule-breakdown.png) | Proyección previa detallada de cuotas con fechas antes de confirmar el gasto real. |
| **Purchases** | Nueva Compra — Confirmación de Registro Exitoso | State | [`states/purchases/purchase-success-confirmation.png`](./states/purchases/purchase-success-confirmation.png) | Pantalla de confirmación con checkmark verde y cálculo inmediato de la nueva línea disponible. |
| **Purchases** | Nueva Compra — Alerta: Importe Excede Línea de Crédito | State | [`states/purchases/purchase-alert-overlimit.png`](./states/purchases/purchase-alert-overlimit.png) | Advertencia por sobregiro con aviso de línea excedida y botón para solicitar ampliación. |
| **Purchases** | Nueva Compra — Validación: Monto Excede Límite | State | [`states/purchases/purchase-error-insufficient-line.png`](./states/purchases/purchase-error-insufficient-line.png) | Bloqueo de registro por falta de disponible en la tarjeta con botón CTA inhabilitado. |

### 6. Debts (Gestión de Deuda, Ajustes, Cronograma y Liquidación)
| Área | Mockup | Tipo | Archivo | Uso |
|:---|:---|:---|:---|:---|
| **Debts** | Detalle de Deuda — Resumen y Acciones | Canonical | [`canonical/debts/debt-detail.png`](./canonical/debts/debt-detail.png) | Ficha de obligación financiera con saldo pendiente, principal original y accesos para pagar o archivar. |
| **Debts** | Ajustes y Cierre Contable — Saldo Pendiente | Canonical | [`canonical/debts/debt-settlement-adjustment.png`](./canonical/debts/debt-settlement-adjustment.png) | Formulario de ajustes de redondeo, condonación de deuda y cierre contable condicional. |
| **Debts** | Cuotas y Cronograma — Configuración de Pagos | Canonical | [`canonical/debts/debt-payment-schedule.png`](./canonical/debts/debt-payment-schedule.png) | Configurador de cronograma con número de cuotas, aviso contable y proyección de importes por vencimiento. |
| **Debts** | Ajustes y Cierre Contable — Saldo Cero (Listo para Liquidar) | State | [`states/debts/debt-settlement-zero-balance.png`](./states/debts/debt-settlement-zero-balance.png) | Estado 100% amortizado con condonación validada y botón de liquidación definitiva habilitado. |
| **Debts** | Detalle de Liquidación — Obligación Liquidada con Éxito | State | [`states/debts/debt-settlement-success.png`](./states/debts/debt-settlement-success.png) | Pantalla de obligación cerrada con acta contable inmutable, folio hash y descarga de constancia PDF. |
| **Debts** | Ajustes y Cierre Contable — Error de Validación | State | [`states/debts/debt-settlement-validation-error.png`](./states/debts/debt-settlement-validation-error.png) | Banner de error contable cuando el monto de condonación excede el saldo pendiente máximo. |

### 7. Add Money (Ingreso de Cuentas e Instrumentos)
| Área | Mockup | Tipo | Archivo | Uso |
|:---|:---|:---|:---|:---|
| **Add Money** | Agregar a Mi dinero | Canonical | [`canonical/add-money/add-to-my-money.png`](./canonical/add-money/add-to-my-money.png) | Entrada unificada contextual para Cuenta bancaria, Tarjeta de crédito, Billetera digital y Efectivo. |

### 8. Sesion & Onboarding (Autenticación)
| Área | Mockup | Tipo | Archivo | Uso |
|:---|:---|:---|:---|:---|
| **Sesión** | Inicio de Sesión | Canonical | [`Sesion/Inicio de Sesion.png`](./Sesion/Inicio%20de%20Sesion.png) | Pantalla principal de login con correo, contraseña y acceso biométrico. |
| **Sesión** | Onboarding 1 | State | [`Sesion/Omboarding 1.png`](./Sesion/Omboarding%201.png) | Pantalla 1 del carrusel introductorio. |
| **Sesión** | Onboarding 2 | State | [`Sesion/Omboarding 2.png`](./Sesion/Omboarding%202.png) | Pantalla 2 del carrusel introductorio. |
| **Sesión** | Onboarding 3 | State | [`Sesion/Omboarding 3 - Corregido.png`](./Sesion/Omboarding%203%20-%20Corregido.png) | Pantalla 3 del carrusel introductorio. |
| **Sesión** | Registro de Usuario | State | [`Sesion/Registro de Usuario.png`](./Sesion/Registro%20de%20Usuario.png) | Formulario de creación de cuenta nueva. |
| **Sesión** | Recuperar Contraseña | State | [`Sesion/Recuperar Contrasea.png`](./Sesion/Recuperar%20Contrase%EF%BF%BDa.png) | Solicitud de envío de enlace de restablecimiento. |
| **Sesión** | Recuperar Contraseña — Paso 2 | State | [`Sesion/Recuperar Contrasea - Paso 2.png`](./Sesion/Recuperar%20Contrase%EF%BF%BDa%20-%20Paso%202.png) | Confirmación de correo de restablecimiento enviado. |
| **Sesión** | Recuperar Contraseña — Correo Incorrecto | State | [`Sesion/Recuperar contrasea - Correo incorrecto.png`](./Sesion/Recuperar%20contrase%EF%BF%BDa%20-%20Correo%20incorrecto.png) | Error cuando el correo no se encuentra registrado. |

### 9. Inspiration & Design System
| Área | Mockup | Tipo | Archivo | Uso |
|:---|:---|:---|:---|:---|
| **Design System** | Calm Emerald Fintech Board | Inspiration | [`inspiration/design-system/calm-emerald-fintech.png`](./inspiration/design-system/calm-emerald-fintech.png) | Board de referencia: paleta `#0B5F51`, `#22C79A`, `#BCEBDD`, `#17211E`, tipografía Inter y componentes. |
| **Gradients** | Emerald Night (Dark Mode) | Inspiration | [`inspiration/gradients/emerald-night.png`](./inspiration/gradients/emerald-night.png) | Estudio de gradiente "Emerald night · Aurora" en Gradient Builder para ambientación Dark Mode. |
| **Gradients** | Emerald Flow (Light Mode) | Inspiration | [`inspiration/gradients/emerald-flow.png`](./inspiration/gradients/emerald-flow.png) | Estudio de gradiente "Emerald · Flow" en Gradient Builder para fondos y transiciones fluidas. |

---

*Índice actualizado automáticamente para desarrollo y sincronización con Codex / Jetpack Compose.*
