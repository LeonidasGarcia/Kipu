# Implementation Plan: EP-PLA - Planes, Límites y Monetización Freemium

**Branch**: `001-planes-monetizacion-freemium` | **Date**: 2026-09-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/012-ep-pla-planes-monetizacion/spec.md`

## Summary

Implementar HU-52 como una vertical local-first dentro del módulo Android existente: una Pantalla 1B Compose basada en la referencia extraída de Stitch, un `PlanSelectionViewModel` con estado observable, una política de acceso de dominio que mantiene Kipu Free, persistencia Room atómica de preferencia y outbox, y sincronización diferida con Supabase mediante una Edge Function y una RPC PostgreSQL transaccional. En Sprint 1, `TRIAL_INTENT` y `PREMIUM_INTENT` son datos comerciales inertes: no abren Google Play, no escriben entitlements y nunca autorizan Premium.

## Control de la Épica

| Campo | Valor |
|-------|-------|
| Épica | EP-PLA - Planes, Límites y Monetización Freemium |
| Responsable | Leonidas Garcia (Integrante 1) |
| Sprint Target | Sprint 1 - "Kipu arranca con identidad y privacidad" |
| Historia | HU-52 - Elegir Free o prueba Premium |
| Estimación | 5 puntos |
| Objetivo técnico | Preferencias de plan, Pantalla 1B, commit local atómico y sincronización asíncrona segura. |

## Technical Context

**Language/Version**: Kotlin 2.4.20; Java 17; SQL PostgreSQL; TypeScript/Deno únicamente en la Edge Function de Supabase

**Primary Dependencies**: Jetpack Compose BOM 2026.08.00, Material 3, Lifecycle 2.11.0, Navigation Compose 2.10.1, Coroutines 1.11.0, Hilt 2.60.1, AndroidX Hilt 1.4.0, Room 2.8.5, DataStore 1.2.1, Supabase Kotlin BOM/Auth 3.8.0, Ktor 3.5.1; se agregan WorkManager 2.11.2, AndroidX Hilt Work 1.4.0, Ktor Content Negotiation/JSON y dependencias de prueba alineadas al catálogo

**Storage**: Room como fuente local autoritativa; DataStore solo para checkpoint de presentación/onboarding; Supabase PostgreSQL para `plan_preferences` y metadata privada de sincronización

**Testing**: JUnit 4, Kotlin Test, kotlinx-coroutines-test, Room in-memory instrumentado, WorkManager Test, Compose UI Test, Navigation Test, pruebas SQL/RLS de Supabase y validación manual en dispositivo Android real

**Target Platform**: Android nativo, `minSdk 24`, `targetSdk 36`, `compileSdk 37`; Supabase Edge Runtime/PostgreSQL para el límite remoto

**Project Type**: Aplicación móvil Android de un módulo con backend Supabase versionado en el mismo repositorio

**Performance Goals**: Confirmación local y navegación en <= 2 segundos para al menos 95% de la matriz; al menos 95% de reconciliaciones en <= 15 minutos bajo las condiciones permitidas por el sistema operativo definidas en SC-006; UI vertical fluida y sin bloqueo por red

**Constraints**: Local-first; commit Room antes de navegar; reintentos idempotentes; RLS y autorización por objeto; precios en unidades menores enteras y moneda `PEN`; cero cobros, Trials activos o Premium en Sprint 1; no `BillingClient`; no migraciones destructivas; sin Top/Bottom Bar, publicidad, slogans ni filler text

**Scale/Scope**: Una pantalla de onboarding, cuatro opciones visuales, tres valores persistidos de intención, cinco límites Free, cuatro códigos terminales de sincronización y un worker por usuario; no incluye billing, verificación de compra, downgrade ni gestión de excedentes

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

### Pre-Research Gate

| Principio/Gate | Estado | Evidencia de cumplimiento |
|----------------|--------|---------------------------|
| I. Financial Integrity First | PASS | La selección no escribe ledger ni objetos financieros; los precios se modelan como enteros en unidades menores con `PEN`. |
| II. Local-First, Retry-Safe | PASS | Preferencia, revisión y outbox se confirman en una transacción Room; la red no bloquea y cada operación conserva identidad estable. |
| III. Security and Privacy | PASS | El backend deriva `auth.uid()`, RLS aísla filas y no se almacenan credenciales bancarias, tokens de compra ni secretos de servidor en Android. |
| V. Freemium Cannot Alter Financial Truth | PASS | Intención, compra, suscripción y entitlement permanecen separados; el acceso S1 es Free y ningún exceso se elimina. |
| VII. Native Android and Boundaries | PASS | Compose/ViewModel, dominio, persistencia, sync y proveedor remoto tienen responsabilidades separadas; la política de acceso no depende de Android o Supabase. |
| VIII. Specification-Driven | PASS | Componentes, contratos y pruebas conservan trazabilidad a HU-52, RN-001..004 y FR-001..032. Stitch se usa como referencia, no como prueba de implementación. |
| IX. Quality Is Correctness | PASS | El plan incluye dominio, persistencia, migraciones, sync, RLS, errores, accesibilidad, Compose y dispositivo real. |
| X. Product Boundary | PASS | No se mueve dinero ni se inicia una compra; Play Billing, HU-53/HU-54 y HU-57 quedan fuera. |

**Gate result**: PASS para diseño. La implementación integrada continúa condicionada a que HU-01 entregue sesión autenticada y el host de navegación disponga de `LoginScreen` y `BiometricConfigScreen`; actualmente no existen en el código.

### Post-Design Re-check

| Verificación | Resultado |
|--------------|-----------|
| Efectos financieros e invariantes documentados | PASS - [data-model.md](data-model.md) prohíbe vínculos desde intención hacia entitlements o ledger. |
| Retry, conflicto, error y offline documentados | PASS - [plans-selection.openapi.yaml](contracts/plans-selection.openapi.yaml) y el modelo de outbox definen todos los estados. |
| Aislamiento y RLS documentados | PASS - tablas públicas/privadas, privilegios y pruebas cruzadas están definidos. |
| Migraciones y preservación documentadas | PASS - migraciones Room/Supabase son versionadas y nunca usan fallback destructivo. |
| Pruebas en límites de fallo documentadas | PASS - [quickstart.md](quickstart.md) cubre JVM, instrumento, Supabase y dispositivo real. |
| Complejidad constitucional no justificada | PASS - no se introduce ningún módulo o proveedor adicional fuera de los límites necesarios. |

## Architecture and Implementation Strategy

### 1. Referencia de Stitch y Sistema de Diseño

La pantalla fue localizada mediante el MCP de Stitch AI, sin regenerarla:

| Elemento | Referencia |
|----------|------------|
| Proyecto | `Kipu V4 Finale` - `projects/5775615138851387862` |
| Design system | `Kipu Andean Modernist` - `assets/a21e2e45f51e490fa03b92fb8cb83c55` |
| Pantalla | `Pantalla 1B: Selección de Plan` - `b8b4bfdcf384409887e54a975c549797` |
| Canvas | Mobile, `780x2412`, adecuado para scroll vertical continuo |
| Autoridad local | `docs/DESIGN.md` y `spec.md` |

La composición de Stitch se usa para jerarquía, orden y densidad, pero no se copia literalmente. El artefacto contiene divergencias que deben eliminarse:

| Divergencia de Stitch | Tratamiento Compose |
|-----------------------|---------------------|
| “Gratis”, “acceso completo” y cobro al terminar | Mostrar Trial solo con elegibilidad verificada y aclarar que S1 registra intención sin activar compra. |
| “Recomendado” y “Ahorro equivalente a 50%” | Eliminar por ser slogans/promoción no aprobada. |
| “Confirmar y Pagar S/ 49.99” | Mantener siempre “Confirmar Plan”; Lifetime registra `PREMIUM_INTENT`. |
| Segundo CTA “Continuar con Plan Free” | Unificar la interacción en selección única y `ConfirmPlanButton`. |
| Microcopy fiscal/regulatorio de relleno | Eliminar. |
| Colores dinámicos `#005c55`/`#f7f9fb` | Sustituir por tokens normativos `#0F766E`, `#115E59`, `#0F172A`, `#FFFFFF`, `#F8FAFC`. |

