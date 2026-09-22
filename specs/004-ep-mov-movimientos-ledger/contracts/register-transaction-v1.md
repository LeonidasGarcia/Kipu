# Contract: `register_transaction_v1`

## Command

```json
{
  "contract_version": 1,
  "idempotency_key": "uuid",
  "request_hash": "sha256-hex",
  "transaction": {
    "id": "uuid",
    "user_id": "derived-from-session",
    "type": "EXPENSE|INCOME|TRANSFER",
    "amount_minor": 1250,
    "currency_code": "PEN",
    "source_account_id": "uuid|null",
    "destination_account_id": "uuid|null",
    "category_id": "uuid|null",
    "merchant_id": "uuid|null",
    "occurred_at": "RFC-3339",
    "note": "string|null"
  }
}
```

El hash se calcula sobre JSON canónico excluyendo `request_hash`. El servidor obtiene el usuario desde la sesión; no acepta `created_by` del cliente.

## Response

```json
{
  "status": "APPLIED|DUPLICATE|CONFLICT|REJECTED",
  "transaction_id": "uuid|null",
  "receipt_id": "uuid|null",
  "server_updated_at": "RFC-3339|null",
  "error": {
    "code": "string",
    "field": "string|null",
    "retryable": false
  }
}
```

## Validaciones

- Monto positivo y representable en minor units.
- Tipo y campos requeridos coherentes; `category_id` es obligatorio para `EXPENSE` y opcional para `INCOME`/`TRANSFER`.
- Origen y destino distintos en transferencia.
- Todas las referencias pertenecen al mismo espacio y son visibles para el usuario.
- Moneda coherente con las cuentas para el alcance S2.
- Clave idempotente única por `user_id` y propósito; el usuario se deriva de la sesión.

## Efectos atómicos

En una sola transacción PostgreSQL: validar, insertar `transactions`, insertar uno o dos `ledger_entries`, registrar recibo y actualizar/publicar proyección. Cualquier fallo revierte todos los efectos.

## Semántica idempotente

- Clave nueva: procesa y responde `APPLIED`.
- Clave existente + mismo hash: responde `DUPLICATE` con el resultado original.
- Clave existente + hash distinto: responde `CONFLICT`, sin nuevos efectos.
- Validación fallida: responde `REJECTED`, sin efectos financieros.
