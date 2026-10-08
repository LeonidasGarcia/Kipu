# EP-DEU — Modelo de datos propuesto

## Alcance e invariantes

El modelo cubre HU-26 a HU-29 de Sprint 5 y reutiliza el modelo financiero existente. Los importes autoritativos son enteros en unidades menores con moneda. El saldo de la obligación no es editable: deriva del principal inicial y de eventos de principal válidos. Los movimientos de principal afectan la cuenta pero no resultados operativos; el interés real es una operación operativa separada.

La autoridad del usuario es `auth.uid()`. Toda apertura, liquidación, cuota, evento, transacción y enlace debe quedar dentro del mismo propietario. Las referencias a cuenta, obligación, cuota y movimiento se validan en el servidor además de tener RLS.

## Estado actual observado

| Objeto | Campos y comportamiento existentes | Hallazgo que bloquea aceptar S5 |
| --- | --- | --- |
| `public.debts` | id, user_id, obligation_type, counterparty_name, total_minor, currency_code, due_date, notes, status, revision, deleted_at | No conserva la fecha de apertura histórica. El borrado owner-only podría activar cascada y borrar eventos. |
| `public.debt_installments` | id, user_id, debt_id, installment_number, due_date, amount_minor, status, revision, deleted_at | El catálogo remoto tiene unique(debt_id, installment_number); el historial local de migraciones debe reconciliar esa restricción. |
| `public.debt_events` | id, user_id, debt_id, transaction_id, installment_id, event_type, amount_minor, occurred_at | amount_minor no distingue delta de principal, interés real ni dirección de ajuste. |
| `public.v_debt_summary` | total_minor menos eventos PAYMENT | Ignora ADJUSTMENT/FORGIVENESS y pagos con transaction VOIDED. |
| Room `KipuDatabase` | Versión 18; no hay entidades, DAO ni tablas de obligaciones | Se requieren entidades, índices y migración local. |
| Sync MOV | Outbox, recibo idempotente y pull de TRANSACTION; no aplica DEBT | El comando y el feed deben agregar eventos/obligaciones y preservar orden/conflictos. |

## Modelo destino

### Obligation / `debts`

| Campo | Tipo lógico | Regla |
| --- | --- | --- |
| id | UUID estable | Se conserva en cliente, servidor, eventos y outbox. |
| user_id | UUID | Dueño derivado de sesión en servidor. |
| obligation_type | PAYABLE / RECEIVABLE | No intercambiables; determinan dirección de caja. |
| counterparty_name | texto | Edición descriptiva no cambia principal. |
| total_minor | entero positivo | Principal inicial; no se edita para corregir historia. |
| currency_code | moneda | Debe coincidir con cuenta de cada movimiento relacionado. |
| opened_on | fecha | Nueva columna aditiva para fecha efectiva, distinta de created_at. |
| due_date | fecha nullable | Vencimiento general opcional. |
| reminder_lead_days | entero nullable | Preferencia específica del recordatorio mínimo de deuda; null significa sin aviso. |
| notes | texto nullable | Descriptivo. |
| status | ACTIVE / SETTLED / CANCELLED | SETTLED solo con saldo derivado cero. CANCELLED conserva cualquier saldo e historia. |
| revision | entero positivo | Revisión optimista y orden de sincronización. |
| deleted_at | timestamp nullable | Soft-delete para tombstone; no ocultar obligaciones con saldo o historia. |

### DebtEvent / `debt_events`

| Campo | Tipo lógico | Regla |
| --- | --- | --- |
| id, user_id, debt_id | UUID | Identidad, dueño y obligación padre. |
| event_type | DISBURSEMENT / PAYMENT / ADJUSTMENT / FORGIVENESS | Tipo de hecho auditable. |
| amount_minor | entero positivo | Magnitud de principal del evento; en PAYMENT es solo principal, no interés. |
| principal_delta_minor | entero con signo nullable para legado | Nuevo campo. Negativo reduce principal, positivo lo aumenta, cero marca un evento de apertura sin volver a contar total_minor. Los eventos nuevos lo informan siempre. Los nulos heredados conservan la semántica histórica: PAYMENT resta amount_minor; los otros tipos no alteran el saldo hasta reconciliar su semántica. |
| transaction_id | UUID nullable | Movimiento de principal que afectó caja; null cuando el evento no produjo movimiento de cuenta. Debe pertenecer al mismo usuario. |
| installment_id | UUID nullable | Cuota relacionada, debe pertenecer a debt_id y user_id del evento. |
| occurred_at | timestamp | Fecha efectiva del evento; anulación de transacción enlazada excluye su efecto sin borrar auditoría. |