`PlanSelectionScreen` usa `Column.verticalScroll`, margen horizontal de 16dp, separación basada en 8dp, tarjetas de 16dp, controles de 12dp y targets mínimos de 48dp. Todos los precios usan `fontFeatureSettings = "tnum"`. La pantalla no aloja `KipuTopAppBar` ni `KipuBottomBar`; el host tampoco debe inyectarlos para esta ruta.

### 2. Presentación, Estado y Navegación

- `PlanSelectionRoute` obtiene `PlanSelectionViewModel` con `hiltViewModel()`, recolecta `PlanSelectionUiState` con ciclo de vida y traduce el evento terminal de confirmación a `onConfirmed()`.
- `PlanSelectionScreen` es stateless: recibe estado y callbacks, no conoce Room, Supabase, WorkManager, `NavController` ni Billing.
- `FreePlanCard`, `PremiumTrialCard` y `ConfirmPlanButton` son componentes privados/reutilizables dentro de la feature y exponen semántica accesible de rol, selección, precio y condiciones.
- `PlanSelectionViewModel` usa `@HiltViewModel`, recibe `@Inject PlanPreferencesRepository`, expone `StateFlow<PlanSelectionUiState>` y emite navegación como evento one-shot separado para evitar repetición tras recomposición.
- La opción inicial es `FREE`, pero no se persiste hasta “Confirmar Plan”. Esta elección conservadora evita una intención Premium accidental.
- Mensual/Anual elegibles producen `TRIAL_INTENT`; Lifetime y opciones sin elegibilidad verificada producen `PREMIUM_INTENT`. La modalidad exacta no se persiste en Sprint 1.
- `LoginScreen` navega a la ruta de selección después de registro exitoso. Tras el commit local, el host navega a `BiometricConfigScreen` y elimina la selección de plan del back stack para no repetir la confirmación por Back.
- El ViewModel no recibe `NavController`; navegación y destino pertenecen al host, conservando la ruta alternativa futura de FR-022 sin acoplarla al dominio.

