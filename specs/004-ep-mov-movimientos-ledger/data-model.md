# Data Model: Movimientos y ledger

## Transaction

Registro de intención de negocio.

| Campo | Tipo lógico | Regla |
|---|---|---|
| `id` | UUID | Generado en cliente para operar offline. |
| `user_id` | UUID | Obligatorio; se deriva de la sesión autenticada y coincide con `auth.uid()`. |
| `type` | enum | `EXPENSE`, `INCOME`, `TRANSFER`. |
| `amount_minor` | long | Mayor que cero; nunca `Double`. |
| `currency_code` | ISO-4217 | Debe coincidir con las cuentas afectadas en S2. |
| `source_account_id` | UUID | Obligatorio para gasto y transferencia. |
| `destination_account_id` | UUID? | Obligatorio y distinto del origen en transferencia. |
| `category_id` | UUID? | Obligatorio para `EXPENSE`; opcional para `INCOME` y `TRANSFER`; debe pertenecer al usuario. |
| `merchant_id` | UUID? | Debe pertenecer al usuario si se informa. |
| `occurred_at` | instant | Fecha/hora efectiva. |
| `note` | string? | Longitud limitada y normalizada. |
| `status` | enum | `ACTIVE` en S2; estados futuros no se exponen aún. |
| `created_by` | UUID | Derivado de sesión, no confiado desde UI. |
| `created_at` / `updated_at` | instant | Auditoría. |

## LedgerEntry

Efecto contable inmutable de una transacción.

| Campo | Tipo lógico | Regla |
|---|---|---|
| `id` | UUID | Único. |
| `transaction_id` | UUID | FK a `Transaction`. |
| `account_id` | UUID | Cuenta afectada. |
| `role` | enum | `SOURCE` o `DESTINATION`. |
| `signed_amount_minor` | long | Gasto/origen negativo; ingreso/destino positivo. |
| `currency_code` | ISO-4217 | Igual a la cuenta y transacción. |
| `created_at` | instant | Inmutable. |

Reglas:

- Gasto: un asiento negativo.
- Ingreso: un asiento positivo.
- Transferencia: dos asientos, uno negativo y otro positivo, creados atómicamente.
- S2 no modifica ni elimina asientos; anulaciones futuras generan contrapartidas.

## LocalCommandReceipt

| Campo | Regla |
|---|---|
| `user_id` + `idempotency_key` | Clave compuesta estable del intento lógico; evita colisiones entre usuarios. |
| `request_hash` | Hash de la representación canónica del comando. |
| `transaction_id` | Resultado asociado. |
| `status` | `PENDING`, `APPLIED`, `DUPLICATE`, `CONFLICT`, `REJECTED`. |
| `response_payload` | Respuesta serializada mínima para repetición determinista. |
| timestamps | Creación y última actualización. |

Una clave existente con hash igual devuelve el recibo. Con hash distinto transiciona a conflicto sin efectos financieros.

## MovementOutbox

| Campo | Regla |
|---|---|
| `id` | UUID del mensaje. |
| `idempotency_key` | Igual al recibo/comando. |
| `aggregate_id` | ID de transacción. |
| `payload` | Comando versionado. |
| `state` | `PENDING`, `IN_FLIGHT`, `RETRY`, `SYNCED`, `FAILED_PERMANENT`. |
| `attempt_count` | Incrementa por intento real. |
| `next_attempt_at` | Backoff de errores transitorios. |
| `lease_until` | Evita procesamiento concurrente. |
| `last_error_code` | Código seguro, sin datos sensibles. |

## BalanceProjection

Proyección materializada local por cuenta: `account_id`, `balance_minor`, `currency_code`, `last_transaction_at`, `updated_at`. Se actualiza en la misma transacción Room que los asientos y se puede reconstruir sumando el ledger.

## SimilarityMatch

No requiere tabla permanente. Es el resultado de una consulta sobre movimientos activos recientes:

```text
same user
AND same source account
AND same type
AND same amount_minor
AND same currency_code
AND occurred_at within proposed 5-minute window
```

La confirmación del usuario crea una nueva `idempotency_key`; reutilizar la anterior sería una repetición exacta.

## Transiciones

```text
UI draft -> validated -> locally committed -> queued
queued -> in-flight -> synced
                  |-> retry -> in-flight
                  |-> failed-permanent
                  |-> conflict
```

La transacción financiera local permanece visible ante fallos transitorios y muestra estado de sincronización. Un rechazo permanente debe ofrecer corrección; no debe borrar silenciosamente el registro.

## Migración hacia el modelo canónico

1. Congelar y mapear columnas/versiones de `financial_movements`.
2. Crear o completar `transactions`, `ledger_entries`, recibos y restricciones.
3. Migrar datos históricos con IDs deterministas y verificar sumas por cuenta.
4. Cambiar lecturas/escrituras del cliente dentro de una versión coordinada.
5. Reconciliar proyecciones y medir divergencias.
6. Retirar el modelo legado sólo en una migración posterior.

No se acepta doble escritura permanente. El dueño y la versión exacta de esta migración deben acordarse con EP-CTA/EP-CCO.
