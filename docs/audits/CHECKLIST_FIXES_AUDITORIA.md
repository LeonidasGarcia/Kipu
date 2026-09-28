# Checklist de fixes — Sprint de estabilización QA

**Fecha:** 28/09/2026  
**Rama:** `fix/sprint-3-qa-stabilization`  
**Auditoría base:** [KIPU_FULL_FUNCTIONAL_UX_AUDIT.md](KIPU_FULL_FUNCTIONAL_UX_AUDIT.md)  
**Revisión cruzada:** AGY, panel derecho.  
**Resultado:** fixes de los bloques 1, 2 y 3 implementados; tests unitarios y APK debug pasan.

## Bloque 1 — Navegación de Notificaciones

- [x] **BUG-NAV-01 — ruta de notificaciones ausente en el `NavHost` activo.** Registré `notificationDestinations(navController)` en `MainActivity`, manteniendo el mismo destino que `KipuNavHost`.
- [x] Añadí una regresión instrumentada sobre el builder de destinos usado en producción: comprueba que `notifications/center` esté en el grafo y que se pueda navegar a él. Se ejecutó en el emulador y pasó.
- [x] Renombré los métodos de pruebas de Notificaciones que contenían espacios; D8 no podía empaquetarlos con la versión DEX configurada. Esto permite compilar el APK instrumentado.

## Bloque 2 — Pagos, saldos y propietario de tarjeta

- [x] **BUG-FIN-01 — parser de importes agrupados.** `MoneyInputParser` admite separadores de miles y decimales de formatos mixtos (`1,000.00`, `1.000,50`, `1,234,567.89`), decimales sin cero inicial y enteros. Rechaza agrupaciones mal formadas, negativos, desbordamiento y entradas ambiguas de un separador seguido de tres dígitos.
- [x] Añadí pruebas unitarias para los casos de formato, agrupación inválida y ambigüedad.
- [x] **BUG-FIN-02 — origen de pago no aceptado por dominio.** El selector ahora ofrece únicamente cuentas activas `BANK`/`SAVINGS`, con moneda coincidente y saldo positivo; deja de ofrecer efectivo u otras cuentas que Room rechaza.
- [x] **BUG-UI-01 — diálogo bloqueado después de un error.** Los errores de pago activan el callback de fallo, muestran el mensaje y restablecen el botón de confirmar. Las cancelaciones de coroutine se propagan. Separé la acción de pago del composable para que la regresión pruebe rechazo y reintento sin depender de un matcher de MockK para IDs inline.
- [x] Añadí una prueba instrumentada de pago fallido seguido de reintento exitoso; pasó en el emulador.
- [x] **BUG-FIN-03 — correcciones consecutivas del saldo inicial.** Room localiza la apertura original y el ajuste vigente no revertido; la siguiente corrección revierte el asiento activo antes de agregar el nuevo ajuste auditable.
- [x] Añadí regresión de Room `S/ 20 → S/ 21 → S/ 20 → S/ 25`, verificando el saldo derivado después de cada corrección; pasó en el emulador.
- [x] **BUG-FIN-04 — propietario provisional al crear tarjeta.** La creación obtiene el `UserId` de `SessionCoordinator.localAccess` y aborta si no hay propietario autenticado disponible; ya no genera un ID aleatorio.
- [x] Añadí pruebas unitarias para resolver el propietario desde sesión válida/no disponible.

### Decisión de especificación para pagos

AGY contrastó el backlog/prototipo, la RPC y la especificación formal de Cuentas y Tarjetas. La cláusula `specs/002-ep-cta-cuentas-tarjetas/spec.md:356` limita RF-C10 a cuentas bancarias o de ahorro activas y excluye efectivo/billeteras; seguí esa regla en cliente y repositorio. El prototipo HU-12 y la RPC de Supabase son más permisivos, por lo que persiste una discrepancia de producto ya identificada en el informe base. Si Producto decide aceptar efectivo o billeteras, debe cambiar también la especificación y la validación del dominio/backend; no basta con ampliar el selector.

## Bloque 3 — Progressive disclosure e historial