### 3. Dominio y Acceso Freemium

- `PlanSelection`, `CommercialOption` y `TrialEligibilitySnapshot` son modelos puros.
- `FeatureAccessPolicy` no consume `PlanPreferencesEntity` ni `CommercialOption`; recibe capacidades, conteos Free y un entitlement efectivo verificado separado.
- En Sprint 1 devuelve `Allowed(FREE_CAPABILITY)`, `Denied(FREE_LIMIT_REACHED)` o `Denied(PREMIUM_ENTITLEMENT_REQUIRED)`.
- El término `PREMIUM_REQUIRED` del input se normaliza al identificador canónico `PREMIUM_ENTITLEMENT_REQUIRED` definido por FR-018.
- `TRIAL_INTENT` y `PREMIUM_INTENT` no escriben ni invalidan `FeatureAccessCacheEntity`.
- Los límites se centralizan en `FreePlanLimits(policyVersion=1, instruments=4, customCategories=5, debts=2, goals=2, budgets=2)` y el endpoint devuelve la misma versión.
- El alcance de HU-52 termina en este contrato determinista y sus pruebas unitarias. Integrar la política en todas las features, gestionar downgrade/excedentes o escribir entitlements pertenece a HU-57/HU-58/HU-54.

### 4. Persistencia Local Atómica

Room es la autoridad para el éxito visible. `PlanPreferencesDao.confirmSelection(...)`, anotado con `@Transaction`, realiza en orden:

1. Busca `operationId` antes de cualquier mutación. Si existe con el mismo usuario y contenido original, devuelve la operación previa; si difiere, falla como conflicto local.
2. Si es nueva, lee/bloquea lógicamente el estado de revisión del usuario.
3. Calcula `selectionRevision = max(lastIssuedRevision, acceptedRevision) + 1`.
4. Hace upsert de `PlanPreferencesEntity` con la nueva selección y timestamps locales.
5. Inserta `SyncOutboxEntity` con `operationId` UUID, revisión, contrato v1 y payload inmutable.
6. Actualiza `PlanSelectionSyncStateEntity.lastIssuedRevision`.
7. Confirma la transacción; solo entonces el repositorio informa éxito y solicita trabajo de sincronización.

`PlanSelectionSyncStateEntity` es metadata técnica adicional necesaria para conservar revisiones aunque el outbox completado se compacte. No altera los cuatro campos normativos de `plan_preferences`.

El enqueue de WorkManager no puede ser atómico con Room. Para cerrar esa ventana:

- El repositorio solicita trabajo único inmediatamente después del commit.
- El arranque de la app y la restauración de sesión vuelven a solicitar trabajo si existe outbox pendiente.
- Un trabajo periódico de reconciliación actúa como red de seguridad, sin reemplazar el trabajo one-shot.
- Cambiar de cuenta nunca envía operaciones del usuario anterior; quedan `WAITING_FOR_AUTH` hasta recuperar una sesión del mismo `userId`.

### 5. Sincronización y Límite Supabase

`SyncPlanSelectionWorker` es un `CoroutineWorker` inyectado con Hilt. Se programa como trabajo único por usuario, con constraint de red y backoff. Drena operaciones en orden de creación, pero el servidor decide vigencia por `selectionRevision`.

Los contratos lógicos `GET /plans/eligibility` y `POST /plans/selection` se implementan en la Edge Function `plans`. Android usa Ktor con el access token actual de Supabase. La lectura devuelve una proyección server-only del historial de cuenta o `UNKNOWN`; la escritura valida método, media type, JWT y payload, y delega una sola RPC PostgreSQL transaccional.

La Edge Function reenvía el JWT del usuario a PostgREST y las RPC obtienen el propietario exclusivamente de `auth.uid()`; el request nunca contiene `user_id`. Las RPC son `SECURITY DEFINER`, propiedad de un rol dedicado `NOLOGIN` sin `BYPASSRLS`, con `search_path` vacío, referencias calificadas y solo los grants mínimos. Ese rol no es propietario de ninguna tabla; RLS se habilita y fuerza sobre preferencias, elegibilidad, cabezas y recibos, con políticas `user_id = auth.uid()` para cada operación permitida. Se revoca DML directo a `anon`/`authenticated` y `EXECUTE` a `PUBLIC`/`anon`; `authenticated` solo puede ejecutar las RPC de selección y elegibilidad. Así, la escritura bloquea la cabeza, aplica la preferencia y registra el recibo atómicamente sin evadir autorización por objeto. Solo un proceso server-only separado y autenticado puede actualizar la proyección de historial, con validación explícita del usuario objetivo.

El hash v1 se calcula en servidor sobre UTF-8 de cinco líneas, en este orden: `contract_version`, UUID canónico minúsculo, revisión decimal sin ceros iniciales, selección en mayúsculas y `selected_at` normalizado a UTC con seis dígitos fraccionarios. Los separadores son `LF`, sin `LF` final; el resultado es SHA-256. Android conserva el payload original y nunca calcula identidad con JSON serializado.

