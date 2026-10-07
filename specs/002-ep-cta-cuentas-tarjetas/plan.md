**Propagated**: 2026-09-24 — Updated from the approved Sprint 3 refinement in spec.md.


# Implementation Plan: EP-CTA - Cuentas y Tarjetas

**Branch**: `002-ep-cta-cuentas-tarjetas` | **Date**: 2026-09-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-ep-cta-cuentas-tarjetas/spec.md` plus the requested Android, Room, Supabase and Stitch architecture constraints.

## Summary

Disenar EP-CTA como una vertical local-first dentro del modulo Android existente. Sprint 2 cerró HU-07 y HU-08; el repositorio contiene Room version 10. Sprint 3 implementa HU-09 a HU-13 sobre los modelos existentes, con migracion local de 10 a 11. T075 fue aprobado: el reset limpio de PostgreSQL 17 y el diff local pasaron; la historia y el esquema remotos presentan drift y se preservaran con migraciones nuevas forward-only. El incremento unifica crédito, tasas, pagos y cuotas sobre el ledger canónico.

Room es la autoridad visible inmediata; PostgreSQL es la autoridad remota reconciliada. Los saldos se derivan exclusivamente de movimientos en unidades menores enteras. `AccountEntity.initialBalanceMinorUnits` se conserva solo como dato inmutable de auditoria y debe coincidir con el movimiento `OPENING`; nunca actua como saldo actual. Una tarjeta de debito no tiene saldo propio y una linea de credito nunca participa en dinero real disponible.

## Delivery Boundary

### Sprint 2 - Incremento implementable

- HU-07: cuentas liquidas, presets, apertura atomica, edicion visual, ajustes auditables, archivo/reactivacion y cupo Free.
- HU-08: tarjetas de debito/credito, vinculo de debito, identificacion segura, coincidencias advertidas y privacidad PAN/CVV.
- El diseño inicial de S2 incorporó Room v3, repositorios, casos de uso, Pantallas 4/5, navegación, outbox, worker, RPC, RLS y pruebas. El esquema que hoy está en el repositorio llegó a la versión 10.
- Baseline remoto reproducible y endurecido como precondicion obligatoria del backend.

### Sprint 3 - Evolucion sobre la misma base

- HU-09 a HU-13: deuda y utilizacion, umbrales, catalogo/TEA, pago amortizador, compra confirmada y cuotas.
- Nuevas proyecciones y comandos; no se reemplazan cuentas, tarjetas, movimientos, recibos ni cursores de sync de Sprint 2.
- No se implementan anticipadamente efectos financieros de Sprint 3 durante el incremento de Sprint 2.

- Correccion #21: la vista de tasas abierta desde una tarjeta consume su identidad persistida y solo muestra una referencia con coincidencia unica y exacta de producto, emisor, red y moneda; la TEA personal se lee de esa tarjeta. No se modifica el snapshot ni se infiere una tasa.

**Propagated**: 2026-10-06 — Updated from the #21 contextual-rate refinement in spec.md.

- Validacion PR #23: las equivalencias entre preset y catalogo son una lista explicita; estados de tarjeta y catalogo son excluyentes, el reintento limpia el error y el borrador de TEA se conserva por tarjeta.

**Propagated**: 2026-10-06 - Updated from the #21 PR #23 validation in spec.md.

## Technical Context

**Language/Version**: Kotlin 2.4.20 y Java 17; SQL PostgreSQL 17; JSON/OpenAPI 3.1 para contratos

**Primary Dependencies**: Jetpack Compose BOM 2026.08.00, Material 3, Lifecycle 2.11.0, Navigation Compose 2.10.1, Coroutines 1.11.0, Hilt 2.60.1, AndroidX Hilt 1.4.0, Room 2.8.5, WorkManager 2.11.2, Supabase Kotlin BOM 3.8.0, PostgREST y Ktor 3.5.1

**Storage**: Room `kipu.db` como fuente local autoritativa y observable; PostgreSQL/Supabase para proyecciones remotas, ledger, recibos idempotentes y cambios de sincronizacion; schemas Room exportados y migraciones SQL versionadas

**Testing**: JUnit 4, Kotlin Test, coroutines-test, Room Testing, WorkManager Test, Compose UI Test, Navigation Test, Hilt Test, Ktor MockEngine, pgTAP/Supabase CLI y validacion manual en Android real

**Target Platform**: Android nativo `minSdk 24`, `targetSdk 36`, `compileSdk 37`; telefonos, tablets y plegables; Supabase PostgreSQL 17 como limite remoto

**Project Type**: Aplicacion movil Android de modulo unico con backend Supabase versionado en el mismo repositorio

**Performance Goals**: Commit local de alta/edicion/archivo p95 <= 500 ms en dispositivo representativo; primera proyeccion local de Pantalla 4 p95 <= 1 s despues de abrir la base; listas fluidas a 60 fps para 100 instrumentos y 10,000 movimientos; ninguna operacion manual espera la red

**Constraints**: Local-first; dinero en `Long` minor units; `abs(amountMinor) <= 99_999_999_999_999`; aritmetica exacta con deteccion de overflow; PEN/USD sin conversion; PAN/CVV ausentes de modelos y logs; RLS y propiedad estructural; migraciones no destructivas; conflicto financiero nunca last-write-wins; Stitch `Kipu Andean Modernist`; accesibilidad 48dp/200%; sin Open Banking ni pagos reales

**Scale/Scope**: Sprint 2 incorpora 4 tablas Room principales, 2 pantallas funcionales, 3 variantes de formulario, 2 tablas publicas remotas mas ledger/recibos/sync privados, un worker por usuario y cuatro instrumentos computables en Free; el diseño se valida hasta 100 instrumentos y 10,000 movimientos por usuario sin fijar un limite Premium no aprobado

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

### Pre-Research Gate

| Principle / Gate | Status | Evidence |
|------------------|--------|----------|
| I. Financial Integrity First | PASS | `Money` usa `Long`+moneda; el ledger/movimientos es autoritativo; apertura, compra, pago y correccion son comandos auditables. |
| II. Local-First, Retry-Safe | PASS | Cada comando confirma proyeccion+movimiento+outbox en Room antes del exito; UUID, hash y recibos hacen seguros los reintentos. |
| III. Security and Privacy | PASS | No existen campos PAN/CVV; toda fila incluye propietario; RLS forzada, grants minimos y RPC derivan `auth.uid()`. |
| IV. Safe and Explainable Automation | PASS | Una compra detectada sigue siendo candidato sin efectos hasta confirmacion; Sprint 2 no incorpora captura automatica. |
| V. Freemium Cannot Alter Truth | PASS | El cupo bloquea nuevas altas/reactivaciones, pero nunca elimina ni excluye historia; offline solo un lease Premium acotado definido por EP-PLA puede levantar limites. Sin lease valido se aplica Free. |
| VI. Lifecycles Preserve History | PASS | Cuentas creadas tienen apertura y por ello se archivan; ajustes/reversos son append-only; delete exige ausencia comprobada de historia/dependencias. |
| VII. Native Android and Boundaries | PASS | Dominio Kotlin puro, puertos Repository, adaptadores Room/Supabase, ViewModels sin calculos financieros y Compose stateless. |
| VIII. Specification-Driven | PASS | El plan conserva HU-07..HU-13, RF-C01..RF-C12 y separa S2/S3. |
| IX. Quality Is Correctness | PASS | Incluye dominio, migraciones, atomicidad, sync, RLS, privacidad, aislamiento, accesibilidad y pruebas reales. |
| X. Product Boundary | PASS | Registra informacion; no mueve dinero, emite credito ni lee bancos/SMS/correo. |

**Pre-research result**: PASS. T075 was approved on 2026-09-24: 23 local migrations reset cleanly, `supabase db diff --local --schema public,internal` returned no schema changes, and the linked project's migration/schema drift was documented for forward-only reconciliation. No remote reset or historical migration rewrite is allowed.

### Post-Design Re-check

| Verification | Result |
|--------------|--------|
| Efectos e invariantes financieros | PASS - [data-model.md](data-model.md) define dinero, ledger, apertura, debito y credito sin doble conteo. |
| Retry, conflicto y offline | PASS - [sync-contract.md](contracts/sync-contract.md) define outbox, cadena causal por aggregate, recibos, revisiones y resultados exhaustivos. |
| RLS, grants y privacidad | PASS - [remote-api.openapi.yaml](contracts/remote-api.openapi.yaml) y [security-boundary.md](contracts/security-boundary.md) limitan lecturas y comandos. |
| Migraciones y preservacion | PASS - Room 10 -> 11 se valida con prueba de migracion. T075 aprobó el reset local y el diff vacío; la diferencia del proyecto remoto se reconcilia forward-only. |
| UI, accesibilidad y masking | PASS - [ui-contract.md](contracts/ui-contract.md) aplica Stitch, `MoneyText`, semantica y layouts adaptativos. |
| Pruebas en limites de fallo | PASS - [quickstart.md](quickstart.md) cubre JVM, Room, workers, Compose, PostgreSQL y dispositivo. |
| Complejidad constitucional | PASS - se conserva un modulo y se introduce solo una outbox financiera especializada, justificada por atomicidad e idempotencia. |

## Architecture and Implementation Strategy

### 1. Boundaries and Packages

Se conserva el modulo `:app` y se adopta la vertical estricta usada por `feature/plans`, no las excepciones de settings que inyectan DAOs en ViewModels.

```text
presentation -> domain <- data
                    ^
                    |
             core/finance/domain
