# Auditoría y plan de fixes — Kipu

**Estado:** diagnóstico y plan base preservados; correcciones, evidencia y verificaciones pendientes en [CHECKLIST_FIXES_AUDITORIA.md](CHECKLIST_FIXES_AUDITORIA.md).  
**Corte revisado:** worktree `010-ep-not-notificaciones-avisos`, 27/09/2026.  
**Alcance:** cambios locales pendientes y rutas relacionadas de gastos, tarjetas, ledger, Room, navegación, sincronización y Supabase.

## Resumen ejecutivo

Hay una causa raíz de alta confianza para el crash reportado al guardar un gasto con tarjeta: la compra se persiste como `EXPENSE` con `card_id` y sin `source_account_id`, pero el modelo de dominio exige una cuenta para todo gasto y no representa la tarjeta. El historial observa la tabla de transacciones y transforma cada fila al dominio; al materializar esa compra, el constructor lanza `IllegalArgumentException`. La ruta es especialmente directa al registrar desde el propio historial, cuyo observador permanece activo mientras se muestra el formulario.

La prueba unitaria añadida al `QuickMovementViewModel` cubre el despacho al caso de uso con un repositorio falso; no cubre el mapeo Room→dominio que falla. La primera corrección debe incluir una regresión de persistencia/observación real antes de ampliar la auditoría funcional.

También se encontraron riesgos verificables en el outbox de instrumentos: comandos `IN_FLIGHT` no tienen lease recuperable y la consulta de pendientes ignora el predecesor causal. Hay una discrepancia entre el contrato del ID de agregado y el ID guardado para compras. Finalmente, el modelo documentado de ledger/deuda no coincide con la representación observada de compras, que usa cuotas y publica una lista de asientos vacía. Esas decisiones deben alinearse antes de certificar saldos.

## Fuentes y límites de la auditoría

La autoridad de negocio usada, en este orden, es:

1. [Constitución Kipu](.specify/memory/constitution.md): integridad financiera, local-first, idempotencia, privacidad y migraciones no destructivas.
2. Contratos y modelo vigentes de [EP-CTA](specs/002-ep-cta-cuentas-tarjetas/) y [EP-MOV](specs/004-ep-mov-movimientos-ledger/), incluidos `credit-commands.md`, `sync-contract.md`, `register-transaction-v1.md` y `data-model.md`.
3. Código, migraciones Room/Supabase y pruebas existentes en esta rama.
4. Informes de remediación anteriores, tratados como evidencia histórica, no como estado remoto actual.

`docs/s1-s2-remote-backend-readiness.md` está fechado el 24/09/2026 y `docs/sprint-1-2-remediation.md` conserva resultados de días anteriores. No se verificó el backend conectado durante esta auditoría. Las discrepancias descritas abajo se deben confirmar en una base local limpia y en staging; no se deben desplegar migraciones a producción como parte del diagnóstico.

La rama ya contiene cambios sin confirmar en formularios de instrumentos, presets visuales, movimientos, catálogo de tasas, animación, navegación y Room. Se preservaron íntegramente. La revisión no reemplaza el Logcat del dispositivo: la causa indicada se deriva del camino de código y predice una excepción concreta, pero se debe contrastar con la traza real antes de cerrar el incidente.

## Hallazgos priorizados

### F-01 — P0: el dominio no puede representar un gasto financiado por tarjeta

**Evidencia:**

- `QuickMovementViewModel.kt:316-367` deriva gastos con tarjeta a `ConfirmCreditPurchase`.
- `OfflineFirstFinancialInstrumentsRepository.kt:1023-1043` inserta una `TransactionEntity` de tipo `EXPENSE`, define `cardId`, y deja `sourceAccountId` nulo.
- `MovementLocalDataSource.kt:317-335` convierte la entidad pasando `sourceAccountId`, pero no copia `cardId` ni `operationKind` al dominio.
- `MovementModels.kt:55-90` exige `sourceAccountId` no nulo para todo `EXPENSE`; el modelo `Transaction` no tiene `cardId`.
- `OfflineFirstMovementRepository.kt:39-64` convierte cada entidad observada con `toDomain()`. `MovementHistoryViewModel.kt:48-64` mantiene ese flujo activo, y `MovementHistoryScreen.kt:289-295` permite abrir el mismo formulario desde el historial.

