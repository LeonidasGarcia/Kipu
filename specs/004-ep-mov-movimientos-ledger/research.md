# Research: EP-MOV Movimientos y ledger

## Decisiones

### 1. Jerarquía de fuentes

La especificación de la épica gobierna el comportamiento; la documentación V4.2 aporta intención de producto y UI; el código/migraciones revelan el estado real. Cuando difieren, se documenta la divergencia y se pide una decisión, sin mezclar modelos silenciosamente.

### 2. Modelo contable canónico

**Decisión Sprint 2**: converger a `transactions` + `internal.ledger_entries`. `financial_movements` queda como estructura heredada que debe migrarse hacia delante.

**Razón**: las entidades V4.2 y el RPC existente ya expresan un registro de negocio separado de sus efectos contables. Es el único modelo que representa una transferencia con débito y crédito atómicos y auditables.

**Alternativa descartada**: mantener doble escritura permanente en ambos modelos; introduce divergencia y duplica reglas de seguridad.

### 3. Representación de transferencias

**Decisión Sprint 2**: una transacción con dos asientos balanceados. `transaction_links` queda para vínculos entre transacciones independientes, como reembolsos o reversas.

Esta decisión alinea el RPC existente y evita doble contabilización.

### 4. Autoridad local y remota

Room es la autoridad inmediata de UI; PostgreSQL es la autoridad reconciliada entre dispositivos. La frontera de aislamiento es `user_id`, derivado de sesión. El alta local y su outbox deben confirmarse en una única transacción. El servidor responde con estado determinista para que el cliente reconcilie sin duplicar.

### 5. Contrato versionado

Se propone `register_transaction_v1` con estados `APPLIED`, `DUPLICATE`, `CONFLICT` y `REJECTED`. La versión impide que una evolución incompatible rompa clientes instalados.

### 6. Duplicados

La idempotencia exacta usa clave y hash del comando normalizado. La similitud es una heurística separada: cuenta origen, tipo, monto y moneda iguales para el mismo usuario en una ventana de cinco minutos. La heurística sólo pide confirmación.

### 7. Reglas de formulario y plan

La categoría es obligatoria para gastos y opcional para ingresos/transferencias en S2. Registro manual, historial y deduplicación son capacidades Free y no consumen cupos. Los filtros avanzados permanecen Premium.

### 8. Alcance de interfaz

Se construye la Pantalla 11 funcional y una Pantalla 10 mínima que permita verificar el alta. Editar, anular, reembolsar y filtrar de forma avanzada pertenecen a historias posteriores.

### 9. Límites de planes

Los valores existentes en `FreePlanLimits.kt` (4/5/2/2/2) se reutilizan. Registrar, ver historial y resolver duplicados no consumen cupos nuevos.

### 10. Guía visual ausente

No se encontró `DESIGN.md` bajo el proyecto ni sus carpetas hermanas. Se reutilizan tema y componentes actuales y se solicita la ubicación de la fuente visual definitiva.

## Hallazgos del repositorio

- Existe un RPC `public.register_transaction` que crea una fila en `transactions` y dos asientos para transferencias.
- El RPC actual recupera por clave idempotente, pero debe comparar también el hash para distinguir repetición de conflicto.
- La validación remota debe cubrir cuenta destino, categoría, comercio, moneda y pertenencia al espacio, no sólo origen.
- Room y parte de EP-CTA usan `financial_movements`; la documentación V4.2 usa `transactions`/`ledger_entries`.
- El plan de EP-CTA ya reconoce la divergencia y exige reconciliación mediante una migración futura.
- La documentación de prototipo describe la Pantalla 10, la Pantalla 11 y un diálogo de posible duplicado, pero sus interacciones eran sólo visuales y no prueban persistencia.

## Resultado

En todos los hallazgos donde se usó informalmente “espacio”, la frontera técnica definitiva es `user_id`; no existe un tenant `workspace_id` en este sprint.

Hay suficiente información para diseñar y descomponer HU18, HU19 y HU23. Las decisiones aprobadas y su historial de conversación quedan en [team-questions.md](./team-questions.md).
