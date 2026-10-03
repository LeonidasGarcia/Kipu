# Implementation Plan: EP-PLA - Planes, Límites y Monetización Freemium

**Propagated**: 2026-10-02 — Added the approved S4 HU-58/HU-59 capability and signed offline lease design; preserved the S1/S2/S3 plan.

**Propagated**: 2026-09-26 — Clarified the distinct meanings of Billing `PENDING`, verification `RETRYABLE`, and the HU-52 selection-sync outbox `PENDING`.

**Propagated**: 2026-09-15 — Updated from spec.md refinement (adopción de tokens del design system Stitch "Kipu Andean Modernist" como única fuente de verdad visual).

**Propagated**: 2026-09-15 — Updated from spec.md refinement (cleanup sin cambios funcionales: eliminadas las referencias residuales al sistema de diseño previo; el plan ya apunta a `docs/stitch-design-system.md`).

**Propagated**: 2026-09-16 — Updated from spec.md refinement (fidelidad de layout, componentes y copy con Pantalla 1B Stitch; Free informativo, Trial estático, Anual preseleccionado, dos CTAs y criterios SC-002/SC-004/SC-008 actualizados).

**Propagated**: 2026-09-26 — Added the S3 architecture for HU-53/HU-54/HU-56, including the onboarding Trial versus Play-backed offer boundary, while preserving the S1/S2 plan and traceability history.

**Branch**: `012-ep-pla-planes-monetizacion` | **Date**: 2026-09-26 | **Spec**: [spec.md](spec.md)

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

**Constraints**: Local-first; commit Room antes de navegar; reintentos idempotentes; RLS y autorización por objeto; precios en unidades menores enteras y moneda `PEN`; cero cobros, Trials activos o Premium en Sprint 1; no `BillingClient`; no migraciones destructivas; sin Top/Bottom Bar; copy comercial limitado a la composición aprobada de Pantalla 1B Stitch

**Scale/Scope**: Una pantalla de onboarding con dos tarjetas informativas, tres alternativas Premium seleccionables y dos CTAs; tres valores persistidos de intención, cinco límites Free más una fila informativa de núcleo manual, cuatro códigos terminales de sincronización y un worker por usuario; no incluye billing, verificación de compra, downgrade ni gestión de excedentes

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
| VIII. Specification-Driven | PASS | Componentes, contratos y pruebas conservan trazabilidad a HU-52, RN-001..004 y FR-001..032. Pantalla 1B Stitch es referencia normativa de fidelidad para layout/componentes, pero continúa sin constituir evidencia de implementación. |
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
| Autoridad local | `docs/stitch-design-system.md` y `spec.md` |

La composición de Stitch se adopta como referencia normativa de fidelidad para jerarquía, orden, densidad, componentes y copy aprobado:

| Componente Stitch | Tratamiento Compose |
|-------------------|---------------------|
| Encabezado | Mostrar «Selecciona tu Plan» y «Configuración inicial de cuenta y suscripción». |
| Kipu Free | Tarjeta informativa no seleccionable con badge «Permanente», texto «No requiere método de pago» y seis filas `check_circle` (núcleo manual más cinco límites). |
| Trial | Tarjeta estática «Prueba Premium Gratis por 7 días», tag «Completo» y descripción aprobada; la elegibilidad no altera su copy. |
| Premium Anual | Primera opción seleccionable y preseleccionada, con «Recomendado», «Ahorro equivalente a 50%» y «S/ 29.99 / año». |
| Premium Mensual / Lifetime | Mostrar «S/ 4.99 / mes» y «S/ 49.99 pago único» con sus descripciones Stitch. |
| Nota y acciones | Nota `info` literal, CTA primario «Confirmar Plan», CTA secundario «Continuar con Plan Free» y footer fiscal aprobado. |
| Colores `#005c55`/`#f7f9fb` | Mantener como tokens normativos; referencia completa en `docs/stitch-design-system.md`. |

