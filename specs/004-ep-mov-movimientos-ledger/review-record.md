# Registro de revisión cruzada EP-MOV

## Alcance

Revisión requerida antes de cerrar HU18, HU19 y HU23. Este archivo no afirma que la implementación esté aprobada; registra quién revisa y qué evidencia falta.

| Área | Revisor | Estado | Evidencia/enlace |
|---|---|---|---|
| Integridad financiera | Auditoría Finanzas / Core Team | [x] | Ledger balanceado verificado en `supabase/tests/database/movements_register_transaction_test.sql`, `movements_idempotency_stress_test.sql` y `MovementRoomMigrationTest.kt`. Verificado en dispositivo físico Samsung SM-S926B. |
| Privacidad y logs | Auditoría Seguridad / Compliance | [x] | Redacción estricta validada en `MovementLogRedactionTest.kt` y `SyncMovementsWorker.kt`. Logs no exponen notas ni tokens. |
| Seguridad/RPC | Auditoría Backend / Supabase | [x] | RLS verificado para referencias propias y aislamiento de espacios en `movements_register_transaction_test.sql`. Migraciones remotas 20260915 a 20260923 aplicadas en producción. |
| Arquitectura/offline-first | Auditoría Android Architecture | [x] | Commit atómico en Room, outbox con leases y sincronización idempotente. Restauración de sesión probada en cierre forzado/reinicio en Android 16. |
| Calidad/pruebas | QA / Test Automation | [x] | 228 pruebas unitarias JVM superadas, 119 pruebas instrumentadas en dispositivo SM-S926B, 19 pruebas de integración Deno en Edge Functions, smoke test manual en vivo con cuenta de prueba. |

## Criterio de cierre

Todas las áreas cuentan con revisión completada y evidencia documentada. HU18, HU19 y HU23 quedan aprobadas y validadas para el cierre de Sprint 1 y Sprint 2.

---

## Sprint 4 — T063: revisión de diseño (2026-10-02)

La evidencia anterior pertenece a S2 y se conserva. Este registro acredita el trabajo documental T058–T063, no pruebas runtime S4, migraciones aplicadas ni aceptación de HU-20/21/22/58/59. La revisión auxiliar independiente no sustituye los sign-offs humanos de PR/release ni T088.

### Revisión independiente

**Herramienta/revisor auxiliar**: Antigravity CLI `agy`, ejecutada con instrucción de solo lectura y sin tests; proceso finalizado con código 0. Se solicitó revisar constitución v2.0.0, spec/plan/tasks, modelo, contratos de revisión/sync/acceso/UI y quickstart. La CLI advirtió que `--mode plan` no tiene efecto con expansión de slash commands deshabilitada; se comprobó el conjunto de cambios después de la ejecución. No se le proporcionaron datos financieros reales.

**Dictamen recibido**: apto para cerrar como gate documental condicionado al registro de esta revisión y a mantener pendientes pruebas y revisión humana. Sin hallazgos críticos o altos. Se revisaron atomicidad, propuestas versus revisiones oficiales, compensaciones sin DELETE, recibos/hash, aliases de ledger, migración v16, periodos, RLS y concesión/reboot.

| Hallazgo AGY | Nivel | Tratamiento |
|---|---|---|
| H-01: falta registro S4 | Medio | Resuelto mediante esta sección; no reutilizar sign-offs S2 como aprobación S4 |
| H-02: riesgo de doble reversión | Informativo | Modelo exige suma completa del ledger; verificar implementación y pruebas T065/T071, sin filtrar originales por estado |
| H-03: no desbloquear integración con stub | Bajo / recordatorio | Contrato de acceso y T084 exigen productor real HU-58/HU-59; dobles solo de pruebas, nunca autorización release |
| H-04: FAILED legado con asientos borrados | Informativo | Modelo/matriz de migración exigen comprobar evidencia y sumas; prueba T066/T085 pendiente |
| H-05: SECURITY DEFINER y BYPASSRLS | Informativo | Contrato exige propietario explícito, search_path fijo y permisos mínimos; ejecución de pruebas T067/T085 pendiente |

### Specification Analysis Report — segunda pasada

Análisis semántico de solo lectura conforme a `speckit-analyze`; contexto explícito EP-MOV comprobado con el script de prerrequisitos. El registro se escribe después del análisis como evidencia de T063.

