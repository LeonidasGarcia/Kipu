# Tasks: Sprint 2 y Sprint 4 — Movimientos y ledger

**Propagated**: 2026-10-02 — T093/F04: alta estándar v2 con array canónico inequívoco validado por servidor; outbox v1 histórica inmutable, dispatch por versión y nueva migración wrapper. Se conserva el trabajo S2 y el DAG existente.

**Input**: documentos de diseño en `specs/004-ep-mov-movimientos-ledger/`

**Alcance histórico S2**: US1/HU18, US2/HU19 y US3/HU23; T001–T057 conservan su contenido y estado de cierre.

**Alcance activo S4**: US4/HU-20, US5/HU-21 y US6/HU-22 (15 puntos EP-MOV; total S4 31 con HU-58/HU-59 de EP-PLA). US7/HU-24 sigue en S6 y US8/HU-25 en S7.

**Propagated**: 2026-10-02 — Refinamiento aprobado P1–P5 y plan S4; nuevas tareas T058–T089, sin regenerar ni renumerar el trabajo S2. Baseline main `c80ea0ddea80dfe5332971f12418758ea1bc9923`, Room v16.

**Propagated**: 2026-10-02 — Correcciones I1/U1/B1/I2 aprobadas: contexto explícito EP-MOV, prueba cuantitativa entre periodos, corte exacto/reinicio y T074 serializada después de T071. T001–T057 permanecen intactas.

**Contexto obligatorio de comandos S4**: antes de invocar scripts Spec Kit en PowerShell, establecer `$env:SPECIFY_FEATURE_DIRECTORY = (Resolve-Path 'specs/004-ep-mov-movimientos-ledger').Path` y `$env:SPECIFY_FEATURE = '004-ep-mov-movimientos-ledger'`; comprobar FEATURE_DIR en el JSON. No confiar en la selección histórica de `.specify/feature.json` ni ejecutar comandos sobre EP-NOT.

**Regla de pruebas**: escribir cada prueba indicada y comprobar que falla por la razón esperada antes de implementar.

## Phase 1: Setup y decisiones compartidas

**Purpose**: eliminar ambigüedades que podrían generar dos verdades financieras.

- [x] T001 Resolver las preguntas bloqueantes 1–5 y 25–27 y registrar las decisiones en `specs/004-ep-mov-movimientos-ledger/team-questions.md`
- [x] T002 Actualizar las decisiones de modelo canónico, transferencia, aislamiento, categoría y migración en `specs/004-ep-mov-movimientos-ledger/research.md`
- [x] T003 [P] Crear la prueba de límites arquitectónicos del nuevo módulo en `app/src/test/java/com/kipu/app/feature/movements/MovementsFeatureBoundaryTest.kt`
- [x] T004 [P] Agregar textos base de alta, historial, sincronización y duplicados en `app/src/main/res/values/strings.xml`

**Checkpoint**: el equipo aprobó una única representación contable y el dueño de los cambios compartidos.

---

## Phase 2: Foundation (bloquea las historias)

**Purpose**: establecer contratos y persistencia compartidos por HU18, HU19 y HU23.

- [x] T005 Crear tipos `Transaction`, `LedgerEntry`, `MovementType` y estados de sincronización con montos `Long` en `app/src/main/java/com/kipu/app/feature/movements/domain/model/MovementModels.kt`
- [x] T006 [P] Definir comandos, resultados y errores del registro en `app/src/main/java/com/kipu/app/feature/movements/domain/model/RegisterTransactionModels.kt`
- [x] T007 Definir observación y registro offline-first en `app/src/main/java/com/kipu/app/feature/movements/domain/MovementRepository.kt`
- [x] T008 [P] Crear entidades Room para transacciones, asientos, recibos y outbox en `app/src/main/java/com/kipu/app/feature/movements/data/local/MovementEntities.kt`
- [x] T009 Crear consultas de historial, saldo, similitud por usuario, recibos con unicidad `user_id + idempotency_key` y outbox en `app/src/main/java/com/kipu/app/feature/movements/data/local/MovementDao.kt`
- [x] T010 Incorporar las entidades, DAO y migración de versión acordada en `app/src/main/java/com/kipu/app/core/database/KipuDatabase.kt` y `app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt`
- [x] T011 Registrar DAO, repositorio, API, worker y reloj/UUID sustituibles en `app/src/main/java/com/kipu/app/feature/movements/di/MovementsModule.kt`

**Checkpoint**: el esquema local compila, migra sin pérdida y el módulo tiene dependencias inyectables.

---

## Phase 3: US1 — HU18 Registrar movimiento manual (Priority: P1) 🎯 MVP

**Goal**: registrar gasto, ingreso o transferencia desde la Pantalla 11 y verlo inmediatamente en un historial mínimo.

**Independent Test**: sin red, crear cada tipo con referencias propias; debe aparecer una sola operación válida en historial con datos y signo correctos.

### Tests for US1

- [x] T012 [P] [US1] Probar validaciones, signos, moneda y campos requeridos de los tres tipos en `app/src/test/java/com/kipu/app/feature/movements/domain/RegisterTransactionTest.kt`
- [x] T013 [P] [US1] Probar persistencia y lectura cronológica local de gasto, ingreso y transferencia en `app/src/androidTest/java/com/kipu/app/feature/movements/data/local/MovementLocalDataSourceTest.kt`
- [x] T014 [P] [US1] Probar estado, validaciones y envío único del formulario en `app/src/test/java/com/kipu/app/feature/movements/presentation/QuickMovementViewModelTest.kt`
- [x] T015 [P] [US1] Probar tabs, campos condicionales y accesibilidad básica en `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/QuickMovementScreenTest.kt`
- [x] T016 [P] [US1] Probar gasto, ingreso, transferencia y referencias ajenas del RPC en `supabase/tests/database/movements_register_transaction_test.sql`

### Implementation for US1

