# Research: EP-PLA - Planes, Límites y Monetización Freemium

**Date**: 2026-09-15  
**Scope**: Phase 0 research for HU-52  
**Spec**: [spec.md](spec.md)

## R-001 - Estructura del Proyecto Android

**Decision**: Mantener `:app` como único módulo y organizar HU-52 bajo `com.kipu.app.feature.plans`, con límites internos de presentación, dominio, datos y sincronización.

**Rationale**: El repositorio contiene únicamente el template inicial y no tiene convención multimódulo. La separación por paquetes entrega testabilidad y responsabilidades claras sin pagar todavía el coste de plugins, APIs entre módulos y configuración de variantes.

**Alternatives considered**: Crear módulos `domain`, `data` y `feature-plans` se descarta para Sprint 1 por complejidad prematura. Colocar toda la feature junto a `MainActivity` se descarta porque mezclaría UI, reglas, Room y proveedor remoto.

## R-002 - Fuente de Diseño Stitch

**Decision**: Usar como referencia estructural la pantalla Stitch `projects/5775615138851387862/screens/b8b4bfdcf384409887e54a975c549797`, dentro de `Kipu V4 Finale`, y aplicar como autoridad normativa `docs/stitch-design-system.md` más `spec.md`.

**Rationale**: El MCP confirmó la pantalla “Pantalla 1B: Selección de Plan”, canvas móvil 780x2412 y design system `Kipu Andean Modernist`. El layout ya resuelve jerarquía y scroll, pero contiene acciones de pago y copy promocional prohibidos.

**Alternatives considered**: Copiar el HTML literalmente se descarta porque incluye “Confirmar y Pagar”, “Gratis”, “Recomendado”, ahorro promocional y filler fiscal. Regenerar la pantalla se descarta porque el artefacto correcto ya existe y una nueva variante perdería trazabilidad.

## R-003 - Estado UI y Navegación

**Decision**: `PlanSelectionScreen` será stateless; `PlanSelectionViewModel` expondrá `StateFlow<PlanSelectionUiState>` y un evento one-shot de confirmación. El host, no el ViewModel, ejecutará navegación.

**Rationale**: Evita que Compose o el ViewModel conozcan `NavController`, facilita pruebas unitarias y asegura que navegar ocurra una sola vez después del commit Room.

**Alternatives considered**: Guardar `NavController` en ViewModel se descarta por acoplamiento Android. Modelar navegación como un booleano permanente en `StateFlow` se descarta por riesgo de replay tras recomposición o recreación.

## R-004 - Selección Inicial Segura

**Decision**: Inicializar la opción visual en Kipu Free y persistirla únicamente al pulsar “Confirmar Plan”. Mensual/Anual/Lifetime nunca se preseleccionan por motivos comerciales.

**Rationale**: Free es permanente y no requiere pago; una selección Premium predeterminada podría percibirse como consentimiento o intención accidental. El default SQL `'FREE'` sigue siendo una defensa de persistencia, no evidencia de confirmación.

**Alternatives considered**: Preseleccionar Anual como hace Stitch se descarta por transparencia. No seleccionar nada es válido, pero agrega fricción sin mejorar la seguridad frente a Free preseleccionado.

## R-005 - Política de Acceso Independiente

**Decision**: `FeatureAccessPolicy` recibe únicamente capacidad solicitada, conteos Free y entitlement efectivo verificado. No recibe ni consulta `PlanPreference`, `CommercialOption` o outbox.

**Rationale**: La ausencia de dependencia hace estructural la regla de que `TRIAL_INTENT` y `PREMIUM_INTENT` no autorizan Premium. En Sprint 1, los motivos canónicos son `FREE_CAPABILITY`, `FREE_LIMIT_REACHED` y `PREMIUM_ENTITLEMENT_REQUIRED`.

**Alternatives considered**: Consultar una preferencia desde la política, usar `isPremium`, conceder Trial local o escribir `feature_access_cache` al confirmar se descarta por violar el principio V de la constitución.

## R-006 - Persistencia Atómica y Revisión Local

**Decision**: Usar una transacción Room que escriba `PlanPreferencesEntity`, `SyncOutboxEntity` y `PlanSelectionSyncStateEntity`. La revisión siguiente es `max(lastIssuedRevision, acceptedRevision) + 1`.

**Rationale**: Una tabla de estado separada conserva la monotonicidad aunque se compacte el outbox. La transacción evita mostrar éxito sin una operación sincronizable durable.

**Alternatives considered**: DataStore se descarta porque no comparte transacción con Room. Calcular `MAX(revision)` solo desde outbox se descarta porque falla tras borrar operaciones completadas. Añadir revisión a `plan_preferences` se descarta porque mezcla transporte con el contrato de negocio de cuatro campos.

## R-007 - Programación Recuperable con WorkManager

**Decision**: Encolar trabajo one-shot único por usuario después del commit, volver a solicitarlo al iniciar/restaurar sesión si hay pendientes y mantener un trabajo periódico de reconciliación como safety net.