`PlanSelectionScreen` usa `Column.verticalScroll`, margen horizontal de 16dp, separación basada en 8dp, tarjetas de 16dp, controles de 12dp y targets mínimos de 48dp. Todos los precios usan `fontFeatureSettings = "tnum"`. La pantalla no aloja `KipuTopAppBar` ni `KipuBottomBar`; el host tampoco debe inyectarlos para esta ruta.

### 2. Presentación, Estado y Navegación

- `PlanSelectionRoute` obtiene `PlanSelectionViewModel` con `hiltViewModel()`, recolecta `PlanSelectionUiState` con ciclo de vida y traduce el evento terminal de confirmación a `onConfirmed()`.
- `PlanSelectionScreen` es stateless: recibe estado y callbacks, no conoce Room, Supabase, WorkManager, `NavController` ni Billing.
- `FreePlanCard`, `PremiumTrialCard`, `PremiumOptionCard`, `CommercialInfoNote`, `ConfirmPlanButton` y `ContinueFreeButton` son componentes privados dentro de la feature y exponen semántica accesible de contenido, rol, selección, precio y condiciones.
- `PlanSelectionViewModel` usa `@HiltViewModel`, recibe `@Inject PlanPreferencesRepository`, expone `StateFlow<PlanSelectionUiState>` y emite navegación como evento one-shot separado para evitar repetición tras recomposición.
- La opción Premium inicial es `ANNUAL`, según la referencia Stitch. Kipu Free no participa en el grupo de selección y se confirma exclusivamente mediante «Continuar con Plan Free».
- Ambos CTAs convergen en el mismo commit local-first: el primario confirma la alternativa Premium seleccionada y el secundario confirma explícitamente `FREE`; solo después del commit se emite el evento one-shot de navegación.
- El copy Trial se renderiza siempre. `TrialEligibilitySnapshot` se conserva en estado únicamente para decidir `TRIAL_INTENT` frente a `PREMIUM_INTENT`, no para variar la presentación.
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

La tabla siguiente describe resultados de entrega de la selección HU-52. Su estado local `PENDING` significa que una operación de outbox aún espera sincronización; no es el estado `PENDING` de una compra informado por Google Play ni el resultado `RETRYABLE` de una verificación de Billing.

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
- Billing no se declara ni se empaqueta en Sprint 1; la auditoría inspecciona fuentes, dependencias y el APK antes de release.

## Testing Strategy

| Capa | Herramientas | Cobertura mínima |
|------|--------------|------------------|
| Dominio | JUnit, Kotlin Test | Mapeo de intención, límites y todos los resultados del contrato mínimo `FeatureAccessPolicy`; prueba explícita de que intención no equivale a entitlement, sin integrar aún todas las features de HU-58. |
| ViewModel | JUnit, coroutines-test, repositorio fake | Anual preseleccionado, cambio entre tres Premium, confirmación Premium/Free por CTAs separados, loading/error, doble-submit, evento one-shot y navegación únicamente tras commit local. |
| Room | Room in-memory en `androidTest` | Transacción preferencia+revisión+outbox, rollback total, UUID estable, monotonicidad, recuperación tras reinicio y migraciones. |
| Worker | WorkManager Test y Ktor mock | Constraint de red, identidad de payload, backoff, auth por usuario y matriz APPLIED/DUPLICATE/STALE/CONFLICT/errores. |
| Compose | `androidx.compose.ui.test` | Orden Free→Trial→Anual→Mensual→Lifetime, Free no seleccionable, Anual preseleccionado, seis filas Free, Trial estático, badges/copy/iconos/sufijos/footer aprobados, dos CTAs, `tnum`, ausencia de barras, targets 48dp, scroll y fuente 200%. |
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
- **Stitch Prompt 1**: `Stich Prompts.md` no está presente en el repositorio. La pantalla y design system ya extraídos mediante MCP constituyen la referencia disponible; si el archivo se incorpora, debe archivarse para trazabilidad sin reemplazar `spec.md` ni el archivo de tokens `docs/stitch-design-system.md`.
- **ADR**: `ADR-012-freemium-intent` debe aprobar separación de intención/entitlement, versionado y resolución de conflictos antes de implementar el backend.

