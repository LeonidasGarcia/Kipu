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

## Incremento S4 — T060: causalidad y revisión

El contrato S2 anterior conserva sus claves/hashes. S4 usa revise-and-void-transaction-v1.md y metadatos explícitos de comando/revisión. Leer recibo antes de comparar revisión tanto local como remoto. Payload/hash no cambian entre reintentos; timeout tras commit remoto se reconoce por recibo. Hash distinto conserva el recibo previo y devuelve conflicto.

La outbox persiste dependencias causales por agregado: alta → revisión → anulación. Solo enviar hijo cuando el padre esté confirmado remoto con la revisión base compatible. Conflicto/permanente detiene descendientes, sin detener agregados independientes ni gastos manuales. No renumerar expectedRevision ni reescribir hash para resolver una carrera: rehacer prepara un comando nuevo.

### Pull y tombstones

1. Servidor emite UPSERT de estado/revisión junto al snapshot, IDs lógicos de efectos, revisiones y referencias de contrapartida. VOIDED es un UPSERT conservado, no DELETE físico.
2. Validar propietario y contratos/moneda/relaciones antes de aplicar. Procesar cada página y checkpoint en una transacción local; si una fila no puede verificarse, no avanzar más allá de ella y mostrar fallo/conflicto seguro.
3. Revisión remota inferior se ignora sin mutar estado; misma revisión y mismo contenido es replay; misma revisión con contenido distinto es conflicto de protocolo.
4. Snapshot remoto superior sin cambios locales pendientes: enlazar aliases de baseline, aplicar solo efectos lógicos aún no incorporados, conservar evidencia y actualizar head/proyecciones/consumo en un commit.
5. Con propuestas locales pendientes, conservar sus snapshots y cadena. No sobrescribirlas ni marcar sync exitoso; iniciar reconciliación descrita abajo.
6. IDs físicos distintos no prueban dos operaciones. Para baseline estándar comparar efectos y cardinalidad exactos y mapear alias; en S4 usar unicidad (user_id,command_id,effect_ordinal). No sumar original remoto de nuevo junto al original local.
7. Conservar VOIDED aunque llegue snapshot ACTIVE de cliente/servidor antiguo. Un payload legacy sin revisión nunca tiene prioridad sobre head S4 conocido. Si no puede establecerse baseline compatible, detener esa cadena y revalidar; no fabricar revisión por timestamp.

### Conflicto de edición local y rama oficial

La propuesta conserva base, snapshot y efectos optimistas; la rama oficial conserva revisiones separadas. No imponer unicidad del número siguiente a ambas ramas locales.

Dentro de un commit de reconciliación: identificar todas las revisiones locales no aceptadas de la cadena y neutralizar sus efectos monetarios realmente aplicados mediante compensación auditable de reconciliación; registrar el descarte/VOIDED del intento local en evidencia separada, sin alterar sus snapshots anteriores ni el movimiento oficial. Luego incorporar deltas remotos verificados por identidad lógica y establecer el head visible oficial; conservar propuesta para que el usuario compare/decida. Si no puede demostrar cuáles efectos locales están aplicados, no adivinar: mantener conflicto pendiente y detener cadena.

Una fila remota previamente aceptada y una propuesta son evidencia distinta. Nunca compensar el original remoto válido como si fuera el intento rechazado. El caso simple prueba caja -2000 de base, -1500 local propuesto y -1800 remoto: neutralizar delta local +500 con -500, aplicar delta remoto +200 y obtener -1800; no sumar dos copias del original. Los IDs de reconciliación y de efectos se persisten una vez en recibo; repetir respuesta no crea otra compensación.

Descartar marca resuelta la propuesta, conservando historia. Rehacer crea nuevo comando sobre head oficial y conserva el intento viejo; si head VOIDED, no permitir rehacer edición para reactivar. Descendientes de la rama descartada quedan visibles como bloqueados/descartados, no se reenvían sobre otra revisión silenciosamente.

### Rechazo financiero permanente (P5)

- Rechazo antes de cualquier commit local: REJECTED sin movimiento/asientos ficticios.
- Rechazo de alta/compra/pago ya confirmado localmente y no aceptado remoto: crear evidencia VOIDED y contrapartidas de todos sus efectos vigentes, una sola vez; preservar ledger, cuotas, asignaciones y vínculos. Actualizar saldo/pasivo/disponibilidad de cuotas en el mismo commit, sin DELETE.
- Rechazo de corrección sobre un original válido: neutralizar solo el intento local y volver al head oficial como en conflicto; original no se convierte a VOIDED. Identificar VOIDED del intento en historial de revisión, no como borrado del hecho válido.
- Especializadas no admiten mantenimiento genérico; la compensación de su rechazo ocurre en el handler propietario EP-CTA (T076), que usa el dominio común. Probar cuotas pagadas/parciales y asignaciones: evidencia preservada, efectos derivados corregidos, ninguna operación económica duplicada.
- Network/5xx/auth temporal no son rechazo financiero permanente. Mantener commit y cola; WAITING_FOR_AUTH no borra datos.

### Pruebas de reconciliación

T068/T071/T072/T076/T077 deben probar cadenas de varias ediciones offline, conflicto del padre con hijos, timeout tras commit, misma revisión con payload distinto, aliases baseline y deltas S4, transferencia y pago especializado rechazado, doble respuesta y replay después de VOIDED. T085 prueba que checkpoint y efectos hacen rollback juntos y que usuario B no lee propuesta/snapshot/recibo de A.


**Propagated**: 2026-10-02 — T093: altas estándar nuevas usan contracts/register-transaction-v2.md (hash de array compacto, validación remota y dispatch por versión); comandos S2 ya encolados permanecen en v1 con bytes/hash originales. No modifica identidad histórica, modelo de ledger ni alcance S4.