**Causa raíz:** el camino nuevo escribe una compra válida según el modelo de crédito, pero el modelo común de movimientos aún codifica el supuesto antiguo de que todo gasto sale de una cuenta líquida. Cuando Room emite la fila, el mapper construye `Transaction(EXPENSE, sourceAccountId = null)` y falla con `Expense requires a source account`. Si el formulario se abrió desde Historial, el colector activo procesa la fila al guardarla; en otras rutas el mismo error aparece al observar/abrir el historial.

**Confianza:** alta para esta excepción y este camino; falta cotejar el Logcat para confirmar que sea el único crash observado y el instante exacto.

**Corrección propuesta:** ampliar el modelo de dominio y sus validadores para representar explícitamente la fuente financiera. Un gasto debe tener exactamente una fuente compatible: cuenta líquida o tarjeta, según `operationKind`; una compra de tarjeta debe tener `cardId`, ningún débito de cuenta y categoría obligatoria. Propagar `cardId`/`operationKind` por mappers, DTOs, `TransactionItem`, historial, búsqueda y navegación de detalle. No usar el ID de la tarjeta como si fuera `sourceAccountId`.

**Regresión obligatoria:** en prueba de integración con Room real, iniciar la observación de transacciones, insertar la tarjeta y categoría, confirmar una compra por el mismo repositorio usado por UI y comprobar que la colección emite sin excepción. La fila resultante debe ser `EXPENSE/CARD_PURCHASE`, mantener `cardId`, `sourceAccountId = null`, categoría y comercio. Añadir prueba Compose desde Historial: abrir el formulario, elegir tarjeta, guardar y verificar que el historial permanece visible sin cerrar la app.

### F-02 — P1: desalineación del contrato de ledger y deuda de tarjeta

`specs/002-ep-cta-cuentas-tarjetas/data-model.md:440,490` indica derivar deuda de efectos confirmados del ledger y que los comandos canónicos usen `internal.ledger_entries`. Sin embargo, `CreditDao.observeOutstandingPrincipalForCard` calcula deuda desde cuotas y asignaciones (`CreditEntities.kt:89-100`); la compra local inserta transacción y cuotas (`OfflineFirstFinancialInstrumentsRepository.kt:1042-1063`), y la RPC canónica publica `ledger_entries: []` (`20260925022130_s3_credit_canonical_purchase_payment.sql:602-609`).

Esto deja ambiguo qué fuente gobierna deuda, saldo histórico, reversas, cargos con interés y reconciliación. **No agregar asientos de forma mecánica**: el asiento actual de movimiento requiere una cuenta y una compra con tarjeta no debe reducir efectivo.

**Plan:** confirmar el contrato contable autorizado con EP-CTA/EP-MOV; documentar si la deuda se deriva de cuotas confirmadas, de una representación explícita de pasivo en ledger, o de ambas con una única regla de proyección. Después alinear Room, RPC, pull, reportes y backfill. Probar compra y pago sin doble gasto, deuda una sola vez, anulación/reverso, cuotas y reconciliación por moneda.

### F-03 — P1: el outbox usa dos identidades incompatibles para la compra

`sync-contract.md:9-14` define `aggregate_id` como ID estable de cuenta/tarjeta. En cambio, `confirmCreditPurchase` consulta el predecesor por `CARD/cardId`, pero guarda `aggregate_type = CARD` con `aggregate_id = transactionId` (`OfflineFirstFinancialInstrumentsRepository.kt:1076-1092`). `SyncInstrumentCommandsWorker` usa además ese mismo `aggregateId` como ID de transacción para actualizar su estado.

