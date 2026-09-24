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

## MovementClassification

| Field | Rules |
|-------|-------|
| `movementId` | Referencia a `financial_movements`; una clasificación actual por movimiento. |
| `categoryId` | Opcional; debe ser elegible y pertenecer al mismo usuario o catálogo. |
| `merchantId` | Opcional; debe referenciar una entrada activa del catálogo. |
| `merchantProvisionalText` | Opcional; solo se permite sin `merchantId`; no crea catálogo. |

`categoryId` y `merchantId` son independientes. Actualizar o limpiar uno preserva el otro.

En transacciones nuevas, una categoría `EXPENSE` solo puede clasificar gastos y una `INCOME` solo ingresos; `GENERAL` puede clasificar ambos. Las transferencias no admiten categoría. La migración no reclasifica ni elimina categorías de transacciones históricas.

`MovementClassification` es un valor propiedad de un movimiento, no una tabla independiente: se persiste junto con el movimiento canónico y conserva referencias nulas para movimientos históricos no clasificados.

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
```

Transiciones prohibidas: crear un tercer nivel, crear ciclo, activar una raíz personalizada que supera el cupo Free, asignar categoría no elegible, conservar comercio y texto provisional simultáneamente, o resolver conflicto mediante sobrescritura automática.