```

- `core/finance/domain`: `Money`, `Currency`, IDs tipados, `Movement`, reglas exactas y utilidades de fecha. Sin Android, Room, Hilt o Supabase.
- `feature/accounts/domain`: cuentas, tarjetas, repositorios y casos de uso de EP-CTA.
- `feature/accounts/data`: entidades Room, DAO, mappers, API remota, repositorio offline-first, outbox y worker.
- `feature/accounts/presentation`: Route/Screen/ViewModel/UI state; solo llama casos de uso.
- `feature/accounts/di`: bindings Hilt; `CoreModule` solo expone DAO/database/Clock.

Una prueba de frontera impide imports de `android.*`, `androidx.room`, `data` o `presentation` desde dominio.

### 2. Financial Domain

- `Money(minorUnits: Long, currency: Currency)` valida el rango y usa operaciones exactas; no acepta `Float`, `Double` o conversion implicita.
- `Currency` admite `PEN` y `USD`; agregaciones se agrupan por moneda.
- `Account` conserva apertura, apariencia y lifecycle, pero su saldo deriva de `Movement` confirmado.
- `Card` es un tipo sellado: `DebitCard` requiere cuenta compatible; `CreditCard` requiere moneda, linea y dias. La entidad Room puede ser plana, pero el mapper rechaza combinaciones imposibles.
- `Movement` es append-only. `OPENING` existe incluso con cero; `ADJUSTMENT` corrige importe/fecha; una moneda equivocada exige archivar y recrear.
- La cuenta persistida siempre tiene apertura, por lo que su retirada normal es archivo. La eliminacion fisica solo aplica a drafts no confirmados o tarjetas sin historia/deuda/dependencias, segun el contrato de lifecycle.

### 3. Local Persistence and Atomic Commands

Room sube de v2 a v3 y agrega `accounts`, `cards`, `financial_movements` e `instrument_sync_outbox`. `AccountDao` y `CardDao` exponen los `Flow` owner-scoped solicitados y contienen sus metodos `@Transaction` de comando; `FinancialMovementDao` e `InstrumentSyncDao` aislan consultas de ledger y leasing de outbox.

`createAccount(command)` realiza atomicamente:

1. Comprueba si `operationId` ya existe y compara hash canonico.
2. Evalua propietario, lease de entitlement efectivo y cupo Free dentro de la transaccion. Sin lease Premium valido, acotado y no reiniciable definido por EP-PLA, se aplica Free aun offline.
3. Inserta `AccountEntity` con `initialBalanceMinorUnits` inmutable.
4. Inserta exactamente un `OPENING` con mismo importe/moneda/fecha.
5. Inserta la outbox inmutable en `PENDING`.
6. Confirma; solo entonces el repositorio emite exito y programa sync.

`registerCard(command)` valida cupo dentro de la misma transaccion. Debito verifica cuenta activa, owner, tipo y moneda; no crea movimiento. Credito persiste linea/configuracion sin crear activo ni deuda.

No se guarda `currentBalance`. Las consultas usan sumas firmadas de movimientos confirmados. `initialBalanceMinorUnits` es evidencia de entrada para auditoria y debe coincidir siempre con `OPENING`.

### 4. Repository, Use Cases and Reactive State

`FinancialInstrumentsRepository` se declara en dominio y su implementacion coordina `SessionCoordinator.localAccess`, DAO, scheduler y API. `LocalAccess.Available(userId, remoteSession)` es la fuente reactiva de propietario: los `Flow` cambian con el owner, `Protected` conserva el owner pero bloquea lectura sensible, y `NoOwner` emite estado vacio. `AuthenticatedSessionProvider` queda limitado al worker/API para obtener credenciales remotas puntuales; nunca se captura una sesion suspendida como owner permanente. El repositorio nunca expone entidades, DTOs o `WorkInfo`.

Casos de uso Sprint 2:

- `ObserveFinancialDashboard`
- `ObserveInstruments`
- `CreateLiquidAccount`
- `RecordOpeningAdjustment`
- `RegisterDebitCard`
- `RegisterCreditCard`
- `UpdateInstrumentAppearance`
- `ArchiveInstrument`
- `ReactivateInstrument`
- `DeleteUnusedCard`

Los DAOs entregan `Flow`; repositorio/casos de uso los mapean; ViewModels combinan a `StateFlow` con `SharingStarted.WhileSubscribed(5_000)`. Los efectos one-shot usan `Channel`/`receiveAsFlow`. El exito visible corresponde al commit local, nunca al remoto.

### 5. Synchronization

Se crea `instrument_sync_outbox`; no se reutiliza `sync_outbox`, cuyo payload es especifico de planes. Estados internos: `PENDING`, `IN_FLIGHT`, `WAITING_FOR_AUTH`, `SYNCED`, `CONFLICT`, `ERROR`. La UI los proyecta al contrato solicitado `PENDING`, `SYNCED`, `ERROR` sin perder diagnostico interno.

- Una fila inmutable por comando con UUID, owner, tipo, aggregate, `predecessorOperationId`, expected revision, contract version, payload canonico, SHA-256, lease, intentos y error seguro.
- Las mutaciones locales de un mismo aggregate forman una cadena causal. El worker no envia una sucesora hasta que el recibo de su predecesora este reconciliado; el servidor valida que ambas pertenezcan al mismo owner/aggregate y toma la revision aceptada de la predecesora como base. Esto permite create -> edit -> archive completamente offline sin falsos conflictos.
- Trabajo unico `instrument-sync-{userId}` con `KEEP`, red requerida, backoff exponencial, leasing y recuperacion de leases vencidos.
- Startup, restauracion de sesion y commit vuelven a solicitar trabajo pendiente; el periodic worker es solo red de seguridad.
- `PendingChangesRepositoryImpl` migra a multibinding `PendingChangesSource` para planes, perfil e instrumentos.
- Comandos financieros se deduplican por `operationId`+hash. Metadata mutable usa compare-and-set por `expectedRevision`; timestamps nunca resuelven verdad financiera.
- El cursor de pull avanza exclusivamente tras aplicar una pagina completa. Una respuesta push nunca marca eventos concurrentes como consumidos.

### 6. Remote Boundary, Baseline Validation and Database Reconciliation

El repositorio contiene `20260920000000_financial_core_baseline.sql` and later alignment migrations. Their clean-reset reproducibility and equivalence to the linked schema have not been demonstrated here. Before writing a Sprint 3 migration:

1. Run a clean PostgreSQL 17 reset using the checked-in migration chain and record its result.
2. Compare the rebuilt schema with the linked project and the canonical data model; classify every difference.
3. Add only forward-only EP-CTA reconciliation/hardening migrations; do not edit applied history or use `IF NOT EXISTS` to hide drift.
4. Require clean `supabase db reset`, pgTAP and advisors without blocking EP-CTA findings.

Arquitectura remota:

- Lecturas: vistas/proyecciones `security_invoker` owner-scoped mediante PostgREST, con grants explicitos porque tablas nuevas ya no se exponen automaticamente. El pull ordenado incluye `ACCOUNT`, `CARD` y `MOVEMENT`; una instalacion nueva no puede reconstruir saldos solo con instrumentos.
- Mutaciones: RPC PostgreSQL tipadas y estrechas. No DML financiero directo desde Android y no dispatcher JSON generico.
- Sprint 2 incluye `record_opening_adjustment_v1`: conserva el `OPENING` y agrega un `REVERSAL` por el importe/fecha originales mas un `ADJUSTMENT` por el importe/fecha corregidos dentro de una sola operacion.
- RPC deriva owner de `auth.uid()`, bloquea cabeza del usuario/aggregate, valida cupo/revision, escribe proyeccion+ledger+sync+receipt en una transaccion.
- RLS habilitada y forzada; `anon` y `PUBLIC` sin acceso; `authenticated` solo SELECT de proyecciones aprobadas y EXECUTE de firmas exactas.
- Si una funcion requiere `SECURITY DEFINER`, vive con `search_path=''`, referencias calificadas, owner `NOLOGIN`, checks explicitos y EXECUTE revocado a `PUBLIC`.

### 7. Screens 4 and 5

`docs/stitch-design-system.md` es la fuente visual. No existen IDs/artefactos Stitch autoritativos de Pantallas 4/5 en el repositorio, por lo que el plan aplica tokens y composicion funcional sin afirmar fidelidad pixel-perfect a un artefacto ausente.

**Pantalla 4 - Dashboard**:

- Seccion `Dinero real disponible`, agrupada por PEN/USD y alimentada por todas las cuentas liquidas no eliminadas. Archivar oculta/bloquea operaciones, pero no hace desaparecer dinero; la lista activa y el total financiero usan filtros distintos.
- Tarjetas de debito como referencias bajo su cuenta; no repiten monto.
- Seccion `Tarjetas de credito` separada; Sprint 2 muestra identidad/linea configurada sin calcular deuda futura. Sprint 3 agrega usado/disponible/total con copy `Credito disponible, no es dinero propio`. Una tarjeta archivada con deuda sigue visible en el resumen de pasivos y puede recibir pagos, aunque no admita compras nuevas.
- Compact: feed vertical. Medium: dos paneles. Expanded: contenedor maximo 1200dp.

**Pantalla 5 - Instrumentos**:

- Grupos Cuentas/Tarjetas y filtro de archivados.
- Form state sellado para cuenta, debito o credito; cambiar tipo descarta campos incompatibles.
- Compact: formulario full-screen. Medium/expanded: list-detail.
- Presets almacenan IDs estables. BCP/BBVA/Interbank/Scotiabank usan tokens aprobados; Banco de la Nacion usa el fallback Kipu etiquetado hasta que el design system publique tokens, sin inventar color de marca.
- `MoneyText` es obligatorio para importes, con `tnum`; enmascarado elimina el valor de semantica y expone `Monto oculto`.
- `MaskedCardReference` muestra solo `•••• 1234` y nunca comparte semantica con masking monetario.

Navegacion agrega `app/dashboard`, `app/instruments`, `app/instruments/create`, `app/instruments/{id}` y edit. Los argumentos contienen IDs internos, nunca montos, alias ni ultimos cuatro. Sesion restaurada y onboarding completo aterrizan en dashboard.

### 8. Privacy Boundary

- No existen propiedades, parametros, analytics o columnas llamadas PAN/CVV/CVC/CID/cardNumber.
- El unico control numerico de identificacion es `lastFourDigits`, exactamente cuatro digitos ASCII.
- Pegar mas de cuatro digitos se rechaza antes de actualizar estado; no se trunca ni se conserva en `SavedStateHandle`, logs o errores.
- DTO/RPC usan allowlist exacta y rechazan campos desconocidos. Recibos guardan hash y resultado, no request crudo.
- Logs contienen `operationId`, tipo, resultado y codigo seguro; nunca alias, issuer libre, last4, importes, token, payload o contenido de notificacion.

### 9. Testing Strategy

| Layer | Minimum evidence |
|-------|------------------|
| Pure domain | Money exacto/overflow/moneda, apertura, debit link, separacion credito-activo, cupo, lifecycle, PAN/CVV boundary, dias cortos y cuotas S3. |
| Repository/ViewModel | Commit-before-success, Flow/StateFlow desde `SessionCoordinator.localAccess`, double submit, efectos one-shot, estados Protected/NoOwner, cambio de owner y scheduler failure recovery. |
| Room | Atomicidad account+opening+outbox, rollback, idempotencia, owner predicates, quinto instrumento concurrente, link debit, sums y migraciones `2->3`/`1->3`. |
| Worker/API | Leasing, backoff, auth mismatch, payload estable, cadena causal create/edit/archive, timeout post-commit, pull de movimientos y resultados APPLIED/DUPLICATE/CONFLICT/REJECTED. |
| Compose/navigation | Secciones separadas, formularios tipados, masking sin leak semantico, presets, 48dp, 200%, compact/list-detail y rutas sin PII. |
| PostgreSQL | Baseline limpio, constraints, RLS/grants, cross-user, RPC atomica, receipts/hash, revisions, tombstones y ledger. |
| Real device | Offline, reinicio, process death, reconnect, Doze/force-stop, TalkBack en tarjeta física simulada/Card Preview, compra en cuotas y amortización de deuda; teclado, tablet/foldable y screenshots sin datos reales. Registrar evidencia por dispositivo/API. |

## Project Structure

### Documentation (this feature)

Sprint 3 financial command contract: [credit-commands.md](contracts/credit-commands.md).

```text
specs/002-ep-cta-cuentas-tarjetas/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── remote-api.openapi.yaml
│   ├── sync-contract.md
│   ├── security-boundary.md
│   └── ui-contract.md
├── checklists/
│   └── requirements.md
└── tasks.md                         # Creado posteriormente por /speckit.tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/kipu/app/
├── core/
│   ├── database/{KipuDatabase.kt,DatabaseConverters.kt,RoomMigrations.kt}
│   ├── finance/domain/model/{Money.kt,Currency.kt,FinancialIds.kt,Movement.kt}
│   └── session/{PendingChangesSource.kt,PendingChangesRepositoryImpl.kt}
├── feature/accounts/
│   ├── domain/{model,FinancialInstrumentsRepository.kt,usecase/}
│   ├── data/
│   │   ├── local/{AccountEntity.kt,CardEntity.kt,FinancialMovementEntity.kt,InstrumentSyncOutboxEntity.kt,AccountDao.kt,CardDao.kt,FinancialMovementDao.kt,InstrumentSyncDao.kt}
│   │   ├── remote/{FinancialInstrumentsApi.kt,FinancialDtos.kt}
│   │   ├── sync/{InstrumentSyncScheduler.kt,SyncInstrumentCommandsWorker.kt}
│   │   └── OfflineFirstFinancialInstrumentsRepository.kt
│   ├── di/AccountsModule.kt
│   └── presentation/{dashboard,instruments,components}/
├── navigation/{AccountsNavigation.kt,KipuNavHost.kt}
└── ui/component/{MoneyText.kt,MaskedCardReference.kt}