- [x] T017 [P] [US1] Implementar normalización, validación y mapeo de comandos en `app/src/main/java/com/kipu/app/feature/movements/domain/RegisterTransactionValidator.kt`
- [x] T018 [US1] Implementar el caso de uso de alta y generación estable de identidad en `app/src/main/java/com/kipu/app/feature/movements/domain/RegisterTransaction.kt`
- [x] T019 [US1] Implementar commit local y observación de historial en `app/src/main/java/com/kipu/app/feature/movements/data/OfflineFirstMovementRepository.kt`
- [x] T020 [P] [US1] Crear DTOs y mapeos de `register_transaction_v1` en `app/src/main/java/com/kipu/app/feature/movements/data/remote/MovementDtos.kt`
- [x] T021 [US1] Implementar el cliente autenticado del RPC en `app/src/main/java/com/kipu/app/feature/movements/data/remote/MovementApi.kt`
- [x] T022 [US1] Crear el esquema canónico, validaciones de pertenencia y `register_transaction_v1` en `supabase/migrations/20260922090000_ep_mov_ledger.sql`
- [x] T023 [P] [US1] Implementar estado y eventos del formulario en `app/src/main/java/com/kipu/app/feature/movements/presentation/QuickMovementViewModel.kt`
- [x] T024 [US1] Implementar la Pantalla 11 (Modal de Registro en Bottom Sheet) con tabs Gasto/Ingreso/Transferencia, referencias tipadas por categoría, selectores, DatePicker, teclado numérico, números tabulares y feedback de guardado local/pendiente según Stich Prompts.md y docs/stitch-design-system.md en `app/src/main/java/com/kipu/app/feature/movements/presentation/QuickMovementScreen.kt`
- [x] T025 [P] [US1] Implementar la Pantalla 10 (Historial/Ledger) con TopBar, buscador, chips de filtro rápido, lista cronológica, badges de sincronización, colores por tokens y números tabulares según Stich Prompts.md y docs/stitch-design-system.md en `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryScreen.kt`
- [x] T026 [US1] Conectar acción rápida e historial al grafo en `app/src/main/java/com/kipu/app/navigation/MovementsNavigation.kt` y `app/src/main/java/com/kipu/app/navigation/KipuNavHost.kt`

**Checkpoint**: HU18 funciona offline para los tres tipos y su resultado es visible sin esperar al servidor.

---

## Phase 4: US2 — HU19 Saldos y confirmación atómica (Priority: P1)

**Goal**: movimiento, asientos, saldo visible y envío pendiente se confirman juntos o no se confirma nada.

**Independent Test**: inyectar un fallo en cada frontera local/remota; nunca debe quedar media transferencia, y un reinicio debe recuperar el envío pendiente.

### Tests for US2

- [x] T027 [P] [US2] Probar rollback de movimiento, asientos, proyección y outbox ante fallos inyectados en `app/src/androidTest/java/com/kipu/app/feature/movements/data/local/MovementLocalDataSourceTest.kt`
- [x] T028 [P] [US2] Probar recuperación de lease, reintento y reinicio del worker en `app/src/androidTest/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorkerTest.kt`
- [x] T029 [P] [US2] Probar rollback remoto y balance de los dos asientos de transferencia en `supabase/tests/database/movements_register_transaction_test.sql`

### Implementation for US2

- [x] T030 [US2] Encapsular movimiento, asientos, proyección, recibo y outbox en una única transacción Room en `app/src/main/java/com/kipu/app/feature/movements/data/local/MovementLocalDataSource.kt`
- [x] T031 [US2] Implementar actualización y reconstrucción de saldo desde asientos en `app/src/main/java/com/kipu/app/feature/movements/data/local/BalanceProjectionStore.kt`
- [x] T032 [US2] Implementar claim con lease, orden estable, backoff y clasificación de errores en `app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorker.kt`
- [x] T033 [US2] Programar sincronización única y recuperable tras el commit local en `app/src/main/java/com/kipu/app/feature/movements/data/sync/MovementSyncScheduler.kt`
- [x] T034 [US2] Hacer atómica la creación remota de transacción, asientos y recibo en `supabase/migrations/20260922090000_ep_mov_ledger.sql`
- [x] T035 [US2] Mostrar estados pendiente, sincronizado, conflicto y error corregible en `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryScreen.kt`

**Checkpoint**: HU19 demuestra atomicidad local/remota, persistencia tras reinicio y saldo reconstruible.

---

## Phase 5: US3 — HU23 Evitar duplicados (Priority: P1)

**Goal**: impedir el doble efecto del mismo comando y advertir operaciones parecidas sin bloquear compras reales.

**Independent Test**: repetir clave/hash, repetir clave con hash distinto y confirmar dos compras similares con claves distintas; los resultados deben ser `DUPLICATE`, `CONFLICT` y dos movimientos reales respectivamente.

### Tests for US3

- [x] T036 [P] [US3] Probar representación canónica, hash, repetición y conflicto en `app/src/test/java/com/kipu/app/feature/movements/domain/TransactionIdempotencyTest.kt`
- [x] T037 [P] [US3] Probar consulta de similitud, recibos y ausencia de efectos al cancelar en `app/src/androidTest/java/com/kipu/app/feature/movements/data/local/MovementLocalDataSourceTest.kt`
- [x] T038 [P] [US3] Probar clave/hash iguales, clave/hash distintos y concurrencia en `supabase/tests/database/movements_register_transaction_test.sql`
- [x] T039 [P] [US3] Probar el diálogo de advertencia, cancelación y confirmación con identidad nueva en `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/DuplicateWarningDialogTest.kt`

### Implementation for US3

- [x] T040 [P] [US3] Implementar JSON canónico y SHA-256 del comando en `app/src/main/java/com/kipu/app/feature/movements/domain/TransactionRequestHasher.kt`
- [x] T041 [US3] Implementar repetición determinista y conflicto por clave/hash en `app/src/main/java/com/kipu/app/feature/movements/data/OfflineFirstMovementRepository.kt`
- [x] T042 [US3] Implementar búsqueda configurable de similitud antes del commit en `app/src/main/java/com/kipu/app/feature/movements/domain/FindSimilarTransactions.kt`
- [x] T043 [US3] Comparar `request_hash` y serializar el resultado previo en `supabase/migrations/20260922090000_ep_mov_ledger.sql`
- [x] T044 [US3] Integrar el diálogo de advertencia y la confirmación con clave nueva en `app/src/main/java/com/kipu/app/feature/movements/presentation/QuickMovementScreen.kt` y `app/src/main/java/com/kipu/app/feature/movements/presentation/QuickMovementViewModel.kt`

**Checkpoint**: HU23 protege reintentos y permite dos operaciones similares legítimas también en Free.

---

## Phase 6: Polish y validación transversal

**Purpose**: probar migraciones, seguridad, rendimiento, accesibilidad y evidencia de cierre.