## Definition of Done

- Los cuatro escenarios Gherkin oficiales y los escenarios técnicos aplicables pasan en CI y dispositivo.
- La transacción Room demuestra atomicidad entre preferencia, revisión y outbox, incluido rollback.
- El worker y el backend demuestran APPLIED, DUPLICATE, STALE, CONFLICT y errores seguros.
- RLS y autorización por objeto rechazan acceso cruzado y bypass de escritura.
- Ningún camino desde intención escribe `entitlements` o `feature_access_cache`, invoca Billing o borra datos excedentes.
- La Pantalla 1B coincide con los tokens, disposición, componentes y copy aprobados de Stitch (`docs/stitch-design-system.md`), es accesible, carece de Top/Bottom Bar y navega únicamente después del commit local del CTA Premium o Free.
- Migraciones Room/Supabase son versionadas, revisables, no destructivas y probadas con datos representativos.
- WorkManager se valida en dispositivo real bajo red, Doze, batería, force-stop y reapertura.
- Otro integrante aprueba la revisión cruzada y el flujo se demuestra en la Review de Sprint 1.

## Sprint 3 Addendum: Arquitectura de HU-53, HU-54 y HU-56

Este incremento extiende el plan histórico S1/S2 para completar 21 puntos de S3. Las afirmaciones previas que excluían Billing y verificación describen el límite de S1; en S3 se integran con el backend verificador, sin habilitar derechos desde Android. Se mantiene la separación de intención, compra, ciclo del proveedor y entitlement efectivo establecida en S1.

### Contexto técnico y versiones

- El catálogo `gradle/libs.versions.toml` no tiene hoy Google Play Billing. Las notas oficiales confirman Google Play Billing Library **9.1.0** como versión estable publicada el 2026-06-18. S3 agrega esta dependencia al catálogo y a `app/build.gradle.kts`; no degrada las versiones existentes. `androidx.core` ya está en 1.19.0, superior al mínimo 1.9 indicado por las notas de Billing.
- Play Billing obtiene ProductDetails/OfferDetails y precios localizados desde Google Play; S/ 4.99, S/ 29.99 y S/ 49.99 son referencias de negocio para el canal de pruebas, no precios de UI cobrables codificados. La configuración real de producto, base plan y oferta de prueba se registra en `research.md` antes de la prueba integrada.
- La app sigue el límite unidireccional existente: Compose/ViewModel → caso de uso/repository → gateway de Play o API autenticada. Los tipos de Play se traducen a modelos de dominio y no definen por sí mismos autorización Premium.

### Flujo de compra y verificación autoritativa

1. Al mostrar el paywall, el gateway consulta el catálogo y las ofertas de Play. El ViewModel conserva placeholders del tamaño final mientras carga y renderiza solo el `formattedPrice`/periodicidad devuelto por Play. Una oferta trial solo se presenta cuando Play la devuelve y la elegibilidad aplica; el detalle muestra duración y fecha real del primer cobro.
2. Tras consentimiento explícito, el gateway inicia la hoja nativa de compra. Un callback local produce únicamente un token candidato. Android no escribe `billing_purchases`, no llama a una operación de consumo/ack autoritativa y no muta `FeatureAccessCacheEntity` con una respuesta local.
3. El repository manda `productId` y `purchaseToken` a `POST /billing/verify`, servido por `supabase/functions/verify-purchase/index.ts`, adjuntando el JWT de sesión Supabase. La ruta deriva el usuario de Auth, valida método/JSON/JWT, paquete de la app y producto, y consulta el endpoint de Google Play Developer API apropiado al tipo de producto.
4. El backend calcula SHA-256 sobre los bytes UTF-8 exactos del token opaco y escribe el registro de compra y el evento de auditoría en forma idempotente. Una restricción UNIQUE sobre `purchase_token_hash` junto con validación de `user_id` impide que la misma compra se reasigne o autorice otra cuenta; conflictos devuelven rechazo seguro. El token no se guarda, replica ni registra en logs.
5. Solo una respuesta de Play `PURCHASED` con producto, usuario y vigencia válidos permite persistir la compra verificada y derivar acceso. El resultado Billing `PENDING` significa pago aún no completado según Play y no aumenta Premium; token inválido, usuario distinto y error del proveedor tampoco lo aumentan. Una falla temporal de consulta produce `RETRYABLE`, conserva el entitlement previamente verificado y permite reintento; su copy dice que Kipu no pudo verificar temporalmente, no que el pago esté pendiente.
6. Después de persistir la compra verificada y conceder su entitlement efectivo, el backend consulta acknowledgement state y reconoce la compra inicial solo si falta reconocimiento. Si el acknowledge falla, reintentar no duplica compra ni acceso. Los renewals no se reconocen como compras iniciales y Lifetime es no consumible: `consume` nunca se invoca para Lifetime.
7. Android aplica únicamente el resultado autenticado de verificación a la proyección/cache local asociada a la cuenta actual. Un cambio de sesión invalida el contexto del cache. Ninguna selección, callback, fixture o estado local puede crear una nueva concesión.

