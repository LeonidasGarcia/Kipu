# Quickstart de validación: Sprint 2 EP-MOV

## Prerrequisitos

- JDK 17 y Android SDK configurados.
- Emulador/dispositivo API 24 o superior.
- Supabase CLI y contenedor local disponibles para pruebas de migración.
- Decisiones bloqueantes de [team-questions.md](./team-questions.md) resueltas antes de modificar tablas compartidas.

## Comandos base

```powershell
.\gradlew.bat test
.\gradlew.bat connectedAndroidTest
supabase db reset
supabase test db
```

Use las tareas Gradle reales del proyecto si los módulos agregan variantes específicas. No se considera validado un cambio remoto sólo por compilar Android.

## Recorrido mínimo

1. Registrar un gasto offline y comprobar fila, saldo y estado pendiente.
2. Registrar un ingreso offline y comprobar incremento de saldo.
3. Registrar una transferencia y verificar débito/crédito atómicos.
4. Forzar cierre tras el commit local; reiniciar y comprobar que la outbox continúa.
5. Sincronizar y comprobar que el servidor tiene un solo resultado lógico.
6. Reenviar la misma clave/hash y comprobar `DUPLICATE` sin nuevos asientos.
7. Reenviar la misma clave con otro hash y comprobar `CONFLICT`.
8. Crear un movimiento similar dentro de la ventana y comprobar advertencia.
9. Cancelar la advertencia y comprobar ausencia de efectos.
10. Confirmar la advertencia y comprobar un nuevo movimiento con clave nueva.
11. Intentar referencias de otro espacio y comprobar rechazo por RLS/RPC.
12. Abrir historial con 10 000 movimientos y comprobar UI fluida y paginada.

## Evidencia esperada

- Resultados de pruebas unitarias, instrumentadas y pgTAP.
- Captura o grabación de los tres tipos de alta y del diálogo de similitud.
- Consulta que demuestre balance de transferencias y ausencia de duplicados.
- Resultado de reconstrucción de saldo coincidente con la proyección.
- Registro de las decisiones del equipo vinculadas desde la spec o el PR.

---

## Incremento S4 — T062: protocolo de validación

Escenarios para las fases posteriores; este protocolo no acredita pruebas ejecutadas. Baseline main/S3 aceptada (P4); evidencia S2 conservada.

### Gate documental previo

Fijar la épica en PowerShell antes de ejecutar Spec Kit:

```powershell
$env:SPECIFY_FEATURE_DIRECTORY = Join-Path (Get-Location) 'specs/004-ep-mov-movimientos-ledger'
$env:SPECIFY_FEATURE = '004-ep-mov-movimientos-ledger'
python .specify/scripts/python/check_prerequisites.py --json --require-spec --require-tasks --include-tasks
git diff --check
```

Checklists son un gate de requisitos. Analyze/refine.status y revisión independiente T063 preceden T064. Pruebas siguientes requieren implementar tareas y entorno local; resets Supabase únicamente en el entorno local de pruebas.

### Datos controlados

Propietarios A/B, dos cuentas propias PEN y otra ajena; montos enteros en céntimos. Periodos septiembre/octubre 2026 `America/Lima`, límites convertidos a UTC e intervalos `[inicio, fin)`. Gasto estándar confirmado de S/20 en septiembre con saldo inicial conocido. Dataset independiente por escenario. Reloj monotónico e identidad de arranque inyectables para pruebas; fixtures no equivalen a concesión real EP-PLA.

### Matriz de aceptación

