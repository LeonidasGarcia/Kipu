# Implementation Plan: EP-CTA - Cuentas y Tarjetas

**Branch**: `002-ep-cta-cuentas-tarjetas` | **Date**: 2026-09-21 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-ep-cta-cuentas-tarjetas/spec.md` plus the requested Android, Room, Supabase and Stitch architecture constraints.

## Summary

Disenar EP-CTA como una vertical local-first dentro del modulo Android existente. Sprint 2 entrega HU-07 y HU-08 mediante modelos financieros puros, repositorios de dominio, Room v3, comandos atomicos de cuenta+apertura y tarjeta, una outbox propia de instrumentos, sincronizacion idempotente mediante RPC PostgreSQL tipadas y Pantallas 4/5 en Compose. Sprint 3 extiende el mismo ledger y contrato de comandos para deuda, alertas, tasas, pagos y compras en cuotas sin introducir una segunda verdad financiera. La implementacion permanece bloqueada hasta recuperar y validar el baseline remoto.

Room es la autoridad visible inmediata; PostgreSQL es la autoridad remota reconciliada. Los saldos se derivan exclusivamente de movimientos en unidades menores enteras. `AccountEntity.initialBalanceMinorUnits` se conserva solo como dato inmutable de auditoria y debe coincidir con el movimiento `OPENING`; nunca actua como saldo actual. Una tarjeta de debito no tiene saldo propio y una linea de credito nunca participa en dinero real disponible.

## Delivery Boundary

### Sprint 2 - Incremento implementable

- HU-07: cuentas liquidas, presets, apertura atomica, edicion visual, ajustes auditables, archivo/reactivacion y cupo Free.
- HU-08: tarjetas de debito/credito, vinculo de debito, identificacion segura, coincidencias advertidas y privacidad PAN/CVV.
- Room v3, migracion `2 -> 3`, repositorios, casos de uso, Pantallas 4/5, navegacion, outbox, worker, RPC, RLS y pruebas asociadas.
- Baseline remoto reproducible y endurecido como precondicion obligatoria del backend.

### Sprint 3 - Evolucion sobre la misma base

- HU-09 a HU-13: deuda y utilizacion, umbrales, catalogo/TEA, pago amortizador, compra confirmada y cuotas.
- Nuevas proyecciones y comandos; no se reemplazan cuentas, tarjetas, movimientos, recibos ni cursores de sync de Sprint 2.
- No se implementan anticipadamente efectos financieros de Sprint 3 durante el incremento de Sprint 2.

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

**Pre-research result**: PASS. El baseline remoto faltante no se acepta como deuda silenciosa: su recuperacion y `supabase db reset` limpio son una precondicion de implementacion y release.

### Post-Design Re-check

| Verification | Result |
|--------------|--------|
| Efectos e invariantes financieros | PASS - [data-model.md](data-model.md) define dinero, ledger, apertura, debito y credito sin doble conteo. |
| Retry, conflicto y offline | PASS - [sync-contract.md](contracts/sync-contract.md) define outbox, cadena causal por aggregate, recibos, revisiones y resultados exhaustivos. |
| RLS, grants y privacidad | PASS - [remote-api.openapi.yaml](contracts/remote-api.openapi.yaml) y [security-boundary.md](contracts/security-boundary.md) limitan lecturas y comandos. |
| Migraciones y preservacion | BLOCKED - Room `2 -> 3` esta disenada, pero Supabase no pasa hasta recuperar el baseline, reconciliar el esquema vinculado y demostrar `supabase db reset` limpio. |
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

### 6. Remote Boundary and Database Recovery

El repositorio no contiene la migracion que creo las tablas financieras referenciadas por `20260920162500_align_domain_schema_names.sql`. Antes de escribir la migracion EP-CTA:

1. Recuperar las migraciones originales desde historial/artefactos/backups y restaurarlas con sus versiones reales.
2. Reconstruir una base PostgreSQL 17 limpia y compararla con un dump de esquema del proyecto vinculado.
3. Agregar una migracion forward-only EP-CTA/hardening; no usar `IF NOT EXISTS` para esconder drift desconocido.
4. Exigir `supabase db reset`, pgTAP y advisors sin hallazgos EP-CTA bloqueantes.

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
| Real device | Offline, reinicio, process death, reconnect, Doze/force-stop, TalkBack, keyboard, tablet/foldable y screenshots sin datos reales. |

## Project Structure

### Documentation (this feature)

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
app/schemas/com.kipu.app.core.database.KipuDatabase/3.json

supabase/
├── migrations/                     # baseline recuperado + migracion EP-CTA/hardening
└── tests/database/financial_*.sql
```

**Structure Decision**: Mantener el modulo Android unico y aplicar Clean Architecture mediante paquetes y pruebas de frontera. Agregar modulos Gradle ahora aumentaria complejidad y divergeria del repositorio sin aportar valor suficiente para dos historias de Sprint 2.

## Complexity Tracking

No existen violaciones constitucionales que requieran excepcion. La outbox especializada y las RPC transaccionales son complejidad necesaria para atomicidad, idempotencia y conflictos financieros; no constituyen capas opcionales.

## Implementation Gates

1. Recuperar el baseline SQL financiero y demostrar reconstruccion limpia antes de crear tablas/RPC EP-CTA.
   El esquema vinculado observado no satisface por si mismo este plan: `accounts` carece de snapshot/apertura, `cards.account_id` es obligatorio y usa `is_credit`, `transactions` no modela `OPENING`, `ledger_entries` prohibe cero y las FKs financieras no son owner-composite. Estas diferencias deben resolverse en migraciones forward-only despues de recuperar el baseline.
2. Corregir/validar la cadena Room existente y aprobar schema v3 antes de fusionar persistencia.
3. Agregar `Postgrest` al `SupabaseClient` si se usa `supabase.postgrest.rpc`; una prueba de integracion debe demostrar el plugin instalado.
4. Actualizar `docs/stitch-design-system.md` antes de usar colores propios de Banco de la Nacion o afirmar fidelidad a Pantallas 4/5.
5. No iniciar Sprint 3 hasta que Sprint 2 demuestre saldo derivado, idempotencia, RLS y aislamiento sin defectos bloqueantes.
