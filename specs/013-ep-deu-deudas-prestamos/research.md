# EP-DEU Sprint 5 — Research

**Fecha de corte**: 2026-10-08  
**Alcance**: HU-26 a HU-29; 29 puntos  
**Método**: lectura del repositorio y documentos de producto, más consultas de catálogo de solo lectura a Supabase. No se modificaron datos remotos ni se ejecutaron pruebas.

## Fuentes de producto

- Kipu Product Backlog V4.2: HU-26 (deuda propia), HU-27 (dinero prestado), HU-28 (pagos/cobros de principal), HU-29 (cuotas, vencimientos y cierre).
- Proceso P17, `Procesos/17-gestionar-deudas-y-prestamos.md`: apertura nueva/histórica, efectos opuestos de PAYABLE y RECEIVABLE, principal separado de interés, cronograma no contable, cierres recalculados, reintentos y avisos.
- Arquitectura y Datos V4.2: definición de `debts`, `debt_installments`, `debt_events`, límite Free combinado de 2 obligaciones activas y matriz de sprints.
- Constitución del repositorio: exactitud financiera, eventos auditables, operación local-first, propiedad/RLS, límites Free que no borran historia y verificación con pruebas de dominio.

La matriz vigente del backlog asigna 8+8+8+5 puntos a las cuatro historias. HU-07, HU-18/HU-19 y HU-57 son bloqueantes según cada HU; HU-42 es dependencia parcial de HU-29. HU-24, HU-40 y HU-44 son dependencias futuras o relacionadas, no alcance adicional de S5.

## Evidencia del repositorio Android

- No existe el módulo `app/src/main/java/com/kipu/app/feature/debts` ni entidades Room de deuda. `KipuDatabase.kt` mantiene Room en versión 18 y registra cuentas, movimientos, cuotas de tarjeta y notificaciones; `RoomMigrations.kt` no añade deuda.
- El patrón local-first de MOV está en `app/src/main/java/com/kipu/app/feature/movements/data/local/MovementLocalDataSource.kt`, `OfflineFirstMovementRepository.kt`, `data/remote/MovementApi.kt` y `data/sync/SyncMovementsWorker.kt`: escritura atómica local, outbox, recibo idempotente, reintento y pull por cursor.
- El pull de movimientos aún no aplica entidades DEBT. DEU necesita sincronización de obligaciones, cuotas, eventos y conflictos, además de pantallas y dominio; las tablas de servidor no constituyen la funcionalidad de extremo a extremo.
- El modelo de movimientos ya reconoce `DEBT_DISBURSEMENT` y `DEBT_PAYMENT` como valores de `operation_kind`. Los calculadores actuales no excluyen esas operaciones del flujo operativo por tipo; habrá que conservar el asiento de caja y excluir principal de ingresos/gastos y consumo presupuestario.
- El centro de notificaciones existe, pero no programa vencimientos de deuda ni resuelve navegación DEBT. P24 describe llamadas desde el proceso de obligaciones; HU-44 generaliza recordatorios y deduplicación en S6. S5 debe entregar el recordatorio mínimo exigido por HU-29 sin declarar construido el motor general de HU-44.

## Evidencia SQL y Supabase

La inspección remota fue de solo lectura:

- Existen `public.debts`, `public.debt_installments`, `public.debt_events`, `public.v_debt_summary`, `public.transactions`, `public.transaction_links` y `internal.command_receipts`.
- Las tres tablas de deuda tienen políticas owner-only para usuarios autenticados. `debts` admite `ACTIVE/SETTLED/CANCELLED`; eventos y cuotas exigen importes positivos. El FK de deuda a eventos/cuotas permite cascada, por lo que el borrado debe restringirse y pasar por una operación que compruebe historia.
- `transaction_links` ya admite `DEBT_AMORTIZATION`, útil para relacionar el tramo de principal y un eventual movimiento separado de interés.
- Existe un solo RPC específico encontrado: `public.record_debt_payment(jsonb)`. Solo acepta `amount_minor`; no acepta principal e interés por separado, no impide pagar más que el pendiente, no verifica expresamente propietario/estado/moneda de la cuenta ni pertenencia de cuota, y no serializa por revisión esperada. El wrapper genérico `apply_sync_command` conoce `RECORD_DEBT_PAYMENT`, pero no los comandos de apertura ni de programación de cuotas.
- El RPC inserta un movimiento EXPENSE para PAYABLE sin `category_id`, aunque la restricción de `transactions` exige categoría para todo EXPENSE. También confirma/liquida comparando la suma de pagos con el principal inicial y no vuelve a abrir una obligación tras anular un pago.
- `v_debt_summary` calcula principal como `total_minor - SUM(PAYMENT)`. No aplica ajustes o condonaciones y no excluye eventos cuyo movimiento relacionado esté VOIDED. La vista debe derivar el saldo desde los eventos vigentes y tratar correcciones/anulaciones.
- RLS owner-only es necesaria pero no sustituye validaciones de referencias dentro de RPC SECURITY DEFINER: las FK actuales son por ID, no pares compuestos de propietario.
- El modelo de arquitectura indica UNIQUE(debt_id, installment_number); las migraciones locales consultadas no muestran esa restricción, mientras el catálogo remoto sí muestra `uq_obligation_installment_no`. Debe reconciliarse el historial de migraciones y el esquema efectivo antes de desplegar cambios.
- El remoto reporta como última migración `20260930035146`; el worktree local también contiene migraciones S4/S5 de 2026-10-02 a 2026-10-07. La deriva debe resolverse antes de aplicar o validar la migración DEU.

## Decisiones para el diseño

1. Crear un módulo Android `feature/debts` y seguir el patrón de entidades, DAO, repositorio offline-first, outbox y worker ya usado por movimientos.
2. Mantener `debts`, `debt_installments` y `debt_events` existentes. Agregar cambios aditivos y versionados; no crear tablas paralelas.
3. Tratar el principal como movimiento de caja no operativo con `operation_kind=DEBT_DISBURSEMENT/DEBT_PAYMENT`. Si se registra interés real, registrarlo como movimiento operativo separado, con categoría de gasto cuando aplique, y vincularlo con el principal usando `DEBT_AMORTIZATION`. Un solo comando de settlement debe confirmar ambos asientos y el evento de principal atómicamente.
4. Añadir un delta explícito de principal a eventos nuevos para que pagos, ajustes y condonaciones se calculen con signo; dejar una ruta de compatibilidad para eventos heredados y prevalidar datos ambiguos antes de migrar.
5. Asegurar que las operaciones compuestas usen recibo idempotente, bloqueo/revisión esperada, validación de propietario en cada referencia y publicación en el feed de sincronización.
6. Implementar la entrega mínima de aviso de deuda en S5 con identidad estable, sustitución/cancelación al cambiar fecha y sin efecto contable. Dejar la política configurable general y la deduplicación entre tipos a HU-44.
7. No reutilizar el RPC remoto actual sin cerrar los defectos listados. No aplicar migraciones remotas durante esta etapa de especificación.

## Riesgos y gates

- No iniciar implementación contra Supabase hasta reconciliar migraciones locales/remotas, probar migración no destructiva y confirmar la restricción única de cuota.
- No activar apertura ni pagos hasta que el comando remoto aplique cupo combinado, dueño de cuenta/deuda/cuota, moneda, saldo máximo, idempotencia y revisión bajo concurrencia.
- No aceptar HU-28 hasta que el principal no aparezca como ingreso/gasto operativo y el interés real sí aparezca una sola vez.
- No aceptar HU-29 hasta que cancelar o anular una liquidación actualice el principal, la cuota, el estado cerrado y el recordatorio.
- No contar la presencia de tablas, un mock, una pantalla, o los datos de una cuenta del teléfono como prueba de comportamiento completo.