Una modificación aislada del campo podría arreglar la cadena causal y romper la actualización de `transactions.sync_status`. **Corrección propuesta:** separar identidad del agregado (tarjeta) e identidad de la transacción destino en el modelo/payload de sincronización, o definir un contrato de comando que exprese ambas de forma inequívoca. Alinear DAO, predecesores, worker, recibo y respuesta remota. Cubrir compra seguida de otra compra, pago, cambio de apariencia y archivo en modo offline/online.

### F-04 — P1: un comando `IN_FLIGHT` puede quedar atascado después de muerte del proceso

`SyncInstrumentCommandsWorker.kt:116-127` cambia el comando a `IN_FLIGHT`; `InstrumentSyncOutboxEntity` no tiene `lease_until`, y `InstrumentSyncDao.getPendingCommands` solo selecciona `PENDING`/`ERROR` (`InstrumentSyncDao.kt:22-29`). No se encontró recuperación de `IN_FLIGHT`. El contrato `sync-contract.md:43-49` exige que leases expirados vuelvan a pendientes. En contraste, el outbox de movimientos sí persiste lease y selecciona leases vencidos (`MovementEntities.kt:148-149`, `MovementDao.kt:90-100`).

**Corrección propuesta:** añadir lease temporal y recuperación atómica al outbox de instrumentos mediante migración forward-only; documentar el comportamiento ante timeout ambiguo para que se reenvíe el mismo operation ID/payload. Simular terminación tras marcar `IN_FLIGHT`, reiniciar worker y verificar un solo efecto remoto.

### F-05 — P1: no se comprueba el predecesor causal antes de enviar

El DAO devuelve pendientes por estado y fecha, pero no exige que `predecessor_operation_id` haya sido aceptado (`InstrumentSyncDao.kt:22-36`). El worker recorre esa lista (`SyncInstrumentCommandsWorker.kt:96-113`). Si un predecesor está en `ERROR` con reintento futuro, la consulta puede excluirlo mientras entrega un sucesor `PENDING`.

**Corrección propuesta:** seleccionar solo comandos cuyo predecesor esté `SYNCED`/aceptado, o cuyo recibo resuelva su revisión base; bloquear sucesores tras conflicto/error terminal y dar una vía visible de resolución. Probar cadena crear tarjeta → compra → archivo con backoff, error permanente, reintento y dos dispositivos.

### F-06 — P1: validación local y remota de compra no es equivalente

El repositorio local verifica propiedad, tipo/archivo, bloqueo de plan, moneda, importe positivo, plazo y comercio (`OfflineFirstFinancialInstrumentsRepository.kt:978-988`), pero no compara deuda actual más compra contra la línea. La RPC canónica sí rechaza `CREDIT_LIMIT_EXCEEDED` (`20260925022130_s3_credit_canonical_purchase_payment.sql:548-563`). Una compra offline fuera de línea puede quedar confirmada en Room y luego pasar a fallo permanente al sincronizar.

**Corrección propuesta:** definir validación optimista contra deuda local y tratamiento explícito de rechazo remoto, incluidos estado visible, conservación/reverso de datos y nueva reconciliación. No permitir que `availableCredit` se mezcle con efectivo. Probar importe igual/superior al cupo, deuda previa, concurrencia de dos dispositivos y estado remoto más reciente.

### F-07 — P1: el pull consume cambios de tarjeta/tombstones sin aplicarlos

El contrato `sync-contract.md:84-94` requiere aplicar cambios `ACCOUNT`, `CARD` y movimientos, y no avanzar el cursor tras una entidad desconocida o incompleta. El pull de `SyncMovementsWorker.kt:123-145` solo aplica `ACCOUNT` y `TRANSACTION`; la rama `change.operation == "DELETE"` retorna `true` para cualquier entidad y el `else -> true` trata cualquier tipo restante como exitoso. Después guarda el checkpoint (`:142-145`). Las migraciones remotas sí escriben eventos `CARD/UPSERT` al registrar o modificar tarjetas y `CARD/DELETE` al borrar tarjetas no utilizadas (`20260921000000_ep_cta_baseline.sql:323-326,510-511`; la migración reciente de estilo también emite `CARD`).

