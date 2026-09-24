# Tasks: Sprint 2 — Movimientos y ledger

**Input**: documentos de diseño en `specs/004-ep-mov-movimientos-ledger/`

**Alcance**: sólo US1/HU18, US2/HU19 y US3/HU23. US4–US8 permanecen especificadas para sprints posteriores, pero no son tareas de esta semana.

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
- [ ] T015 [P] [US1] Probar tabs, campos condicionales y accesibilidad básica en `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/QuickMovementScreenTest.kt`
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
- [ ] T039 [P] [US3] Probar el diálogo de advertencia, cancelación y confirmación con identidad nueva en `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/DuplicateWarningDialogTest.kt`

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
- [ ] T047 [P] Medir y paginar historial de 10 000 filas sin bloquear UI en `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/MovementHistoryPerformanceTest.kt`
- [x] T048 [P] Completar semántica TalkBack, objetivos de 48 dp y representación no basada sólo en color en `app/src/main/java/com/kipu/app/feature/movements/presentation/QuickMovementScreen.kt` y `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryScreen.kt`
- [x] T049 Aplicar redacción de notas, referencias y errores de sincronización en `app/src/main/java/com/kipu/app/feature/movements/data/sync/SyncMovementsWorker.kt`
- [ ] T050 Ejecutar y documentar todos los escenarios de `specs/004-ep-mov-movimientos-ledger/quickstart.md`, incluidos cierre/reapertura, reintento y segundo dispositivo. La base remota actual no es compatible con los RPC del cliente.
- [x] T051 Marcar respuestas, evidencia y sólo los criterios de HU18/HU19/HU23 satisfechos en `specs/004-ep-mov-movimientos-ledger/team-questions.md` y `specs/004-ep-mov-movimientos-ledger/checklists/requirements.md`
- [ ] T052 [P] Probar backfill PostgreSQL desde `financial_movements`, conservación de saldos y datos representativos en `supabase/tests/database/movements_migration_test.sql`
- [x] T053 [P] Probar que la deduplicación permanece disponible en Free sin consumir cupos en `app/src/test/java/com/kipu/app/feature/movements/domain/FreeDeduplicationTest.kt`
- [x] T054 [P] Probar que logs y errores no exponen notas, tokens ni datos financieros sensibles en `app/src/test/java/com/kipu/app/core/logging/MovementLogRedactionTest.kt`
- [ ] T055 [P] Ejecutar 100 reintentos idénticos por operación y demostrar un solo efecto en `supabase/tests/database/movements_idempotency_stress_test.sql`
- [ ] T056 [P] Medir en dispositivo la finalización de un alta manual común en menos de 10 segundos con datos preparados en `app/src/androidTest/java/com/kipu/app/feature/movements/presentation/ManualEntryAcceptanceTest.kt`
- [ ] T057 Obtener cross-review documentado de finanzas, privacidad, seguridad, arquitectura y pruebas en `specs/004-ep-mov-movimientos-ledger/review-record.md` (revisores y evidencias pendientes).

---

## Deferred Backlog (no ejecutar esta semana)

| Historia | Contenido | Sprint previsto |
|---|---|---|
| US4 / HU20 | Edición auditable y conflictos de revisión | 4 |
| US5 / HU21 | Anulación lógica y contrapartidas | 4 |
| US6 / HU22 | Búsqueda y filtros avanzados | 4 |
| US7 / HU24 | Obligaciones, préstamos y metas | 6 |
| US8 / HU25 | Reembolsos y conciliación entre periodos | 7 |

## Dependencies & Execution Order

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

1. Resolver T001–T002 antes de editar migraciones o `KipuDatabase`.
2. Completar Foundation y hacer pasar migración/compilación.
3. Entregar US1 como MVP demostrable offline.
4. Completar US2 y repetir pruebas con fallos y reinicios.
5. Completar US3 y probar repeticiones concurrentes y compras similares legítimas.
6. Ejecutar Phase 6, realizar cross-review y adjuntar evidencia; no adelantar el backlog diferido.
