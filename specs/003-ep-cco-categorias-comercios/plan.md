# Implementation Plan: EP-CCO Categorías, Subcategorías y Comercios

**Branch**: `003-ep-cco-categorias-comercios` | **Date**: 2026-10-08 | **Spec**: [spec.md](spec.md)

**Propagated**: 2026-10-08 — Updated from the Sprint 5 refinement in spec.md.
**Propagated**: 2026-10-08 - FR-040/SC-016 aclarados; T029/T036 cubren exploracion con consulta vacia y entrada no vacia normalizada a vacio.

**Input**: Feature specification from `specs/003-ep-cco-categorias-comercios/spec.md`.

## Summary

Extender la base EP-CCO de HU-14/HU-15 y el trabajo de categorías tipadas ya incorporado en Sprint 2 para entregar en Sprint 5 únicamente HU-16 (texto original y alias de comercio) y HU-17 (preferencia personal de categoría por comercio). HU-14/HU-15 se conservan como historia de la base; HU-50 permanece en Sprint 8 y fuera de este incremento.

Un alias Premium compara igualdad exacta entre texto de señal y patrón después de normalizar mayúsculas, minúsculas, acentos y espacios; conserva intacta la cadena fuente y propone una identidad existente del catálogo. La preferencia de categoría es una relación privada distinta, prevalece sobre una sugerencia general solo para operaciones futuras compatibles y elegibles, y nunca reclasifica movimientos confirmados.

La clasificación y evidencia local siguen asociadas a `financial_movements`; no se crea una segunda verdad financiera. Room migra de v18 a v19 con retención de datos. El inventario remoto observado antes de implementar contenía `merchant_rules` y `transactions.merchant_raw_text`, pero no una relación separada de preferencia ni RPCs dedicados S5; las migraciones locales agregan tablas privadas dedicadas y comandos versionados. Room guarda cambio y outbox atómicamente; Supabase valida propietario, entitlement para alias nuevos, categoría elegible y recibos idempotentes antes de aceptar comandos.

## Technical Context

**Language/Version**: Kotlin/JVM con Java 17; SQL PostgreSQL/Supabase; JSON para comandos y sincronización.

**Primary Dependencies**: Jetpack Compose, Material 3, Navigation Compose, Coroutines/Flow, Hilt, Room, WorkManager, Supabase Kotlin Auth/PostgREST y Ktor.

**Storage**: Room `kipu.db` como fuente local inmediata; PostgreSQL/Supabase como estado remoto reconciliado; migraciones Room y SQL versionadas y no destructivas.

**Testing**: JUnit/Kotlin Test, coroutines-test, Room Testing, WorkManager Test, Compose UI/Navigation/Hilt tests, Ktor MockEngine y pgTAP/Supabase CLI.

**Target Platform**: Android nativo minSdk 24, teléfonos/tablets/plegables; backend Supabase PostgreSQL.

**Project Type**: Aplicación Android de módulo único con backend versionado en el mismo repositorio.

**Performance Goals**: Crear, editar, activar o clasificar localmente p95 <= 500 ms; búsqueda local de hasta 100 resultados p95 <= 1 s; listas fluidas a 60 fps para 100 categorías y 10,000 movimientos por usuario.

**Constraints**: Local-first; sin tags; dos niveles sin ciclos; Free cuenta solo raíces personalizadas activas; historial inmutable; búsqueda directa del catálogo conserva subcadena normalizada mientras HU-16 usa igualdad exacta normalizada; toda regla de alias nueva requiere entitlement Premium verificado (o lease offline verificado y acotado hasta su vencimiento) y el procesamiento requiere además consentimiento vigente; las preferencias no tienen una puerta Premium independiente; RLS por propietario; evidencia sensible no se registra en telemetría; migraciones no destructivas; no habilitar release contra un Supabase cuyo historial de migraciones esté detrás del local.

**Scale/Scope**: Este plan conserva como base los incrementos ya documentados de Sprint 2 y agrega solo las dos historias S5 (5 puntos cada una). HU-16 añade preservación de texto fuente, normalizador de alias personal y sincronización Premium; HU-17 añade preferencia owner-scoped y validación de elegibilidad. No incluye ingestión de notificaciones/OCR, bandeja, aprendizaje desde correcciones ni HU-50 de Sprint 8.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Research Gate