- [x] **UX-01 — jerarquía de categorías.** Reemplacé los chips planos con selector modal de dos niveles: las raíces se pueden elegir o desplegar y sus hijas aparecen indentadas solo al expandirlas. Los hijos se agrupan por `parentCategoryId`, no por etiqueta; la selección conserva el ID propio y muestra `Padre > Hija`.
- [x] Añadí regresión con dos raíces homónimas para confirmar que sus hijas no se mezclen. AGY confirmó que `parentCategoryId` corresponde a la relación oficial `categories.parent_id`; también alineé la detección de raíces con `parentCategoryId == null`.
- [x] **Tokens y accesibilidad.** Apliqué el sistema Kipu mediante colores semánticos del tema, radio superior de 24dp, espaciado de 12/16dp, nodos raíz e hijos con altura táctil mínima de 48dp y semántica `selected`/`expanded` para lector de pantalla.
- [x] **Movimiento.** Acordeón y chevron usan `KipuMotionTokens.FastMillis`, `FastOutSlowInEasing` y `rememberReducedMotionEnabled()`; con movimiento reducido la expansión y el colapso se presentan sin animación.
- [x] **Separación de comercio.** El comercio conserva su propio bloque visual y acción para quitarlo, separado del selector de categorías.
- [x] **UX-02 — pagos de tarjeta en el historial.** `CARD_PAYMENT` recibe el título explícito «Pago de tarjeta» y el subtítulo muestra origen → tarjeta.
- [x] **UX-03 — transferencia recién guardada.** El evento de guardado comunica el tipo de movimiento; al guardar transferencia, el historial limpia búsqueda y filtro para que la fila aparezca inmediatamente y conserva el snackbar de confirmación.
- [x] **UX-04 — tarjetas archivadas con deuda.** Dashboard agrupa esas tarjetas en instrumentos archivados, muestra el distintivo «Archivada · Deuda pendiente» con colores semánticos de alerta y evita duplicarlas en el resumen de deuda.
- [x] Añadí regresiones instrumentadas para selector, historial de pagos y comportamiento de transferencia/filtros.

## Verificación

- [x] `./gradlew test` — **BUILD SUCCESSFUL**; 289 tests, 0 fallos, 0 errores, 0 omitidos.
- [x] `./gradlew assembleDebug` — **BUILD SUCCESSFUL**.
- [x] `compileDebugAndroidTestKotlin` — **BUILD SUCCESSFUL**; fuentes de instrumentación compiladas.
- [x] Regresiones instrumentadas focalizadas pasaron en el emulador Pixel 10 (API 17): navegación de notificaciones (1), corrección repetida de saldo en Room (1), selector de movimiento (2) y reintento de pago (1). La prueba de título/subtítulo del historial quedó marcada como pasada en el reporte de la ejecución filtrada combinada.
- [ ] **Suite instrumentada completa:** no se obtuvo un cierre limpio. Una ejecución llegó a iniciar 156 tests, pero el daemon ADB alternó entre versiones 40/41 y reinició la conexión; el runner terminó antes de completar la suite. En esa ejecución aparecieron además estos casos no relacionados con los cambios funcionales de este fix: `CreditLiabilityMigrationTest.migrationLinksCardsAndRebuildsPurchaseAndPaymentLiabilityEffects` (check en línea 131), `SyncInstrumentCommandsWorkerTest.confirmedPurchaseUsesCanonicalRpcAndAcknowledgesDuplicateIdempotently` (esperaba `Success`, obtuvo `Retry`) y la expectativa preexistente de accesibilidad de tarjeta física en `CreditAccessibilitySemanticsTest`. El fallo del nuevo test de reintento en esa corrida era su matcher de MockK para un ID inline; se eliminó esa fragilidad y la prueba focalizada pasó. La suite completa no se volvió a ejecutar después de ese ajuste.

## Dictamen

Los tres bloques pedidos están implementados y AGY aprobó las revisiones estáticas de Notificaciones, pagos/saldos y UX. Los checks obligatorios del prompt (`test` y `assembleDebug`) pasan. La cobertura focalizada instrumentada respalda las regresiones principales; queda pendiente estabilizar ADB y resolver las tres fallas ajenas a estos cambios para cerrar la suite instrumentada global.
