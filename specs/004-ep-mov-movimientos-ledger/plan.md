# Implementation Plan: Movimientos y ledger

**Propagated**: 2026-10-02 — T093/F04: alta estándar v2 con array canónico inequívoco validado por servidor; outbox v1 histórica inmutable, dispatch por versión y nueva migración wrapper. Se conserva el trabajo S2 y el DAG existente.

**Branch**: `004-ep-mov-movimientos-ledger` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

**Propagated**: 2026-10-02 — Refinamiento S4 de HU-20/HU-21/HU-22 y decisiones P1–P5. Se conserva el diseño histórico S2; se agregan diseño, dependencias y gates S4. Modelo/contratos S4 se completan con T058–T063 antes de código financiero.

**Propagated**: 2026-10-02 — Correcciones aprobadas de analyze: consumo base por periodo comprobable, corte exacto de concesión y reinicio sin evidencia, contexto de ejecución y serialización T071→T074. El diseño detallado se concreta en los derivados de Phase 7.

**Input**: especificación de `specs/004-ep-mov-movimientos-ledger/spec.md` y documentación funcional V4.2 de `Kipu_Documentacion`.

**Ejecución Phase 7 (2026-10-02)**: T058–T063 completadas como diseño; derivados en `data-model.md`, `contracts/revise-and-void-transaction-v1.md`, `contracts/sync-and-deduplication.md`, `contracts/history-query-access.md`, `contracts/ui-contract.md` y `quickstart.md`. Revisión auxiliar independiente y analyze registrados en `review-record.md`. Pruebas/código/integración siguen pendientes; HU-58/59 bloquean T084.

## Summary

El Sprint 2 implementa HU18, HU19 y HU23: registrar gasto, ingreso o transferencia manual; actualizar saldos de forma atómica; y evitar reenvíos exactos mientras se advierten posibles duplicados. La solución usa Room como autoridad visible local y una bandeja de salida para sincronizar con Supabase/PostgreSQL. El modelo remoto objetivo es `transactions` + `internal.ledger_entries`; `financial_movements` se considera legado y requiere una migración hacia delante acordada con EP-CTA antes de modificar el esquema compartido.

S4 extiende el incremento consolidado con HU-20/21/22 (15 puntos): correcciones estándar, VOIDED y contrapartidas, consultas Free y filtros autorizados. Las decisiones aprobadas P1–P5 gobiernan esta extensión; HU-58/59 aportan 16 puntos desde EP-PLA. La baseline aceptada es main `c80ea0ddea80dfe5332971f12418758ea1bc9923`, con Room v16.

## Delivery Boundary

Incluido en Sprint 2 (histórico):

- Pantalla 11 funcional para Gasto, Ingreso y Transferencia.
- Flujo de registro local, actualización de saldo, outbox y sincronización remota.
- Idempotencia exacta por `idempotency_key` + `request_hash`.
- Advertencia de similitud dentro de cinco minutos.
- Pantalla 10 mínima para verificar que el movimiento aparece en el historial.
- Pruebas unitarias, de integración Room, de migración/RPC y de UI críticas.

Diferido al cerrar Sprint 2 (histórico):

- ~~HU20 edición, HU21 anulación, HU22 reembolso, HU24 filtros avanzados y HU25 permisos granulares.~~ Corrección de trazabilidad: HU-20 edición, HU-21 anulación y HU-22 búsqueda/filtros se activan en S4; HU-24 obligaciones/metas sigue en S6 y HU-25 reembolsos en S7. La asignación anterior de HU-22/24/25 era incorrecta.
- Acciones contextuales Editar/Anular/Reembolsar y filtros avanzados de la Pantalla 10.

## Technical Context

