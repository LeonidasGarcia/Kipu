# Sync Contract: Categories and Movement Classification

## Local command envelope

Cada comando persistido antes de sincronizar contiene `operationId`, `ownerId`, tipo, identificador de agregado, `expectedRevision` cuando aplica, carga canónica, `payloadHash`, hora de creación, estado e intentos. La misma combinación `operationId` y hash devuelve el mismo resultado; el mismo ID con hash distinto es una colisión rechazada.

## Command types

| Command | Aggregate | Required remote checks |
|---------|-----------|------------------------|
| `CREATE_CATEGORY` | Categoría | Owner, raíz/subcategoría, jerarquía, cupo de raíces activas y tipo raíz/heredado. |
| `UPDATE_PRESENTATION` | Presentación | Owner, revisión esperada y conflicto explícito. |
| `SET_CATEGORY_ACTIVE` | Categoría | Owner, estado del padre, cupo si reactiva raíz personalizada y revisión esperada para conflicto explícito. |
| `UPDATE_MOVEMENT_CLASSIFICATION` | Movimiento | Owner del movimiento, categoría elegible, comercio activo, exclusividad comercio/texto y fuente `CaptureCandidate` trazable cuando haya texto original. |
| `RESOLVE_CONFLICT` | Conflicto | Owner, conflicto abierto y operación de resolución única. |
| `UPSERT_MERCHANT_ALIAS_RULE` | Regla de alias | Owner derivado de sesión, comercio activo, revisión; Premium vigente solo para crear una regla nueva. |
| `DELETE_MERCHANT_ALIAS_RULE` | Regla de alias | Owner, revisión esperada y tombstone autorizado; no requiere DELETE directo de tabla. |
| `UPSERT_MERCHANT_CATEGORY_PREFERENCE` | Preferencia comercio-categoría | Owner, comercio existente, categoría propia/de sistema, elegibilidad por tipo/estado/plan y revisión. |
| `DELETE_MERCHANT_CATEGORY_PREFERENCE` | Preferencia comercio-categoría | Owner, revisión esperada y tombstone idempotente. |

La creación usa el RPC local `create_category_v1(p_payload jsonb)` con el cuerpo PostgREST `{ "p_payload": { ... } }`. `p_payload.category_type` acepta `EXPENSE`, `INCOME` o `GENERAL`: la raíz nueva recibe el tipo seleccionado; una subcategoría hereda el tipo de su raíz y un tipo explícito incompatible se rechaza. Los clientes anteriores que omiten el tipo crean una raíz `GENERAL`.

El DTO del catálogo remoto expone `category_type`. La migración local y Room conservan el modelo vigente (`origin`, `remote_revision`) y asignan `GENERAL` a toda categoría preexistente, sin inferir el tipo ni reescribir referencias históricas.

Las transacciones nuevas admiten categorías `GENERAL` en gastos e ingresos, y categorías tipadas solo en el tipo coincidente. Una transferencia no admite categoría.

La evaluación de señal aplica la regla alias local solo a un `CaptureCandidate` aprobado con procedencia trazable, después de verificar entitlement y consentimiento vigentes. Crear una regla nueva offline requiere una lease Premium verificada y acotada por la vigencia conocida del entitlement; una bandera local editable no basta. Las reglas se comparan por igualdad exacta sobre texto normalizado; no por prioridad. Si el conjunto de coincidencias conduce a más de un comercio canónico, el resultado es `REVIEW_REQUIRED`. El texto fuente viaja como valor inmutable separado del patrón normalizado y no se expone en logs/telemetría. La preferencia personal se evalúa para una operación futura y, si es elegible, precede a una sugerencia general; no emite una actualización de movimientos confirmados.

Los comandos alias/preferencia son parte del contrato planeado S5, no de las RPCs actualmente observadas en Supabase. Antes de integración deben existir RPCs o un boundary equivalente con checks de servidor, tabla/índice/RLS revisados, recibos idempotentes y migraciones aplicadas. No se debe inferir que la tabla `merchant_rules` actual cumple por sí sola estas reglas ni que `category_presentations` representa preferencias.

## Results

| Result | Local behavior |
|--------|----------------|
| `APPLIED` | Marcar comando sincronizado y conservar la proyección local. |
| `DUPLICATE` | Aplicar la respuesta de recibo sin crear nuevo efecto. |
| `CONFLICT` | Persistir ambas versiones de presentación o ciclo de vida y mostrar resolución; no reintentar automáticamente. |
| `REJECTED` | Mantener el cambio local visible con error accionable; no modificar historial. |
| `REVIEW_REQUIRED` | No asignar comercio; conservar evidencia y pedir selección explícita ante destinos alias distintos. |
| `SIGNAL_NOT_AUTHORIZED` | No crear una nueva regla ni evaluar la señal si falta entitlement o consentimiento vigente; mantener disponible el flujo manual. |
| `RETRYABLE_FAILURE` | Conservar `PENDING` y reintentar con backoff. |

## Ordering and failure

- La asignación de clasificación de un movimiento se escribe en la misma transacción Room que su outbox.
- El worker se ejecuta por usuario y no expone datos con `LocalAccess.Protected` o `NoOwner`.
- El worker obtiene el catálogo inicial de comercios antes de habilitar resultados de catálogo y lo actualiza al recuperar conectividad. La proyección conserva versión y momento de actualización para informar si los resultados pueden estar desactualizados.
- Un timeout después del commit remoto se resuelve leyendo el recibo por `operationId`; nunca repitiendo un DML ciego.
- Un conflicto de categoría concierne a presentación o estado activo/inactivo. No hay conflicto automático que altere importes, estado o identidad de un movimiento.
- Cambios de alias y preferencias se ordenan por revisión y se resuelven explícitamente si son incompatibles; un tombstone se conserva hasta confirmación remota para que un reintento no restaure una regla/preferencia borrada.
- Las migraciones locales de Supabase están por delante del historial live observado. La aplicación de migraciones pendientes, revisión efectiva de RLS/grants y la validación con dos usuarios son gates de integración/release; no se modificó la base remota al producir este contrato.