| Hallazgo previo | Estado | Evidencia de corrección |
|---|---|---|
| I1 HIGH: selección histórica EP-NOT | Resuelto | FEATURE_DIR resuelve a 004; `.specify/feature.json` selecciona EP-MOV; tasks exige entorno explícito por comando |
| U1 HIGH: consumo de periodos sin cálculo verificable | Resuelto | FR-012/HU-20 escenario 7, modelo ExpenseConsumptionQuery, plan y T059/T069/T071: A=0/B=20, rollback y VOIDED excluido |
| B1 MEDIUM: frontera 72h/reinicio ambiguos | Resuelto | FR-032/HU-22 escenario 7, contrato de acceso y T061/T084: tiempo confiable estrictamente anterior al límite; sin continuidad tras reboot exige revalidar |
| I2 LOW: paralelismo en prueba compartida | Resuelto | T074 sin [P], depende de T071; DAG serializa el archivo compartido |

**Resultado**: 0 nuevos conflictos críticos/altos entre spec, plan y tasks; sin contradicción constitucional de diseño detectada. No se identifican tareas sin historia/requisito asociado. Riesgos de implementación quedan cubiertos por tareas abiertas, no se consideran pruebas satisfechas.

| Requisitos | Tareas de cobertura | Alcance |
|---|---|---|
| FR-001–003/010; SC-006 | T012–026/036–044/056/087 | Alta S2 y regresión |
| FR-004–009/019/026–028; SC-002–005/008/010 | T027–057/064/068/071/077/084/085/087 | Integridad, offline, aislamiento, Free y privacidad |
| FR-011–015/029–031; SC-009/011/012 | T058–060/062–078/085/087/088 | Edición, anulación y conflictos S4; reembolso SC-009 diferido |
| FR-016–018/032; SC-007/013 | T061/062/079–084/086 | Consulta y guardias S4, integración EP-PLA pendiente |
| SC-001 | T050/058/063/087–089 | Escenarios por sprint; no anticipar los de HU-24/25 |
| FR-020–025 | Backlog US7/US8 | Diferidos a S6/S7, sin tareas ejecutables S4 |

**Métricas**: 32 FR +13 SC =45 requisitos; 89 tareas. Cobertura ejecutable de épica 39/45 (86.7%); cobertura del alcance activo/histórico 39/39 (100%). Seis FR diferidos tienen trazabilidad de backlog. Cobertura mide asociación con tareas, no implementación ni resultados de pruebas. Tras cerrar T063: 63 tareas completas (57 históricas y 6 de diseño S4), 26 pendientes.

### Gates y evidencia pendiente

| Gate | Estado |
|---|---|
| Contexto EP-MOV y artefactos requeridos | PASS documental; FEATURE_DIR 004 |
| Checklists requisitos/UX | 16/16 y 10/10; sin modificar marcadores |
| Preservación T001–T057 | Comprobación textual contra HEAD, descripciones y estados idénticos |
| Identidades y dependencias | T001–T089 únicos, DAG S4 sin ciclos; T071→T074 serial |
| Refine status | Sin marcadores STALE; refinamiento y propagación 2026-10-02; derivados presentes y reconciliados semánticamente |
| Integridad/RLS/offline/Free | PASS de diseño por contratos; sin declarar PASS runtime |
| Código, migraciones y suites S4 | Pendientes T064–T087 |
| Productor HU-58/HU-59 | Pendiente externo; bloquea aceptación integrada T084 |
| Cross-review humano y aceptación HU | Pendientes T088/T089 |

Validaciones realizadas: script de prerrequisitos Spec Kit con épica explícita, revisión de inventario/estados/checklists y preservación S2, análisis de dependencias y `git diff --check`. No se ejecutaron Gradle, instrumentación, pgTAP ni migraciones en esta fase documental. Hooks git commit antes/después de implement/analyze son opcionales y se omiten; no hubo commit ni push.

**Conclusión de T063**: gate documental cerrado; próxima tarea ejecutable T064 (pruebas de dominio/contrato antes de implementar). El cierre de Fase 7 no representa cierre del Sprint 4.


## Implementación ? T091/T092 (2026-10-02)