| Resultado | Acción local |
|-----------|--------------|
| `APPLIED` | Reconciliar preferencia/timestamp/cupos, actualizar revisión aceptada y completar outbox. |
| `DUPLICATE` | Reconciliar como éxito previo y completar outbox sin repetir efectos. |
| `STALE` | Adoptar revisión/preferencia remota, marcar operación terminal y no reenviar con otra revisión automáticamente. |
| `CONFLICT` | Conservar preferencia remota, marcar conflicto terminal no sensible y requerir una nueva acción explícita para otra selección. |
| `UNAUTHENTICATED` | Pasar a `WAITING_FOR_AUTH`; refrescar sesión y reenviar exactamente la misma operación para el mismo usuario. |
| `INVALID_REQUEST`/`UNSUPPORTED_VERSION`/`FORBIDDEN` | Marcar error terminal, mantener selección local Free-safe y no entrar en loop. |
| `UNAVAILABLE`/429/timeout/respuesta ambigua | Mantener `PENDING`, respetar `Retry-After` cuando exista y reintentar payload idéntico. |

La función/RPC no tiene privilegios, triggers ni llamadas hacia billing, suscripciones, `entitlements`, claims Premium o `feature_access_cache`.

### 6. Migraciones y Configuración Segura

- Crear Room database v1 con schema exportado y versionado; no usar `fallbackToDestructiveMigration`.
- Crear migración Supabase versionada para `plan_preferences`, proyección de elegibilidad server-only, cabeza de revisión, recibos, constraints, índices, grants, RLS, rol ejecutor y funciones.
- Los objetos de sincronización remota viven en esquema privado; `plan_preferences` permanece en `public` con RLS habilitado.
- Probar migración desde base limpia y datos representativos; un objeto remoto homónimo inesperado debe detener la migración para reconciliar drift.
- Android solo recibe URL y publishable/anon key apropiada; nunca `service_role` ni secretos de servidor.
- Excluir del backup automático cualquier base/cache/outbox mientras no exista una estrategia de restauración aprobada que impida replay entre cuentas o extensión de acceso Premium.
- Aunque Billing 9.1.0 ya está declarado, esta feature no importa ni invoca `BillingClient`; un chequeo estático lo valida antes de release.

## Testing Strategy

| Capa | Herramientas | Cobertura mínima |
|------|--------------|------------------|
| Dominio | JUnit, Kotlin Test | Mapeo de intención, límites y todos los resultados del contrato mínimo `FeatureAccessPolicy`; prueba explícita de que intención no equivale a entitlement, sin integrar aún todas las features de HU-58. |
| ViewModel | JUnit, coroutines-test, repositorio fake | Selección inicial Free, chips, loading/error, una sola confirmación, evento one-shot y navegación únicamente tras commit local. |
| Room | Room in-memory en `androidTest` | Transacción preferencia+revisión+outbox, rollback total, UUID estable, monotonicidad, recuperación tras reinicio y migraciones. |
| Worker | WorkManager Test y Ktor mock | Constraint de red, identidad de payload, backoff, auth por usuario y matriz APPLIED/DUPLICATE/STALE/CONFLICT/errores. |
| Compose | `androidx.compose.ui.test` | Contenido, selección única, `tnum`, ausencia de barras/promoción/filler, targets 48dp, scroll, semántica, fuente 200% y callback de navegación. |
| Supabase | Supabase CLI, pgTAP/integration tests | RLS efectiva bajo rol ejecutor en las cuatro tablas user-owned, grants, auth, frontera del escritor de elegibilidad, concurrencia, recibos, vector de hash canónico, entrega 3-2-1, timeout post-commit y cero escrituras de entitlement. |
| Plataforma | Dispositivo Android real | Offline/reconexión, proceso muerto, Doze, restricción de batería, force-stop/reapertura y límites del SLA. |

Los cuatro escenarios Gherkin oficiales de HU-52 son obligatorios. Los escenarios complementarios de `spec.md` cubren persistencia, concurrencia, aislamiento, accesibilidad y no destrucción.

## Project Structure

### Documentation (this feature)

