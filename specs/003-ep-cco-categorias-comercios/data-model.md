# Data Model: EP-CCO

## Category

| Field | Rules |
|-------|-------|
| `id` | Identidad estable global. |
| `ownerId` | Obligatorio para categorías personalizadas; vacío para sistema. |
| `parentId` | Vacío para raíz; una subcategoría referencia una raíz del mismo owner o catálogo. |
| `origin` | `SYSTEM` o `CUSTOM`; solo `CUSTOM` consume cupo Free. |
| `categoryType` | `EXPENSE`, `INCOME` o `GENERAL`; las categorías históricas pasan a `GENERAL` sin inferir por movimientos. |
| `isActive` | Una subcategoría es elegible solo si ella y su raíz están activas. |
| `revision` | Incrementa por mutación y participa en detección de conflicto. |

**Invariants**: Máximo dos niveles; sin autorreferencia ni ciclos; padre e hijo comparten propietario/origen aplicable y `categoryType`. Las raíces `GENERAL` y sus subcategorías aparecen en ambas pestañas. Una raíz inactiva inhabilita nuevas asignaciones de toda su rama y no altera movimientos históricos.

Las nuevas raíces toman el tipo seleccionado en la pestaña Gastos o Ingresos. Las subcategorías heredan el tipo de su raíz y se rechaza un tipo explícito incompatible. Las filas preexistentes se migran como `GENERAL`, preservando las referencias históricas de movimientos.

## CategoryPresentation

| Field | Rules |
|-------|-------|
| `categoryId` + `ownerId` | Identidad compuesta única. |
| `name`, `icon`, `color` | Presentación editable por usuario. |
| `revision` | Compare-and-set para ediciones remotas. |

Una categoría personalizada puede usar su propia presentación; una categoría de sistema usa su presentación owner-scoped. Ninguna edición cambia la identidad de la categoría enlazada por movimientos.

## MerchantCatalogEntry

| Field | Rules |
|-------|-------|
| `id` | Identidad Kipu estable. |
| `name` | Nombre visible del catálogo. |
| `normalizedName` | Forma buscable normalizada; permite subcadena. |
| `isActive` | Solo entradas activas se muestran y se asignan. |
| `catalogVersion`, `lastSyncedAt` | Identifican la última versión local para comunicar resultados potencialmente desactualizados. |

No tiene owner, alias, regla personal ni operación de alta por cliente.

## MerchantAliasRule (Sprint 5 / HU-16)

| Field | Rules |
|-------|-------|
| `id` | Identidad estable de la regla. |
| `ownerId` | Obligatorio; cada usuario ve y modifica solo sus reglas. |
| `normalizedPattern` | Patrón normalizado guardado para comparación exacta: case-fold, quitar acentos, recortar y colapsar espacios; conserva puntuación y no admite comodines ni matching parcial. |
| `merchantId` | Referencia obligatoria a un comercio canónico activo del catálogo Kipu. |
| `revision`, `deletedAt` | Revisión para sincronización; baja lógica/tombstone para propagar eliminación sin DELETE directo. |

Crear toda regla nueva requiere Premium y confirmación manual del comercio canónico. Si varias reglas elegibles con el mismo patrón apuntan a comercios distintos, la evaluación queda en revisión humana, sin resolver por prioridad. Una regla no crea ni edita catálogo y solo afecta señales futuras.

El diseño remoto reutiliza `merchant_rules` para estas filas owner-scoped después de validar y ajustar sus constraints/RPCs; su presencia actual no demuestra por sí sola que ya cumpla HU-16. `merchant_rules.category_id` no representa la preferencia personal de categoría de HU-17.

## MovementClassification

| Field | Rules |
|-------|-------|
| `movementId` | Referencia a `financial_movements`; una clasificación actual por movimiento. |
| `categoryId` | Opcional; debe ser elegible y pertenecer al mismo usuario o catálogo. |
| `merchantId` | Opcional; debe referenciar una entrada activa del catálogo. |
| `merchantProvisionalText` | Opcional; solo se permite sin `merchantId`; no crea catálogo. |
| `merchantRawText` | Texto fuente exacto de una señal, si existe; se conserva sin normalización ni reemplazo. |

`categoryId` y `merchantId` son independientes. Actualizar o limpiar uno preserva el otro.

La comparación de HU-16 usa una copia normalizada de `merchantRawText`; el valor fuente se conserva byte por byte en el modelo de movimiento/evidencia y no se presenta como un alias ni como texto provisional. La búsqueda directa del catálogo sigue usando subcadena y es un flujo distinto.

En transacciones nuevas, una categoría `EXPENSE` solo puede clasificar gastos y una `INCOME` solo ingresos; `GENERAL` puede clasificar ambos. Las transferencias no admiten categoría. La migración no reclasifica ni elimina categorías de transacciones históricas.

`MovementClassification` es un valor propiedad de un movimiento, no una tabla independiente: se persiste junto con el movimiento canónico y conserva referencias nulas para movimientos históricos no clasificados.

## MerchantCategoryPreference (Sprint 5 / HU-17)

| Field | Rules |
|-------|-------|
| `ownerId` + `merchantId` | Identidad compuesta; una preferencia vigente por usuario y comercio. |
| `categoryId` | Categoría elegida por el usuario; debe pertenecer al usuario o al catálogo y ser elegible para el tipo futuro. |
| `revision`, `updatedAt` | Permiten sincronización idempotente y detectar cambios fuera de orden. |
| `deletedAt` | Tombstone opcional para propagar la baja. |

La preferencia es distinta de `merchant_rules.category_id` y `CategoryPresentation`. No agrega una puerta Premium independiente. Solo se aplica a operaciones futuras del mismo usuario/comercio si la categoría y su raíz están activas, habilitadas por plan y compatibles con el tipo; si no, se pide una elección. Nunca reescribe movimientos confirmados.

## CategoryConflict

| Field | Rules |
|-------|-------|
| `id` | Identidad estable del conflicto. |
| `categoryId`, `ownerId` | Vinculan el conflicto a una categoría privada. |
| `type` | `PRESENTATION` o `LIFECYCLE`; determina qué atributos incompatibles se resuelven. |
| `localVersion`, `remoteVersion` | Dos versiones completas conservadas, incluida la presentación o el estado activo/inactivo según el tipo. |
| `status` | `OPEN` o `RESOLVED`; solo el usuario propietario puede resolver. |
| `resolutionOperationId` | Hace idempotente la decisión del usuario. |

## State transitions

```text
Category root: ACTIVE <-> INACTIVE
Subcategory eligibility: eligible <-> blocked by own/root inactivity
CategoryConflict: OPEN -> RESOLVED (presentation or lifecycle)
Movement classification: unclassified <-> category assigned
Movement merchant: none <-> catalog merchant | provisional text
MerchantAliasRule: absent -> active -> revised -> tombstoned
MerchantCategoryPreference: absent -> eligible -> replaced -> tombstoned
```

Transiciones prohibidas: crear un tercer nivel, crear ciclo, activar una raíz personalizada que supera el cupo Free, asignar categoría no elegible, conservar comercio y texto provisional simultáneamente, resolver conflicto mediante sobrescritura automática, evaluar una señal sin entitlement+consentimiento, elegir un comercio entre destinos alias distintos sin revisión, o aplicar una preferencia a una categoría inactiva/bloqueada/incompatible.