**Efecto:** una segunda instalación puede adelantar su cursor sin recibir la tarjeta ni aplicar el tombstone. La creación, deuda/utilización, archive/delete o restauración de un owner deja de converger entre dispositivos aunque la transacción de compra haya sido aceptada.

**Corrección propuesta:** procesar `CARD` y tombstones con proyección owner-scoped, conservar orden de secuencia y solo persistir checkpoint después de aplicar atómicamente toda la página. Para tipo/operación sin handler, devolver fallo y dejar el cursor antes del evento. Probar alta/actualización/archive/delete de tarjeta y transacciones intercaladas en dos dispositivos. El snapshot actual de una tarjeta archivada también debe conservar el `UPSERT` de creación antes del evento `ARCHIVE`, para que un cliente limpio tenga primero la fila en Room.

### F-08 — P2: el test del ViewModel no cubre persistencia y sincronización del camino nuevo

`QuickMovementViewModelTest` verifica que una tarjeta llama a un fake y no al registro de cuenta. No cubre el modelo `Transaction`, Room, cuotas, recibo local, outbox, historial ni worker. El formulario también debe revisarse para estado `isSaving`, taps repetidos, cambio entre cuenta/tarjeta, moneda, selección de categoría y propagación de errores.

**Corrección propuesta:** mantener pruebas unitarias pequeñas, añadir integración Room/worker y prueba UI de Historial. Asegurar que las llamadas duplicadas mientras se guarda no creen dos operation IDs ni dos compras; preservar una identidad durante un reintento ambiguo. La advertencia de posible duplicado debe comparar por tarjeta y no asumir cuenta origen.

### F-09 — P1: el pull de transacciones remotas elimina la identidad de tarjeta

`SyncTransactionPayload` (`SyncChangePayloads.kt:17-31`) no incluye `card_id`, `operation_kind` ni `installment_count`. `applyPulledTransaction` (`SyncMovementsWorker.kt:186-208`) construye la fila Room con esos campos omitidos, por lo que una compra hecha en otro dispositivo se guarda como gasto sin vínculo a tarjeta y vuelve a chocar con el modelo actual al mapearla. Esto complementa F-07: incluso si se conserva el evento `TRANSACTION`, su contenido no permite reconstruir fielmente una compra de crédito.

**Corrección propuesta:** ampliar el contrato de pull y DTO con metadatos de compra, comprobar ownership de la tarjeta antes de persistir y aplicar transacción/cuotas/relaciones de manera atómica. Añadir prueba de worker con un payload `CARD_PURCHASE` que llegue desde otro dispositivo y verificar lectura del historial y conservación de todos los campos.

### F-10 — P1: el simulador de compra desde detalle puede omitir la categoría obligatoria

`AccountDetailScreen.kt:245-256` invoca `confirmCreditPurchase` pasando tarjeta, importe, comercio, fecha y cuotas, pero no una categoría. El contrato `register-transaction-v1.md:47` exige `category_id` para `EXPENSE`; `credit-commands.md:13` también incluye categoría en el comando de compra. El repositorio acepta `categoryId` anulable y el DTO lo serializa como `null`, de modo que esta entrada alternativa puede crear una compra sin clasificar o depender de una validación remota no alineada.

**Corrección propuesta:** dar a este flujo una selección/derivación explícita de categoría y bloquear confirmación cuando falte; alinear validador local y RPC. Añadir prueba de UI/ViewModel que verifique el ID de categoría hasta el comando remoto y un caso negativo que no persista compra ni cuotas.

### F-11 — P2: hashes de idempotencia local y servidor usan representaciones distintas