El contrato usa la función física `verify-purchase`; la ruta lógica documentada es `/billing/verify`. El cuerpo no acepta `user_id` y nunca responde con el purchase token. Los resultados normalizados son `VERIFIED`, `PENDING`, `REJECTED` y `RETRYABLE`; `PENDING` es un pago pendiente confirmado por Play, mientras `RETRYABLE` es una falla temporal al consultar/verificar. El detalle contiene el tipo de producto, la condición de compra/ciclo y la vigencia verificada que la UI necesita.

### Persistencia, esquema y seguridad

Las entidades oficiales de Kipu se reflejan en S3 sin inventar tablas `subscriptions` o `entitlements` que no aparecen en el diccionario V4.2:

| Objeto | Responsabilidad y cambio S3 |
| :--- | :--- |
| `public.billing_products` | Catálogo administrativo de producto (`id`, `store_product_id`, `base_plan_id`, `name`, `plan_type`, `features`, `is_active`). Extender el check de tipo para incluir `PRO_LIFETIME`; solo la API autenticada lee el catálogo y administración controla escritura. |
| `public.billing_purchases` | Registro verificado por usuario: `user_id`, `order_id`, `purchase_token_hash` único, `product_id`, `purchase_state`, `entitlement_state`, `starts_at`, `expires_at`, `verified_at` y timestamps. No hay escritura directa desde cliente; `expires_at = NULL` representa Lifetime. Agregar `REVOKED` al check de estado de derecho. |
| `internal.billing_events` | Auditoría append-only, privada al cliente. S3 registra verificaciones saneadas con `purchase_token_hash`; RTDN no se procesa hasta HU-55. No guardar token crudo ni payload que lo revele. |
| `FeatureAccessCacheEntity` existente | Cache local derivado de compra ya verificada, separado de preferencia/selección. No es autoridad remota ni se restaura entre cuentas como evidencia. |

S3 crea una migración versionada, aditiva y no destructiva para estas tablas en caso de que no existan en el esquema instalado; alinea las definiciones de fuente y agrega `PRO_LIFETIME`/`REVOKED` sin cambiar registros financieros. `CANCELED_ACTIVE` se calcula en dominio desde `purchase_state = CANCELLED` y vigencia no vencida; los estados brutos `GRACE_PERIOD`, `ON_HOLD` y `PAUSED` se normalizan a `IN_GRACE_PERIOD` y `ACCOUNT_HOLD`, respectivamente. `EXPIRED` y `REVOKED` niegan el acceso de esa compra. La agregación de permisos conserva Premium si existe cualquier compra Lifetime verificada vigente.