- T091: un fallo de aplicador, secuencia o final de página lanza una excepción dentro de la transacción externa Room. La captura ocurre después de rollback; no quedan filas, ledger, proyecciones ni checkpoint parciales. Cancelación se propaga.
- T092: reclamación transaccional de PENDING/RETRY y de IN_FLIGHT con lease vencido (incluida igualdad). Cada envío reclama una sola fila justo antes de la llamada; confirma outbox y sync_status en una transacción y verifica identidad del lease para rechazar respuestas tardías. La cancelación de Worker/API no se transforma en PARSE_ERROR ni incrementa intentos; el lease permite recuperación.
- Pruebas nuevas: rollback por entidad desconocida, snapshot inválido, gap y final incorrecto; reclamación concurrente, lease vivo/vencido, owner/estado/backoff; replay remoto DUPLICATE manteniendo clave/hash/payload y un ?nico asiento local; respuesta tardía descartada; cancelación recuperable; fallo inyectado en escritura de sync_status revierte confirmación completa.
- Replay remoto usa API mock y Room real: acredita recuperación y no duplicación local; no acredita RPC/RLS desplegados. Pruebas PostgreSQL e integración real S4 permanecen abiertas.
- Antes de la corrección se reprodujeron fallos de rollback y reclamación. Se corrigió además el orden de fixture de tarjeta (cuenta antes de FK) y el timeout de observación Room (IO real, mismo límite de cinco segundos).
- Comando: `./gradlew.bat -PisolatedAndroidTests=true :app:connectedLabAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.feature.movements.data.sync.SyncMovementsWorkerTest,com.kipu.app.feature.movements.data.local.MovementOutboxLeaseTest,com.kipu.app.feature.movements.data.local.MovementLocalDataSourceTest`.
- Resultado final: **27/27 PASS**, BUILD SUCCESSFUL, 1m25s. Dispositivo: Pixel_10 AVD, Android 17, API 37.2, x86_64, 4GB RAM; variante lab aislada. El primer arranque con 2GB terminó por lowmemorykiller (cero pruebas); no se contó como fallo funcional.
- Cross-review AGY de solo lectura: confirmó T091/reclamación/replay y detectó absorción de cancelación y confirmación no atómica; ambas corregidas y cubiertas por pruebas. Observaciones ARCHIVE ausente/página vacía con salto presuponen compactación del backend no demostrada; no se cambió el contrato de secuencia continua ni se avanzó checkpoint sin evidencia. Se revisarán con T068. No representa aprobación de release T088.
- T091/T092 cerradas; T068/T085/T087 conservan sus verificaciones ampliadas. T090/T093 y el resto de implementación S4 siguen abiertos. No commit/push.


## Implementación — hash v2 y dominio S4 (2026-10-02)

- Refinamiento T093/F04 realizado con refine.update → refine.propagate: FR-008 precisa serialización inequívoca y compatibilidad de cola histórica. Se añadió contracts/register-transaction-v2.md; plan/tasks/model/research/sync reflejan el cambio; sin regenerar contenido S2.
- Altas nuevas: hash de array JSON compacto UTF-8, null distinto de vacío, textos sin trim y dispatch por contract_version. Versión 1 existente se envía a RPC v1 con clave/hash/contenido originales. Nuevas altas estándar usan v2; no modifica RPC de crédito S3 ni migraciones aplicadas.
- Nueva migración creada por CLI instalada: 20261002234503_s4_register_transaction_v2_hash.sql; wrapper valida identidad/auth, tipos/campos, milisegundos exactos y hash derivado de propietario de servidor antes de delegar atómicamente. Helper interno sin grants a cliente, RPC solo authenticated.
- TDD hash: 7 pruebas iniciales, 3 fallos reproducidos por colisión/null/golden vector; después de corrección tests HTTP/hasher/factory y regresión de movimientos pasan.
- PostgreSQL local 17.6: 22/22 pgTAP PASS para RPC v2, golden Kotlin/Postgres, delimitadores, null/vacío, Unicode, offset, hash alterado, campos no firmados, submilisegundos, auth/grants, replay/conflicto y un único ledger/recibo, compatibilidad v1. Harness ejecutó dentro de BEGIN/ROLLBACK la base local + migraciones prerrequisito card_style/pull_projection + migración nueva; ningún cambio persistido ni despliegue remoto.
- Límite explícito: el ensayo de todas las migraciones pendientes de main abortó por función internal.trg_emit_sync_change() ausente (hardening S3). No se fabricó función ni se omitió hardening para declarar baseline completa PASS. Se solicitó fuente de la base auditada; T067/T085/T087 requieren resolver esa reproducibilidad. CLI migration up --local tampoco accedió al puerto 54322: contenedor existente sin binding; harness usó docker exec psql.
- T064/T065: pruebas escritas antes de modelos (compilación falló inicialmente por tipos nuevos ausentes). Se añadieron modelos/puertos de mantenimiento/consumo y planificador puro; no realiza persistencia. Revisión oficial y propuesta se distinguen; snapshots/identidad lógica de ledger son explícitos. Estándar, owner, expectedRevision, naturaleza/moneda, referencias históricas/nuevas, ledger comprobable, transferencia, VOIDED y overflow se validan antes de un plan. Los casos de uso y commits atómicos se implementan en tareas posteriores.
- Comando unitario: `./gradlew.bat -PisolatedAndroidTests=true :app:testLabUnitTest --tests=com.kipu.app.feature.movements.*`; resultado **58/58 PASS**, última ejecución BUILD SUCCESSFUL en 20s, incluyendo 19 pruebas MovementRevisionTest y golden hash VOID. El vector VOID queda documentado para T067; no es ejecución remota de revisiones.
- Cross-review AGY de T093 solicitado; aún sin resultado (timeout inicial). T093 sigue abierta hasta revisar hallazgos. Foundation de Room/T066, revisiones RPC y UI siguen pendientes. No se declara DoD.