El cliente (`OfflineFirstFinancialInstrumentsRepository.kt:953-958`) calcula `requestHash` concatenando campos con `|`. La RPC (`20260925022130_s3_credit_canonical_purchase_payment.sql:517-519`) deriva otro hash de JSONB canónico (`contract_version` y `transaction`) e indica que el hash enviado por cliente no es identidad confiable. El contrato `register-transaction-v1.md:26` describe hash sobre JSON canónico excluyendo `request_hash`.

La RPC tiene autoridad y su recibo aún puede ser idempotente, por lo que la discrepancia no se presenta como causa del crash ni como prueba de duplicación remota. Sí deja dos huellas distintas en cliente/servidor y complica comparar conflictos o reconciliar receipts. **Corrección propuesta:** elegir y documentar quién calcula el hash canónico; alinear algoritmo con vectores dorados o dejar claro que el campo cliente es solo de transporte y nunca compararlo con la huella del recibo remoto. Probar igualdad byte por byte y mismo operation ID con payload idéntico/distinto.

## Revisión adicional de cambios y superficies

El fix del crash es bloqueante, pero el cierre de calidad debe revisar también las modificaciones pendientes en:

- **Lógica financiera y ledger:** dinero en `Long`/unidades menores, moneda compatible, gasto reconocido al comprar, pago como transferencia, efectivo y pasivo reducidos por el mismo pago, principal de cuotas que suma exactamente el importe, refunds/reversas y no duplicación en métricas.
- **Tarjetas e instrumentos:** propiedad, archivo/bloqueo Free, línea/utilización, red y moneda, catálogo de productos, presets de estilo no financieros y `style_preset_id` separado de campos contables.
- **Room y migraciones:** cadena desde esquemas soportados hasta v13, esquema exportado, claves compuestas por owner, FK, índices, datos previos y rollback ante fallo de transacción. La migración 12→13 agrega `style_preset_id`; comprobar upgrade real, no solo base vacía.
- **Navegación y ViewModels:** selector de fuente mutuamente excluyente, selección obsoleta, estado después de rotación/proceso recreado, back/dismiss, eventos de una sola entrega, errores recuperables y no exposición de IDs como nombres de instrumentos.
- **Persistencia y concurrencia:** commit Room atómico de transacción/cuotas/recibo/outbox, taps simultáneos, reintentos con identidad estable, operaciones fuera de orden, proceso muerto y sesión/owner cambiado.
- **Offline/backend:** programación tras commit, restauración al iniciar, backoff, lease vencido, RPC canónica, rechazo de estado, pull en otro dispositivo, RLS y permisos de función; probar base local limpia y staging antes de cualquier rollout. No tratar los informes de esquema remoto del 24/09 como una observación actual.
- **UI/UX y accesibilidad de los cambios recientes:** formularios unificados, acordeones y presets, tasas, estados de carga/error, contraste/tamaño táctil ≥48dp, TalkBack, strings UTF-8 y reducción de movimiento para tilt/transiciones.

## Plan de trabajo ejecutable

### Fase 0 — Captura reproducible y preservación de evidencia (P0)

1. Registrar commit/base, `git diff`, versión APK, variante, Android/API, modelo de dispositivo, owner de prueba, tarjeta/currency/limit, categoría, conexión y punto exacto de cierre.
2. Reproducir desde Historial: abrir `+`, seleccionar tarjeta de crédito activa, gasto válido y categoría, guardar. Repetir desde el otro punto de entrada disponible.
3. Capturar Logcat y timestamp justo antes de reproducir. Confirmar o descartar `IllegalArgumentException: Expense requires a source account`; guardar stacktrace completo y no solo el mensaje del toast.
4. Repetir con instalación limpia y con base existente actualizada. No borrar la única copia de datos del usuario para hacer la prueba.

**Puerta:** stacktrace mapeado a archivo/símbolo y escenario reproducible; si no coincide, abrir nueva rama de diagnóstico sin perder F-01 como defecto estructural.