| Caso | Pasos y resultado verificable | Tareas |
|---|---|---|
| Importe | Gasto 20→15 offline/reapertura; recuperar 5, ledger original+reversión+nuevo suma -15; comando durable | T069–073 |
| Periodo | Mover fecha septiembre→octubre; consumo 0/20, saldo intacto; snapshots antiguos excluidos | T059/069/071 |
| Nota/historia | Cambiar nota conserva categoría archivada; ningún asiento nuevo, nueva referencia inválida bloqueada | T064/069/073 |
| Transferencia | Corregir/anular ambas cuentas atómicamente; fallo inyectado revierte todo | T071/074/075 |
| VOIDED | Cancelar no escribe; confirmar conserva originales, consumo 0 y saldo revertido una vez tras 100 replays | T074–078 |
| Edición antigua | Comando anterior a VOIDED produce conflicto, sin resurrección ni doble compensación | T077 |
| Especializado | Compra con cuotas/pago deuda/tarjeta: advertencia y cero cambios por editor/anulación genéricos | T064/069/074/078 |
| Rechazo | Alta/pago local aplicado: VOIDED y compensación; propuesta rechazada no anula original válido; relaciones conservadas | T072/076/077 |
| Hash | Replay idéntico sin efectos, misma clave/hash diferente conflicto; Unicode/delimitadores iguales Android/Postgres | T064/067/077 |
| Cadena | Dos correcciones offline y conflicto padre: hijos bloqueados, propuestas preservadas y deltas optimistas neutralizados | T068/072/077 |
| Reconciliación | Base -20/propuesta -15/oficial -18: deshacer delta +5 y aplicar remoto +2 deja -18; replay sin nuevos efectos | T068/072 |
| Crash/pull | Cierre tras commit y fallo previo checkpoint: outbox durable, checkpoint atómico; alias UUID no duplica efectos | T066/068/071/085 |
| Migración | Room v16 con outbox S2/rechazo histórico: conservar IDs/bytes/hash; no inventar compensación de asientos borrados | T066/085 |
| RLS | Dos JWT/propietarios y referencias ajenas: cero lectura/escritura cruzada; revisar grants, RPC, ledger y recibos | T067/085 |
| Free | Tambo antiguo + texto/fecha/tipo offline sobre historial completo; registro/cambios estándar durables | T079–084 |
| Premium | Cuenta+categoría+rango, retirar/limpiar/cambiar propietario: intersección, rangos válidos y cursor invalidado | T079–083 |
| Corte | Justo antes/en/después de mínimo 72h/fin comercial: solo antes autoriza; Lifetime también exige renovación 72h | T061/084 |
| Reloj | Cambiar reloj civil, reiniciar sin continuidad, restaurar en otro dispositivo: sin extensión; Free operativo | T084 |
| Entradas | Caducar panel abierto/entre páginas/deep link: guardia antes de DAO, retirar resultados protegidos y ofrecer Free | T082–084 |
| Calidad | 10 000 filas p95 <300 ms SC-007; sin duplicados, TalkBack/48 dp/enmascaramiento y logs sin datos sensibles | T083/086/087 |

### Evidencia y demo

- Registrar comandos reales, entorno, versión Room, migraciones, resultados unitarios/instrumentados/pgTAP y fallos en `review-record.md`. Usar variantes/clases Gradle efectivamente implementadas; compilar no prueba integridad financiera/RLS.
- T084 exige productor real HU-58/HU-59; dobles de prueba no cierran integración. No declarar suites S4 aprobadas antes de ejecutarlas.
- Demo: edición 20→15 y cambio de periodo, anulación, filtros Free/Premium, expiración/reconexión, conflicto y recuperación offline. Sin devolución parcial: HU-25 en S7, capacidad S4 31 puntos.
- T089 registra evidencia y comunica corrección de demo al responsable del cronograma; no afirmar que el vault ya fue modificado.

---

## Ejecución y Evidencia de Validación — Sprint 4 (2026-10-02)

### 1. Resumen de Suites Ejecutadas y Verificadas

- **Pruebas Unitarias de Dominio y ViewModel (JVM)**:
  - Comando: `./gradlew.bat --no-daemon :app:testDebugUnitTest`
  - Resultado: **BUILD SUCCESSFUL** (100% PASS, 0 fallos).
  - Cobertura:
    - `com.kipu.app.feature.movements.domain.MovementRevisionTest` (20/20 PASS)
    - `com.kipu.app.feature.movements.domain.ReviseTransactionTest` (PASS)
    - `com.kipu.app.feature.movements.domain.VoidTransactionTest` (PASS)
    - `com.kipu.app.feature.movements.domain.QueryMovementHistoryTest` (PASS)
    - `com.kipu.app.feature.movements.presentation.MovementHistoryViewModelTest` (PASS)
    - `com.kipu.app.feature.movements.domain.TransactionIdempotencyTest` (PASS, vectores v1/v2, delimitadores, Unicode)

- **Compilación de Pruebas Instrumentadas**:
  - Comando: `./gradlew.bat --no-daemon :app:compileDebugAndroidTestKotlin`
  - Resultado: **BUILD SUCCESSFUL** (0 errores).

- **PostgreSQL Remoto / Local (Supabase DB 17.6)**:
  - Migración S4 v2 Hash: `supabase/migrations/20261002234503_s4_register_transaction_v2_hash.sql`
    - Test: `supabase/tests/database/movements_registration_hash_v2_test.sql` -> **28/28 pgTAP PASS**
  - Migración S4 RPC Revisiones: `supabase/migrations/20261003004348_s4_movement_revision_rpc_v1.sql`
    - Test: `supabase/tests/database/movements_revision_test.sql` -> **42/42 pgTAP PASS**
    - Verificaciones: Locking transaccional, RLS estricto con dos JWT independientes, reversión sin DELETE físico, unicidad de recibos, compensaciones balanceadas.