- Activar RLS en objetos del esquema expuesto. `billing_purchases` permite lectura solo de filas propias; el cliente no recibe grants de inserción/actualización/borrado. `billing_products` queda de lectura para el uso autenticado y escritura administrativa. `internal.billing_events` permanece privada y append-only.
- La Edge Function valida identidad desde JWT y guarda credenciales de Google Publisher API y credenciales Supabase de servidor en secretos de función. Ningún secreto de servicio se incluye en build Android. El acceso privilegiado de escritura se limita al runtime backend; no se agrega una función `SECURITY DEFINER` pública para eludir RLS.
- Índices y constraints respaldan consultas por usuario/estado y unicidad de hash. Operaciones concurrentes sobre el mismo token terminan en un registro canónico o en conflicto de dueño, nunca en dos entitlement.
- La lista actual de `supabase/migrations/` y el source Android no contienen tablas/cliente de Billing; el primer migration y los adaptadores son trabajo nuevo de S3, no compatibilidad ya implementada.

### Ciclo de vida y acceso

La matriz normativa completa está en [spec.md](spec.md), HU-56. La traducción de dominio es:

| Lifecycle Kipu | Base verificada | Acceso |
| :--- | :--- | :---: |
| `ACTIVE` | Compra `PURCHASED` recurrente vigente o Lifetime con `expires_at = NULL` | Premium |
| `IN_GRACE_PERIOD` | Play autoriza el periodo de gracia | Premium temporal, sujeto a la vigencia de Play |
| `ACCOUNT_HOLD` | Play indica hold/pausa sin derecho activo | Free para esa compra |
| `CANCELED_ACTIVE` | Renovación cancelada y `now() < expires_at` | Premium hasta `expires_at`; copy «No se renovará» |
| `EXPIRED` | Periodo vencido confirmado por Play | Free; datos intactos |
| `REVOKED` | Compra revocada por Play | Sin derecho por esa compra |

Un pago Billing `PENDING` confirmado por Play es resultado de verificación sin acceso, no uno de los estados Premium. Una verificación `RETRYABLE` es un error temporal de proveedor: conserva el acceso previo y ofrece reintento. Ambos se mantienen separados del `PENDING` local de la outbox de selección HU-52. El estado de producto, compra, ciclo de vida y permiso efectivo nunca se colapsan en un único campo editable por UI. La pantalla muestra la cancelación y vigencia sin adelantar la sección completa «Mi Plan» de HU-60.

### UI Compose, tokens Stitch y motion

- Mantener una superficie de compra S3 desplazable y tarjetas accesibles, en orden Anual preseleccionado, Mensual y Lifetime. Esta compra es distinta del onboarding HU-52: la tarjeta estática de Trial de la Pantalla 1B solo registra intención y nunca indica una oferta de Play disponible; la superficie S3 consulta Play y muestra únicamente trials/ofertas disponibles y elegibles, o indisponibilidad. Anual usa badge tonal «Más popular», que no promete un ahorro calculado con una moneda/precio distinto. Lifetime usa «Pago único para siempre» y explicita no renovación/no trial.
- Usar `background #F7F9FB`, tarjetas `surface_container_lowest #FFFFFF`, selección `surface_tint #006A63` con borde 2dp, CTA `primary_container #0F766E`/pressed `#005C55`, badge `secondary_container #A8ECE5` con texto `on_secondary_container #266D68`, e Inter. Mantener áreas táctiles 48×48dp, WCAG AA 4.5:1 como mínimo y buscar AAA 7:1 cuando la combinación semántica de tokens lo permita.
- La selección eleva la tarjeta y anima el contorno con spring corto y microescala `1.02`. La carga de precios usa shimmer lineal de 2s sin cambiar el tamaño reservado; el usuario con movimiento reducido recibe un placeholder estático. Los estados cambian con continuidad: `purchase pending` → `verifying` → resultado; check sobrio de éxito de hasta 500ms solo después de `VERIFIED`.
- Éxito, pago `PENDING`, indisponibilidad y error `RETRYABLE` se presentan como estados inline/card dentro de la hoja/superficie actual; no se añade una nueva ruta de éxito/fallo ausente de los prototipos. El pago pendiente explica que falta completarse en Play y que no activa Premium; `RETRYABLE` explica que no se pudo verificar temporalmente, conserva el acceso previo y ofrece reintento. El `PENDING` de sincronización HU-52 no se muestra como estado de compra.
- Mostrar precio y moneda localizados, condiciones, primer cobro real cuando hay trial y enlace a administración de suscripciones Google Play. El vínculo no implementa la futura vista completa HU-60.