### Fase 1 — Modelo, mapper y prueba de regresión del crash (P0)

1. Escribir primero la prueba fallida de dominio/mapeo para compra de tarjeta con `sourceAccountId = null` y `cardId != null`; preservar pruebas de gastos normales.
2. Extender validación y mapeo sin reutilizar IDs: cuenta para gasto normal, tarjeta para `CARD_PURCHASE`, rechazo de fuentes ausentes o dobles.
3. Actualizar item/presentación del historial para mostrar alias/red/últimos cuatro de la tarjeta con enmascaramiento; mantener importe, categoría y comercio.
4. Ejecutar integración con la observación activa antes de insertar, prueba de UI desde Historial y caso de compra recibida por pull desde un segundo dispositivo (F-09). Añadir compra de tarjeta en `QuickMovementScreenTest`.

**Puerta:** ningún flujo de lectura emite crash; la compra aparece una vez y no modifica saldo líquido.

### Fase 2 — Invariantes financieras y contrato contable (P1)

1. Resolver explícitamente el origen de verdad de deuda (ledger/cuotas) con EP-CTA/EP-MOV y actualizar contrato/modelo antes de tocar cálculos.
2. Probar unidad monetaria y límites, currency mismatch, cuenta/tarjeta ajena, categoría ausente/inactiva, cuotas 1..36, sumas con resto, fechas de corte/vencimiento, card limit y pago parcial FIFO; cubrir el simulador desde detalle (F-10).
3. Comparar proyección con fuente canónica para compras, pagos y reversas. Compra reconoce una sola vez el gasto y pasivo; pagar nunca vuelve a crear gasto; crédito disponible no suma al efectivo/patrimonio.
4. Contrastar comportamiento local y RPC; un rechazo remoto no puede dejar al usuario con una operación aparentemente sincronizada ni borrar el historial.

**Puerta:** invariantes constitucionales pasan en pruebas de dominio, Room y PostgreSQL; resultados exactos por moneda y operación quedan documentados.

### Fase 3 — Room, migraciones y atomicidad (P1)

1. Validar esquema exportado y migraciones soportadas hasta v13 con bases que contengan cuentas, tarjetas, transacciones, cuotas, pagos y outbox.
2. Probar upgrade desde v12 con y sin `style_preset_id`; revisar también rutas históricas realmente soportadas, no añadir `fallbackToDestructiveMigration`.
3. En una sola transacción Room, verificar rollback total ante fallo en cada etapa de compra: fila de transacción, cuotas, recibo y outbox. Confirmar claves compuestas `(user_id, ...)` y aislamiento entre dos owners.
4. Reabrir base y reconstruir observables; comparar fila leída con la escritura original, incluyendo `cardId` y `operationKind`.

**Puerta:** upgrade conserva datos, commit/rechazo es atómico y Room no tiene una proyección contable paralela sin reconciliación.

### Fase 4 — Outbox, worker y concurrencia (P1)

1. Separar identidad del agregado y transaction ID destino en el contrato y en almacenamiento.
2. Añadir lease/recovery de `IN_FLIGHT` y enforcement del predecesor causal.
3. Probar timeout después de aceptación remota pero antes del ACK, muerte del proceso, red que vuelve, backoff, 4xx permanente, conflicto, cambio de owner y segundo dispositivo.
4. Lanzar llamadas concurrentes/repetidas y verificar mismo operation ID en retry, payload hash estable, un recibo y ningún efecto duplicado.
5. Probar cadena de alta de tarjeta → compra → pago → archivo/reactivación y asegurar orden, estado final y visibilidad del error.
6. Para pull, insertar en una página cambios `ACCOUNT`, `CARD`, compra `TRANSACTION` con `card_id`/`operation_kind`/cuotas y `CARD/DELETE`; forzar un tipo desconocido en medio y verificar que no se salta ni persiste el checkpoint de los eventos no aplicados (F-07/F-09).
7. Comparar los hashes/idempotency vectors locales y SQL conforme a una única definición documentada (F-11).