- [x] T045 [P] Agregar prueba de migración desde la versión Room anterior y verificar saldos existentes en `app/src/androidTest/java/com/kipu/app/core/database/MovementRoomMigrationTest.kt`
- [x] T046 [P] Agregar pruebas RLS para referencias propias y de otro espacio en `supabase/tests/database/movements_register_transaction_test.sql`
- [x] T047 [P] Medir y paginar historial de 10 000 filas sin bloquear UI en `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/MovementHistoryPerformanceTest.kt`
- [x] T048 [P] Completar semántica TalkBack, objetivos de 48 dp y representación no basada sólo en color en `app/src/main/java/com/kipu/app/feature/movements/presentation/QuickMovementScreen.kt` y `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryScreen.kt`
- [x] T049 Aplicar redacción de notas, referencias y errores de sincronización en `app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorker.kt`
- [x] T050 Ejecutar y documentar todos los escenarios de `specs/004-ep-mov-movimientos-ledger/quickstart.md`, incluidos cierre/reapertura, reintento y segundo dispositivo con migraciones remotas aplicadas y RPCs sincronizados.
- [x] T051 Marcar respuestas, evidencia y sólo los criterios de HU18/HU19/HU23 satisfechos en `specs/004-ep-mov-movimientos-ledger/team-questions.md` y `specs/004-ep-mov-movimientos-ledger/checklists/requirements.md`
- [x] T052 [P] Probar backfill PostgreSQL desde `financial_movements`, conservación de saldos y datos representativos en `supabase/tests/database/movements_register_transaction_test.sql`
- [x] T053 [P] Probar que la deduplicación permanece disponible en Free sin consumir cupos en `app/src/test/java/com/kipu/app/feature/movements/domain/FreeDeduplicationTest.kt`
- [x] T054 [P] Probar que logs y errores no exponen notas, tokens ni datos financieros sensibles en `app/src/test/java/com/kipu/app/core/logging/MovementLogRedactionTest.kt`
- [x] T055 [P] Ejecutar 100 reintentos idénticos por operación y demostrar un solo efecto en `supabase/tests/database/movements_idempotency_stress_test.sql`
- [x] T056 [P] Medir en dispositivo la finalización de un alta manual común en menos de 10 segundos con datos preparados en `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/ManualEntryAcceptanceTest.kt`
- [x] T057 Obtener cross-review documentado de finanzas, privacidad, seguridad, arquitectura y pruebas en `specs/004-ep-mov-movimientos-ledger/review-record.md`.

---

## Backlog por Incremento (historial de diferimiento S2)

| Historia | Contenido | Sprint previsto |
|---|---|---|
| US4 / HU20 | Edición auditable y conflictos de revisión | 4 — activado en Phase 9 |
| US5 / HU21 | Anulación lógica y contrapartidas | 4 — activado en Phase 10 |
| US6 / HU22 | Búsqueda y filtros avanzados | 4 — activado en Phase 11 |
| US7 / HU24 | Obligaciones, préstamos y metas | 6 |
| US8 / HU25 | Reembolsos y conciliación entre periodos | 7 |

## Dependencies & Execution Order

Las reglas siguientes documentan S2; el DAG S4 se incorpora al final del archivo.

- Phase 1 bloquea cualquier cambio de esquema compartido.
- Phase 2 bloquea US1, US2 y US3.
- US1 entrega el flujo vertical mínimo; US2 endurece su atomicidad; US3 añade idempotencia y similitud.
- Las pruebas [P] de cada historia pueden escribirse en paralelo antes de su implementación.
- Las tareas SQL que comparten `20260922090000_ep_mov_ledger.sql` se ejecutan en orden T022 → T034 → T043; T052 valida la migración histórica antes del cierre.
- Las tareas de UI que comparten archivos se ejecutan en orden T024/T025 → T035 → T044 → T048.

## Parallel Examples

### US1

```text
T012 dominio | T013 Room | T014 ViewModel | T015 Compose | T016 pgTAP
T020 DTOs    | T023 estado UI | T025 historial mínimo
```

### US2

```text
T027 atomicidad Room | T028 worker/reinicio | T029 atomicidad PostgreSQL
```

### US3

```text
T036 hash | T037 similitud local | T038 idempotencia remota | T039 diálogo
```

## Implementation Strategy

Estrategia histórica S2; para S4 seguir Phase 7–12 y sus gates.

1. Resolver T001–T002 antes de editar migraciones o `KipuDatabase`.
2. Completar Foundation y hacer pasar migración/compilación.
3. Entregar US1 como MVP demostrable offline.
4. Completar US2 y repetir pruebas con fallos y reinicios.
5. Completar US3 y probar repeticiones concurrentes y compras similares legítimas.
6. Ejecutar Phase 6, realizar cross-review y adjuntar evidencia; no adelantar el backlog diferido.

## Phase 7: S4 — Contratos, decisiones y gate de diseño

**Goal**: cerrar derivados antes de tocar código financiero; P1–P5 ya están aprobadas y no requieren repetir las preguntas.

- [x] T058 Registrar decisiones P1–P5, baseline aceptada y fuentes P14/P15/HU en `specs/004-ep-mov-movimientos-ledger/research.md` y `team-questions.md`; documentar corrección del guion S4 y mantener HU-25 en S7 (FR-029–032).
- [x] T059 Detallar estados/revisiones/snapshots, compensaciones, referencias históricas, consumo base por propietario/moneda/periodo sobre payload vigente e intervalos explícitos y migración desde Room v16 en `specs/004-ep-mov-movimientos-ledger/data-model.md`, contrastando el esquema remoto existente sin reescribir migraciones S2/S3 (FR-011/012/014/015/030/031; depende de T058).
- [x] T060 Definir contratos versionados de corregir/anular, hash/expectedRevision, recibos y reconciliación de rechazo/conflicto/pull en `specs/004-ep-mov-movimientos-ledger/contracts/revise-and-void-transaction-v1.md` y `contracts/sync-and-deduplication.md`; preservar el hecho vigente y propuestas (FR-013–015/026/030/031; depende de T059).
- [x] T061 [P] Definir frontera de autorización HU-58/HU-59, consulta/paginación/deep links, corte exacto (`tiempo confiable >= límite` deniega Premium) y revalidación tras reinicio sin continuidad temporal verificable en `specs/004-ep-mov-movimientos-ledger/contracts/history-query-access.md`; enlazar contratos de `specs/012-ep-pla-planes-monetizacion/` y registrar dependencias pendientes del productor sin simular concesiones verificadas (FR-016–018/027/032; depende de T058).
- [x] T062 [P] Refinar edición estándar, advertencia especializada, anulación, conflictos y filtros de Pantalla 10/11 en `specs/004-ep-mov-movimientos-ledger/contracts/ui-contract.md` y escenarios S4 en `quickstart.md` (FR-011–018/029/032; depende de T060 y T061).
- [x] T063 Obtener revisión financiera/arquitectónica del modelo/contratos y ejecutar `speckit-analyze` sobre los artefactos reconciliados; registrar cobertura y corregir críticos en `specs/004-ep-mov-movimientos-ledger/review-record.md` (depende de T059–T062; bloquea código S4).