**Language/Version**: Kotlin 2.4.20, Java 17, SQL/PLpgSQL para PostgreSQL 17  
**Primary Dependencies**: Jetpack Compose (BOM 2026.08), Room 2.8.5, WorkManager 2.11.2, Hilt, Supabase Kotlin 3.8  
**Storage**: Room/SQLite local; Supabase/PostgreSQL remoto  
**Testing**: JUnit, kotlinx-coroutines-test, Room instrumentation, Compose UI tests, Supabase CLI/pgTAP  
**Target Platform**: Android minSdk 24, targetSdk 36, compileSdk 37  
**Project Type**: aplicación Android con backend Supabase gestionado por migraciones  
**Performance Goals**: alta local visible en menos de 300 ms p95; historial de 10 000 movimientos consultable sin bloquear UI; sincronización reintentable  
**Constraints**: offline-first, operaciones monetarias enteras en minor units, RLS estricta, sin doble contabilización, sin pérdida ante cierre/reinicio  
**Scale/Scope**: tres tipos básicos, una pantalla de alta y una lista mínima; un usuario/espacio sólo puede operar referencias de su ámbito

## Constitution Check

*GATE: debe mantenerse antes y después del diseño.*

| Principio | Resultado | Evidencia |
|---|---|---|
| Especificación antes de código | PASS | `spec.md`, investigación y contratos preceden la implementación. |
| Offline-first | PASS | Room confirma localmente y outbox sincroniza/reintenta. |
| Corrección financiera | PASS | Montos en minor units, asientos balanceados y modelo canónico documentado; la migración tendrá revisión de EP-CTA. |
| Seguridad y aislamiento | PASS | RPC valida membresía/propiedad y las tablas mantienen RLS. |
| Calidad verificable | PASS | Cada historia incluye pruebas independientes y criterios medibles. |
| Cambios evolutivos | PASS | Se exige migración hacia delante desde `financial_movements`, sin doble escritura permanente. |

**Gate de implementación**: las decisiones del incremento quedan fijadas en [research.md](./research.md); cualquier cambio posterior debe registrarse como decisión aprobada antes de alterar tablas compartidas.

## Architecture

### Flujo principal

```text
Compose UI
  -> RegisterTransactionUseCase
  -> MovementRepository
  -> Room transaction (movement + ledger/projection + outbox + receipt)
  -> WorkManager
  -> register_transaction_v1 RPC
  -> command receipt remoto
  -> reconciliación local
```

### Fuente de verdad y saldos

- Room es la autoridad de lectura visible mientras el dispositivo está offline.
- PostgreSQL es la autoridad reconciliada entre dispositivos y usa `user_id` como frontera de aislamiento.
- Una transferencia se modela inicialmente como una transacción de negocio con dos asientos: `SOURCE = -amount` y `DESTINATION = +amount`.
- El saldo proyectado se actualiza en la misma transacción que el movimiento y se puede reconstruir desde el ledger.
- `transaction_links` relaciona operaciones distintas; no sustituye los dos asientos de una transferencia salvo decisión explícita del equipo.

### Idempotencia y similitud

- Repetición exacta: misma clave y mismo hash devuelve el resultado anterior sin nuevos efectos.
- Conflicto: misma clave y hash distinto se rechaza.
- Similitud: misma cuenta, tipo, monto y moneda dentro de cinco minutos presenta advertencia; confirmar genera una clave nueva y permite continuar.
- Una advertencia de similitud no es identidad ni un bloqueo definitivo.

### UI del incremento

Regla de formulario: la categoría es obligatoria para Gasto y opcional para Ingreso/Transferencia.

