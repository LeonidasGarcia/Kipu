# Contract: corrección y anulación estándar S4 — T060

**Version**: 1. **Estado**: diseño de S4, no endpoint desplegado. **Trazabilidad**: HU-20/21; FR-011–015/029–031; P2/P5; P14.

## Frontera y comandos

RPC propuestas: `revise_transaction_v1(jsonb)` y `void_transaction_v1(jsonb)`, llamadas autenticadas desde MovementApi. Revisar nombres/overloads reales al crear T067. No modificar register_transaction_v1 ni reinterpretar bytes/hashes de alta S2 pendientes.

| Campo del sobre | Tipo / regla |
|---|---|
| contract_version | entero 1 |
| command_type | REVISE_TRANSACTION o VOID_TRANSACTION; debe coincidir con RPC |
| idempotency_key | UUID estable del comando, nuevo para una acción lógica nueva |
| transaction_id | UUID propio existente |
| expected_revision | entero positivo de la revisión base conocida |
| depends_on_command_id | UUID nullable del comando previo de la cadena offline |
| reason | string nullable; auditoría, no clave de identidad |
| request_hash | SHA-256 hexadecimal de los bytes canónicos definidos abajo |
| revised_payload | snapshot completo de campos editables solo en REVISE; null en VOID |

Snapshot REVISE: `type`, `operation_kind`, `amount_minor` (entero positivo Long), `currency_code`, `source_account_id`, `destination_account_id`, `category_id`, `merchant_id`, `merchant_provisional_text`, `occurred_at` (UTC RFC3339 con precisión de milisegundos), `note`. Null explícito en campos opcionales. No aceptar user_id, saldos, privilegios, asientos, revisión resultante ni estado arbitrario del cliente.

El cliente puede transportar identidad y propuesta; el servidor deriva propietario y efectos desde el estado vigente y reglas de dominio. Ni hash ni JWT por sí solos acreditan propiedad del objeto.

## Canonicalización inequívoca

S4 usa su propio hasher versionado. SHA-256 sobre UTF-8 de un array JSON compacto con orden fijo:

`["MOV_REVISION_V1", command_type, idempotency_key, transaction_id, expected_revision_decimal_string, depends_on_command_id_or_null, reason_or_null, payload_or_null]`

Para REVISE, payload es el array fijo:

`[type, operation_kind, amount_minor_decimal_string, currency_code, source_account_id_or_null, destination_account_id_or_null, category_id_or_null, merchant_id_or_null, merchant_provisional_text_or_null, occurred_at_epoch_millis_decimal_string, note_or_null]`

- UUID en minúsculas; enum/moneda normalizados por contrato antes de preparar el comando.
- Decimal strings sin signo +, ceros iniciales ni fracciones. Esto evita diferencias de serialización numérica entre plataformas.
- Strings: escapes JSON obligatorios para comillas/backslash/control; sin escape opcional de `/`; Unicode válido emitido como UTF-8 directo, sin normalización Unicode silenciosa ni trimming al reenviar. Null no equivale a string vacío.
- El reloj de auditoría, user_id derivado, request_hash, metadatos locales y respuestas no participan.
- Canonicalizar una vez al preparar y persistir payload/hash; servidor vuelve a calcular sobre la solicitud normalizada. Golden vectors de null/vacío, `;`, comillas, backslash, Unicode, fechas y montos máximos son obligatorios en T064/T067.

## Precondiciones y validación

1. Autenticar sesión y derivar propietario; rechazar inexistente/ajeno con respuesta neutra, sin revelar snapshot.
2. Consultar recibo propio por clave. Hash/contract/type iguales devuelven resultado previo; contenido distinto produce conflicto de identidad sin modificar el recibo original.
3. Para comando nuevo, bloquear head/transacción y reconsultar recibo bajo exclusión mutua. Validar base y cadena; solo una mutación puede aceptar una revisión dada.
4. Movimiento estándar propio CONFIRMED/REVISED sin cuotas, deuda, tarjeta u otras relaciones especializadas. La ausencia histórica de operation_kind solo es compatible si se acredita STANDARD.
5. No permitir cambiar tipo, moneda o naturaleza. Importe positivo/representable; moneda igual a las cuentas; origen/destino distintos en transferencia; categoría de tipo compatible y referencias propias.
6. Para relación sin cambio conservar historia bloqueada/archivada; al seleccionar otra, exigir elegibilidad actual. No derivar autorización Premium de UI; mantenimiento manual sigue Free sujeto a integridad y selección vigente.
7. ExpectedRevision obsoleta produce conflicto. VOIDED no se reactiva. Anulación nueva sobre VOIDED con revisión actual devuelve ALREADY_VOIDED sin efectos; con revisión antigua produce conflicto. Replay de la anulación original se reconoce por recibo antes de ese control.