## Phase 8: S4 — Foundation de revisiones, persistencia y sync

- [x] T064 [P] Probar contratos de comando, revisión esperada, guardia STANDARD, validación de relaciones y planes de compensación en `app/src/test/java/com/kipu/app/feature/movements/domain/MovementRevisionTest.kt` (FR-011–015/029–031; depende de T063).
- [x] T065 Añadir modelos de revisión/comando/compensación, estado financiero y puertos de corregir/anular/consulta en `app/src/main/java/com/kipu/app/feature/movements/domain/model/MovementModels.kt`, `MovementRevisionModels.kt` y `domain/MovementRepository.kt`; preservar API de alta (depende de T064).
- [x] T066 Probar y añadir migración no destructiva desde Room v16, entidades/DAO de revisiones y recibos en `app/src/androidTest/java/com/kipu/app/core/database/MovementRoomMigrationTest.kt`, `app/src/main/java/com/kipu/app/core/database/RoomMigrations.kt`, `KipuDatabase.kt`, `feature/movements/data/local/MovementEntities.kt` y `MovementDao.kt`; versionar el nuevo esquema en `app/schemas/` (depende de T065).
- [x] T067 [P] Probar y añadir RPC de corrección/anulación, bloqueo de revisión, recibos, RLS/grants y emisión de cambios en una nueva migración bajo `supabase/migrations/` y `supabase/tests/database/movements_revision_test.sql`; no modificar migraciones aplicadas (FR-013–015/030/031; depende de T065 y T060).
- [x] T068 Extender payload/DTO/API y worker para comandos encadenados por agregado, pull de revisiones/contrapartidas/tombstones y checkpoint atómico en `app/src/main/java/com/kipu/app/feature/movements/data/MovementOutboxPayloadFactory.kt`, `data/remote/MovementDtos.kt`, `MovementApi.kt` y `data/sync/SyncMovementsWorker.kt`; probar replay/reordenamiento en `app/src/androidTest/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorkerTest.kt` (depende de T066 y T067).

## Phase 9: US4 — HU-20 Edición estándar auditable

**Independent Test**: S/20→S/15 recupera S/5, fecha cambia periodos, nota conserva categoría bloqueada; operación especializada no cambia y revisión incompatible produce conflicto.

- [x] T069 [P] [US4] Probar importe/fecha/cuentas/categoría/comercio/nota, referencias históricas y prohibición de edición especializada en `app/src/test/java/com/kipu/app/feature/movements/domain/ReviseTransactionTest.kt`; verificar con intervalos/zona explícitos gasto S/20 trasladado de A a B: consumo A=0/B=20, saldo intacto y anulaciones excluidas (FR-011–013/029/031; depende de T065).
- [x] T070 [US4] Implementar validación y plan de compensación del caso de uso en `app/src/main/java/com/kipu/app/feature/movements/domain/ReviseTransaction.kt`; solo nota no agrega asientos y transferencias ajustan ambos extremos (depende de T069).
- [x] T071 [US4] Probar rollback/replay/reinicio e implementar commit revisión+compensaciones+proyecciones+recibo+outbox y consulta interna de consumo base por periodo en `app/src/androidTest/java/com/kipu/app/feature/movements/data/local/MovementRevisionLocalTest.kt`, `app/src/main/java/com/kipu/app/feature/movements/data/local/MovementLocalDataSource.kt`, `MovementDao.kt`, `BalanceProjectionStore.kt` y `data/OfflineFirstMovementRepository.kt`; comprobar A=0/B=20 después de mover la fecha y totales originales tras rollback, sin sumar snapshots antiguos (depende de T066 y T070).
- [x] T072 [US4] Implementar propuesta persistente y resolución explícita descartar/rehacer sobre revisión vigente en `app/src/main/java/com/kipu/app/feature/movements/data/OfflineFirstMovementRepository.kt` y `data/sync/SyncMovementsWorker.kt`; probar reconciliación de efectos optimistas sin sobrescribir historia ni anular el hecho vigente en `app/src/androidTest/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorkerTest.kt` (FR-013/015/026; depende de T068 y T071).
- [x] T073 [US4] Implementar y probar editor Pantalla 11, resumen de cambios, advertencia especializada y comparación de conflicto en `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementEditorViewModel.kt`, `MovementEditorSheet.kt` y `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/MovementEditorScreenTest.kt`; conectar en `app/src/main/java/com/kipu/app/navigation/MovementsNavigation.kt` (depende de T072 y T062).

## Phase 10: US5 — HU-21 VOIDED y rechazo financiero auditable

**Independent Test**: anular gasto/transferencia, cancelar, repetir y entregar edición antigua; conservar ledger original y revertir una sola vez.

