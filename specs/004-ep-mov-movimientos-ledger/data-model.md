# Data Model: Movimientos y ledger

Las secciones iniciales conservan el diseño S2. El incremento S4 al final define su evolución desde Room v16 y prevalece para revisiones/rechazos; no se afirma que estas estructuras nuevas estén implementadas.

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

## Incremento S4 — T059 (2026-10-02)

### Baseline y evolución

Baseline aceptada: main `c80ea0ddea80dfe5332971f12418758ea1bc9923`; KipuDatabase v16. Postgres ya contiene transactions con revision/status, transaction_revisions con snapshots, ledger interno, recibos y sync_changes. Room tiene transactions ACTIVE/FAILED y sync_status, ledger, proyecciones, recibos/outbox; no posee revisiones S4 ni revisión remota en TransactionEntity. Estas diferencias se resuelven mediante una migración nueva y contratos versionados, no modificando los archivos históricos.

### Transaction vigente

| Campo S4 | Room / dominio | Postgres / regla |
|---|---|---|
| identity | (user_id, id), ambos conservados | id UUID existente; propietario autenticado |
| status | CONFIRMED, REVISED, VOIDED | mismos estados financieros; sync_status separado |
| operation_kind | STANDARD o naturaleza existente | no inventar tipos; G/I/T sigue intacto |
| revision | revisión local de la rama visible, entero positivo | revisión oficial secuencial por transacción |
| acknowledged_revision | última revisión oficial incorporada; nullable en alta pendiente | devuelta por servidor, nunca inferida de timestamps |
| current_revision_id | snapshot que explica el payload local vigente | revisión oficial o evidencia de origen |
| payload | importe Long, moneda, fecha efectiva, referencias, nota y clasificación vigentes | mismos hechos, propietario no editable |
| source | procedencia existente o MANUAL para altas manuales comprobables | conservar procedencia conocida; no inferir banco/OCR para legado desconocido |
| sync_status | PENDING/IN_FLIGHT/SYNCED/CONFLICT/FAILED_PERMANENT y estados históricos | no convierte por sí mismo CONFIRMED en VOIDED |

Guardia genérica: operation_kind ausente en estándar histórico se normaliza solo cuando los datos prueban que es estándar; CARD_PURCHASE, CARD_PAYMENT, legacyKind especializado o relaciones de cuotas/deuda/reembolso/meta se rechazan en edición/anulación genérica. Verificar relaciones además del enum; la categoría opcional no prueba elegibilidad. Mantener tipo, moneda y naturaleza económica durante la edición; cambios de G/I/T o moneda exigen otro alcance aprobado. Cuentas/categorías nuevas son propias y elegibles; referencias históricas sin cambio pueden conservarse aunque estén archivadas/bloqueadas.

### TransactionRevision append-only

Room: nueva entidad `transaction_revisions` con clave (user_id, revision_id); índice (user_id, transaction_id, local_revision). Postgres: evolucionar la tabla existente sin recrearla, con unicidad de revisión oficial por propietario/transacción y protección de propietario/FK.

| Campo | Regla |
|---|---|
| revision_id | identidad estable del snapshot, emitida al preparar comando local; ID oficial puede tener alias |
| transaction_id, user_id | mismo propietario que transacción y todas sus relaciones |
| command_id, command_type | identidad y operación que explican el cambio |
| base_revision, local_revision | revisión esperada y revisión local resultante; inmutables |
| official_revision | nullable mientras pendiente; asignación oficial se conserva en metadatos de reconciliación separados |
| previous_payload, new_payload | snapshots completos, incluyendo estado/tipo/moneda/naturaleza/fecha/referencias; no parches parciales |
| change_reason, created_at | motivo opcional, fecha de auditoría; nunca decide precedencia |
| provenance | LOCAL_COMMAND, REMOTE_SNAPSHOT o MIGRATION_BASELINE |

Dos ramas pueden proponer el mismo número siguiente. No imponer unicidad local del número oficial sobre snapshots pendientes: usar revision_id y asignación oficial independiente; conservar ambos snapshots ante conflicto. En remoto solo una revisión oficial puede ocupar el número. No actualizar/eliminar snapshots para acomodar otra rama.

### Ledger y reconciliación de identidad

Conservar `ledger_entries` existentes e inmutables. Añadir metadatos/vínculos sin alterar importes originales: comando, ordinal de efecto, revisión, efecto revertido y rol. Los nuevos efectos tienen clave lógica única (user_id, command_id, effect_ordinal), tanto en Room como en remoto. Los IDs físicos existentes pueden diferir entre cliente/servidor; un mapa de alias enlaza identidad lógica, row_id local y row_id remoto, sin reemplazar evidencia contable.

- Una anulación invierte una sola vez el conjunto de efectos vigentes de la revisión objetivo. REVERSAL es evidencia contable, no un cuarto tipo de movimiento.
- Corrección con cambio monetario/cuenta: revertir efectos vigentes y agregar efectos del nuevo payload. Una transferencia siempre afecta ambos extremos juntos.
- Fecha, nota o clasificación sin cambio en efecto monetario no requieren asientos de caja; el snapshot explica cambio de periodo/clasificación.
- El saldo es la suma del ledger completo original + compensaciones + efectos nuevos, por propietario/cuenta/moneda; no filtrar el ledger por status del movimiento y luego volver a aplicar contrapartidas.
- En estándar S2 sin identidad lógica compartida, al recibir el snapshot base verificar coincidencia exacta de sus efectos (cuenta, rol, moneda, importe y cardinalidad) y enlazar aliases; no insertar otra copia del efecto. Si no puede demostrarse correspondencia, conflicto; nunca adivinar ni reemplazar filas.
- Nuevos vínculos de reversión resuelven a efectos propios y ya aplicados; unicidad lógica/recibo impide compensarlos dos veces. No comparar dos conjuntos completos diferentes como si fueran el mismo baseline S2; incorporar deltas S4 por revisión/identidad.