**Puerta:** ninguna fila queda atascada, ningún sucesor se adelanta y cada retry converge al mismo resultado.

### Fase 5 — Navegación, ViewModels y UI (P2)

1. Probar combinaciones tipo movimiento × fuente: gasto/cuenta, gasto/tarjeta, ingreso/cuenta y transferencia/cuentas; bloquear tarjeta en ingreso/transferencia.
2. Probar cambios rápidos de selector, moneda PEN/USD, tarjeta archivada/bloqueada, categoría/comercio que se vuelven obsoletos, taps dobles, rotación, back y dismiss.
3. Verificar resultado y error visibles, eventos de éxito de una sola emisión, formulario no cerrado ante fallo, y estado `isSaving` resistente a reentrada.
4. Para los cambios de UI de instrumentos, comprobar que presets no alteran producto/red/tipo ni contabilidad, navegación de crear/editar y persistencia de estilo.
5. Revisar traducciones, contraste WCAG AA, TalkBack, targets de 48dp y reducción de movimiento.

**Puerta:** ninguna ruta Compose/nav/ViewModel produce crash, pérdida de formulario o afirmación de sincronización no respaldada.

### Fase 6 — Supabase y E2E de extremo a extremo (P1/P2)

1. Reconstruir migraciones en Supabase local; ejecutar pgTAP para RPC, idempotencia, cuotas, ledger según contrato acordado, RLS de dos usuarios, grants, ownership, schema y respuestas de error.
2. Probar el DTO Kotlin contra `register_transaction_v1` y validar status/receipt/transaction ID real. Consultar el esquema actual de staging; no desplegar al proyecto conectado durante auditoría.
3. Ejecutar recorrido instrumentado online/offline/reinicio/segundo dispositivo con base de prueba aislada.
4. Completar flujo de compra con cuotas y pago parcial, reconciliando historial, efectivo, deuda, cuotas, recibos y sync status al céntimo.