- [x] T074 [US5] Probar VOIDED, compensación de ambas cuentas, cancelar sin escritura, replay y bloqueo especializado en `app/src/test/java/com/kipu/app/feature/movements/domain/VoidTransactionTest.kt` y `app/src/androidTest/java/com/kipu/app/feature/movements/data/local/MovementRevisionLocalTest.kt`; ejecución serial por archivo compartido con T071 (FR-014/015/029–031; depende de T071).
- [x] T075 [US5] Implementar caso de uso y commit atómico de VOIDED con snapshots, contrapartidas, recibo y outbox en `app/src/main/java/com/kipu/app/feature/movements/domain/VoidTransaction.kt` y `data/local/MovementLocalDataSource.kt`; conservar relaciones e historia (depende de T074 y T071).
- [x] T076 [US5] Reemplazar DELETE físico en rechazos de efectos confirmados por VOIDED/compensación idempotente en `app/src/main/java/com/kipu/app/feature/accounts/data/sync/SyncInstrumentCommandsWorker.kt`, `feature/movements/data/local/MovementDao.kt` y DAOs de cuotas/asignaciones bajo `feature/accounts/data/local/`; probar rechazo de compra/pago, saldos/pasivo y referencias conservadas en `app/src/androidTest/java/com/kipu/app/feature/accounts/FinancialRejectionCompensationTest.kt` (P5/FR-030; depende de T075 y T068).
- [x] T077 [US5] Probar dos dispositivos, conflicto, 100 replays y edición posterior a VOIDED en `supabase/tests/database/movements_revision_test.sql` y `app/src/androidTest/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorkerTest.kt`; comprobar cero compensaciones duplicadas y originales preservados (FR-013–015/030/031; depende de T072 y T076).
- [x] T078 [US5] Implementar y probar diálogo de anulación con consecuencias, advertencia especializada y estado histórico VOIDED en `app/src/main/java/com/kipu/app/feature/movements/presentation/VoidMovementDialog.kt`, `MovementHistoryScreen.kt` y `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/MovementHistoryScreenTest.kt` (depende de T075 y T073).

## Phase 11: US6 — HU-22 Consultas Free/Premium

**Independent Test**: encontrar Tambo antiguo Free, combinar cuenta/categoría/rango con Premium, limpiar cero coincidencias y denegar enlace/filtro con concesión caducada conservando consulta básica.

- [x] T079 [P] [US6] Probar consultas básicas/avanzadas, intersección, rangos inválidos, cursor estable y referencias históricas en `app/src/test/java/com/kipu/app/feature/movements/domain/QueryMovementHistoryTest.kt` y `app/src/androidTest/java/com/kipu/app/feature/movements/data/local/MovementHistoryQueryTest.kt` (FR-016–018/032; depende de T066 y T061).
- [x] T080 [US6] Implementar consulta tipada y guardia de capacidad antes de DAO, incluidos paginación/enlace y alternativa básica, en `app/src/main/java/com/kipu/app/feature/movements/domain/QueryMovementHistory.kt` y `domain/model/MovementHistoryQuery.kt`; consumir la política efectiva de EP-PLA sin verified=true aislado (depende de T079; aceptación integrada espera HU-58/HU-59).
- [x] T081 [US6] Implementar consultas SQL paginadas por propietario, fecha/ID y filtros combinables en `app/src/main/java/com/kipu/app/feature/movements/data/local/MovementDao.kt` y `data/OfflineFirstMovementRepository.kt`; seleccionar índices con evidencia y preservar VOIDED/referencias archivadas (depende de T080 y T071).
- [x] T082 [US6] Probar deep links, propietario, cambio de filtros y caducidad con panel abierto; integrar autorización y cursor en `app/src/test/java/com/kipu/app/feature/movements/presentation/MovementHistoryViewModelTest.kt`, `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryViewModel.kt` y `navigation/MovementsNavigation.kt` (depende de T081 y T072).
- [x] T083 [US6] Implementar fechas, chips, panel avanzado, limpiar y estados vacío/error en `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryScreen.kt`; probar privacidad, TalkBack y targets de 48dp en `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/MovementHistoryScreenTest.kt` (depende de T082 y T078).
- [x] T084 [US6] Integrar y probar HU-58/HU-59 reales: acceso justo antes/en/después del límite, expiración comercial anterior, reloj/reinicio sin evidencia y reconexión, historial básico y gasto manual durable en `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/MovementHistoryAccessIntegrationTest.kt`; registrar evidencia en `specs/004-ep-mov-movimientos-ledger/quickstart.md` (depende de T083 y entrega EP-PLA HU-58/HU-59; fixtures no cierran este gate).

## Phase 12: S4 — Integración, DoD y Review

- [x] T085 [P] Validar Room v16→nueva versión y migraciones Postgres con datos representativos, ledger/saldos/historia preservados, aislamiento con dos JWT y RPC protegidos en `app/src/androidTest/java/com/kipu/app/core/database/MovementRoomMigrationTest.kt` y `supabase/tests/database/movements_revision_test.sql` (SC-003/005/008/012; depende de T077 y T081).
- [x] T086 [P] Medir consulta básica de 10 000 movimientos contra SC-007 (p95 <300 ms), paginación sin duplicados y UI sin bloqueo en `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/MovementHistoryPerformanceTest.kt`; registrar dispositivo/datos/resultados en `specs/004-ep-mov-movimientos-ledger/quickstart.md` (depende de T083).
- [x] T087 Ejecutar regresión S2/S3, integración offline/sync y S4, build/lint/tests pertinentes, verificación de no DELETE en corrección/rechazo financiero y logs sin datos sensibles; registrar comandos/resultados en `specs/004-ep-mov-movimientos-ledger/review-record.md` (FR-019/026–028; SC-002–005/008–013; depende de T084–T086).
- [x] T088 Obtener cross-review de finanzas, seguridad/RLS, privacidad, arquitectura y evidencia; reconciliar cobertura FR/SC y ejecutar analyze/refine.status en `specs/004-ep-mov-movimientos-ledger/review-record.md` y `checklists/requirements.md` (depende de T087).
- [x] T089 Ejecutar demo S4 sin devolución parcial, con conflicto/recuperación y persistencia/reconexión; actualizar evidencia de HU-20/21/22 y comunicar al responsable la corrección del cronograma del vault en `specs/004-ep-mov-movimientos-ledger/quickstart.md` y `review-record.md`; aceptar solo HU completas, HU-25 sigue S7 (depende de T088).

## Dependencies & Execution Order — Sprint 4

- Diseño: T058 → T059 → T060; T058 → T061; T060/T061 → T062; T059–T062 → T063. Sin T063 no se inicia código financiero.
- Foundation: T063 → T064 → T065 → T066/T067 → T068. T067 también depende del contrato T060.
- Edición: T065 → T069 → T070; T066/T070 → T071; T068/T071 → T072 → T073. T073 también depende de T062.
- Anulación/rechazos: T071 → T074 → T075; T075/T068 → T076; T072/T076 → T077; T075/T073 → T078.
- Consultas: T066/T061 → T079 → T080; T080/T071 → T081; T081/T072 → T082; T082/T078 → T083; T083 + HU-58/HU-59 EP-PLA → T084.
- Cierre: T077/T081 → T085; T083 → T086; T084–T086 → T087 → T088 → T089.
- [P] permite solo archivos independientes. Serializar trabajos sobre MovementLocalDataSource, MovementDao, workers, quickstart y pruebas compartidas; coordinar EP-CTA al retirar DELETE de rechazos.
- T001–T057 son historia S2 y no se reabren ni renumeran. US7/HU-24 y US8/HU-25 continúan diferidas; no crear tareas de implementación anticipada para satisfacer cobertura global de la épica.