### Recibos, outbox y propuesta en conflicto

Extender los recibos con contract_version, command_type, expected_revision, resulting_revision y resultado original. Unicidad (user_id, idempotency_key) existente se conserva; usar nuevos UUID para comandos distintos. Mismo hash devuelve resultado previo antes de evaluar revisión; hash distinto nunca cambia recibo original.

Outbox añade command_type/version, expected_revision, depends_on_command_id y payload inmutable. Orden causal por transacción; no enviar una corrección antes del alta ni una descendiente de un comando en conflicto. Conservar referencia a propuestas y error seguro; no guardar tokens ni textos financieros en logs.

Nueva entidad local `movement_conflict_proposals`: propietario, transaction_id, proposal_id, command_id, base_revision, proposed_snapshot, local_revision_ids y remote_revision_id. Separar estado de resolución de snapshots contables inmutables. Descartar o rehacer sigue el procedimiento de sync-and-deduplication, no actualiza destructivamente ledger/revisiones. Un conflicto bloquea la cadena descendiente hasta reconciliar; otros agregados pueden sincronizar.

### Consumo base de periodos — FR-012

Frontera interna `ExpenseConsumptionQuery`: user_id derivado de sesión, currency, fromInclusive/toExclusive (instantes), zoneId explícita al convertir fechas y categoryIds opcionales como conjunto. Consultar únicamente payload vigente de EXPENSE en CONFIRMED/REVISED. Cada ID entra una vez; nunca sumar snapshots ni asientos como gastos. Transferencias/pagos de tarjeta no consumen; compras de tarjeta vigentes sí son gasto, aunque no admitan mantenimiento genérico.

Resultado: amount_minor Long y versión de datos/snapshot consultado. No mezclar monedas. categoryIds se intersecta sobre IDs de transacción únicos; cuando un consumidor aporta raíz y subcategoría coincidentes no cuenta dos veces. El consumidor presupuestario futuro aporta periodo/elegibilidad, no modifica contabilidad. Este cálculo interno no introduce una pantalla analítica Free ni implementa administración de presupuestos.

| Fixture explícita | A | B | Caja respecto al alta original |
|---|---:|---:|---:|
| Gasto S/20 con fecha en A | 2000 | 0 | -2000 |
| Misma operación revisada solo a fecha B | 0 | 2000 | -2000 |
| Operación VOIDED | 0 | 0 | 0 |
| Fallo antes de commit de cambio A→B | 2000 | 0 | -2000 |

Fixture: A=[2026-09-01T00:00:00-05:00,2026-10-01T00:00:00-05:00), B=[2026-10-01T00:00:00-05:00,2026-11-01T00:00:00-05:00), zoneId America/Lima y PEN. La zona de fixture no fija una nueva política global. Una lectura coherente de ambos totales usa la misma transacción/snapshot Room; tras commit, A y B ya corresponden al payload vigente sin esperar worker de invalidación. Toda proyección materializada existente afectada se actualiza en ese commit.

### Migración Room v16 y Postgres

1. Revalidar baseline al iniciar T066; elegir siguiente versión disponible (17 si continúa v16), registrar JSON de esquema y migración explícita. No fallback destructivo.
2. Conservar IDs, importes, monedas, timestamps, referencias, ledger, recibos y bytes de outbox pendiente. Backfill estándar ACTIVE→CONFIRMED con snapshot de baseline; recuperar revisión oficial por sincronización si no existe evidencia local, sin inventar revisión remota.
3. FAILED histórico no se convierte automáticamente a VOIDED ni se compensan importes por su etiqueta. Auditar efectos realmente presentes y recibo de rechazo: conservar evidencia existente; si ya fueron eliminados, no fabricarlos. Casos no demostrables quedan para reconciliación, no reparación silenciosa.
4. Incorporar entidades/metadatos anteriores y claves únicas; conservar mapping de aliases entre base local y remoto. No convertir datos especializados a STANDARD.
5. Postgres: migración aditiva sobre tablas existentes; validar datos antes de añadir FK/unicidad. Nuevas referencias compuestas impiden enlaces entre propietarios. Ledger/revisiones append-only y sin DELETE de clientes; RPC valida propietario y referencias, locks, recibo y revisión dentro de transacción.
6. Ledger/recibos internos siguen fuera de Data API; lecturas propias de transacciones/revisiones tienen RLS, writes financieros de clientes revocados. SECURITY DEFINER, si es necesario para RPC existente, usa propietario mínimo, search_path fijo y comprobaciones explícitas; no depender de RLS para contener un rol BYPASSRLS.
7. Sync VOIDED usa UPSERT con snapshot/tombstone conservado; DELETE del protocolo histórico no se usa para movimientos S4. Conservar tombstones/revisiones/recibos mientras puedan llegar dispositivos atrasados.
8. Probar alta limpia, upgrade con transacciones estándar/crédito y outbox pendiente, sumas pre/post idénticas salvo comandos explícitos, rollback, replay y dos usuarios. El diseño no aplica migraciones ni demuestra esos resultados todavía.


**Propagated**: 2026-10-02 — T093: altas estándar nuevas usan contracts/register-transaction-v2.md (hash de array compacto, validación remota y dispatch por versión); comandos S2 ya encolados permanecen en v1 con bytes/hash originales. No modifica identidad histórica, modelo de ledger ni alcance S4.
