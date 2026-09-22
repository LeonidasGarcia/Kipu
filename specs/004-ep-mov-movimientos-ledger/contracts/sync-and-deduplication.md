# Contract: sincronización y duplicados

## Tabla de decisión exacta

| Clave | Hash | Resultado | Efectos nuevos |
|---|---|---|---|
| No existe | N/A | `APPLIED` o `REJECTED` | Sólo si es válido. |
| Existe | Igual | `DUPLICATE` | Ninguno. |
| Existe | Distinto | `CONFLICT` | Ninguno. |

## Similitud local

Antes del commit, buscar movimientos activos del mismo usuario con cuenta origen, tipo, monto y moneda iguales dentro de una ventana de cinco minutos. La fecha efectiva, destino, categoría y comercio se muestran como contexto, pero no son dimensiones obligatorias de la coincidencia en S2.

- Sin coincidencia: continuar.
- Con coincidencia y cancelación: no escribir nada.
- Con coincidencia y confirmación: generar nueva clave y continuar.

La similitud nunca reemplaza la idempotencia exacta ni produce rechazo remoto por sí sola.

## Procesamiento de outbox

1. Reclamar mensaje pendiente cuyo `next_attempt_at` venció.
2. Marcar `IN_FLIGHT` con lease limitado.
3. Invocar el contrato versionado.
4. Marcar `SYNCED` ante `APPLIED` o `DUPLICATE` equivalente.
5. Marcar conflicto/permanente para intervención de UI.
6. Reprogramar errores transitorios con backoff y jitter.
7. Recuperar leases vencidos tras reinicio.

El orden debe ser estable por cuenta cuando comandos posteriores dependan de anteriores.

## Seguridad

No incluir notas, nombres o tokens en logs. Los mensajes de error almacenan códigos seguros. RPC y RLS validan autenticación, membresía y propiedad de todas las referencias.