### Recuperación de baseline local (2026-10-02)

El usuario aportó la definición canónica de internal.trg_emit_sync_change y autorizó crearla localmente. Se creó desde supabase/local-baseline/trg_emit_sync_change.sql, sin nuevos triggers ni permisos cliente. La siguiente pasada detectó diez rutinas y tablas de recurrencia ausentes. El MCP Supabase permitió recuperar sus definiciones/estructura directamente de pg_proc/pg_attribute/pg_constraint en Kipu mediante SELECT de solo lectura. No se consultaron datos financieros ni se modificó producción.

Archivos local-baseline registran únicamente prerrequisitos estructurales ya existentes en remoto; no activan funciones futuras de app, integración de compras, alertas o recurrencias ni acreditan HU diferidas. Las rutinas copian código canónico pero no otorgan permisos cliente; las tablas vacías tienen RLS habilitada y sin grants. Los contratos nuevos siguen sus validaciones propias, sin convertir helpers antiguos en autoridad de Premium offline.

Con esos prerrequisitos, la validación transaccional de **todas las migraciones pendientes de main + v2** terminó correctamente: **22/22 pgTAP PASS**, incluido el hardening S3. El harness sigue BEGIN/ROLLBACK; no modifica history ni despliega v2. La ausencia anterior de baseline se resolvió para este ensayo; aún se requieren suites completas T085/T087. T064/T065 cerradas por modelos/puertos y 58/58 pruebas unitarias; no incluye persistencia ni casos de uso T070/T075.

## Continuacion de implementacion - baseline y Room v17 (2026-10-02)

- El archivo canonico supabase/sprint_3_remote_baseline_routines.sql se aplico a supabase_db_kipu mediante psql --single-transaction; PostgreSQL acepto las diez funciones. Una consulta local posterior enumero diez rutinas coincidentes. No hubo escrituras remotas.
- Room v16->v17 anade snapshots de revision, asignacion oficial separada, identidades de efectos/aliases, propuestas y metadatos versionados de recibos/outbox. La migracion crea snapshot MIGRATION_BASELINE desde cada movimiento existente, conserva ledger/saldo/payload outbox/estado FAILED y deja sin fabricar acknowledged_revision, source, command_id o compensaciones.
- Los modelos permiten representar esa baseline como revision local 1 con officialRevision=null y commandId=null; la planificacion no la confunde con un recibo del servidor.
- Los guardas append-only y de propietario se crean tanto en v16->v17 como en MovementSchemaCallback para bases nuevas. app/schemas/.../17.json se genero desde Room; los DAOs incluyen insercion y consulta con claves de propietario.
- Verificacion: MovementRevisionTest 20/20 PASS; MovementRoomMigrationTest 5/5 PASS en Pixel_10 API 37.2. Se comprobo upgrade v16 con outbox IN_FLIGHT y asiento intactos, historia FAILED intacta, baseline sin asignacion oficial, unicidad/owner de asignaciones y aliases, triggers y apertura fresca.
- Dos ejecuciones combinadas mas amplias alcanzaron error de transporte/reset ADB del runner; su informe parcial no se usa como gate. La clase Room se repitio aislada y termino BUILD SUCCESSFUL.
- T066 y la reconciliacion T094 quedan cerradas. T067/T068 y casos de uso, UI, consumo y queries permanecen abiertos. No commit/push.

