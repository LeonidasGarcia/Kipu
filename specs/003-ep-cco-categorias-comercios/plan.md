# Implementation Plan: EP-CCO Categorías, Subcategorías y Comercios

**Branch**: `003-ep-cco-categorias-comercios` | **Date**: 2026-09-22 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/003-ep-cco-categorias-comercios/spec.md`.

## Summary

Entregar HU-14 y HU-15 como una vertical local-first de categorías y comercios. Las categorías serán datos privados del usuario con jerarquía estricta de dos niveles; las categorías predeterminadas se personalizarán mediante una presentación por usuario para conservar la identidad del catálogo y los movimientos históricos. Los comercios serán un catálogo administrado por Kipu, de solo lectura para el cliente y buscable por subcadena normalizada.

La clasificación se adjunta de manera independiente a `financial_movements`, el ledger canónico actual. No se ampliará la tabla heredada `transactions`, pues hacerlo crearía una segunda verdad de categorías y comercios. Room confirma atómicamente la clasificación y una outbox antes de informar éxito; Supabase reconcilia comandos idempotentes, conserva conflictos de presentación de categorías y aplica autorización por propietario.

## Technical Context

**Language/Version**: Kotlin/JVM con Java 17; SQL PostgreSQL/Supabase; JSON para comandos y sincronización.

**Primary Dependencies**: Jetpack Compose, Material 3, Navigation Compose, Coroutines/Flow, Hilt, Room, WorkManager, Supabase Kotlin Auth/PostgREST y Ktor.

**Storage**: Room `kipu.db` como fuente local inmediata; PostgreSQL/Supabase como estado remoto reconciliado; migraciones Room y SQL versionadas y no destructivas.

**Testing**: JUnit/Kotlin Test, coroutines-test, Room Testing, WorkManager Test, Compose UI/Navigation/Hilt tests, Ktor MockEngine y pgTAP/Supabase CLI.

**Target Platform**: Android nativo minSdk 24, teléfonos/tablets/plegables; backend Supabase PostgreSQL.

**Project Type**: Aplicación Android de módulo único con backend versionado en el mismo repositorio.

**Performance Goals**: Crear, editar, activar o clasificar localmente p95 <= 500 ms; búsqueda local de hasta 100 resultados p95 <= 1 s; listas fluidas a 60 fps para 100 categorías y 10,000 movimientos por usuario.

**Constraints**: Local-first; sin tags; dos niveles sin ciclos; Free cuenta solo raíces personalizadas activas; historial inmutable respecto a identidad de categorías; conflicto de presentación nunca se sobrescribe silenciosamente; comercio y categoría son campos independientes; catálogo de comercios sin DML del cliente; RLS por propietario; migraciones no destructivas.

**Scale/Scope**: Sprint 2 añade una vertical Android, Room v4, extensión de movimientos, caché de catálogo de comercios, outbox/conflictos de categorías, una ruta de categorías y selectores reutilizables para movimientos. No entrega alias, reglas, texto original ni preferencias personales.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Research Gate

| Principle / Gate | Status | Evidence |
|------------------|--------|----------|
| I. Financial Integrity First | PASS | `financial_movements` conserva una sola verdad; categoría y comercio son clasificaciones opcionales, no afectan importes, saldos ni ledger. |
| II. Local-First, Retry-Safe | PASS | Cambios locales de categoría y asignaciones de movimiento se confirman con outbox; IDs de operación, hash y revisión hacen seguros los reintentos; conflictos requieren elección explícita. |
| III. Security and Privacy | PASS | Categorías, conflictos y referencias de movimiento son owner-scoped; RLS fuerza aislamiento. El catálogo general de comercios es solo lectura y no admite DML de clientes. |
| IV. Safe and Explainable Automation | PASS | La búsqueda solo devuelve coincidencias por subcadena normalizada; no crea comercios, alias ni asignaciones automáticas. |
| V. Freemium Cannot Alter Truth | PASS | El límite bloquea crear/reactivar raíces personalizadas, pero preserva categorías excedentes, subcategorías y movimientos. |
| VI. Financial Lifecycles Preserve History | PASS | Inactivar o personalizar una categoría no rompe las referencias históricas; ninguna operación borra movimientos. |
| VII. Native Android and Boundaries | PASS | Reglas de jerarquía, cupo, elegibilidad y conflictos se mantienen en dominio Kotlin puro; Room/Supabase/UI permanecen adaptadores. |
| VIII. Specification-Driven | PASS | El plan traza HU-14/HU-15 y FR-001 a FR-019; excluye explícitamente los elementos de Sprint 5. |
| IX. Quality Is Correctness | PASS | Incluye pruebas de dominio, Room v3->v4, sincronización, RLS, migración, accesibilidad y dispositivo real. |
| X. Product Boundary | PASS | No incorpora etiquetas, IA, pagos, Open Banking ni nuevas clases de movimiento. |

**Pre-research result**: PASS.

### Post-Design Re-check

| Verification | Result |
|--------------|--------|
| Ledger y preservación histórica | PASS - [data-model.md](data-model.md) adjunta clasificación a `financial_movements` sin cambiar importes ni estados. |
| Offline, idempotencia y conflicto | PASS - [sync-contract.md](contracts/sync-contract.md) define commit local, recibos, revisión y resolución explícita. |
| RLS, catálogo y límites de acceso | PASS - [remote-api.openapi.yaml](contracts/remote-api.openapi.yaml) define RPCs owner-scoped y lectura de catálogo sin DML. |
| Migraciones y aislamiento | PASS - migraciones Room v3->v4 y SQL forward-only, con pruebas de datos existentes y cross-user, son puertas de implementación. |
| UX, filtros y accesibilidad | PASS - [ui-contract.md](contracts/ui-contract.md) separa categoría/comercio, prohíbe tags y cubre estados vacío, conflicto y límites. |
| Verificación integral | PASS - [quickstart.md](quickstart.md) define evidencia JVM, Room, worker, Compose, PostgreSQL y dispositivo. |

## Project Structure

### Documentation (this feature)

```text
specs/003-ep-cco-categorias-comercios/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
app/src/main/java/com/kipu/app/
├── core/
│   ├── database/{KipuDatabase.kt,RoomMigrations.kt}
│   ├── di/CoreModule.kt
│   ├── finance/domain/model/Movement.kt
│   └── session/{SessionCoordinator.kt,PendingChangesSource.kt}
├── feature/categories/
│   ├── domain/{model,CategoriesRepository.kt,usecase/}
│   ├── data/
│   │   ├── local/{CategoryEntity.kt,CategoryPresentationEntity.kt,MerchantCatalogEntity.kt,CategoryConflictEntity.kt,CategoryDao.kt,MerchantCatalogDao.kt}
│   │   ├── remote/{CategoriesApi.kt,CategoryDtos.kt}
│   │   ├── sync/{CategorySyncScheduler.kt,SyncCategoryCommandsWorker.kt}
│   │   └── OfflineFirstCategoriesRepository.kt
│   ├── di/CategoriesModule.kt
│   └── presentation/{categories,components}/
├── feature/accounts/data/local/{FinancialMovementEntity.kt,FinancialMovementDao.kt,InstrumentSyncOutboxEntity.kt}
├── navigation/{KipuNavHost.kt,SettingsNavigation.kt}
└── feature/settings/presentation/ProfileSettingsScreen.kt

