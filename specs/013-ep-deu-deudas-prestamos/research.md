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

## Preflight de implementación (2026-10-08)

- **GO solo para desarrollo local**: el historial versionado de este worktree llega a `20261007144500`; la inspección read-only previa registró que el proyecto remoto llegaba a `20260930035146`. La diferencia incluye migraciones S4 y de categorías posteriores. No se aplicará DDL ni se ejecutarán pruebas destructivas contra el proyecto remoto.
- El stack local compartido tiene aplicado un conjunto distinto de migraciones CCO (`20261008161406`, `20261008161540`, `20261008161631`) que no existe en este worktree. Antes de probar DEU se debe recrear únicamente la base local desde los archivos versionados de esta rama y validar su estado con `supabase migration list --local`.
- El RPC heredado registra `PAYMENT` con `amount_minor` positivo; el resumen vigente resta esos pagos del principal inicial. Los registros heredados `ADJUSTMENT` y `FORGIVENESS` no tienen una dirección documentada que permita reconstruir su efecto. La migración DEU debe preservar esos registros y abortar si encuentra alguno sin una resolución explícita; no debe inferir del signo de `amount_minor`.
- El índice único de cuotas difiere entre el catálogo remoto observado y las migraciones locales. Antes de agregarlo, un preflight debe detectar números duplicados por deuda y abortar sin cambiar datos; solo el estado limpio puede recibir la restricción versionada.
- **NO-GO para integración/release remota** hasta reconciliar la historia de migraciones, revisar datos de deuda heredados y confirmar esquema/RLS/grants efectivos. El alcance de implementación y pruebas de esta rama queda limitado al stack local y no declara el producto listo para despliegue.

## Resultado de implementación y revisión (2026-10-08)

- Se implementaron HU-26 a HU-29 en `feature/debts`: aperturas PAYABLE/RECEIVABLE nuevas e históricas, edición protegida por historial, liquidación atómica de principal/interés, sincronización local-first, cronogramas exactos, avisos WorkManager y cierre con cancelación/ajuste/condonación auditables. Room queda en versión 20: v18→v19 crea las tablas de deuda y outbox; v19→v20 agrega el enlace opcional del movimiento de interés.
- Se mantuvo el orden TDD: primero se añadieron pruebas de dominio, SQL, repositorios y Compose; las pruebas focalizadas quedaron rojas al faltar el comportamiento y se pusieron verdes con la implementación. El plan de cuotas y los recordatorios no generan movimientos. CANCELLED conserva y presenta el saldo pendiente.
- La revisión del contrato confirmó validación de propietario y moneda, recibos idempotentes, revisión esperada, clave de cupo combinado, referencias compuestas, feed por cursor y guardas contra movimientos anulados. Se corrigió una expectativa demasiado estricta del test de reintento: cada duplicado puede reactivar el trabajo único de sincronización para recuperar un enqueue interrumpido; WorkManager mantiene una sola unidad de trabajo por usuario.
- Evidencia del worktree: `./gradlew testDebugUnitTest` (480 pruebas, 0 fallos); 36 pruebas instrumentadas de deuda en SM-A165M/Android 16 (0 fallos) y prueba Room de migración v18→v19 (1/1); 7 archivos pgTAP locales (139 aserciones, 0 fallos); script de concurrencia Free (una apertura aplicada y otra rechazada, dos obligaciones activas).
- Ajustes posteriores a review: el opening event usa un ID estable local/remoto; el borrado conserva cualquier evento y permite eliminar deuda heredada sin evento ni operacion asociada, aunque conserve principal; cuotas pendientes/parciales se cancelan al reemplazar el plan; lista y detalle separan plan de cuotas de pagos. La suite Android de DEU (incluye guard remoto con eventos y etiqueta PARTIAL) y siete archivos pgTAP quedaron verdes; la concurrencia Free aplico una apertura y rechazo la otra. No hubo cambios remotos.
- `npx supabase migration list --local` muestra aplicadas las siete migraciones DEU de `20261008120000` a `20261008170000`. La prueba pgTAP de migración preserva IDs, propietario, movimientos y saldo con datos sintéticos dentro de una transacción revertida. No se ejecutó `debt_sprint5_migration_upgrade.ps1`: su primer paso recrearía la base local compartida, y el reset general conocido se detiene antes por la referencia preexistente a `public.recurrence_occurrences` en `20260928110000_credit_card_pull_projection.sql`. La comprobación aislada del upgrade legacy queda pendiente antes de cualquier despliegue.
- La arquitectura V4.2 se actualizó en `C:/Users/Alume/orca/KipuApp/Kipu md/03_Kipu_V4.2_Arquitectura_y_Datos.md` con campos, eventos firmados, vínculos de movimientos, cuota, seguridad, sync y gate de migración. Ese archivo pertenece a otro repositorio actualmente en `main` con cambios no relacionados; se dejó el cambio sin commit y no se tocaron sus otros archivos.
- La inspección remota se mantuvo read-only: no se aplicaron migraciones, RPCs ni cambios de datos remotos. Se conserva **NO-GO de despliegue** hasta reconciliar migraciones local/remoto y ejecutar el upgrade legacy en una base aislada.