---

## Cierre de Sprint 4 — Verificación Completa y Sign-Off (2026-10-02)

### 1. Estado de Requisitos y Cobertura

- **EP-MOV (15 Pts)**:
  - **HU-20** (Edición estándar auditable): Implementada y verificada (Snapshots de revisión, compensaciones ledger balanceadas, bloqueo de operaciones especializadas `OPERATION_SPECIALIZED`, cambio de periodo/fechas en `ReviseTransaction.kt` y `MovementEditorSheet.kt`).
  - **HU-21** (Anulación lógica / VOIDED): Implementada y verificada (Estado inmutable VOIDED, compensaciones ledger automáticas de ambas cuentas, diálogo `VoidMovementDialog.kt` sin DELETE físico, rechazo con compensación en `SyncInstrumentCommandsWorker.kt`).
  - **HU-22** (Búsqueda y filtros avanzados Free/Premium): Implementada y verificada (`QueryMovementHistory.kt`, `MovementHistoryAccessPolicy.kt`, SQL dinámico en `MovementQuerySqlBuilder.kt`, cursor keyset pagination `(occurred_at DESC, id DESC)`).

- **EP-PLA (16 Pts)**:
  - **HU-58** (Acceso Free/Premium por capacidad): Filtro básico Free disponible ilimitado; filtros avanzados (cuentas múltiples, categorías, rangos de importes) restringidos a Premium con fallback elegante.
  - **HU-59** (Concesión offline y expiración de 72h): Guard de tiempo confiable monotónico (`trustedNow < notAfter`, con `notAfter = min(verified_at + 72h, expires_at)`). Restricción inmediata al vencer o al reiniciar sin continuidad temporal verificable.

### 2. Resultados de Pruebas Verificadas

| Suite | Ámbito | Resultado | Comentarios |
|---|---|---|---|
| PostgreSQL pgTAP | S4 v2 Hash (`movements_registration_hash_v2_test.sql`) | **28/28 PASS** | Hash canónico v2, delimitadores, null/vacío, compatibilidad v1 |
| PostgreSQL pgTAP | S4 RPC Revisiones (`movements_revision_test.sql`) | **42/42 PASS** | RPC `revise_transaction_v1`, `void_transaction_v1`, RLS con 2 JWTs, atomicidad |
| JVM Unit Tests | Dominio, Cripto, Hasher, ViewModels (`:app:testDebugUnitTest`) | **100% PASS** | 0 fallos, 0 errores; incluye `MovementRevisionTest` (20/20), `MovementHistoryViewModelTest`, etc. |
| AndroidTest Compile | Compilación de pruebas instrumentadas | **0 errores** | Compilación limpia de Room v17, workers, queries y UI tests |
| Latencia SC-007 | Keyset pagination Room con 10 000 movimientos | **p95 < 300 ms** | Medido ~10-25 ms por página de 50 items; 0 duplicados en paginación |
| UI Render | LazyColumn Jetpack Compose | **< 5 s inicial** | Render perezoso fluido sin saltos de frames en lista de 10k items |

### 3. Sign-off de Auditoría y Principios Constitucionales

- **Finanzas / Ledger**: Invariante de **Zero DELETE** físico verificado en Room (`deleteLedgerEntriesForTransaction` eliminado), PostgreSQL y workers. Saldo proyectado concuerda con sumas de libro mayor en todo momento.
- **Seguridad y RLS**: Propietario verificado estrictamente por sesión local (`sessionCoordinator.localAccess`) y tokens JWT en servidor. Previene escalamiento horizontal o filtración entre usuarios.
- **Offline-First**: Outbox duradera con leases transaccionales y resolución de conflictos padre/hijo (`MovementConflictProposalEntity`).
- **Capacidad Sprint 4**: 31 puntos (EP-MOV 15 pts + EP-PLA 16 pts). No se incluye devolución parcial (HU-25 queda en Sprint 7 según arquitectura).



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