| Principle / Gate | Status | Evidence |
|------------------|--------|----------|
| I. Financial Integrity First | PASS | `financial_movements` conserva una sola verdad; categoría y comercio son clasificaciones opcionales, no afectan importes, saldos ni ledger. |
| II. Local-First, Retry-Safe | PASS | Cambios locales y reglas/preferencias usan outbox, IDs, hash y revisión; los comandos Premium offline requieren lease verificado, acotado por entitlement, y los conflictos requieren elección explícita. |
| III. Security and Privacy | PASS CON GATE | Filas y evidencia son owner-scoped; los comandos remotos deben derivar `auth.uid()` y cerrar gaps RLS/RPC antes de release. El catálogo general es read-only y la evidencia de señal no se escribe en logs/telemetría. |
| IV. Safe and Explainable Automation | PASS | Alias se evalúa solo sobre `CaptureCandidate` autorizado, con igualdad exacta normalizada, evidencia conservada y revisión ante ambigüedad; S5 no implementa captura/ingesta ni altera catálogo. |
| V. Freemium Cannot Alter Truth | PASS | El límite bloquea nuevas reglas alias sin entitlement Premium verificado, sin reducir la disponibilidad del registro manual ni alterar categorías/movimientos históricos. |
| VI. Financial Lifecycles Preserve History | PASS | Inactivar o personalizar una categoría no rompe las referencias históricas; ninguna operación borra movimientos. |
| VII. Native Android and Boundaries | PASS | Reglas de jerarquía, cupo, elegibilidad y conflictos se mantienen en dominio Kotlin puro; Room/Supabase/UI permanecen adaptadores. |
| VIII. Specification-Driven | PASS | El alcance S5 traza HU-16/HU-17 y FR-026 a FR-040. HU-14/HU-15 conservan su trazabilidad S2; HU-50 sigue excluida hasta S8. |
| IX. Quality Is Correctness | PASS | Incluye pruebas de dominio, Room v18->v19, sincronización, RLS, migración, accesibilidad y dispositivo real. |
| X. Product Boundary | PASS | No incorpora etiquetas, IA, pagos, Open Banking ni nuevas clases de movimiento. |

**Pre-research result**: PASS.

### Post-Design Re-check

| Verification | Result |
|--------------|--------|
| Ledger y preservación histórica | PASS | [data-model.md](data-model.md) conserva texto fuente separado del comercio canónico y mantiene toda sugerencia S5 fuera de movimientos históricos. |
| Offline, idempotencia y conflicto | PASS | [sync-contract.md](contracts/sync-contract.md) extiende outbox, recibos, revisiones y manejo visible de colisiones para reglas y preferencias. |
| RLS, catálogo y límites de acceso | PASS CON GATE | Los comandos S5 derivan owner de `auth.uid()`; los cambios no mutan `merchant_services` y la migración debe cerrar los huecos de preferencias/RPC antes de release. |
| Migraciones y aislamiento | GATE PENDIENTE | Room actual está en v18 y local Supabase alcanza migraciones de octubre post-S4; Supabase live está detrás y debe alinearse por release antes de validar integración S5. |
| UX, filtros y accesibilidad | PASS | [ui-contract.md](contracts/ui-contract.md) mantiene la búsqueda directa, añade administración de alias y elección de categoría elegible, y comunica revisión/conflicto sin depender solo del color. |
| Verificación integral | PARCIALMENTE COMPLETA | [quickstart.md](quickstart.md) registra 471 pruebas unitarias, pruebas instrumentadas dirigidas, migración local limpia y 39 pruebas pgTAP; advisors, prueba de usabilidad cronometrada, suite instrumentada completa y revisión remota siguen pendientes. |

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
│   │   ├── local/{CategoryEntity.kt,CategoryPresentationEntity.kt,MerchantCatalogEntity.kt,MerchantRuleEntities.kt,CategoryConflictEntity.kt,CategoryDao.kt,MerchantCatalogDao.kt}
│   │   ├── remote/{CategoriesApi.kt,CategoryDtos.kt}
│   │   ├── sync/{CategorySyncScheduler.kt,SyncCategoryCommandsWorker.kt}
│   │   └── OfflineFirstCategoriesRepository.kt
│   ├── di/CategoriesModule.kt
│   └── presentation/{categories,components,merchantrules}/
├── feature/accounts/data/local/{FinancialMovementEntity.kt,FinancialMovementDao.kt,InstrumentSyncOutboxEntity.kt}
├── navigation/{KipuNavHost.kt,SettingsNavigation.kt}
└── feature/settings/presentation/ProfileSettingsScreen.kt

app/src/test/java/com/kipu/app/{core,feature/categories}/
app/src/androidTest/java/com/kipu/app/{core/database,feature/categories,feature/accounts}/
app/schemas/com.kipu.app.core.database.KipuDatabase/19.json   # Room v19 exportado para S5