app/src/test/java/com/kipu/app/{core/finance,feature/accounts}/
app/src/androidTest/java/com/kipu/app/{core/database,feature/accounts}/
app/schemas/com.kipu.app.core.database.KipuDatabase/10.json

supabase/
├── migrations/                     # baseline versionado + migracion EP-CTA/hardening
└── tests/database/financial_*.sql
```

**Structure Decision**: Mantener el modulo Android unico y aplicar Clean Architecture mediante paquetes y pruebas de frontera. Agregar modulos Gradle ahora aumentaria complejidad y divergeria del repositorio sin aportar valor suficiente para dos historias de Sprint 2.

## Complexity Tracking

No existen violaciones constitucionales que requieran excepcion. La outbox especializada y las RPC transaccionales son complejidad necesaria para atomicidad, idempotencia y conflictos financieros; no constituyen capas opcionales.

## Implementation Gates

1. Demonstrate a clean reconstruction from the checked-in SQL baseline and compare it with the linked project before creating Sprint 3 migrations/RPCs.
   The linked schema observed does not satisfy this plan by itself: `accounts` lacks the opening snapshot, `cards.account_id` is required and uses `is_credit`, `transactions` does not model `OPENING`, `ledger_entries` rejects zero, and financial FKs are not owner-composite. Resolve differences only through reviewed forward-only migrations.
2. Validar la cadena Room existente hasta v10 y aprobar la migración 10 -> 11, su JSON exportado y pruebas de preservación antes de fusionar persistencia.
3. Agregar `Postgrest` al `SupabaseClient` si se usa `supabase.postgrest.rpc`; una prueba de integracion debe demostrar el plugin instalado.
4. Actualizar `docs/stitch-design-system.md` antes de usar colores propios de Banco de la Nacion o afirmar fidelidad a Pantallas 4/5.
5. No iniciar Sprint 3 hasta que Sprint 2 demuestre saldo derivado, idempotencia, RLS y aislamiento sin defectos bloqueantes.

## Dependency Gate Evidence (2026-09-24)

- EP-CTA HU-07/HU-08 and EP-MOV HU-18/HU-19/HU-23 implementation tasks are checked complete in their existing Sprint 2 task artifacts. HU-18 also depends on EP-CCO HU-14; its category task and device acceptance are recorded complete.
- The repository contains the corresponding account/card, transaction/ledger, category and duplicate-detection code and tests. A fresh `testDebugUnitTest --rerun-tasks` run completed successfully; 228 unit tests are recorded in Gradle XML. `assembleDebug` and `compileDebugAndroidTestKotlin` also succeeded in this session.
- Instrumented device tests were not rerun: the local ADB client/server versions conflict (client 40 vs server 41), and the device listing failed. Re-run the Room/movement acceptance tests with a coherent ADB setup before treating their persistence gate as freshly verified.
- The Supabase clean local reset passed and its `public,internal` diff was empty. The remote project has documented history, schema, policy and grant drift; Sprint 3 database changes must reconcile it forward-only, preserve the historical migrations and retain the legacy RPC wrappers.

## Sprint 3 Plan: HU-09 to HU-13

**Sprint Goal**: Kipu distinguishes credit and validates purchases.

**Scope boundary**: This increment adds only EP-CTA HU-09, HU-10, HU-11, HU-12 and HU-13. HU-07 and HU-08 remain the closed Sprint 2 baseline. No later HU in EP-CTA is included.

### Dependency and delivery order

0. T075 is complete and approved. Use the clean local baseline as the migration test oracle; preserve the linked project's data/history and resolve its documented drift through new forward-only migrations before enabling the new canonical commands.
1. Verify the Sprint 2 outputs HU-07 and HU-08, plus external prerequisites HU-18, HU-19 and HU-23, are implemented and green before their dependent implementation begins. Their status is a gate, not something this plan assumes.
2. Start HU-09 after HU-08, HU-18 and HU-19. It establishes the base consultation of confirmed debt, credit line, available credit and cycle dates; it does not add a dependency on HU-13.
3. HU-11 can proceed in parallel with HU-09 after HU-08 and the T075 readiness gate; its Android catalog flow uses the imported T079 snapshot. HU-13 may design its no-interest principal path independently, then integrate rate-backed estimates after HU-11.
4. HU-10 follows HU-09 and develops threshold alerts over confirmed utilization. Its event contract can be prepared alongside EP-NOT HU-42. End-to-end verification using a credit purchase originated in Kipu completes after HU-13.
5. HU-12 follows HU-07, HU-09, HU-18 and HU-19 and develops payment of confirmed debt. End-to-end verification from an app-originated purchase through card payment completes after HU-13.
6. HU-13 follows HU-08, HU-18, HU-19 and HU-23. It closes the app-originated purchase path used by end-to-end verification of debt consultation, threshold alerts and card payment. HU-11 remains a partial dependency for rate-backed estimates only.

### Sprint 3 canonical model and command boundaries

- Keep `public.credit_products` as the single reference catalog source and `public.cards.personal_tea_bps` as the per-card rate. Reconcile the existing `referential_rate_catalog` migration/data into this canonical table; do not introduce another rate-catalog table or a historical simulation table. The Sprint 3 snapshot is the official `KIPU_CATALOGO_TARJETAS_CREDITO_PERU_2026.md` at 2026-09-24: exactly 44 confirmed products (BCP 18, BBVA 10, Interbank 16). Preserve source-supported purchase TEA by PEN/USD, ranges/profile conditions, membership cost/conditions, source references and editorial caveats. Missing or conflicted values remain explicitly unpublished/conflicted and are never inferred. Show `Tasa referencial al 24/09/2026`; do not auto-expire by age.
- Keep public.credit_installments and public.credit_payment_allocations as the canonical persisted schedule and payment-allocation relations. Add their missing local Room projections with Kotlin Long amounts; do not create parallel remote entities.
- Read debt from confirmed credit-card ledger effects. A credit line or unused capacity never contributes to liquid money or net worth as an asset.
- A confirmed card purchase is one EXPENSE transaction with CARD_PURCHASE semantics on the liability card. It recognizes the purchase principal as expense when confirmed and writes its installment schedule atomically. A simulation is a display-only estimate and writes no ledger entries.
- A card payment is a TRANSFER with CARD_PAYMENT semantics. One canonical atomic command reduces the source liquid asset and card liability, allocates the paid amount to installments, and creates no operational expense. Replace or delegate the existing pay_credit_card_v1 and confirm_credit_purchase_v1 movement-only paths; never keep two accounting authorities.
- Allocate card payments by ascending `due_date`; a partial payment reduces the principal of the oldest outstanding installment and does not advance until that installment is paid. Use purchase occurrence time, installment number and stable installment ID as deterministic tie-breaks for equal due dates. Allocation sum equals the transfer amount.
- HU-10 emits a stable CREDIT_UTILIZATION_THRESHOLD_CROSSED event containing owner-scoped card and operation references, threshold and utilization. EP-NOT/HU-42 consumes and deduplicates it through the shared `app_notifications` contract and owns notification persistence. The Dashboard and card-detail alert surfaces consume active alerts through that same contract; EP-CTA has no notification state or table of its own.

### Canonical command ownership

| Task | Owner and boundary |
|------|--------------------|
| T080 | Canonical database purchase command: `register_transaction_v1`, ledger/installments atomicity and legacy RPC compatibility. |
| T065 | Android integration of purchase confirmation with T080; no independent server accounting path. |
| T081 | Canonical database payment command: `allocate_credit_payment_v1`, FIFO allocation and legacy RPC compatibility. |
| T058 | Android integration of card amortization with T081; no independent server accounting path. |

### Deterministic credit and installment rules

- Preserve the user's preferred closing and due days in the range 1..31. For a given month, the effective date is min(preferred day, days in that month); clamping never mutates the preferred value. The existing remote constraint capped at 28 must be reconciled before implementation.
- A purchase on or before the effective closing date belongs to that billing cycle. A purchase after it belongs to the next cycle. The first installment is due on the earliest effective due_day strictly after that cycle's effective closing date; subsequent installments are monthly, with short months clamped independently.
- Split a zero-interest principal in integer minor units: base = total / count, remainder = total % count, and add one minor unit to each of the first remainder installments. S/100 over 3 installments is S/33.34, S/33.33, S/33.33.
- For an interest estimate, convert TEA to TEM as (1 + TEA)^(1/12) - 1, then use the French fixed-payment formula. At zero TEM, payment is principal divided by count. Monetary output is rounded to minor units deterministically; all simulated rows are labeled estimates and never post financial entries. Persisted confirmed purchase rows represent only the actual confirmed amount/schedule.
- Compute threshold crossings from committed utilization transitions at 50%, 80% and 100%. A new crossing is emitted only when utilization moves from below to at-or-above a threshold; a later drop below re-arms it. The event uses the financial operation identity for idempotent retries.

### Architecture and repository reconciliation gates

- The checked-in schema currently has competing paths: the old card payment/purchase RPCs write financial_movements, while the canonical model is transactions plus internal.ledger_entries, credit_installments and credit_payment_allocations. The current register_transaction_v1 implementation also needs explicit CARD_PURCHASE support. T075 approved a clean local baseline and forward-only remote reconciliation; one canonical command path and compatibility wrappers remain required.
- Align cards.closing_day/due_day validation with the backlog's 1..31 preference and its short-month effective-date rule. Keep historical values; do not rewrite dates or balances.
- Reconcile `referential_rate_catalog` with `credit_products` and preserve the dated catalog snapshot, per-currency purchase TEA/ranges, membership disclosures, source references and source status. Extend the existing canonical table only where columns are missing; do not infer or auto-expire data.
- Room is currently version 10 in the checked-in application. Plan the next schema version from that actual baseline (10 to 11 unless another authorized migration changes it), preserve existing rows, and export/test the resulting schema. Do not use the old Sprint 2 Room 2-to-3 plan.
- Validate the Sprint 2 dependencies HU-07/HU-08 and the external HU-18/HU-19/HU-23 acceptance suites, the Supabase clean baseline, RLS and idempotency before enabling these commands.

### Planned validation evidence

The minimum Sprint 3 evidence is listed in quickstart.md: exact S/100 three-installment allocation; FIFO partial payment that decreases asset and liability without increasing expense; credit-line separation from real money; 50/80/100% threshold crossing, deduplication and re-arming; purchase confirmation and retry idempotency; owner-isolated RLS; all 44 dated catalog rows and source caveats; TalkBack/Compose semantics for Card Preview, installment purchase and debt amortization; and regression of the Sprint 1/Sprint 2 ledger and instrument flows. Build, lint, unit, Room migration, Compose, synchronization and pgTAP/database tests must be green before Sprint 3 closure.

### Decisions requiring review before implementation

- Approve the forward-only migration that changes the effective preferred-day constraints from 1..28 to 1..31 while retaining monthly clamping.
- Approve the physical field mapping for the dated catalog snapshot and preserve source conflicts/unpublished values; there is no age-based expiration.
- Agree the stable event identity jointly with the EP-NOT owner.
- T075 validated the clean local baseline and documented linked-project drift; reconcile legacy RPCs with forward-only migrations before changing production-facing command dispatch.