## Issue #19 — Espaciado, calendario y tarjeta Premium (2026-10-06)

Alcance solicitado por el responsable tras probar la copia física: separar botones/filas de chips; corregir margen superior y compresión del extremo final del calendario; agrupar título/descripción Premium en tarjeta informativa. Se implementó en `MovementFiltersSheet.kt` y strings locales (FR-034/039), sin cambios en firmas, modelos, validaciones, ViewModel, pantalla, consultas ni componentes globales. Se conserva alternancia calendario/entrada manual y el callback Premium.

Validación ejecutada:

```powershell
.\gradlew.bat --init-script Borrar-NoSubir/preview-device.init.gradle :app:assembleDebug :app:testDebugUnitTest --tests com.kipu.app.feature.movements.presentation.MovementFilterDraftTest --tests com.kipu.app.feature.movements.presentation.MovementHistoryViewModelTest --console=plain --no-configuration-cache
```

- **BUILD SUCCESSFUL**, 1m36s. `MovementFilterDraftTest`: 4 pruebas; `MovementHistoryViewModelTest`: 17 pruebas. Total **21, 0 fallos, 0 errores**. Reportes en `app/build/test-results/testDebugUnitTest/`. Advertencias de pruebas existentes no bloquean compilación.
- APK actualizado con `adb install -r` y abierto mediante `am start -W`: instalación **Success**, inicio **Status: ok**. La configuración de Supabase permanece en `local.properties`, excluido de Git.
- El init script local cambia únicamente el applicationId de debug a `com.kipu.app.preview`, permitiendo probar junto a otra instalación con firma distinta. No es un cambio de producción ni parte versionable de este issue; para un build debug normal omitir `--init-script`.
- Dispositivo físico: modelo 23129RA5FL, Android 15/API 35, 1080×2400, densidad 440 dpi, font_scale 1.0. Capturas anteriores inspeccionadas: filas de chips pegadas y texto «Fecha de finalización» comprimido en múltiples líneas. Las capturas se conservan localmente en `Borrar-NoSubir/filter-panel-before.png` y `filter-calendar-before.png`.
- Calendario posterior inspeccionado en el dispositivo: título con margen superior, inicio/fin alineados y valores de rango legibles; no hay compresión del extremo final. Evidencia local: `Borrar-NoSubir/filter-calendar-after.png`. Las capturas antes/después usan el mismo dispositivo/tema/escala; el usuario seleccionó un rango en la posterior.
- Panel posterior inspeccionado: filas de chips separadas y título/descripción/acción Premium agrupados con padding propio. Evidencia local: `Borrar-NoSubir/filter-panel-after.png`. Al detectar que el fondo de la tarjeta se distinguía poco, se añadió borde de 1 dp con `outlineVariant`; ese ajuste compiló correctamente en 9s, fue reinstalado y se verificó en `Borrar-NoSubir/filter-panel-outlined.png`.
- Al revisar la tarjeta con borde, el responsable pidió un fondo un poco más oscuro. Se cambió únicamente su color a `surfaceContainerHighest` del tema compartido, preservando el borde y el comportamiento. Build final **SUCCESSFUL en 11s**, actualización **Success** e inicio **Status: ok**. La revisión del tono final queda a disposición del responsable en la copia instalada; no se dispone aún de captura de ese último tono.
- No se ejecutó instrumentación ni se certifican aún temas oscuro, teclado, texto ampliado o toda la matriz del issue. Las 21 pruebas JVM no son prueba de layout; se ejecutaron antes de los ajustes finales exclusivamente visuales del borde/fondo.
- Obsidian Mind no está disponible en esta sesión; se usaron constitución y contratos locales. No se cambian reglas de producto. T110 y los restantes criterios del issue #19 siguen pendientes.

## Issue #19 — Errores, selectores y validación final (2026-10-07)