### Pruebas, dependencias y gates de entrega S3

- Kotlin/JVM: catalog mapping, estados de carga, interacción Billing gateway y garantía callback/no-Premium; test ViewModel cubre success únicamente tras backend.
- Compose instrumentation: precios reales/falsos localizados mediante gateway fake, selección, target/semántica, placeholder sin layout shift, reduced motion, éxito solo verificado, y copy/UI diferenciados para pago `PENDING` y error de verificación `RETRYABLE`.
- Supabase Edge: JWT/método/body, llamada stub del proveedor, `PENDING`, token inválido y ajeno, idempotencia, ack ya reconocido, error temporal, token scrub y cero escritura desde Android.
- SQL/seguridad: migración con datos representativos, RLS/grants, hash UNIQUE, colisión de propietario, writer del servidor, append-only de `internal.billing_events`, incluyendo concurrencia/replay.
- Canal de pruebas real de Play: completar compras mensual/anual/Lifetime, confirmar los importes/moneda ofertados por Play, probar pendiente cuando el medio lo ofrezca, acknowledgement, estado cancelado vigente, expiración, account hold/gracia según disponibilidad, y demostrar que cancelar la hoja conserva el acceso previo. Fixtures no sustituyen la compra de prueba integrada.
- Gate de alcance: no incluir RTDN/reconciliación/restauración de HU-55 ni la pantalla completa de Mi Plan/Lifetime de HU-60. No declarar soporte de esas historias con esta integración bajo demanda.

### Archivos y estructura S3

```text
gradle/libs.versions.toml
app/build.gradle.kts
app/src/main/java/com/kipu/app/feature/plans/domain/model/BillingProduct.kt
app/src/main/java/com/kipu/app/feature/plans/domain/model/BillingVerificationResult.kt
app/src/main/java/com/kipu/app/feature/plans/domain/model/PurchaseLifecycle.kt
app/src/main/java/com/kipu/app/feature/plans/data/billing/PlayBillingGateway.kt
app/src/main/java/com/kipu/app/feature/plans/data/remote/VerifyPurchaseApi.kt
app/src/main/java/com/kipu/app/feature/plans/presentation/PlanPurchaseViewModel.kt
app/src/main/java/com/kipu/app/feature/plans/presentation/PlanPurchaseScreen.kt
supabase/functions/verify-purchase/index.ts
supabase/functions/verify-purchase/index_test.ts
supabase/migrations/ (timestamp generated by `supabase migration new billing_purchase_verification_s3`)
supabase/tests/database/billing_purchase_lifecycle_test.sql
```

`data-model.md` y `contracts/plans-selection.openapi.yaml` conservan la base HU-52/S1 durante esta propagación. La tarea T069 actualiza el modelo para las entidades/estados de compra y agrega `contracts/verify-purchase.openapi.yaml`; T070 amplía las pruebas de esquema y T071 crea la migración después de que modelo y contrato estén definidos. El contrato de selección permanece sin cambios.

### Referencias técnicas verificadas el 2026-09-26

