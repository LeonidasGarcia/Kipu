# Sync Contract: Categories and Movement Classification

## Local command envelope

Cada comando persistido antes de sincronizar contiene `operationId`, `ownerId`, tipo, identificador de agregado, `expectedRevision` cuando aplica, carga canónica, `payloadHash`, hora de creación, estado e intentos. La misma combinación `operationId` y hash devuelve el mismo resultado; el mismo ID con hash distinto es una colisión rechazada.

## Command types

| Command | Aggregate | Required remote checks |
|---------|-----------|------------------------|
| `CREATE_CUSTOM_CATEGORY` | Categoría | Owner, raíz/subcategoría, jerarquía, cupo de raíces activas. |
| `UPDATE_CATEGORY_PRESENTATION` | Presentación | Owner, revisión esperada y conflicto explícito. |
| `SET_CATEGORY_ACTIVE` | Categoría | Owner, estado del padre, cupo si reactiva raíz personalizada y revisión esperada para conflicto explícito. |
| `ASSIGN_MOVEMENT_CLASSIFICATION` | Movimiento | Owner del movimiento, categoría elegible, comercio activo y exclusividad comercio/texto. |
| `RESOLVE_CATEGORY_CONFLICT` | Conflicto | Owner, conflicto abierto y operación de resolución única. |

## Results

| Result | Local behavior |
|--------|----------------|
| `APPLIED` | Marcar comando sincronizado y conservar la proyección local. |
| `DUPLICATE` | Aplicar la respuesta de recibo sin crear nuevo efecto. |
| `CONFLICT` | Persistir ambas versiones de presentación o ciclo de vida y mostrar resolución; no reintentar automáticamente. |
| `REJECTED` | Mantener el cambio local visible con error accionable; no modificar historial. |
| `RETRYABLE_FAILURE` | Conservar `PENDING` y reintentar con backoff. |

## Ordering and failure

- La asignación de clasificación de un movimiento se escribe en la misma transacción Room que su outbox.
- El worker se ejecuta por usuario y no expone datos con `LocalAccess.Protected` o `NoOwner`.
- El worker obtiene el catálogo inicial de comercios antes de habilitar resultados de catálogo y lo actualiza al recuperar conectividad. La proyección conserva versión y momento de actualización para informar si los resultados pueden estar desactualizados.
- Un timeout después del commit remoto se resuelve leyendo el recibo por `operationId`; nunca repitiendo un DML ciego.
- Un conflicto de categoría concierne a presentación o estado activo/inactivo. No hay conflicto automático que altere importes, estado o identidad de un movimiento.