## Cobertura del Refinamiento S4

| Requisitos / decisión | Tareas | Evidencia |
|---|---|---|
| FR-011/012; HU-20 | T059/060/064–073 | Snapshots, compensaciones, cambios de cuenta/periodo y solo nota |
| FR-013/015/026; conflictos/sync | T060/067/068/072/077 | Revisión esperada, replay, reordenamiento y no resurrección |
| FR-014/030/031; P5 | T059/060/066/067/071/074–078 | VOIDED, rollback y rechazo sin DELETE ni doble compensación |
| FR-016–018/032; HU-22/P3 | T061/062/079–084/086 | Free, autorización Premium, cursor y caducidad integrada |
| FR-029; P2 | T060/062/064/069/073/074/078 | Advertencia especializada sin mutación genérica |
| FR-004–009/019/027/028; regresión | T064/068/071/077/084/085/087 | Saldos enteros, persistencia, Free, privacidad y no duplicación |
| SC-002–005/007–013 | T064/069/074/077/079/084–088 | Dominio, Room, RPC, rendimiento y aceptación |
| P1/P4; alcance y baseline | T058/063/088/089 | main aceptado, 31 puntos, demo sin HU-25 |

SC-001 conserva los escenarios oficiales por sprint; SC-006 mantiene el alta histórica y su regresión. FR-020–025 y SC-009 para reembolsos se trazan a US7/US8 del backlog S6/S7, no a tareas ejecutables S4. Phase 7 (T058–T063) completada el 2026-10-02; revisión de diseño y analyze en review-record.md. Próxima tarea T064. Ausencia de STALE y cierre documental no equivalen a implementación o DoD.


## Phase 13: Convergence

**Evaluación**: 2026-10-02, estado actual de código frente a spec/plan/tasks y constitución v2.0.0; lectura estática, sin ejecutar suites ni consultar un entorno desplegado. Fuente de intención: los artefactos aprobados de EP-MOV. Se revisaron 32 FR, 13 SC y 42 escenarios AC (34 en US1–US6, 8 diferidos US7/US8), 10 decisiones técnicas (dominio, atomicidad, persistencia/migración, idempotencia, causalidad/pull, conflictos, periodos, acceso, UI y verificación), y los 9 principios constitucionales según su aplicabilidad. FR-020–025, los 8 AC de US7/US8 y la porción de reembolsos de SC-009 siguen S6/S7. No se añade implementación de esos módulos.

**Resultado**: `tasks_appended`; 13 hallazgos agrupados: 4 `contradicts`, 3 `missing`, 6 `partial`, 0 `unrequested`; severidades 4 CRITICAL y 9 HIGH. Las tareas siguientes son remediaciones/verificaciones trazables del trabajo pendiente y enlazan tareas existentes; no representan una segunda implementación del mismo flujo. T001–T089 y sus marcadores se conservan. No interpretar los checks históricos o el diseño T058–T063 como evidencia runtime S4.

**Orden**: atender primero los críticos respetando prerrequisitos reales; esta sección no exige esperar al cierre T089 para comenzar. T091/T092/T093 pueden comenzar con el gate documental T063 cerrado. T090 se coordina con los modelos/compensaciones T065/T075/T076. Las demás verificaciones se cierran al completar el trabajo enlazado. No introducir dependencias inversas desde T064–T089 que generen ciclos. Serializar tareas que comparten workers/DAO/pruebas. T084 sigue bloqueada por el productor real EP-PLA.