app/src/test/java/com/kipu/app/{core,feature/categories}/
app/src/androidTest/java/com/kipu/app/{core/database,feature/categories,feature/accounts}/
app/schemas/com.kipu.app.core.database.KipuDatabase/4.json

supabase/
├── migrations/                         # migración EP-CCO forward-only
└── tests/database/category_merchant*.sql
```

**Structure Decision**: Se conserva el módulo Android único y la vertical `feature/categories` con frontera `presentation -> domain <- data`. `feature/accounts` conserva el ownership de los movimientos y expone una mutación de clasificación atómica; categorías no duplica el ledger ni crea un editor financiero nuevo.

## Architecture and Implementation Strategy

### Domain and lifecycle

- `Category` representa una raíz o subcategoría por usuario. Una raíz puede estar activa o inactiva; una subcategoría solo es elegible si ella y su raíz están activas.
- Las categorías predeterminadas pertenecen al catálogo Kipu. `CategoryPresentation` es una superposición por usuario de nombre, icono y color, con identidad de categoría estable.
- `MerchantCatalogEntry` pertenece a Kipu; únicamente contiene el nombre normalizado buscable, estado activo, versión de catálogo y momento de última sincronización; no tiene alias ni reglas personales.
- `MovementClassification` contiene `categoryId`, `merchantId` y `merchantProvisionalText` opcionales. Categoría y comercio se actualizan y eliminan independientemente. El texto provisional solo es válido sin comercio seleccionado y nunca crea entradas de catálogo.
- `CreateCustomRootCategory` y `ReactivateRootCategory` usan `FeatureAccessPolicy` con `Capability.CustomCategories` y el conteo de raíces personalizadas activas. Las subcategorías no consumen cupo.

### Persistence and synchronization

- Room migra de v3 a v4: añade categorías owner-scoped, presentaciones de sistema por usuario, caché versionada de comercios y conflictos; agrega las tres referencias de clasificación a `financial_movements`. Los movimientos existentes conservan valores nulos.
- La transacción local de asignación verifica dueño, elegibilidad de categoría y exclusividad comercio/texto provisional; actualiza el movimiento y escribe un comando inmutable de outbox antes de devolver éxito.
- Las ediciones de presentación y los cambios de estado activo/inactivo usan `expectedRevision`. Ante choque remoto, Room conserva la versión local y la remota con tipo `PRESENTATION` o `LIFECYCLE` en `CategoryConflictEntity`; la UI exige elegir una y emite un comando de resolución. No hay last-write-wins.
- El worker por usuario usa `WorkManager`, backoff y recibos remotos `operationId + payloadHash`. Un fallo de red posterior al commit local se reintenta sin duplicar la clasificación. La primera sincronización obtiene el catálogo inicial de comercios y las reconexiones lo actualizan; la UI distingue caché potencialmente desactualizada de catálogo no disponible.

### Remote boundary

- Una migración SQL forward-only agrega restricciones de jerarquía, relaciones owner-safe de movimientos, presentaciones privadas, conflicto auditable y búsqueda de catálogo indexada por nombre normalizado; además crea el catálogo inicial activo con Alimentación, Transporte y Servicios.
- Las operaciones de categorías y clasificación usan RPCs tipadas que derivan el usuario de `auth.uid()`, validan jerarquía/cupo/revisión y escriben cambio+recibo en una única transacción.
- RLS está habilitada y forzada. El cliente solo puede leer el catálogo general de comercios y sus propias categorías, presentaciones, conflictos y movimientos. Se revocan grants de DML directo sobre `merchant_services` para `authenticated`.

### UI and navigation

- La fila Categorías de ajustes abre la ruta de administración. La pantalla lista raíces y subcategorías, permite editar presentación, activar/inactivar y muestra límite Free.
- `CategoryPicker` muestra exclusivamente categorías elegibles; `MerchantPicker` busca en el catálogo por subcadena normalizada y muestra estado vacío con opción de guardar texto provisional.
- Los selectores se mantienen en campos de estado distintos dentro de la nueva ruta `movement/edit/{movementId}` y su `MovementClassificationEditor`. Limpiar/cambiar uno no toca el otro. Formularios y filtros no muestran ni persisten tags.

### Verification strategy

- Dominio: dos niveles, ciclos, elegibilidad, límite de cinco, downgrade, independencia y conflicto.
- Room: migración v3->v4, preservación de movimientos, transacciones atómicas, aislamiento de owner y búsquedas normalizadas.
- Sync: reintentos, colisión de operación, cambios concurrentes, conflicto explícito y resolución.
- PostgreSQL: restricciones, RLS/grants, catálogo read-only, RPC, recibos, cross-user y migración sobre datos existentes.
- Compose/dispositivo: navegación, TalkBack, objetivo táctil de 48dp, escalado al 200%, offline/reinicio/reconexión y estados límite/vacío/conflicto.

## Complexity Tracking

No existen violaciones constitucionales. La outbox y el almacenamiento de conflictos añaden complejidad necesaria para el requisito explícito de conservar ediciones incompatibles y para la operación local-first; no son capas opcionales.