- [Play Billing Library release notes](https://developer.android.com/google/play/billing/release-notes): 9.1.0 figura estable desde 2026-06-18.
- [Google Play Billing security](https://developer.android.com/google/play/billing/security) y [backend integration](https://developer.android.com/google/play/billing/backend): verificación y reconocimiento en servidor, no conceder por `PENDING`, no consumir un no-consumible.
- [Supabase Edge Function authentication](https://supabase.com/docs/guides/functions/auth) y [function secrets](https://supabase.com/docs/guides/functions/secrets): validar el JWT del usuario y mantener claves/secretos de servidor fuera del cliente.

### Definition of Done de HU-53/HU-54/HU-56

- Los escenarios de [spec.md](spec.md) pasan en pruebas de dominio, API, SQL/RLS, Compose y canal de pruebas real cuando dependen de Google Play.
- Ningún callback ni estado PENDING concede Premium; asociación cross-account, repetición, acknowledgement, y Lifetime no consumible se prueban.
- Los seis estados de HU-56 y el solapamiento Lifetime calculan acceso efectivo correctamente; cancelar conserva acceso solo hasta la expiración verificada.
- La migración es aditiva, compatible con datos representativos, y `billing_purchases` permanece de solo lectura para cliente.
- UI mantiene tokens Stitch, 48dp, contraste AA, estados verificables, fecha del primer cobro, gestión de Play y movimiento reducido.
- Revisión cruzada y evidencia del test track quedan registradas antes de declarar completo Sprint 3.

## Sprint 4 Technical Approach — HU-58/HU-59 (16 pts)

### Goal and boundaries

Apply Premium capabilities only from a validated effective entitlement and provide a bounded offline concession. HU-58 movement filter semantics remain defined by the EP-MOV history contract; this EP-PLA increment supplies the trusted entitlement evidence and applies the same policy to existing Premium quota gates. Free manual/local operations, basic history, and the outbox remain usable after concession expiry. Financial data and movement lifecycle are not modified by entitlement transitions.

### Architecture decisions

- The existing HU-54 Google Play verifier remains the sole issuer path. It returns a signed grant only after successful owner authentication, provider verification, persistence, and effective-entitlement aggregation. `PENDING`, `REJECTED`, and `RETRYABLE` never mint or refresh a grant.
- Extend `POST /billing/verify` compatibly with optional `installationPublicKey`. Older clients continue verification but receive no offline grant; the current client sends a P-256 Android Keystore public key. The owner always comes from Supabase Auth.
- The Edge Function signs exact UTF-8 grant payload bytes with ES256. Claims bind user, installation public-key thumbprint, grant/policy version, effective tier, server verification time, known commercial end, strict `notAfter`, grant ID and key ID. The signing private key stays in Edge Function secrets; Android contains configured public verification keys only.
- Room v17→v18 adds nullable grant and monotonic-anchor columns without altering/deleting existing rows. A v17 cache row has no signed grant and therefore remains Free until a real authenticated re-verification.
- Android validates signature, claims, session owner, local Keystore thumbprint, current boot count and elapsed-realtime anchor before exposing entitlement evidence. It computes trusted time from server-signed time plus monotonic elapsed delta; `System.currentTimeMillis()` is not a lease clock. Missing key/configuration, unknown `kid`, invalid claims, changed boot count or elapsed regression fails closed for Premium.
- `notAfter = min(serverVerifiedAt + 72h, knownEntitlementEnd)`; Lifetime still uses the 72h cap. A transport failure does not extend a previously valid grant. A verified Free/revoked response clears it.
- Update the legacy `public.get_feature_access()` output so `offline_valid_until` is null and can no longer be mistaken for a rolling entitlement grant. This migration is local and versioned; no remote schema or secret is changed by this implementation.
- Continue excluding Room/DataStore/preferences and related local session state from Android backup and device transfer. Do not persist purchase tokens or private keys.

### Delivery phases

1. **Contract and schema**: refine OpenAPI, policy and data-model artifacts; define signed grant payload, key rotation identifier and fail-closed behavior; add Room v18 and an additive local Supabase migration.
2. **Tests first**: cover domain capability classes, signature/claim tampering, owner/install mismatch, unknown key, exact 72h boundary, commercial-expiry boundary, Lifetime, reboot/clock changes, network failure and Free fallback.
3. **Server grant issuance**: accept and validate installation public key, calculate the public-key thumbprint, sign claims using server-only ES256 JWK configuration, return no grant for non-entitling outcomes, and avoid logging or persisting secrets.
4. **Android lease**: create/load the installation key, verify grant bytes with configured public keys, persist the server grant and same-boot elapsed anchor atomically, evaluate trusted time, and remove use of client `Instant.now()` as verification evidence.
5. **Capability integration**: provide validated entitlement evidence to EP-MOV history access and existing instrument/category quota gates; preserve basic/free fallback and present reconnection state on invalid/expired lease.
6. **Acceptance and DoD**: validate Room upgrade/data preservation, local migration/function grants, Edge API fakes, history/basic fallback, feature-policy gates, clock simulation, backups, secrets, and release-key configuration; record constraints in S4 validation artifacts.

### S4 scope-specific files

| Area | Main files |
|---|---|
| Domain policy and grant claims | `app/src/main/java/com/kipu/app/feature/plans/domain/FeatureAccessPolicy.kt`, `.../domain/model/EffectiveEntitlement.kt`, new offline lease models/policy |
| Local persistence and device trust | `.../data/local/FeatureAccessCacheEntity.kt`, `.../data/local/FeatureAccessCacheDao.kt`, `app/src/main/java/com/kipu/app/core/database/KipuDatabase.kt`, Room migration registration, Android Keystore/clock adapters |
| Server verifier | `app/src/main/java/com/kipu/app/feature/plans/data/remote/VerifyPurchaseApi.kt`, `.../data/billing/BillingRepository.kt`, `supabase/functions/verify-purchase/index.ts`, grant signer module and tests |
| Consumers | `app/src/main/java/com/kipu/app/feature/movements/data/PlansMovementEntitlementProvider.kt`, `.../movements/domain/QueryMovementHistory.kt`, and existing category/instrument quota decision points |
| Database | New local migration disabling the legacy rolling offline timestamp; preserve authenticated owner checks and existing privileges/RLS |
| Evidence | `specs/012-ep-pla-planes-monetizacion/validation/quickstart-results.md`, `validation/security-release-audit.md`, and Room schema upgrade tests |

### Risks and operational requirements

- The signed format is fail-closed. Production must configure the Edge Function signing JWK/key ID and matching Android verification public key through the approved release configuration. Never add the signing private key or service-role credentials to the repository or Android artifacts.
- ES256 WebCrypto signatures use the fixed-width `r||s` encoding; Android verification must convert/validate this encoding correctly before using the JCA verifier.
- Room migrations preserve old data but intentionally do not grandfather old unsigned Premium cache rows. A real server revalidation is needed to establish the device-bound grant.
- A reboot, device restore, app reinstall, or unavailable boot counter forces online Premium revalidation. Free local registration, basic queries and queued sync remain available.

### S4 Definition of Done

- FR-048–FR-055 and SC-020–SC-025 map to tasks and pass their applicable unit, instrumentation, Edge Function and local database checks.
- Server grants are only issued for verified effective Premium; Android rejects signature, owner, installation, policy, boot and expiry mismatches.
- `notAfter` is strictly exclusive and bounded by both 72h and commercial expiry, including Lifetime.
- Free/manual/local/outbox/history-basic remain available during outage and after lease expiry; no financial history changes because access changed.
- Room upgrade from v17 preserves existing records and unsigned legacy cache cannot grant Premium; the database migration does not alter remote state.
- Key material, billing tokens and service credentials do not leak into logs, Android artifacts or committed env files; release key configuration requirements are documented.
- Cross-review and S4 validation evidence are recorded before completion.

## S4 UI/UX refinement — 2026-10-03

**Propagated**: 2026-10-03 — FR-056–FR-058 / SC-026–SC-027; preserve signed lease, schema and all S1–S3 artifacts.

Provide explicit restore-and-verify through BillingPurchaseRepository, reusing authenticated verification and signed-cache persistence. This command returns completion rather than depending on offers or an unbounded event stream; it handles no recoverable purchase, pending, transient failure and timeout without manufacturing Premium. History tracks typed feedback, guards concurrent requests and current owner, and recomputes access after completion. Ver Premium continues to open existing offers.

Render an access card with concise title/body/action and semantic informational/warning theme tokens. Announce meaningful state changes politely, keep minimum 48 dp targets and adaptive layout. AnimatedVisibility owns card lifecycle, AnimatedContent renders its lambda target, and reduced motion disables custom transitions. Commercial and monotonic authorization remain independent of presentation. Coordinate with EP-MOV T103–T109; T112/T113 retain their device/release gates and wait for the UI refinement evidence.