- Pantalla 11: tabs Gasto/Ingreso/Transferencia; monto, cuenta origen, destino para transferencia, categoría/comercio opcionales según reglas acordadas, fecha/hora, nota y confirmación de posible duplicado.
- Pantalla 10 mínima: grupos cronológicos y filas con icono, descripción, cuenta, monto con signo y estado de sincronización.
- Se implementa con fidelidad visual estricta a `docs/stitch-design-system.md` y `Stich Prompts.md` (Pantalla 10 para Historial y Pantalla 11 para Modal de Registro en Bottom Sheet). Reutiliza tema, componentes, tokens de color (Primary #0F766E, Income #16A34A, Expense #E85D5D) y números tabulares `tnum`.

### Sincronización

- La confirmación local escribe movimiento, ledger/proyección, recibo local y outbox atómicamente.
- El worker reclama elementos con lease, procesa en orden estable y clasifica errores transitorios, permanentes y conflictos.
- Un cierre entre confirmación y llamada remota no pierde el comando; un cierre después de la llamada se resuelve con idempotencia.

## Project Structure

### Documentation

```text
specs/004-ep-mov-movimientos-ledger/
|-- spec.md
|-- plan.md
|-- research.md
|-- data-model.md
|-- quickstart.md
|-- team-questions.md
|-- review-record.md
|-- contracts/
|   |-- register-transaction-v1.md
|   |-- sync-and-deduplication.md
|   `-- ui-contract.md
`-- tasks.md
```

### Source Code

```text
app/src/main/java/com/kipu/app/
|-- core/database/
|-- feature/movements/
|   |-- data/local/
|   |-- data/remote/
|   |-- data/sync/
|   |-- domain/model/
|   |-- domain/
|   |-- presentation/
|   `-- di/
|-- navigation/
`-- ui/component/

app/src/test/
app/src/androidTest/
supabase/migrations/
supabase/tests/
```

**Structure Decision**: mantener la organización existente por feature y crear `feature/movements` con capas `data`, `domain`, `presentation` y `di`; los cambios remotos viven únicamente en migraciones versionadas y pruebas de Supabase.

## Post-design Constitution Check

El diseño continúa en PASS. El modelo canónico, la migración desde `financial_movements`, la representación de transferencias, el aislamiento por `user_id`, la regla de categoría y la versión del contrato remoto quedaron documentados como decisiones de Sprint 2. Cualquier modificación futura requiere una nueva decisión trazable.

## Complexity Tracking

No se solicitan excepciones constitucionales. La bandeja de salida y los recibos son necesarios para offline-first e idempotencia; no constituyen capas opcionales.

## Incremento Sprint 4 — Diseño Propagado

### Fase 0 — Contratos y gate previo a implementación (T058–T063)

- Registrar P1–P5 y la baseline en research/team-questions con referencias a P14, P15, HU y revisión aprobada. La aceptación de main no convierte casillas o mocks en evidencia de proveedor.
- Completar data-model.md: mapeo del estado histórico Room ACTIVE/FAILED al estado financiero CONFIRMED/REVISED/VOIDED; revisión vigente, snapshots append-only, vínculo entre reversals y efectos originales, y separación de sync_status. Examinar el esquema remoto existente antes de agregar estructuras.
- Definir comandos versionados de corrección/anulación con identidad estable, hash canónico, transactionId, expectedRevision y payload; propietario derivado de sesión. Recibo previo se consulta antes de comparar revisión para reconocer un replay legítimo.
- Definir contrato de lectura/reconciliación de revisiones, contrapartidas y tombstones; pull con versión monotónica y checkpoint atómico. Una llegada obsoleta no reemplaza un VOIDED vigente.
- Definir guardia de operación estándar: compras/pagos de tarjeta, cuotas y dependencias especializadas bloquean edición/anulación genérica con advertencia. No se implementan reembolsos ni flujos propietarios de deuda.
- Documentar la frontera EP-PLA: autorización efectiva por capacidad para filtros avanzados, revalidación al ejecutar/paginar/enlazar, concesión hasta 72 h acotada por expiración conocida, protección de reloj y registro manual siempre operativo. Emisión y verificación del ancla pertenecen a HU-58/59.
- Revisión financiera/arquitectónica de estos derivados antes de cambiar Room, RPC o workers. Migraciones históricas S2/S3 no se reescriben.

### Fase 1 — Dominio, Room, compensación y outbox (T064–T078)

El agregado de movimiento mantiene identidad y revisión esperada. El dominio compara payload anterior/nuevo, valida referencias propias y moneda y calcula un plan de compensación en unidades menores enteras. Se puede conservar una referencia histórica bloqueada cuando no se sustituye; una nueva selección debe ser elegible. Un cambio solo de nota agrega revisión sin asientos innecesarios.

Una corrección financiera agrega contrapartidas de los efectos vigentes y el nuevo conjunto de efectos; no edita el ledger anterior. Revisiones y compensaciones permiten reconstruir el saldo. No se combina la exclusión de los originales por estado con otra aplicación de reversals que revierta dos veces. El consumo base de un periodo es una consulta interna sobre payload vigente de gastos confirmados/revisados, por propietario, moneda e intervalo [inicio, fin); no suma snapshots antiguos. Mover S/20 de A a B produce A=0/B=20 sin alterar caja; anular produce A=0/B=0. La lectura después del commit observa ambos resultados coherentes y un rollback conserva los anteriores. El cálculo no expone analítica Premium ni construye administración EP-PRE/EP-DEU; los módulos futuros consumen esta frontera y agregan sus reglas de elegibilidad.

El commit Room comprueba propietario/revisión, persiste revisión, asientos, proyecciones, recibo y outbox juntos y solo después comunica éxito. Postgres aplica la misma frontera mediante RPC, recibos y bloqueo de revisión; mantiene RLS/grants y ledger interno sin DML financiero directo de clientes. Transferencias corrigen o revierten ambas cuentas juntas.

Anulación conserva el original y agrega una revisión VOIDED y sus contrapartidas; repetir devuelve el recibo sin nuevos efectos. El rechazo financiero de efectos ya confirmados localmente conserva evidencia y compensa una sola vez. La ruta de rechazo de SyncInstrumentCommandsWorker y los DAO relacionados deben retirar DELETE físico de ledger/cuotas/asignaciones con historia, preservando la distinción entre operación especializada rechazada y edición genérica no permitida. Rechazos antes de commit no generan transacción ni contabilidad ficticia.

La outbox identifica el tipo de comando y serializa cadenas dependientes por agregado (alta → corrección → anulación), además de proteger los efectos conjuntos de transferencias. Reintenta con la misma identidad/hash; un nuevo intento sobre otra revisión tiene nueva identidad. Pull debe actualizar revisiones existentes y aplicar compensaciones una vez; INSERT IGNORE por identidad no basta.

Conflicto conserva propuesta y versión vigente, sin última escritura ni anulación del hecho vigente. Descartar o rehacer debe reconciliar de forma auditable cualquier efecto optimista ya aplicado y generar, al rehacer, un nuevo comando sobre revisión actual. El contrato de reconciliación se cierra en T060 antes de implementar.

### Fase 2 — Integración de capacidades y vigencia (T061, T079–T084)

EP-MOV consume la política de EP-PLA; no infiere Premium de preferencia, callback, compra pendiente ni verified=true sin vigencia. HU-58 depende de HU-52/HU-57; HU-59 depende de HU-54/HU-58. La baseline aceptada permite diseñar, pero la aceptación de filtros protegidos exige ambas capacidades integradas.

La vigencia requiere tiempo confiable estrictamente anterior a min(validación+72 h, vencimiento conocido); igualdad ya deniega Premium. Al caducar o no poder demostrar continuidad temporal tras reinicio, exigir reconexión para nuevas acciones Premium y mantener registro manual, sincronización y consulta básica. El ancla no se renueva desde reloj manipulable, reinicio, reinstalación o cambio de dispositivo; un contador monotónico no mantiene continuidad entre boots. Las pruebas EP-MOV consumen respuestas sustituibles de esa política; no implementan ni dan por validada la concesión EP-PLA.

### Fase 3 — Consultas y búsqueda (T079–T082)

- Consulta tipada limitada al propietario: Free combina texto, fechas y tipos G/I/T sobre toda la historia.
- Premium añade cuenta, tarjeta, categoría, comercio, monto, fuente y estado mediante intersección; validar criterios y autorización antes del DAO, también en enlaces directos y paginación.
- Orden estable por fecha efectiva e ID, cursor que se invalida al cambiar filtros; consultas SQL paginadas en lugar de filtrar toda la lista en ViewModel.
- Referencias archivadas y transacciones VOIDED siguen legibles. Distinguir ausencia de historia de cero coincidencias; limpiar restaura consulta básica.
- Caducidad con panel abierto no ejecuta criterios avanzados; conserva consulta básica y ofrece reconexión. Selecciones guardadas no se borran por pérdida de Premium.

### Fase 4 — UI Compose (T073, T078, T083–T084)

Pantalla 10 añade detalle, búsqueda/fechas/chips, filtros protegidos y estados de VOIDED/sync/conflicto. Pantalla 11 se reutiliza para edición con resumen anterior/nuevo; diálogos de anulación explican efectos en ambas cuentas y cancelación no escribe nada. Operaciones especializadas muestran advertencia amigable. Respetar design system Stitch, privacidad, TalkBack y objetivos de 48dp. ViewModels no realizan cálculo financiero autoritativo.

### Fase 5 — Integración, regresión y Review (T085–T089)

Probar dominio, Room, RPC/RLS, migración desde v16, outbox/pull, UI y dispositivo en las fronteras relevantes. Inyectar fallo antes de cada commit; probar 100 replays, revisión obsoleta, llegada posterior a VOIDED y rechazo financiero especializado sin DELETE. Validar S/20→S/15, ambas cuentas de transferencia, cambio de periodo y nota sin efectos extra; mantener regresiones S2/S3 y ausencia de duplicación del pasivo/pago.

La matriz integrada con EP-PLA cubre Free, Premium vigente/caducado, vencimiento comercial anterior, Lifetime, reloj alterado, suspensión/reinicio y reconexión; registro manual y cola permanecen operativos. No afirmar HU-59 validada mediante un stub de EP-MOV.

Demo: corregir gasto, anular transferencia, mostrar filtros básicos/avanzados autorizados o denegados, conflicto/recuperación y gasto manual con concesión caducada seguido de reconexión idempotente. La devolución parcial se retira de S4 por P1 y permanece en S7; notificar al responsable la actualización del cronograma del vault.

### Gate constitucional y cobertura S4

FR-011–018 y FR-029–032 se trazan a T058–T089; FR-004–009, FR-019 y FR-026–028 se verifican por regresión y sync. SC-002–005 y SC-007–013 tienen evidencia específica S4; SC-006 mantiene evidencia/regresión del alta. FR-020–025 y SC-009 para reembolsos quedan en S6/S7.

El refinamiento está propagado documentalmente; no se declara PASS de implementación nueva. Gate de inicio: T058–T063, revisión de contratos/modelo y analyze sin críticos. Gate de aceptación: tareas completas, integración real de HU-58/59, cross-review y evidencia de DoD. No hay excepción a integridad, RLS, privacidad, preservación de historia ni núcleo manual Free.

### Remediación del hash de alta (T093)

Seguir contracts/register-transaction-v2.md: nuevos payloads contract_version=2 y RPC v2; no reinterpretar colas históricas v1. El servidor deriva owner, recalcula hash, rechaza campos no firmados y delega registro estándar existente atómicamente. Reconciliar Kotlin/Postgres con golden vectors y probar compatibilidad antes de cerrar T093.

## S4 UI/UX refinement — 2026-10-03

**Propagated**: 2026-10-03 — FR-033–FR-039 / SC-014–SC-017; preserved completed S2/S4 design and financial implementation.

Use the existing Stitch theme, Inter, semantic warning surfaces, 4/8 dp spacing, adaptive heights and 48 dp touch targets. Read-only movement detail is a history-owned modal, including VOIDED, with explicit edit/void callbacks and owner-scoped revision reads. Filter editing is a local draft; apply validates decimal money/currency and inclusive UI dates converted to exclusive domain bounds. Accounts/categories/merchants/cards use existing historical references; labels never expose IDs. Basic and advanced criteria remain subject to existing domain access decisions. Repository read failures expose retry separately from empty content.

Use MoneyText/LocalBalanceMasked in every financial summary and password transformation plus sanitized semantics for masked input. Compare each changed editor field; preserve atomic commit and specialized restrictions. Animate access cards and summary regions with KipuMotionTokens and rememberReducedMotionEnabled; never fade retained protected results. UI decisions are independent of animation completion.

Validation: draft cancel/apply, invalid dates/amounts/currencies, filters removed individually, masked semantics, VOIDED read-only detail, owner changes, error retry, interrupted motion and direct access restoration. JVM tests run locally; instrumentation must compile and be executed when a device is available. Record device limitations accurately.


**Propagated**: 2026-10-03 — Source-provenance baseline finding: T110 resolves the canonical persisted provenance and its authorized query/UI mapping before complete HU-22 acceptance. This is an open baseline dependency, independent of the completed FR-033–FR-039 visual refinement. Do not substitute sync state or account identity for provenance. No provenance schema or historical backfill is approved by this visual change.

## Issue #19 — Corrección de espaciado reportada en dispositivo (2026-10-06)

T111–T113 concretan FR-034/039 dentro de `MovementFiltersSheet.kt` y sus strings: separación vertical en FlowRow y grupos avanzados; cabecera del DateRangePicker con padding y extremos de igual ancho; tarjeta Premium informativa con acción explícita. Mantener entrada manual/calendario, callbacks, Saver, privacidad y validaciones existentes. No editar pantalla, ViewModel, consultas ni componentes globales.

Verificar build y regresión existente de borrador, actualizar la copia de pruebas física y comprobar visualmente los tres hallazgos. Registrar comandos y límites reales en `review-record.md`; las capturas del dispositivo del usuario permanecen locales. T110 continúa fuera del alcance.

### Alcance restante autorizado

El responsable acepta el resumen/retirada de filtros aplicados y el comportamiento de «Limpiar» tal como están. T115–T118 completan errores locales, selectores y entrega sin cambiar esas interacciones. Reutilizar `MovementFilterDraft.validate` para presentar errores por clave (`from`, `to`, `currency`, `min`, `max`), y refrescarlos durante correcciones después de un intento inválido. El selector específico usa búsqueda y `LazyColumn` con claves estables por ID; conserva selecciones ocultas por la búsqueda y permite retirar referencias seleccionadas ausentes del catálogo mediante nombre histórico seguro. Los nombres duplicados no fusionan IDs.

Verificar con la variante aislada `lab` y fixtures sintéticos, preservando la instalación y sesión del usuario. La evidencia versionada compara el panel anterior a T115/T116 con el resultado en igual dispositivo/tema. Registrar explícitamente qué se comprobó por fixture y qué por integración; no certificar compras, consultas ni pendientes de Sprint 4 con pruebas de presentación.

La prueba compacta usa un `ComposeView` con `ContextThemeWrapper` y configuración local (densidad ×1,25; fuente ×1,6), heredada por los diálogos. Un override de `LocalDensity` externo no basta para las ventanas Android. Material3 1.4.0 impone 360 dp al contenedor de `DatePickerDialog`; por debajo de ese ancho usar un contenedor local limitado a la ventana, conservando `DateRangePicker`, estado, validaciones y callbacks. Las acciones pueden pasar a otra fila. El diálogo nativo permanece en anchos de 360 dp o más.