## Operación atómica

Aplicar dentro de una sola transacción Room o PostgreSQL: comprobar revisión, guardar snapshots anterior/nuevo, calcular plan de efectos, agregar compensaciones/efectos nuevos, actualizar payload/head, recalcular proyecciones existentes, guardar recibo y outbox local o sync_changes remoto. Ningún éxito antes del commit completo. Error intermedio produce rollback, no recibo de éxito ni media transferencia.

| Acción | Evidencia / efecto |
|---|---|
| Corregir S/20→S/15 | Ledger original -2000 conservado; +2000/-1500 agregados; caja neta -1500 y payload 1500 |
| Cambiar cuenta | Neutralizar efecto en cuenta anterior y aplicar nueva cuenta en el mismo commit |
| Cambiar transferencia | Neutralizar ambos extremos y aplicar ambos nuevos juntos |
| Solo nota/clasificación/fecha | Nueva revisión; cero asientos de caja si el efecto monetario no cambia; consumos desde payload vigente |
| VOID | Nueva revisión VOIDED, contrapartidas de efectos vigentes; historia legible y gasto vigente cero |
| Cancelar edición/diálogo | Ninguna revisión, recibo, efecto ni outbox nuevos |

## Resultados

Sobre de respuesta: status APPLIED/DUPLICATE/CONFLICT/REJECTED; transaction_id propio, receipt_id, resulting_revision, server_updated_at y código seguro. APPLIED incluye resultado REVISED/VOIDED/ALREADY_VOIDED y snapshot vigente/identidades lógicas de efectos; DUPLICATE incluye el resultado original, sin efectos nuevos.

CONFLICT devuelve current_revision/snapshot solo al propietario autorizado y preserva la propuesta local. REJECTED distingue OPERATION_SPECIALIZED, INVALID_REFERENCE, INVALID_AMOUNT, ALREADY_VOIDED_FOR_EDIT o REQUEST_HASH_MISMATCH. AUTH_REQUIRED/NOT_AVAILABLE no revelan objetos ajenos. Fallos de red/5xx reintentan la misma identidad; no se clasifican como rechazo financeiro definitivo.

Una solicitud inválida antes de confirmar no crea efectos. Si el cliente ya había confirmado efectos y recibe rechazo permanente, aplicar el contrato de compensación/reconciliación de sync-and-deduplication: conservar evidencia, VOIDED del intento rechazado y contrapartidas; nunca borrar ni anular un hecho remoto válido por conflicto de edición.

## Seguridad y compatibilidad

Writes financieros solo por RPC; RLS de lectura por propietario, FK de relaciones propias, índices de búsqueda/locking y permisos mínimos. Ledger/recibos internos fuera de API. Prohibir UPDATE/DELETE de evidencia append-only para clientes; RPC privilegiada valida ownership explícitamente con search_path fijo. No registrar payload, notas, nombres ni tokens en logs.

Cliente S4 requiere servidor que anuncie soporte del contrato. Un servidor antiguo no puede recibir revisiones como altas nuevas: mantener cola y mostrar indisponibilidad de sync S4 hasta compatibilidad; alta S2 conserva su contrato. T067 despliega contrato antes del cliente distribuido y verifica compatibilidad de pull.

## Pruebas exigidas

T064/T067: hashing inequívoco y límites, propiedad/RLS, carrera con misma clave, dos revisiones sobre la misma base, rollback después de cada frontera, replay después de VOIDED, nueva clave sobre VOIDED, vínculos ajenos, relación histórica conservada/nueva bloqueada, nota sin asientos, transferencia íntegra y operación especializada sin mutación genérica. T071/T077: sumas ledger/proyección y consumo A/B coincidentes antes/después/reintento.


### Golden vector VOID — T064/T067

```json
["MOV_REVISION_V1","VOID","33333333-3333-3333-3333-333333333333","22222222-2222-2222-2222-222222222222","1",null,"anulación ñ 🦙; \"x\"\\\n",null]
```

SHA-256: `44deef93fef683489a80af88b387f2e5e34bf0c43e9751793b52f5518743135d`.