**Comandos documentados para la fase de pruebas** (no ejecutados en esta auditoría):

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
supabase start
supabase db reset
supabase test db
```

Usar además las suites aisladas ya definidas en `specs/002-ep-cta-cuentas-tarjetas/quickstart.md` y `specs/004-ep-mov-movimientos-ledger/quickstart.md`; ajustar la tarea Gradle al flavor de dispositivo aislado aprobado para no sobrescribir la app personal.

## Guion E2E de aceptación

1. Preparar owner A con cuenta líquida PEN S/500 y tarjeta de crédito PEN con línea S/1,000 y deuda cero; conservar snapshot de saldos.
2. En modo avión, abrir Historial → `+`, registrar compra S/120 con tarjeta y categoría. Esperado: un `EXPENSE/CARD_PURCHASE`, deuda S/120, crédito disponible S/880, saldo líquido aún S/500, cuotas cuyo principal totaliza S/120 y un comando/outbox pendiente.
3. Cerrar forzosamente y reiniciar sin red. Esperado: la compra y cuotas siguen visibles; no aparece duplicado; el comando vuelve a ser elegible al vencer lease.
4. Restaurar red. Esperado: una aceptación/recibo remoto; un solo gasto y efecto de deuda; estado `SYNCED`; la segunda instalación del owner A recibe la misma transacción/cuotas.
5. Reenviar el mismo operation ID/payload. Esperado: `DUPLICATE`/resultado original sin nuevos efectos. Reutilizar identidad con payload distinto: conflicto sin cambios.
6. Confirmar una compra de S/100 en tres cuotas: S/33.34, S/33.33, S/33.33. Pagar S/40 desde la cuenta: un `TRANSFER/CARD_PAYMENT`, efectivo S/460, deuda menor por S/40, asignación parcial FIFO al vencimiento más antiguo y cero gasto adicional.
7. Repetir con monto sobre la línea, moneda incorrecta, categoría no válida, tarjeta archivada/ajena, doble tap, pérdida de proceso antes/después de respuesta y dos dispositivos actuando concurrentemente.

## Criterios para cerrar la auditoría y pasar a implementación

- F-01 reproducido con Logcat y una regresión automatizada que falla antes y pasa después de la corrección.
- Cada operación tiene invariantes y una única fuente de verdad entre Room, cuotas, ledger, RPC y pull; no hay compras aceptadas localmente que desaparezcan silenciosamente al sincronizar.
- Migraciones no destructivas, recuperación de leases, cadena causal e idempotencia probadas ante muerte/reintento/concurrencia.
- Unit tests, Room/instrumented, UI, worker, pgTAP/RLS y recorrido E2E tienen evidencia reproducible y guardada; no basta con compilar.
- Las modificaciones visuales y navegación pasan accesibilidad y no mezclan línea de crédito con activos líquidos.
- El backend conectado sigue sin cambios hasta completar staging, revisión de seguridad y aprobación de despliegue.

## Revisión independiente de AGY

Se consultó en modo de solo lectura a Antigravity CLI en `term_00241382-86d8-4de4-8acf-6a2a86d078d7`, contrastando contratos, código y pruebas. AGY **corroboró independientemente F-01**: la fila persistida como `EXPENSE` con `cardId` y `sourceAccountId = null` llega al `toDomain()` de movimientos y falla por la invariante “Expense requires a source account”; señaló además que el registro queda en Room y puede volver a disparar el fallo en lecturas posteriores hasta que se corrija/procese esa fila. Esta secuencia coincide con el escenario y stacktrace esperado del plan; el Logcat del dispositivo sigue siendo evidencia necesaria para vincularla al incidente observado.

AGY también confirmó que los tests existentes de `QuickMovementViewModel` usan repositorios falsos, y propuso la regresión con Room real observando `observeTransactions` durante `confirmCreditPurchase`. Coincide con F-03 sobre el ID de agregado, pero su sugerencia de guardar solo `cardId` como `aggregateId` requiere separar explícitamente el ID de tarjeta del ID de transacción que el worker usa para actualizar `sync_status`; no se debe cambiar ese campo aisladamente. Sus hallazgos adicionales de metadatos ausentes al hacer pull (F-09), categoría no propagada desde el simulador de detalle (F-10) y diferencia entre hashes cliente/SQL (F-11) se verificaron contra el código y contratos citados arriba. La revisión externa refuerza la prioridad, pero no reemplaza ejecutar la regresión ni verificar el backend de staging.

## Actualización de auditoría tras el fix — tarjeta archivada en dispositivo nuevo

En una revisión independiente posterior, AGY contrastó la semántica SQL de pull con `SyncMovementsWorker`. Detectó que `pull_financial_changes_v1` reemplazaba cualquier operación de una tarjeta actualmente archivada por `ARCHIVE`, aunque la secuencia pedida comenzara por el evento original `UPSERT`. La rama Android `ARCHIVE` busca la tarjeta existente y devuelve fallo si no está; por eso un dispositivo con cursor inicial 0 podía repetir el pull sin avanzar nunca.

Se corrigió la proyección para conservar `sc.operation` cuando la tarjeta aún existe, sin perder el snapshot actual ni su revisión vigente. El alta original se entrega como `UPSERT` con `is_archived=true`, seguida del evento `ARCHIVE`; una tarjeta físicamente eliminada continúa entregándose como `DELETE`. Se documentó el contrato y se añadieron dos regresiones: pgTAP que exige `UPSERT,ARCHIVE` y el snapshot archivado, y Room/worker que consume ambos eventos desde una base vacía. La prueba Android compila; pgTAP todavía debe ejecutarse en PostgreSQL local/staging porque Docker, Supabase CLI y `psql` no están disponibles en este entorno.
