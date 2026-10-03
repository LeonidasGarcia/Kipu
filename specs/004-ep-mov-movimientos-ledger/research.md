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

## Incremento S4 — T058: decisiones y evidencia (2026-10-02)

El contenido anterior registra S2. La fuente funcional S4 es spec.md y las decisiones P1–P5 aprobadas por producto; el código es evidencia de baseline, no autorización para redefinirlas.

| Decisión | Fundamento | Alternativa descartada |
|---|---|---|
| Main `c80ea0ddea80dfe5332971f12418758ea1bc9923` / Room v16 es baseline aceptada (P4) | Aceptación explícita de producto tras auditoría DB S3 | Reabrir aceptación S3 o atribuir a esta fase pruebas de proveedor no ejecutadas |
| S4 entrega HU-20/21/22 (15 puntos MOV) junto a HU-58/59 (16 PLA); HU-25 sigue S7 (P1) | Capacidad aprobada de 31 puntos | Implementar devolución parcial para cumplir el guion antiguo |
| Mantenimiento genérico solo STANDARD, con G/I/T y sin relaciones especializadas (P2) | P14 exige proteger cuotas, deuda y referencias; tipo TRANSFER no convierte CARD_PAYMENT en estándar | Corregir por separado un pago o compra en cuotas |
| Premium offline válido solo antes del menor límite conocido; igualdad caduca, reinicio sin continuidad confiable exige revalidación (P3/B1) | HU-59, P31 y constitución II; Free y registro manual permanecen operativos | Usar reloj civil, reiniciar 72 h tras boot o convertir la app en solo lectura |
| Rechazo de efectos locales confirmados usa VOIDED y compensación auditable, sin DELETE (P5) | P14 y constitución I/VI | Eliminar ledger/cuotas/asignaciones para rehacer un saldo |
| Consumo base por periodo usa payload vigente, una vez por transacción (U1) | HU-20 exige recalcular periodo anterior/nuevo; no existen módulos presupuestarios que puedan darse por integrados | Sumar snapshots/reversals como nuevos gastos o declarar éxito por solo invalidar caché |
| Comandos Spec Kit fijan FEATURE_DIR; T074 se ejecuta después de T071 (I1/I2) | El selector almacenado era EP-NOT; ambas tareas comparten prueba Room | Confiar en el nombre de rama o escribir en paralelo el mismo archivo |

### Evidencia local consultada (no pruebas nuevas)

- `app/src/main/java/com/kipu/app/core/database/KipuDatabase.kt`: versión 16; las entidades de movimiento carecen de historial de revisiones S4.
- `feature/movements/data/local/MovementDao.kt`: lectura por propietario y ledger sumable; elimina ledger para rechazo desde EP-CTA. El diseño S4 reemplaza esa ruta, sin usarla para nuevas correcciones.
- `feature/movements/data/sync/SyncMovementsWorker.kt`: una transacción ya existente se reconcilia comparando el conjunto previo de ledger; no sustituye el payload completo por revisión nueva. Los IDs locales/servidor del baseline pueden diferir.
- `feature/movements/domain/TransactionRequestHasher.kt`: representación S2 delimitada; S4 usa un contrato separado con bytes inequívocos sin cambiar hashes de outbox S2 pendiente.
- `supabase/migrations/20260920000000_financial_core_baseline.sql`: existen `transactions.revision`, `transaction_revisions`, `internal.ledger_entries`, recibos y sync_changes. Sync admite UPSERT/DELETE; VOIDED se transmite como UPSERT, no DELETE.
- `specs/012-ep-pla-planes-monetizacion/contracts/feature-access-policy.md`: contrato histórico S1 sin concesión offline S4; debe evolucionar en EP-PLA antes del cierre T084. Definir el consumidor no demuestra entrega del productor.

### Fuentes de intención

- Obsidian Mind: `work/active/kipu/Procesos/14-corregir-o-anular-movimiento.md` §§3–5; `15-consultar-y-filtrar-historial.md` §§3–5; `31-aplicar-cupos-accesos-y-vigencia-offline.md` §§4.11–4.14.
- `work/active/kipu/Kipu md/02_Kipu_V4.2_Product_Backlog.md`: HU-20/21/22/58/59.
- `work/active/kipu/Spec Kit in Kipu.md`, `reference/Spec Kit — Referencia Global.md`, `.specify/memory/constitution.md` v2.0.0.
- P1 corrige el guion local. El cronograma del vault aún debe actualizarse por su responsable; no se escribió en el vault ni se enviaron mensajes externos.

### Diseño derivado

T059 concreta el modelo y consumo base; T060 concreta comando/reconciliación; T061 define la frontera de acceso para EP-PLA; T062 define UI/validación. Son decisiones técnicas revisables bajo las reglas aprobadas, sin migraciones aplicadas ni compatibilidad real declarada. T063 exige revisión del diseño y analyze antes de Foundation.


**Propagated**: 2026-10-02 — T093: altas estándar nuevas usan contracts/register-transaction-v2.md (hash de array compacto, validación remota y dispatch por versión); comandos S2 ya encolados permanecen en v1 con bytes/hash originales. No modifica identidad histórica, modelo de ledger ni alcance S4.

## S4 UI/UX approved research — 2026-10-03

User approved the broadened UI/UX Pro Max and Compose Animations plan. Recommendations prioritize readable minimal finance surfaces, explicit filter application/recovery, privacy and semantic state labels. Sources: existing Stitch design system; Obsidian Mind `work/active/kipu/Procesos/15-consultar-y-filtrar-historial.md`, `work/active/kipu/Procesos/14-corregir-o-anular-movimiento.md`, and HU-58/HU-59 in the product backlog. Motion uses existing shared tokens/reduced-motion adapter. No new financial operation, undo of VOIDED, remote deployment or database migration is introduced.


## Evidencia de cierre del refinamiento — 2026-10-03

**Audit evidence**: 2026-10-03 — Se preserva FR-017. La auditoría detectó un pendiente heredado: el filtro por **fuente** exigido por HU-22 no tiene un campo de procedencia en `Transaction`/`AdvancedHistoryCriteria`/la proyección Room consumida por esta UI. Sincronización y cuenta de origen son conceptos distintos. Este refinamiento implementa los controles respaldados por los contratos actuales; no fabrica procedencia histórica ni modifica el ledger para inferirla. T110 conserva explícitamente el trabajo restante de contrato y filtro por fuente antes de aceptar HU-22 íntegramente.

Evidencia de autoridad: consulta MCP Obsidian Mind `"HU-22" "fuente" filtros avanzados historial manual SMS notificación`, contrastada con la sección completa de `work/active/kipu/Kipu md/02_Kipu_V4.2_Product_Backlog.md` (HU-22, reglas 1–3). El requerimiento original continúa vigente.
