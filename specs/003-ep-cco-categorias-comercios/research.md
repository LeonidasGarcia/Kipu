# Research: EP-CCO Categorías, Subcategorías y Comercios

## Canonical movement attachment

**Decision**: Adjuntar categoría, comercio y texto provisional a `financial_movements`, no a la tabla heredada `transactions`.

**Rationale**: `financial_movements` es el ledger activo en Android y ya soporta cuentas, tarjetas, operaciones estables y sincronización. Una clasificación en `transactions` excluiría movimientos actuales y crearía dos verdades financieras.

**Alternatives considered**: Extender solo `transactions` se rechaza por duplicación. Crear una tabla de clasificaciones sin referencia al movimiento se rechaza porque no garantiza una única asociación vigente por movimiento.

## Presentation of system categories

**Decision**: Conservar el catálogo Kipu inmutable en identidad y guardar una `CategoryPresentation` owner-scoped para el nombre, icono y color elegidos por cada usuario.

**Rationale**: Una superposición conserva movimientos históricos y evita que una edición de un usuario cambie la presentación de los demás.

**Alternatives considered**: Mutar la fila de categoría del sistema se rechaza por ser compartida. Duplicar categorías del sistema por usuario se rechaza porque pierde identidad común y complica migración/historial.

## Hierarchy and Free quota

**Decision**: Validar jerarquía, owner, estado y cupo tanto en dominio/Room como en RPC PostgreSQL. El cupo cuenta solamente raíces personalizadas activas.

**Rationale**: La validación local permite operar sin red; la remota impide que dispositivos concurrentes, datos manipulados o reintentos excedan el límite. Las subcategorías heredan elegibilidad de la raíz y no consumen cupo propio.

**Alternatives considered**: Validar solo en UI o Room se rechaza por no proteger la sincronización. Limitar todas las categorías activas se rechaza por contradecir FR-007.

## Conflict handling

**Decision**: Una edición de presentación o un cambio de estado activo/inactivo usa revisión esperada; al detectar versiones incompatibles se guardan ambas en un conflicto tipado y el usuario elige cuál aplicar.

**Rationale**: FR-018 y el principio local-first prohíben la sobrescritura silenciosa. Esta regla afecta la presentación o elegibilidad de categoría, nunca importes ni efectos de ledger.

**Alternatives considered**: Last-write-wins y first-write-wins se rechazan porque eliminan una edición válida sin elección del usuario. Fusionar campos automáticamente se rechaza por producir una presentación o estado no elegido.

## Merchant search and catalog authority

**Decision**: El cliente busca solo entradas Kipu por subcadena de `normalized_name` tras normalizar mayúsculas, espacios y acentos. El catálogo es read-only para usuarios autenticados.

**Rationale**: FR-019 permite coincidencia parcial pero prohíbe alias, equivalencias semánticas e invención de resultados. La autoridad del catálogo queda en Kipu.

**Alternatives considered**: Búsqueda difusa/semántica se rechaza por falta de evidencia. Permitir alta de comercios por usuarios se rechaza porque pertenece al alcance diferido y contradice el catálogo controlado por Kipu.

## Remote authorization

**Decision**: Aplicar RLS forzada y grants mínimos; los comandos usan RPCs con `auth.uid()` y validación de owner. El catálogo solo concede `SELECT` a `authenticated` sobre entradas activas de sistema.

**Rationale**: Las filas privadas deben aislarse por objeto. La actualización directa de `merchant_services` por clientes existentes debe retirarse para cumplir el catálogo general controlado por Kipu.

**Alternatives considered**: Confiar en que el cliente incluya `user_id` se rechaza por BOLA/IDOR. Mantener DML directo de comercio se rechaza por permitir alterar el catálogo.