**Rationale**: Room y WorkManager no comparten transacción. El escaneo de outbox cierra la ventana de proceso muerto entre commit y enqueue, mientras WorkManager aporta constraints, backoff y supervivencia a reinicios permitidos por Android.

**Alternatives considered**: Navegar después de esperar red se descarta por romper local-first. Un servicio foreground permanente se descarta por coste y UX. Confiar solo en el enqueue inmediato se descarta por la ventana de fallo entre almacenes.

## R-008 - Inyección del Worker

**Decision**: Usar `@HiltWorker`, `HiltWorkerFactory` y AndroidX Hilt Work 1.4.0 para inyectar repositorio/API en `SyncPlanSelectionWorker`.

**Rationale**: Hilt ya es la estrategia DI del proyecto y el worker debe reconstruirse después de muerte del proceso. Las dependencias específicas de WorkManager aún no están declaradas.

**Alternatives considered**: Service locator estático y creación manual del grafo se descartan por ocultar dependencias y dificultar pruebas. Pasar clientes o tokens en `inputData` se descarta por seguridad y límites de serialización.

## R-009 - Frontera Remota Edge Function + RPC

**Decision**: Implementar el contrato lógico `POST /plans/selection` en la Edge Function `plans` y delegar la transición a una única RPC PostgreSQL transaccional.

**Rationale**: La función concentra HTTP, JWT y validación; PostgreSQL serializa cabeza, preferencia y recibo bajo una transacción. Un timeout después del commit converge al reenviar el mismo `operation_id`.

**Alternatives considered**: Upsert directo desde Android se descarta por no garantizar recibos o clasificación de conflictos. Varias escrituras desde Edge se descartan por no ser atómicas. Una RPC directa es viable, pero no preserva por sí sola la ruta HTTP solicitada ni una frontera uniforme de errores.

## R-010 - Transporte Android

**Decision**: Usar Ktor directamente contra `/functions/v1/plans/selection`, obteniendo el bearer token de Supabase Auth y declarando Content Negotiation/JSON como dependencias directas.

**Rationale**: Ktor Android ya está configurado y permite el subpath exacto de la Edge Function. Declarar APIs usadas directamente evita depender de transitivos de Supabase.

**Alternatives considered**: `functions-kt` simplifica invocaciones por nombre, pero la ruta con subpath requiere igualmente control HTTP. Retrofit/OkHttp se descarta para no introducir un segundo stack de red.

## R-011 - Autorización y RLS

**Decision**: Reenviar el JWT del usuario desde Edge a PostgREST y ejecutar RPC `SECURITY DEFINER` propiedad de un rol dedicado `NOLOGIN`, sin `BYPASSRLS`, que no es propietario de las tablas. Usar `search_path` vacío, referencias calificadas y RLS habilitada/forzada con `user_id = auth.uid()` en preferencias, elegibilidad, cabezas y recibos. Revocar DML directo y conceder a `authenticated` solo `EXECUTE` de las RPC. La proyección de historial tiene un escritor server-only separado, de privilegio mínimo y con validación explícita del usuario objetivo. Nunca aceptar `user_id` en requests del cliente.

**Rationale**: Evita IDOR y bypass del protocolo de revisión. RLS actúa como defensa por objeto y no sustituye la autorización del límite HTTP.

**Alternatives considered**: Confiar en `user_id` del body, service-role en Android, una RPC `SECURITY INVOKER` sin DML o escrituras PostgREST directas se descartan. Un definer propietario de la tabla o con `BYPASSRLS` también se descarta porque anularía la defensa por objeto.

## R-012 - Modelo Remoto de Idempotencia

**Decision**: Separar `public.plan_preferences`, `private.plan_selection_heads` y `private.plan_selection_receipts`. Bloquear la cabeza por usuario y clasificar por revisión, `operation_id` y un SHA-256 v1 sobre cinco líneas UTF-8 normalizadas: contrato, UUID minúsculo, revisión decimal, selección mayúscula y timestamp UTC con seis dígitos fraccionarios, separadas por LF y sin LF final.

**Rationale**: Conserva limpio el modelo de negocio y permite APPLIED, DUPLICATE, STALE y CONFLICT bajo concurrencia. Los recibos duran la vida de la cuenta y se eliminan por cascada.

**Alternatives considered**: Timestamps como orden se descartan por clock skew/manipulación. Revisiones estrictamente contiguas se descartan porque entrega offline fuera de orden es normal. Advisory locks se descartan frente a una fila de cabeza explícita y comprobable.

## R-013 - Semántica de Respuestas y Reintentos

**Decision**: Tratar APPLIED/DUPLICATE/STALE/CONFLICT como resultados HTTP 200 terminales; 401 espera sesión del mismo usuario; errores de validación/autorización son terminales; red, timeout, 429 y 5xx son reintentables con payload idéntico.