- [x] T090 CRITICAL [F01] Sustituir el borrado financiero en `app/src/main/java/com/kipu/app/feature/accounts/data/sync/SyncInstrumentCommandsWorker.kt:rejectCreditCommand`, `feature/movements/data/local/MovementDao.kt:deleteLedgerEntriesForTransaction` y DAOs de cuotas/asignaciones por VOIDED y compensación idempotente de efectos realmente aplicados; preservar originales, relaciones y pasivo/saldos. Completar T076 con pruebas de compra/pago rechazados y replay en `app/src/androidTest/java/com/kipu/app/feature/accounts/FinancialRejectionCompensationTest.kt`; coordinar T065/T075 y no habilitar mantenimiento genérico de operaciones especializadas, per FR-030, US5/AC7, Constitution I/VI (contradicts).
- [x] T091 CRITICAL [F02] Hacer que todo fallo de página o snapshot aborte realmente la transacción Room en `app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorker.kt:pullChanges` y sus aplicadores; `return@withTransaction false` y excepciones absorbidas permiten commit de escrituras previas. Verificar ausencia de cambios en filas, ledger, proyecciones y checkpoint tras entidad desconocida, gap de secuencia, snapshot inválido y fallo al final; ejecutar/ampliar `unknownChangeRollsBackWholePageAndDoesNotAdvanceCursor` en `app/src/androidTest/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorkerTest.kt`. Integrar con T068/T085; no confundir Retry con rollback, per FR-006/031, SC-003, plan: pull/checkpoint atómico, Constitution II (contradicts).
- [x] T092 CRITICAL [F03] Recuperar comandos `IN_FLIGHT` con lease vencido en `app/src/main/java/com/kipu/app/feature/movements/data/local/MovementDao.kt:claimPendingOutbox` y `data/sync/SyncMovementsWorker.kt`; hoy solo se seleccionan PENDING/RETRY y el worker marca IN_FLIGHT antes de invocar API, dejando una fila abandonada tras muerte del proceso. Probar reclamación atómica/exclusión entre workers, cierre antes/después de commit remoto, misma clave/hash y un ?nico efecto en `app/src/androidTest/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorkerTest.kt`; conservar outbox histórica y completar recuperación en T068, per FR-007/009/026, US1/AC5, US2/AC3, SC-008, Constitution II (contradicts).
- [x] T093 CRITICAL [F04] Eliminar la ambigüedad de serialización de nuevos comandos en `app/src/main/java/com/kipu/app/feature/movements/domain/TransactionRequestHasher.kt` y contratos de hash remoto correspondientes: los pares (merchantProvisionalText=`a;note:b`, note=`c`) y (merchantProvisionalText=`a`, note=`b;note:c`) generan la misma cadena actual con contenido diferente. Añadir vectores de delimitadores, null/vacío y Unicode a `app/src/test/java/com/kipu/app/feature/movements/domain/TransactionIdempotencyTest.kt` y pruebas remotas bajo `supabase/tests/database/`; preservar bytes/hash de outbox S2 ya pendiente y compatibilidad mediante versionado, sin reinterpretar comandos antiguos. Coordinar contrato S4 T060/T064/T067 y registrar cualquier evolución del contrato de alta mediante el workflow de refinamiento antes de implementarla, per FR-008/009/019, US3/AC1, plan: identidad y hash canónico, Constitution II (contradicts). Contrato actualizado: contracts/register-transaction-v2.md; incluir dispatch v1/v2 y prueba de preservación de colas históricas.
- [x] T094 HIGH [F05] Completar y verificar modelos/puertos y migración no destructiva de T064–T066 en `app/src/main/java/com/kipu/app/feature/movements/domain/model/MovementModels.kt`, `MovementRevisionModels.kt`, `domain/MovementRepository.kt`, `data/local/MovementEntities.kt`, `core/database/RoomMigrations.kt`, `KipuDatabase.kt` y `app/schemas/`: la versión actual es 16, el enum solo ACTIVE/FAILED y faltan revisiones, asignación oficial separada, propuestas, aliases y recibos/outbox S4. Probar upgrade con comandos pendientes y FAILED histórico sin fabricar compensaciones de asientos borrados, per FR-011/031, SC-008/012, plan: modelo y migración S4 (missing).
- [x] T095 HIGH [F06] Completar T069–T075 y comprobar casos de uso `ReviseTransaction.kt`/`VoidTransaction.kt` y commit local revisión+efectos+proyecciones+recibo+outbox en `app/src/main/java/com/kipu/app/feature/movements/domain/`, `data/local/MovementLocalDataSource.kt` y `data/OfflineFirstMovementRepository.kt`; actualmente MovementRepository solo expone alta/lectura/similitud. Verificar estándar, referencias históricas, revisión esperada, nota sin asientos, transferencia completa, cancelación sin escritura y replay, per FR-011–015/029/031, US4/AC1–7, US5/AC1–6 (missing).
- [x] T096 HIGH [F07] Completar T067/T077/T085 mediante nueva migración bajo `supabase/migrations/` y `supabase/tests/database/movements_revision_test.sql`: las tablas remotas incluyen estados/revisiones base, pero no se encontraron RPCs `revise_transaction_v1`/`void_transaction_v1`. Verificar recibo antes de revisión, locking/carreras, ownership de todas las referencias, grants mínimos, RLS, evidencia append-only y emisión atómica de cambios; no asumir que una política histórica FOR ALL prueba permisos actuales ni modificar migraciones aplicadas, per FR-013–015/030/031, SC-002/003/005/012, plan: RPC S4 (partial).
- [x] T097 HIGH [F08] Completar T068/T072/T077 en `app/src/main/java/com/kipu/app/feature/movements/data/MovementOutboxPayloadFactory.kt`, `data/remote/MovementDtos.kt`, `MovementApi.kt` y `data/sync/SyncMovementsWorker.kt`: API solo registra altas, pull no conserva head/revisión de movimientos y `reconcilePulledLedgerEntries` compara el conjunto completo existente. Incorporar causalidad padre/hijo, revisión monotónica, aliases/deltas ?nicos, VOIDED sin resurrección y propuestas persistentes con descarte/rehacer y compensación de optimismo. Probar cadena offline, conflicto padre y replay sin duplicar original, per FR-013/015/026/031, US4/AC3/6, US5/AC4/5, plan: reconciliación S4 (partial).
- [x] T098 HIGH [F09] Completar consulta interna de consumo de T069/T071 en `app/src/main/java/com/kipu/app/feature/movements/data/local/MovementDao.kt`, `MovementLocalDataSource.kt` y `data/OfflineFirstMovementRepository.kt`; no existe cálculo por periodo sobre payload vigente. Verificar propietario/moneda/intervalos/zona explícitos, ID ?nico, jerarquía sin doble conteo y exclusión VOIDED/transferencias/pagos: gasto 20 de A→B deja A=0/B=20 y caja igual, anular A=B=0 y rollback conserva originales. Sin implementar administración presupuestaria ni reembolso, per FR-012, US4/AC2/7, plan: ExpenseConsumptionQuery (missing).
- [x] T099 HIGH [F10] Completar T079–T082 en `app/src/main/java/com/kipu/app/feature/movements/domain/QueryMovementHistory.kt`, `domain/model/MovementHistoryQuery.kt`, `data/local/MovementDao.kt` y `presentation/MovementHistoryViewModel.kt`; hoy se cargan todas las filas y se filtran en memoria por texto/tipo, sin rango de fechas ni cursor. Verificar SQL paginado por fecha/ID, combinación de criterios, moneda/rangos válidos, referencias archivadas, VOIDED e invalidación por cambios de criterios/propietario/dataset, per FR-016–018, US6/AC1–4/6, SC-007, plan: consultas S4 (partial).
- [x] T100 HIGH [F11] Integrar el consumidor protegido de T080/T082/T084 con el productor real HU-58/HU-59 en `app/src/main/java/com/kipu/app/feature/movements/domain/QueryMovementHistory.kt`, `presentation/MovementHistoryViewModel.kt` y `navigation/MovementsNavigation.kt`; `feature/plans/domain/FeatureAccessPolicy.kt` autoriza por verified=true y `EffectiveEntitlement` no contiene concesión/ancla. Verificar antes de DAO/enlaces/páginas/reanudación tiempo confiable <min(validación+72h, fin conocido), igualdad/reboot sin continuidad deniegan Premium, Free durable siempre; coordinar implementación EP-PLA sin fabricar un entitlement en EP-MOV. T084 y esta tarea no cierran con stubs, per FR-017/018/032, US6/AC4/5/7, SC-010/013, plan: frontera EP-PLA (partial).
- [x] T101 HIGH [F12] Completar T073/T078/T083 en `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementEditorViewModel.kt`, `MovementEditorSheet.kt`, `VoidMovementDialog.kt`, `MovementHistoryScreen.kt` y `navigation/MovementsNavigation.kt`: faltan editor/anulación/conflictos/filtros avanzados y navegación correspondiente. Probar resumen anterior/nuevo, advertencia especializada, cancelar sin escritura, fila anulada legible, limpiar/cero resultados/reconexión, TalkBack/48 dp/enmascaramiento en todas las superficies, per FR-011–018/029/032, US4/US5/US6, plan: Pantallas 10/11 (partial).
- [x] T102 HIGH [F13] Completar evidencia verificable de T064–T089 y remediaciones T090–T101 en `app/src/test/`, `app/src/androidTest/`, `supabase/tests/database/`, `specs/004-ep-mov-movimientos-ledger/quickstart.md` y `review-record.md`: faltan suites S4 de revisiones/migración/acceso y `MovementHistoryPerformanceTest.kt` usa ViewModel mock con umbral de render 5 s, sin medir consultas locales p95 <300 ms. Registrar pruebas de rollback/replay/migración/RLS/reloj/offline/regresión, dispositivo/dataset, integración EP-PLA real, cross-review y demo sin HU-25; conservar el test de render como prueba distinta y no reutilizar aprobación S2/diseño como PASS S4. Depende del trabajo financiero/query/UI e integración real T084; no declarar Sprint cerrado mientras haya bloqueantes, per SC-001–013 según alcance S4, plan: Fase 5/DoD, Constitution IX (partial).