El saldo vigente se calcula como principal inicial más la suma de deltas de eventos válidos. Para eventos heredados sin delta, se conserva la regla de lectura anterior de PAYMENT. Un evento con movimiento relacionado solo cuenta si dicho movimiento sigue vigente. Ajustes y condonaciones nuevos usan delta firmado y no editan el principal inicial.

### Installment / `debt_installments`

- Conserva id, user_id, debt_id, installment_number, due_date, amount_minor, status y revision.
- amount_minor es principal planificado positivo. La suma del cronograma debe ser exactamente el principal configurado; el residuo de unidades menores se reparte determinísticamente.
- Añadir configuración/fecha de aviso solo si la preferencia no puede derivarse de `debts.reminder_lead_days`; no persistir un segundo saldo ni marcar PAID por vencimiento.
- UNIQUE(debt_id, installment_number), con validación además de pertenencia del padre y propietario.
- El estado de cuota se deriva de pagos válidos asignados; al anular un pago se recalculan cuota y obligación.

### Settlement y movimiento financiero

Un único comando de liquidación acepta principal, interés real (por defecto cero), cuenta, cuota opcional, fecha, revisión esperada e identidad estable de operación.

- El tramo de principal crea un movimiento con `operation_kind=DEBT_PAYMENT`; no cuenta como ingreso, gasto ni consumo de presupuesto.
- El interés real, si existe, crea un movimiento operativo separado con la categoría apropiada y se enlaza al tramo de principal mediante `transaction_links.link_type=DEBT_AMORTIZATION`.
- El evento PAYMENT guarda solo el principal. La suma de salidas/entradas de ambos tramos coincide con principal más interés y se confirma junto con ledger, cuota, evento, recibo y feed de sync.
- Para gastos de principal, la regla actual de categoría obligatoria debe permitir el tipo no operativo DEBT_PAYMENT sin inventar una categoría de consumo. El movimiento de interés operativo conserva la categoría requerida.
- Para PAYABLE, la caja disminuye por principal+interés y el pasivo solo por principal. Para RECEIVABLE, la caja aumenta por principal+interés y la cuenta por cobrar solo disminuye por principal.
- Anular/corregir el grupo debe invalidar ambos movimientos del mismo settlement y recalcular evento, cuota, pendiente y estado en una sola operación.

### Apertura y estado

- PAYABLE nuevo: evento de apertura, aumento de caja por principal y pasivo por principal; el movimiento lleva DEBT_DISBURSEMENT y no es ingreso.
- PAYABLE histórico: evento de apertura sin movimiento de caja.
- RECEIVABLE nuevo: evento de apertura, disminución de caja y aumento de cuenta por cobrar; el movimiento lleva DEBT_DISBURSEMENT y no es gasto.
- RECEIVABLE histórico ya reflejado en apertura: evento de apertura sin una segunda salida.
- SETTLED requiere pendiente cero. CANCELLED no significa SETTLED y mantiene saldo consultable.
- Borrado físico solo mediante operación protegida si no existe historia financiera vinculada. El cliente no elimina directamente filas cuyo FK tenga cascada.

## Local-first y sync

1. Room agrega DebtEntity, DebtInstallmentEntity, DebtEventEntity, sus DAOs/índices y una migración desde la versión 18.
2. Cada comando persiste su cambio local, proyección de saldo, outbox y recibo dentro de una transacción Room.
3. El envío es idempotente por operation_id + request_hash. Repetir la misma identidad y contenido devuelve el resultado anterior; reusar identidad con contenido distinto se rechaza.
4. El servidor publica cambios de deuda en el feed consumido por las demás instalaciones. El pull de Android reconoce DEBT, DEBT_INSTALLMENT y DEBT_EVENT.
5. Una revisión obsoleta con impacto financiero genera conflicto explícito; no se resuelve con último-escritor-gana.
6. Recordatorios mínimos se reprograman por identidad estable debt_id/installment_id/due_date. HU-44 conservará la administración general y la deduplicación transversal.

## Migración y compatibilidad

- No crear nuevas tablas paralelas: auditar y extender debts, debt_installments, debt_events y v_debt_summary.
- Hacer una prevalidación de eventos ADJUSTMENT/FORGIVENESS y cuotas antes de aplicar el nuevo cálculo. Si el significado de datos heredados no puede reconstruirse, detener la migración y documentar el caso en vez de recalcular saldos en silencio.
- La migración debe conservar IDs, movimientos, cuentas, saldos, RLS, grants e historia existente; versionar los deltas nuevos y mantener lectura compatible de eventos antiguos.
- Reconciliar primero las migraciones locales S4/S5 contra el remoto y añadir de forma idempotente UNIQUE(debt_id, installment_number) si el estado reconciliado lo requiere.