**Rationale**: Un timeout puede ocultar un commit exitoso; cambiar identidad, revisión o payload rompería la idempotencia. STALE/CONFLICT requieren reconciliación, no renumeración automática.

**Alternatives considered**: Reintentar todo se descarta por loops permanentes. Convertir automáticamente un conflicto en una revisión nueva se descarta porque transforma una intención sin acción explícita del usuario.

## R-014 - Timestamps y Dinero

**Decision**: Usar `java.time.Instant` y conversión Room a epoch microseconds; `selected_at` conserva el evento local y `updated_at` remoto lo asigna PostgreSQL. Los precios son `Long amountMinor` con `currency = PEN`.

**Rationale**: Preserva semántica offline y precisión interoperable sin permitir que el reloj ordene conflictos. Los enteros evitan errores monetarios de punto flotante.

**Alternatives considered**: `Double`/`Float` y last-write-wins por timestamp se descartan por la constitución. Sobrescribir `selected_at` con hora servidor se descarta porque pierde el instante del evento local.

## R-015 - Elegibilidad de Trial

**Decision**: Modelar `TrialEligibilitySnapshot(status, source, verifiedAt, validUntil)` y leerlo mediante `GET /plans/eligibility` desde una proyección privada que el cliente no puede escribir. Solo historial remoto verificado y vigente produce `ELIGIBLE`; un consumo confirmado puede mantener `INELIGIBLE` sin expiración. Sin evidencia vigente, usar `UNKNOWN` y copy no promisorio.

**Rationale**: Sprint 1 no integra Play Billing. El fallback conservador permite informar que existe una oferta sujeta a verificación, sin presentar siete días como aplicables ni inventar elegibilidad.

**Alternatives considered**: Considerar elegible a toda cuenta nueva, reiniciar elegibilidad al reinstalar o confiar en un booleano local se descarta por riesgo de Trial repetido/ficticio.

## R-016 - Estrategia de Pruebas

**Decision**: Separar pruebas JVM de dominio/ViewModel, pruebas instrumentadas de Room/Compose/WorkManager, pruebas Supabase de migración/RLS/concurrencia y pruebas reales de restricciones Android.

**Rationale**: Cada límite falla de forma distinta. Ni mocks de red prueban RLS, ni WorkManager Test prueba Doze real, ni una captura Stitch prueba Compose funcional.

**Alternatives considered**: Cubrir solo happy path UI se descarta por el gate IX. End-to-end exclusivo se descarta porque dificulta aislar reglas deterministas y vuelve lenta la retroalimentación.

## R-017 - Migraciones y Backup

**Decision**: Exportar esquemas Room, versionar migraciones Supabase y excluir estado local sensible del backup automático hasta aprobar una estrategia de restauración ligada a la misma cuenta.

**Rationale**: Restaurar outbox/cache bajo otra sesión puede producir replay o autorización obsoleta. La constitución prohíbe migraciones destructivas y extensión Premium por reinstalación/restauración.

**Alternatives considered**: `fallbackToDestructiveMigration`, cambios manuales en Dashboard y backup irrestricto se descartan. Un restore selectivo por tabla no es posible si todas comparten el mismo archivo Room, por lo que el diseño de backup debe resolverse a nivel de base/proyecto.

## R-018 - Dependencias del Repositorio

**Decision**: Agregar WorkManager, AndroidX Hilt Work/compiler, coroutines-test, WorkManager Test, Navigation Test, Hilt Android Testing y dependencias Ktor JSON; reutilizar las versiones ya centralizadas y añadir un plugin Room alineado a 2.8.5 para schemas.

**Rationale**: Compose, Navigation, Hilt base, Room, Supabase Auth y Ktor Android ya existen. Solo deben añadirse capacidades realmente usadas y declararse directamente.

**Alternatives considered**: Agregar Retrofit, RxJava, un segundo contenedor DI o Billing se descarta. La dependencia Billing preexistente se elimina porque no se usa en HU-52 y no debe aparecer en el artefacto release.

## Resolved Unknowns

- Proyecto y pantalla Stitch: resueltos mediante MCP, con IDs estables registrados.
- Stack Android y versiones: resueltos desde Gradle real del repositorio.
- Endpoint: Edge Function `plans` + RPC PostgreSQL transaccional.
- Persistencia de revisión: tabla Room y cabeza privada remota separadas.
- Recuperación post-commit: rescan de outbox + trabajo único + safety net periódico.
- Elegibilidad sin Play: proyección server-only con vigencia explícita; `UNKNOWN` conservador cuando falta evidencia.
- Conflicto entre `PREMIUM_REQUIRED` y spec: prevalece `PREMIUM_ENTITLEMENT_REQUIRED` de FR-018.
- `Stich Prompts.md`: no existe localmente; el artefacto Stitch extraído y `docs/stitch-design-system.md` son la evidencia disponible, sin afirmaciones adicionales.