## Phase 14: Approved S4 UI/UX refinement (2026-10-03)

**Propagated**: 2026-10-03 — FR-033–FR-039 / SC-014–SC-017; historical tasks stay intact.

- [X] T103 Refine approved S4 UX decisions, consumer contract, plan and UI state model; preserve ledger/schema and prior task history (FR-033–039).
- [X] T104 Add meaningful draft validation/cancel/apply, owner-switch, read-error and privacy-semantic coverage in `app/src/test/` and `app/src/androidTest/` (SC-014–017; depends on T103).
- [X] T105 Implement owner-scoped read-only detail with existing revision history and explicit actions, including VOIDED, in `MovementHistoryViewModel.kt`, `MovementDetailSheet.kt` and repository read boundaries (FR-033; depends on T103).
- [X] T106 Implement draft filters, date/account/category/card/merchant/financial-state/sync-status controls, currency/decimal amount validation, applied/parked chips and individual removal in `MovementHistoryViewModel.kt` and `MovementFiltersSheet.kt` (FR-034; depends on T104).
- [X] T107 Protect editor/conflict/void amounts and semantics; implement changed-field Before/After comparison and consistent theme tokens in `MovementEditorSheet.kt` and `VoidMovementDialog.kt` (FR-035/037; depends on T104).
- [X] T108 Polish history rows, separate financial/sync states, empty/error actions and accessible motion in `MovementHistoryScreen.kt` (FR-036/038/039; depends on T105–T107 and EP-PLA T116).
- [X] T109 Run applicable JVM regressions and AndroidTest compilation, inspect privacy/access/motion behavior, and record device limitations plus analyze/refine status in `quickstart.md` and `review-record.md` (SC-014–017; depends on T108 and EP-PLA T117). Runtime acceptance remains pending until actual device evidence.

Dependency DAG: T103 → T104; T103 → T105; T104 → T106/T107; T105/T106/T107 + EP-PLA T116 → T108; T108 + EP-PLA T117 → T109. No completed historical checkbox is reused as evidence of this refinement.


- [ ] T110 [Baseline HU-22] Resolve canonical movement-source provenance with Obsidian Mind and the existing data authorities; refine/propagate the Room/domain/query contract and implement the source selector with authorization/combination tests, preserving unknown historical provenance (FR-017; full HU-22 acceptance remains open; requires a confirmed data contract before implementation).

**Propagated**: 2026-10-03 — T103–T109 completed for the approved UI refinement. T110 is a pre-existing functional gap uncovered by cross-artifact review. EP-PLA T112/T113 retain their baseline/release gates. No statement here closes Sprint 4 as a whole.

## Issue #19 — Corrección visual reportada en dispositivo (2026-10-06)

- [x] T111 Corregir separación vertical entre filas de chips y controles avanzados en `MovementFiltersSheet.kt`, con márgenes coherentes en cabecera, pie y búsqueda (FR-034/039; criterios 1/7 de #19).
- [x] T112 Corregir margen superior del calendario y ancho/alineación de inicio y fin, conservando rango y alternancia de entrada manual/calendario en `MovementFiltersSheet.kt` y strings específicos (FR-034; criterio 7 de #19).
- [x] T113 Agrupar título/descripción Premium en tarjeta informativa no clicable con acción explícita, preservando recuperación y bloqueo actual (FR-034/039; criterio 1 de #19).
- [x] T114 Ejecutar build y regresión aplicable, actualizar la copia física y registrar evidencia/límites de T111–T113 en `review-record.md`; no declarar completo el resto del issue (depende de T111–T113).

## Issue #19 — Alcance restante autorizado (2026-10-06)

El responsable acepta el resumen/retirada de filtros aplicados y el comportamiento actual de «Limpiar» (puntos 3 y 4 de la lista de pendientes conversacional). No modificar `MovementAppliedFilters.kt` ni esas interacciones. T110 sigue fuera de este issue.

- [x] T115 Ubicar errores existentes de inicio/fin, moneda y mínimo/máximo junto a sus controles y actualizar mensajes al corregir el borrador, sin cambiar `MovementFilterDraft.validate` ni firmas públicas (FR-034; criterio 6).
- [x] T116 Mejorar el selector de referencias con lista perezosa acotada, búsqueda, resumen por nombres y retirada de referencias históricas ausentes del catálogo visible, conservando IDs y selección al buscar (FR-034; criterios 2/3).
- [x] T117 Verificar T115/T116 en JVM y dispositivo mediante fixtures sintéticos: listas extensas, cancelación, restauración, cambio de propietario/acceso, privacidad, teclado, pantalla reducida y temas; adjuntar comparación visual y registrar comandos/resultados reales (criterios 4/7/8).
- [x] T118 Realizar commits del alcance, subir `feat/movimientos-filtros-ux` y abrir PR con `Closes #19`, sin merge; excluir carpeta local `Borrar-NoSubir` y configuración privada (depende de T117). Entrega vigente autorizada tras la limpieza: [PR #31](https://github.com/LeonidasGarcia/Kipu/pull/31); PR #30 cerrado sin merge.