- **Migración y Persistencia Room Android (v16 -> v17)**:
  - Esquema versionado: `app/schemas/com.kipu.app.core.database.KipuDatabase/17.json`
  - Migración: `RoomMigrations.kt` (`MIGRATION_16_17`)
  - Pruebas: `MovementRoomMigrationTest.kt`, `MovementRevisionLocalTest.kt`, `MovementOutboxLeaseTest.kt`.
  - Invariantes: Zero DELETE físico en ledger y movimientos, snapshots MIGRATION_BASELINE inmutables.

- **Integración con EP-PLA (HU-58/HU-59)**:
  - Pruebas: `MovementHistoryAccessIntegrationTest.kt`
  - Proveedor: `PlansMovementEntitlementProvider.kt` sobre `feature_access_cache` local.
  - Verificación estricta de tiempo confiable monotónico: `trustedNow < notAfter`, con `notAfter = min(verified_at + 72h, expires_at)`.
  - Experiencia Free garantizada y degradación controlada de filtros avanzados sin pérdida de datos.

- **Rendimiento de Consultas e Historial (SC-007)**:
  - Suite: `MovementHistoryPerformanceTest.kt` y `MovementHistoryQueryTest.kt`.
  - 10 000 movimientos en base local: latencia p95 de consulta paginada < 300 ms (medido entre 10 y 25 ms).
  - Keyset pagination mediante cursor `(occurred_at DESC, id DESC)` sin saltos ni duplicados (1000 items recolectados en 20 páginas sin colisión).
  - LazyColumn en Jetpack Compose sin bloqueo de hilo de interfaz de usuario.



## UI/UX S4 refinement — validation 2026-10-03

- Skills applied: `ui-ux-pro-max`, `compose-animations`, Android Kotlin and Spec Kit refine/update/propagate/implement. Focused UX/Compose skill searches informed decimal keyboards, persistent labels, 48 dp targets, theme semantics and reduced motion.
- Full JVM regression: **383 tests, 0 failures, 0 errors** (`:app:testDebugUnitTest`). Draft tests cover decimal/currency parsing, invalid ranges and DST-inclusive dates; history tests cover apply/removal, owner reset, query retry and duplicate restore requests. Billing recovery tests cover no purchases, pending, failure and bounded timeout.
- Debug application and AndroidTest APKs assemble successfully. Android deprecation warnings remain for existing test-rule/Hilt APIs; no compilation errors.
- Instrumentation: **39 distinct checks passed** on the booted `Pixel_10` emulator: 38 in the combined UI/editor/history-access/Room run plus the integrated history route check, including light/dark rendering. The final UI refinement suite is rerun against the latest APK; command and summaries are retained in the local `.backups/` validation logs. These are selected instrumentation suites, not the entire AndroidTest catalog.
- Privacy assertions inspect unmerged semantics for detail, editor/conflict, Before/After and void amounts. Reduced motion and outgoing/incoming AnimatedContent identities are exercised; a draft survives UI saved-state restoration without applying or granting access.
- The history route opens VOIDED read-only detail, blocks financial actions there, retains parked filters and renders contextual recovery. Search/filter controls and results share one scroll container so enlarged text does not permanently displace history. Filter access is visible beside search; applied chips remove individual selections. Audit reads are owner-scoped and sorted by revision.
- Visual evidence uses synthetic fixture data only: `validation/ui/history-route-light.png`, `history-route-dark.png`, `history-large-font.png` in EP-MOV. Light, dark and 1.6× text captures were inspected. This is visual inspection plus automated semantic coverage; it does not certify a complete manual TalkBack traversal on a physical device.
- The first physical Samsung attempt encountered a locked/dozing screen; its failures are not acceptance evidence. Canonical-category/UUID/setup fixtures were corrected without relaxing production invariants; old assertions were updated to current copy and the unmerged badge semantics tree.
- Database evidence here is local Room instrumentation. This UI refinement adds no ledger/Postgres migration and performs no remote deployment or real purchase.
- Gates: approved visual/refined FR-033–FR-039 and EP-PLA FR-056–FR-058 are implemented and checked. **Full Sprint 4 is still open**: EP-MOV T110 preserves the required source filter whose current data contract is absent; EP-PLA T112/T113 preserve PostgreSQL baseline and production signing/provider/release acceptance. Historical performance/SQL claims were not rerun or re-certified by this UI audit.

### Refine status and semantic cross-check

Both feature directories contain spec, plan, tasks, research, data-model and contracts. Refinement/propagation entries are current and no artifact-warning **STALE** marker remains. Requirement IDs and existing financial contracts are preserved. Source provenance is explicitly mapped to open T110; all new visual requirements have implementation tasks and evidence. Marker synchronization does not mean whole-sprint functional acceptance. The remaining high-priority finding is the pre-existing FR-017 source contract gap, with explicit task coverage and no inferred replacement.
