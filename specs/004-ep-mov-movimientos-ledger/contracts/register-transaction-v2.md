# Contract: `register_transaction_v2` — hash inequívoco de altas estándar

**Propagated**: 2026-10-02 — T093/F04, FR-008/009/019. Evolución técnica aprobada por la remediación converge; no agrega HU.

## Compatibilidad

- Nuevas altas estándar usan contract_version=2 y RPC register_transaction_v2(p_command jsonb).
- Las filas S2 existentes conservan exactamente payload, hash, clave y contract_version=1; el worker selecciona register_transaction_v1. No recalcular hashes al reclamar, migrar o reenviar.
- No se altera ninguna migración aplicada ni el comportamiento de altas especializadas S3. Versiones distintas de 1/2 se rechazan antes de red. Desplegar v2 antes de distribuir cliente nuevo; una RPC ausente no acredita aceptación.
- El wrapper v2 valida propietario derivado de auth.uid(), formato y hash, y reutiliza el registro estándar v1 dentro de la misma transacción. Se conserva el recibo y locking actuales; no hay efectos antes de validar el hash. No acepta user_id/operation_kind/card_id del cliente para este contrato estándar.

## Canonicalización exacta

SHA-256 UTF-8 del array JSON compacto (sin espacios externos), en este orden:

`["MOV_REGISTER_V2",userId,type,amountMinorDecimalString,currency,sourceAccountIdOrNull,destinationAccountIdOrNull,categoryIdOrNull,merchantIdOrNull,merchantProvisionalTextOrNull,occurredAtEpochMillisDecimalString,noteOrNull]`

UUID de propietario/referencias en minúsculas; enum y moneda en mayúsculas. Valores monetarios y epoch son strings decimales base 10 sin ceros adicionales. Textos sin trim ni normalización Unicode: null y vacío son distintos. Escape JSON obligatorio para comillas, backslash y controles; Unicode se codifica directamente UTF-8, sin ASCII escaping. No concatenar textos con separadores. timestamp RFC-3339 representa exactamente los milisegundos del array; no aceptar fracciones submilisegundo en v2. Hash hexadecimal minúsculo de 64 caracteres. Clave de idempotencia y transaction.id no forman parte del contenido financiero canónico (como en S2); el recibo identifica la clave y conserva el resultado original.

Campos de transaction: los del DTO estándar S2 (incluido merchant_provisional_text). El servidor rechaza campos desconocidos para evitar contenido no firmado. Tipo, monto, fecha y referencias siguen validaciones S2 y ownership. El esquema v1 permanece legible para colas históricas; su texto histórico «JSON canónico» no describe la concatenación de implementación S2 y no se toma como prueba de hashing inequívoco.

## Evidencia requerida

T093: vectores idénticos Kotlin/Postgres con delimitadores, null/vacío, comillas/backslash/controles, Unicode; hash alterado rechaza sin efectos; misma clave/contenido replay, contenido distinto conflicto; payload v1 original se reenvía intacto. T067/T085/T087 verifican wrapper/grants, regresión S2/S3 y RPC real. Pruebas locales no sustituyen ejecución PostgreSQL.

### Golden vector común

```json
["MOV_REGISTER_V2","11111111-1111-1111-1111-111111111111","EXPENSE","2500","PEN","22222222-2222-2222-2222-222222222222",null,null,null,"Bodega ñ 🦙","1000000","a;note:b \"x\"\\\n"]
```

SHA-256: `de3c19c4dae2a263d61fb22d62e1d46847ae83379e954fdd5e3377b63b2d5218`.