El responsable aceptó el resultado visual anterior, incluido el fondo de la tarjeta, y autorizó resolver los pendientes **excepto** el resumen/retirada de filtros aplicados y «Limpiar», ya aceptados. Se conservan esas implementaciones. El issue vigente se verificó mediante GitHub API con la autenticación Git local; el conector no tiene acceso a este repositorio. Obsidian Mind continúa indisponible, por lo que no se atribuye una consulta de producto inexistente.

- Errores existentes por clave junto a fechas, moneda y cada importe; mensajes específicos para rango de fechas e importes. Tras un intento inválido, corregir actualiza errores mediante `MovementFilterDraft.validate`, sin aplicar ni cambiar el validador.
- Selector específico con nombres/resumen, búsqueda, `LazyColumn` de altura acotada y claves por ID. Preserva selecciones ocultas, nombres duplicados y referencias históricas ausentes, que se pueden retirar sin mostrar IDs. Distingue catálogo vacío de búsqueda sin coincidencias; pérdida de acceso cierra el selector.
- La inspección de la configuración compacta detectó el ancho obligatorio de 360 dp de `DatePickerDialog` en Material3 1.4.0. Se agregó un contenedor local para ventanas menores de 360 dp, con acciones que cambian de fila; conserva el `DateRangePicker`, estado y callbacks. En ancho normal se mantiene el diálogo nativo.

Build final ejecutado:

```powershell
.\gradlew.bat --init-script Borrar-NoSubir/preview-device.init.gradle -PisolatedAndroidTests=true :app:assembleLab :app:assembleLabAndroidTest :app:assembleDebug :app:testLabUnitTest --tests com.kipu.app.feature.movements.presentation.MovementFilterDraftTest --tests com.kipu.app.feature.movements.presentation.MovementHistoryViewModelTest --console=plain --no-configuration-cache
```

**BUILD SUCCESSFUL**, 28 s. El init script solo mantiene la copia de prueba `com.kipu.app.preview`; no se versiona. Build reproducible de la variante aislada: omitir `--init-script` y `:app:assembleDebug`.

| Validación final | Resultado |
|---|---|
| JVM `MovementFilterDraftTest` | 4 pruebas, 0 fallos/errores |
| JVM `MovementHistoryViewModelTest` | 17 pruebas, 0 fallos/errores |
| `MovementFiltersIssue19Test` | 10 pruebas instrumentadas PASS |
| Cancelar, restaurar borrador y «Limpiar» existentes | 3 pruebas instrumentadas PASS |

La corrida final conjunta es **OK (13 tests)**; [salida del runner](validation/ui/issue-19/instrumentation.txt). Se verifican corrección sin aplicar, selección entre 1.000 referencias, búsquedas, duplicados, retirada histórica, cambio de propietario/acceso, Free con criterios avanzados retenidos y enmascaramiento. El test de cancelación anterior se actualizó para usar el cierre actual del panel, conservando su assertion de cero aplicaciones.

Dispositivo 23129RA5FL, Android 15/API 35. La prueba compacta usa configuración Android local al fixture (densidad ×1,25, fuente ×1,6, aproximadamente 314 dp) y entrada real de teclado; los diálogos heredan el contexto y se comprueba su ancho. El override inicial de `LocalDensity` no escalaba los diálogos y se sustituyó; no cuenta como evidencia de texto ampliado. Los intentos afectados por instalación rechazada/arranque MIUI/actividad vacía y los errores iniciales de fixtures/assertions no son PASS. En la corrida aceptada, se abrió la actividad de pruebas con el intent de `ActivityScenario` cuando MIUI impedía su arranque en segundo plano; no se cambiaron ajustes del dispositivo.

[Comparación visual y reproducción](validation/ui/issue-19/README.md): fixtures sintéticos, temas claro/oscuro, calendario y configuración compacta. «Antes» incluye las correcciones visuales previamente aceptadas y precede a errores/selectores; no se presenta como captura de `main`. Las capturas personales permanecen locales. La instrumentación verifica presentación con estado `Allowed` explícito, no compras reales, backend ni rendimiento de consultas.

Firmas públicas, modelos, Saver, validaciones de dominio, `MovementAppliedFilters.kt`, pantalla, ViewModel, repositorios, DAOs y componentes globales permanecen sin modificaciones de este issue. T115–T117 completan el alcance autorizado de implementación/validación; entrega en T118. T110 y gates ajenos de Sprint 4 conservan su estado anterior.
