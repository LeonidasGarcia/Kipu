# Contrato interno — Comandos de obligaciones

**Estado**: contrato de planificación para HU-26–HU-29; no implementado.

## Reglas comunes

- El propietario se deriva de la sesión autenticada del servidor; el cliente no puede elegir ni reemplazar `user_id`.
- Los importes se envían como enteros de unidades menores junto con moneda. Nunca se aceptan floats.
- Todo comando financiero incluye `contract_version=1`, `operation_id` estable, `request_hash` y, cuando edita una obligación existente, `expected_revision`.
- El servidor valida que deuda, cuota, cuenta, categoría y movimientos referenciados pertenezcan al mismo usuario y que la cuenta esté activa y use la misma moneda.
- El servidor serializa cambios por obligación, confirma eventos, movimientos, ledger, cuotas, recibos y cambios de sync en la misma transacción.
- Repetir la misma identidad y hash devuelve el resultado anterior. Reusar la identidad con otro hash devuelve conflicto. Fallos transitorios devuelven resultado reintentable y no dejan efectos parciales.
- RLS protege lecturas/escrituras owner-only; los RPC SECURITY DEFINER repiten comprobaciones explícitas de propiedad.
- No se permite que el cliente borre filas de deuda directamente. El borrado físico pasa por el comando condicional y no se habilita si se perdería historia financiera.

## Apertura — `OPEN_DEBT`

### Solicitud

| Campo | Requerido | Regla |
| --- | --- | --- |
| contract_version | sí | Versión de contrato soportada. |
| operation_id, request_hash | sí | Idempotencia. |
| event_id | sí | UUID estable del evento de apertura, derivado de operation_id y compartido por el outbox local y el servidor. |
| debt_id | sí | UUID estable del cliente. |
| obligation_type | sí | PAYABLE o RECEIVABLE. |
| counterparty_name | sí | Texto no vacío después de normalización de espacios. |
| total_minor, currency_code | sí | Principal positivo y moneda válida. |
| opened_on | sí | Fecha efectiva de apertura. |
| opening_mode | sí | NEW_CASH_FLOW o HISTORICAL. |
| account_id | condicional | Obligatorio para desembolso/cobro de caja nuevo. Propietario, estado y moneda verificados. |
| due_date, reminder_lead_days, notes | no | Preferencia de vencimiento y aviso; no causan movimiento financiero. |
| expected_revision | no | Se usa para cambios sobre obligación existente, no para crear. |

### Comportamiento

- PAYABLE + NEW_CASH_FLOW aumenta cuenta y pasivo por el principal; usa DEBT_DISBURSEMENT y no crea ingreso operativo.
- PAYABLE + HISTORICAL registra principal sin entrada de caja.
- RECEIVABLE + NEW_CASH_FLOW requiere cuenta y reduce caja mientras aumenta por cobrar, sin gasto operativo.
- RECEIVABLE + HISTORICAL registra la apertura sin segunda salida de caja.
- El servidor valida la cuota combinada de obligaciones activas en la misma sección crítica que crea la obligación; el límite Free vigente es 2.

### Respuesta

`APPLIED` o `DUPLICATE` con debt_id, revision, remaining_minor, currency_code y movimientos vinculados; `REJECTED` con código de negocio; `CONFLICT` con revisión actual; o `RETRYABLE` sin efectos parciales.

## Liquidación — `SETTLE_DEBT`

### Solicitud

| Campo | Requerido | Regla |
| --- | --- | --- |
| operation_id, request_hash | sí | Idempotencia de la liquidación agrupada. |
| debt_id, expected_revision | sí | Obligación propia vigente y revisión esperada. |
| account_id | sí | Cuenta propia activa de la moneda de la obligación. |
| principal_minor | sí | Entero > 0 y ≤ principal pendiente recalculado. |
| interest_minor | sí | Entero ≥ 0; representa solo interés real confirmado. |
| interest_category_id | condicional | Requerida cuando hay interés y el tramo es EXPENSE. Debe ser categoría elegible del usuario. |
| installment_id | no | Si existe, debe pertenecer a la obligación y usuario. |
| occurred_at, notes | no | Fecha del hecho y nota. |

### Comportamiento

- PAYABLE genera salida de caja por principal + interés; reduce deuda solo por principal. RECEIVABLE genera entrada por ambos componentes; reduce cuenta por cobrar solo por principal.
- El tramo de principal es DEBT_PAYMENT y no cuenta como ingreso/gasto ni consumo de presupuesto. El interés real, si existe, se registra como movimiento operativo separado y enlazado con DEBT_AMORTIZATION; solo ese tramo participa en resultados.
- La operación crea el evento PAYMENT y los movimientos/asientos vinculados de forma atómica.
- No se recorta el exceso ni se permite principal mayor al pendiente.
- El endpoint que invalida/corrige una liquidación debe operar sobre el grupo principal + interés; la vista de saldo ignora eventos cuyo principal está VOIDED y recalcula cuotas/estado.

### Respuesta

`APPLIED` o `DUPLICATE` con IDs del evento, principal y tramo de interés, nuevo pendiente, estado y revisión. `REJECTED`, `CONFLICT` o `RETRYABLE` según regla común.

## Cronograma — `SET_DEBT_SCHEDULE`

- Recibe debt_id, expected_revision, operación idempotente y una lista ordenada de installment_id, número, due_date y principal_minor.
- La suma de cuotas debe igualar el principal planificado. Secuencia y fechas son válidas; importes son positivos.
- Guardar o alcanzar un vencimiento no crea pago ni reduce principal.
- Reemplazar/cancelar el cronograma cancela cuotas PENDING y PARTIAL y elimina o reprograma solo recordatorios pendientes asociados; el resultado es idempotente.

## Cierre — `CLOSE_DEBT`

- Recibe debt_id, expected_revision, operation_id y acción SETTLE, CANCEL, ADJUST o FORGIVE.
- SETTLE solo se acepta si el pendiente derivado es cero.
- ADJUST/FORGIVE requieren importe, dirección/motivo auditable y una revisión vigente; ninguna acción puede ocultar saldo sin registrar el evento.
- CANCEL conserva cualquier principal vigente, eventos y consulta histórica; no se presenta como pago.
- El cupo activo se libera según estado y política existente, sin borrar historia.

## Borrado condicional — `DELETE_DEBT_IF_UNREFERENCED`

- Solo permitido cuando la obligación no tiene eventos de apertura, liquidaciones, ajustes, condonaciones ni otra historia que se perdería.
- Cualquier evento asociado responde `REJECTED/HISTORY_PRESERVED`; la apertura también protege el saldo pendiente y el historial.
- Una deuda heredada sin operacion ni evento asociado puede eliminarse aunque conserve principal, conforme a HU-26 AC4.
- El borrado elimina la obligación y sus elementos puramente planificados en una sola transacción; no usa una cascada directa desde la tabla.

## Sync y consultas

- Push usa un comando de sync con los contratos anteriores y el recibo común.
- Cada respuesta aplicada agrega entidades DEBT, DEBT_INSTALLMENT y DEBT_EVENT al feed de cambios con owner, revisión y cursor.
- Pull pagina por cursor y aplica un estado coherente localmente. Un revision mismatch se expone como conflicto, no como éxito silencioso.
- Los recordatorios mínimos usan identidad derivada de deuda/cuota/vencimiento para reemplazar o cancelar una programación local repetida.