```text
specs/012-ep-pla-planes-monetizacion/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── feature-access-policy.md
│   └── plans-selection.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md                         # Creado posteriormente por /speckit.tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/kipu/app/
├── KipuApplication.kt
├── MainActivity.kt
├── navigation/
│   └── KipuNavHost.kt
├── core/
│   ├── database/
│   │   ├── KipuDatabase.kt
│   │   └── DatabaseConverters.kt
│   ├── network/
│   │   └── SupabaseNetworkModule.kt
│   └── di/
│       └── CoreModule.kt
└── feature/plans/
    ├── domain/
    │   ├── model/
    │   ├── FeatureAccessPolicy.kt
    │   ├── PlanPreferencesRepository.kt
    │   └── ConfirmPlanSelection.kt
    ├── data/
    │   ├── local/
    │   │   ├── PlanPreferencesEntity.kt
    │   │   ├── FeatureAccessCacheEntity.kt
    │   │   ├── SyncOutboxEntity.kt
    │   │   ├── PlanSelectionSyncStateEntity.kt
    │   │   └── PlanPreferencesDao.kt
    │   ├── remote/
    │   │   ├── PlanSelectionApi.kt
    │   │   └── PlanSelectionDtos.kt
    │   ├── sync/
    │   │   ├── SyncPlanSelectionWorker.kt
    │   │   └── PlanSyncScheduler.kt
    │   └── OfflineFirstPlanPreferencesRepository.kt
    ├── di/
    │   └── PlansModule.kt
    └── presentation/
        ├── PlanSelectionRoute.kt
        ├── PlanSelectionScreen.kt
        ├── PlanSelectionViewModel.kt
        └── PlanSelectionUiState.kt

app/src/test/java/com/kipu/app/feature/plans/
├── domain/
└── presentation/

app/src/androidTest/java/com/kipu/app/feature/plans/
├── data/
├── sync/
└── presentation/

app/schemas/                         # Esquemas Room versionados

supabase/
├── migrations/
├── functions/plans/
└── tests/
```

**Structure Decision**: Mantener un único módulo `:app` porque el repositorio aún es mínimo y no existe una convención multimódulo. La separación por `core` y `feature/plans` establece límites verificables sin introducir complejidad Gradle prematura. El backend Supabase se versiona en `supabase/` para que migraciones, función y pruebas formen parte de la misma revisión.

## Dependencies and Delivery Gates

- **HU-01**: Debe entregar una sesión Supabase válida, `userId` estable y callback de registro exitoso. EP-PLA no implementa autenticación.
- **Pantalla 1C**: El host debe aportar `BiometricConfigScreen`; EP-PLA solo emite confirmación. La ausencia actual impide afirmar el recorrido integrado, pero no bloquea construir/probar la feature aislada.
- **Supabase de desarrollo**: Antes de migrar, inventariar objetos, grants y configuración real. No se asume que un mock o contrato equivale a despliegue.
- **Stitch Prompt 1**: `Stich Prompts.md` no está presente en el repositorio. La pantalla y design system ya extraídos mediante MCP constituyen la referencia disponible; si el archivo se incorpora, debe archivarse para trazabilidad sin reemplazar `spec.md` o `docs/DESIGN.md`.
- **ADR**: `ADR-012-freemium-intent` debe aprobar separación de intención/entitlement, versionado y resolución de conflictos antes de implementar el backend.

## Definition of Done

- Los cuatro escenarios Gherkin oficiales y los escenarios técnicos aplicables pasan en CI y dispositivo.
- La transacción Room demuestra atomicidad entre preferencia, revisión y outbox, incluido rollback.
- El worker y el backend demuestran APPLIED, DUPLICATE, STALE, CONFLICT y errores seguros.
- RLS y autorización por objeto rechazan acceso cruzado y bypass de escritura.
- Ningún camino desde intención escribe `entitlements` o `feature_access_cache`, invoca Billing o borra datos excedentes.
- La Pantalla 1B coincide con los tokens normativos, es accesible, carece de barras/promoción/filler y navega después del commit local.
- Migraciones Room/Supabase son versionadas, revisables, no destructivas y probadas con datos representativos.
- WorkManager se valida en dispositivo real bajo red, Doze, batería, force-stop y reapertura.
- Otro integrante aprueba la revisión cruzada y el flujo se demuestra en la Review de Sprint 1.