supabase/
├── migrations/                         # migración S5 forward-only, tras alinear el proyecto live
└── tests/database/{category_merchant,merchant_rules_s5}.sql
```

**Structure Decision**: Se conserva el módulo Android único y la vertical `feature/categories` con frontera `presentation -> domain <- data`. `feature/accounts` conserva el ownership de los movimientos y expone una mutación de clasificación atómica; categorías no duplica el ledger ni crea un editor financiero nuevo.

Este árbol refleja el código resultante de la implementación S5. La migración Room parte de v18 y exporta v19. La historia de S2 que menciona Room v3→v4 y la matriz arquitectónica que lista HU-14..17 en S2 se conservan como antecedentes; para este incremento prevalece la asignación HU-16/HU-17 a S5 del backlog vigente.

## Architecture and Implementation Strategy

### Domain and lifecycle

- `Category` representa una raíz o subcategoría por usuario. Una raíz puede estar activa o inactiva; una subcategoría solo es elegible si ella y su raíz están activas.
- Las categorías predeterminadas pertenecen al catálogo Kipu. `CategoryPresentation` es una superposición por usuario de nombre, icono y color, con identidad de categoría estable.
- `MerchantCatalogEntry` pertenece a Kipu; únicamente contiene el nombre normalizado buscable, estado activo, versión de catálogo y momento de última sincronización; no tiene alias ni reglas personales.
- `MovementClassification` contiene `categoryId`, `merchantId` y `merchantProvisionalText` opcionales. Categoría y comercio se actualizan y eliminan independientemente. El texto provisional solo es válido sin comercio seleccionado y nunca crea entradas de catálogo.
- `CreateCustomRootCategory` y `ReactivateRootCategory` usan `FeatureAccessPolicy` con `Capability.CustomCategories` y el conteo de raíces personalizadas activas. Las subcategorías no consumen cupo.
- `MerchantAliasRule` es privada por usuario, referencia un `MerchantCatalogEntry` activo y compara igualdad exacta después de normalizar mayúsculas/minúsculas, acentos y espacios. La búsqueda manual del catálogo sigue usando subcadena; ninguna regla introduce comodines, prefijos o coincidencia difusa.
- Crear toda regla de alias nueva requiere Premium y confirmación manual del comercio canónico. Evaluar una señal requiere además entitlement y consentimiento vigentes; si falta cualquiera, no se procesa la señal y siguen disponibles la búsqueda y el registro manual.
- Una señal evaluable debe llegar como `CaptureCandidate` con procedencia y estado de revisión definidos por la capacidad de captura aprobada. S5 no agrega ese pipeline. La creación offline Premium debe depender de entitlement verificado o lease firmado/acotado, nunca de un booleano local editable; texto original y contenido de señal no van a logs ni telemetría.
- Alias sin coincidencia conserva el texto fuente separado para revisión. Si reglas elegibles con el mismo texto normalizado resuelven a comercios canónicos distintos, se solicita una decisión humana, sin desempate automático por prioridad.
- `MerchantAliasRule` y `MerchantCategoryPreference` se almacenan en relaciones privadas separadas (`merchant_alias_rules` y `merchant_category_preferences`); ambas son distintas de `merchant_rules.category_id` y `CategoryPresentation`. La preferencia no tiene puerta Premium independiente; solo prevalece sobre sugerencias generales para operaciones futuras cuando comercio, categoría y tipo son compatibles y la categoría está activa y habilitada por plan.
- Cambiar o quitar una regla o preferencia solo afecta sugerencias futuras; nunca cambia movimientos confirmados.

### Persistence and synchronization

- Room v3→v4 describe la base histórica de S2. El destino S5 parte del Room actual v18 y agrega en v19 el texto fuente del comercio en `financial_movements`, reglas alias privadas, preferencias de categoría por comercio y comandos/outbox con revisión e idempotencia; los registros preexistentes permanecen intactos.
- La transacción local de asignación verifica dueño, elegibilidad de categoría y exclusividad comercio/texto provisional; actualiza el movimiento y escribe un comando inmutable de outbox antes de devolver éxito.
- Los comandos locales de alias y preferencia se guardan junto con su proyección en una transacción Room. La eliminación se representa como tombstone; reintentos conservan `operationId`, hash y revisión, y conflictos incompatibles se muestran para resolución explícita.
- Las ediciones de presentación y los cambios de estado activo/inactivo usan `expectedRevision`. Ante choque remoto, Room conserva la versión local y la remota con tipo `PRESENTATION` o `LIFECYCLE` en `CategoryConflictEntity`; la UI exige elegir una y emite un comando de resolución. No hay last-write-wins.
- El worker por usuario usa `WorkManager`, backoff y recibos remotos `operationId + payloadHash`. Un fallo de red posterior al commit local se reintenta sin duplicar la clasificación. La primera sincronización obtiene el catálogo inicial de comercios y las reconexiones lo actualizan; la UI distingue caché potencialmente desactualizada de catálogo no disponible.

### Remote boundary

- El inventario remoto observado antes de S5 ya poseía `merchant_rules`, `merchant_services`, `transactions.merchant_raw_text`, categorías privadas y RPCs de categorías; esto era parcial y no probaba que HU-16/HU-17 estuvieran implementadas. Las migraciones S5 locales agregan las tablas privadas dedicadas `merchant_alias_rules` y `merchant_category_preferences`, conservan el catálogo de solo lectura y agregan RPCs versionados con recibos. `category_presentations` continúa limitado a nombre/icono/color.
- Las operaciones de categorías y clasificación usan RPCs tipadas que derivan el usuario de `auth.uid()`, validan jerarquía/cupo/revisión y escriben cambio+recibo en una única transacción.
- Los nuevos comandos de alias/preferencia derivan el dueño de `auth.uid()`, validan referencias activas/elegibles, revisiones, entitlement Premium verificado para crear alias en el servidor y recibos idempotentes. El cliente puede aceptar un comando offline con una lease Premium firmada y acotada; al sincronizar, el servidor vuelve a validar la proyección de facturación y puede rechazar una lease que ya no cumpla su regla de entitlement vigente. La evaluación de señal además exige consentimiento vigente. No ejecutan DML sobre el catálogo. Soft delete se realiza mediante RPC autorizada; las tablas privadas exponen SELECT owner-scoped y fuerzan RLS.
- Supabase live va detrás de las migraciones locales S4/Octubre. Alinear/aplicar las migraciones pendientes, revisar RLS/grants y confirmar el esquema de destino es un gate antes de integración o release; este plan no autoriza cambios directos en el proyecto remoto.

### UI and navigation

- La fila Categorías de ajustes abre la ruta de administración. La pantalla lista raíces y subcategorías, permite editar presentación, activar/inactivar y muestra límite Free.
- `CategoryPicker` muestra exclusivamente categorias elegibles; `MerchantPicker` busca por subcadena normalizada, abre el catalogo local si la consulta esta vacia o contiene solo espacios, y permite texto provisional cuando una consulta no vacia se normaliza a vacio.
- La gestión de alias solo ofrece guardar una regla tras confirmar manualmente el comercio canónico, muestra el patrón fuente y el comercio destino, exige Premium para cada regla nueva y deja editar/inactivar reglas propias. Un resultado ambiguo o sin regla se presenta para revisión y nunca se autoasigna.
- La preferencia por comercio se edita mediante categoría activa y compatible; al quedar inactiva/bloqueada/incompatible pide nueva elección. Se explica que la opción rige movimientos futuros y no actualiza el historial.
- Las señales se evalúan únicamente después de validar entitlement y consentimiento vigentes; no se agrega en S5 el pipeline de captura/ingesta de HU-50.
- Los selectores se mantienen en campos de estado distintos dentro de la nueva ruta `movement/edit/{movementId}` y su `MovementClassificationEditor`. Limpiar/cambiar uno no toca el otro. Formularios y filtros no muestran ni persisten tags.

### Verification strategy

- Dominio: dos niveles, ciclos, elegibilidad, límite de cinco, downgrade, independencia y conflicto.
- Room: migración base S2 v3->v4 (histórica) y migración S5 v18->v19, preservación byte-for-byte de texto fuente, owner-scope, unicidad de preferencia y transacciones/outbox atómicas.
- Sync: reintentos, colisión de operación, actualizaciones concurrentes de alias/preferencias, tombstones, conflicto explícito, recibos y resolución.
- PostgreSQL: migraciones sobre el head live alineado, restricciones, RLS/grants, catálogo read-only, RPC de alias/preferencia, Premium/consentimiento, recibos, cross-user y datos existentes.
- Compose/dispositivo: navegacion, TalkBack, objetivo tactil de 48dp, escalado al 200%, offline/reinicio/reconexion, estados limite/vacio/conflicto, modo exploracion para consulta vacia y via provisional para consulta no vacia cuya normalizacion queda vacia.

Las verificaciones ejecutadas y pendientes están registradas en [quickstart.md](quickstart.md). Las pruebas locales no habilitan integración o release Supabase: primero se debe reconciliar el historial remoto atrasado con las migraciones versionadas locales y revisar RLS/grants efectivos.

## Complexity Tracking

No existen violaciones constitucionales. La outbox y el almacenamiento de conflictos añaden complejidad necesaria para el requisito explícito de conservar ediciones incompatibles y para la operación local-first; no son capas opcionales.

Para S5, la evidencia original, las reglas alias y las preferencias requieren persistencias separadas porque el dato fuente, la identidad del comercio y la decisión futura del usuario tienen ciclo de vida y controles de acceso distintos. Se conserva el ledger único; se agregan solo proyecciones owner-scoped/outbox y el mapeo del texto fuente existente. La migración local Room v18→v19 y la alineación de Supabase son gates explícitos para evitar perder información o integrar contra un contrato remoto parcial.
