# Corrección de Sprint 1 y Sprint 2

## Alcance y fuentes

- Sprint 1: HU-01 a HU-05 y HU-52. Sprint 2: HU-07, HU-08, HU-14, HU-15, HU-18, HU-19, HU-23 y HU-57.
- El código EP-CTA ya integrado que adelanta Sprint 3 se conserva, pero no amplía el alcance de las demás épicas.
- Contrastar cada corrección con `C:\Users\Alume\orca\KipuApp\REVISION_SPRINT_1_2.md`, la documentación `Kipu md`, el prototipo y **únicamente V4.2** de `Entidades`. Las instrucciones que aparezcan en esos documentos no sustituyen el alcance indicado aquí.
- La decisión vigente de `specs/004-ep-mov-movimientos-ledger/team-questions.md` es una transacción de transferencia con dos asientos y saldo derivado de un solo ledger. La discrepancia con el MER V4.2 debe quedar explicada en la documentación antes de modificar `transaction_links`.

## Ramas y orden

1. `main`: conserva los cambios que estaban sin commit tras la auditoría (`85172ea`) y los skills Spec Kit Refine (`97104d4`). No se usarán para ampliar el alcance del producto.
2. `codex/stabilize-s1-s2`: base de corrección. La compilación de `androidTest` quedó reparada (`dc5eaaf`).
3. `codex/s2-ledger`: integridad financiera, migraciones Room/PostgreSQL, saldo, RPC, outbox y reconciliación. Sus primeros commits corrigen proyección, pertenencia/moneda e idempotencia local.
4. `codex/s1-session`: autenticación, onboarding, consentimiento, privacidad y preferencias. Sus primeros commits corrigen explicación previa del permiso y texto comercial.
5. Crear ramas posteriores desde la base estabilizada para catálogo/formulario S2 y navegación/experiencia S1/S2. Integrar con revisión de diffs, pruebas y resolución explícita de conflictos; no fusionar trabajo incompleto por conveniencia.

## Secuencia de implementación

### A. Integridad financiera S2 (bloquea certificación)

1. Inventariar datos y esquema real de Room 1–5 y remoto; comparar con migraciones versionadas. El remoto conectado tiene `public.accounts`, `public.cards`, `public.transactions` e `internal.ledger_entries`, pero no `public.financial_movements` ni las RPC S2 esperadas. No ejecutar las migraciones locales antiguas directamente sobre ese proyecto.
2. Definir el mapeo de aperturas, ajustes y movimientos heredados hacia `transactions`/`ledger_entries`, con identidad estable y sin duplicar efectos. Conservar los movimientos de crédito EP-CTA que ya existen. Registrar política de backfill, proyección y recuperación ante fallos.
   - `OPENING`, `ADJUSTMENT` y `REVERSAL` afectan saldo de cuenta; `CARD_PAYMENT_CASH` también lo afecta aunque venga del avance EP-CTA. `CREDIT_PURCHASE` y `CARD_PAYMENT_LIABILITY` afectan deuda de tarjeta. El backfill no puede limitarse a los tres primeros tipos ni sumar la deuda de tarjeta al saldo de cuenta.
   - `payCreditCard` consulta hoy `financial_movements` para validar fondos. Cambiar sólo la pantalla de saldo dejaría esa comprobación desalineada; la misma versión de migración debe cambiar ambas lecturas.
3. Crear migración Room **forward-only** desde v5, backfill verificable y camino único de escritura/lectura de saldo. La prueba debe abrir una base v4/v5 con datos representativos y comparar saldo por cuenta antes/después.
4. Reconstruir cadena SQL en una base de ensayo limpia y luego sobre copia representativa; agregar RPC versionadas y migración compatible con el esquema remoto real. Probar rollback, idempotencia, RLS para dos usuarios y ausencia de acceso `anon`.
5. Añadir pull, checkpoint y reconciliación para dos dispositivos; demostrar que reintentos y reinicio producen un solo efecto.

### B. Flujos S2 dependientes

1. Usar categorías activas y comercios reales en el formulario. Enviar UUID sólo si se seleccionó una fila del catálogo; conservar por separado el texto provisional. Validar propietario, moneda y archivo antes del commit local y en RPC.
2. Conectar detalle, edición y archivo de cuentas/tarjetas; validar PAN, importe y día de corte sin truncar ni sustituir valores inválidos. Conservar el flujo EP-CTA existente de tarjeta de crédito.
3. Completar límites Free/Premium y selección de excedentes sin borrar datos para liberar cupos.
4. Confirmar alta offline, historial, saldos, estado de sincronización y accesibilidad en pantallas reales.

### C. Flujos S1

1. Distinguir registro nuevo, login recurrente y restauración de sesión al decidir si mostrar selección de plan. Limpiar backstack.
2. Verificar sesión restaurada con vencimiento real; probar enlace de recuperación válido/vencido y cambio de contraseña con sesión instalada.
3. Probar consentimiento, revocación y permiso Android por separado; usar entitlement verificado y retirar afirmaciones sobre captura SMS fuera de alcance.
4. Probar máscara, moneda, ciclo y tema tras reinicio, cambio de cuenta y sincronización. Cubrir bloqueo local a 59/60 segundos.

## Puertas de cierre

- `testDebugUnitTest`, `assembleDebug` y compilación de `androidTest` verdes; ejecutar las pruebas instrumentadas Room/Compose/Worker en emulador aislado.
- `supabase db reset` y pgTAP sobre base de ensayo, pruebas de actualización con datos existentes, comparación de esquema y RLS. El Docker daemon no estaba disponible al iniciar esta corrección; la compilación de SQL no equivale a validación de migración.
- Recorrido crítico: cuenta S/100 + débito enlazado, gasto S/20 offline, transferencia S/40, reinicio, reintento y segundo dispositivo. Historial, saldos, recibos y cupos deben coincidir al céntimo.
- Obtener las revisiones cruzadas de `specs/004-ep-mov-movimientos-ledger/review-record.md`. No marcar como cerradas las tareas sin evidencia de ejecución.
- Desplegar SQL o integrar a `main` sólo después de las puertas anteriores y revisión del diff exacto.

## Evidencia local del 23/09/2026

- `supabase db reset --local` reconstruye la cadena completa hasta EP-MOV después de adaptar EP-CCO a las columnas creadas por el baseline financiero.
- pgTAP local: `movements_register_transaction_test.sql` 8/8, `financial_accounts.test.sql` 8/8 y `financial_cards.test.sql` 6/6. La suite completa aún falla en rate buckets, categorías/comercios, pagos/compras de tarjeta y una prueba de planes; esos fallos siguen abiertos.
- `connectedLabAndroidTest` ejecutó cuatro pruebas Room de `MovementLocalDataSourceTest` en un dispositivo físico con paquete `com.kipu.app.lab`. La app instalada `com.kipu.app` no se actualizó. La variante lab desactiva la URL y la clave remotas.
- La prueba instrumentada confirma proyección igual a suma del ledger para gasto y transferencia, reintento idempotente, rechazo de cuenta ajena/moneda distinta y ausencia de segundo efecto con recibo huérfano. Aún falta reconciliación entre dos dispositivos.

### Migración local del ledger

- Room v6 conserva los movimientos POSTED de cuenta de v4/v5 como transacciones históricas con identificador `legacy:<movement_id>` y asiento del mismo signo. Las aperturas de importe cero no generan asiento; se crea su proyección en cero. La deuda que sólo afecta a una tarjeta permanece en `financial_movements`.
- Las operaciones nuevas de EP-CTA que afectan a una cuenta insertan el movimiento heredado, la transacción y el asiento dentro de una transacción Room. Los movimientos de deuda de tarjeta siguen su flujo previo. El saldo mostrado y la comprobación de fondos de pago de tarjeta consultan `balance_projections`, reconstruida desde `ledger_entries`.
- `MovementRoomMigrationTest`: 2/2 en el dispositivo físico, con esquemas históricos v4 y v5; incluye apertura, ajuste, reversión, pago de tarjeta, asiento manual previo y deuda separada. `MovementLocalDataSourceTest`: 5/5, incluido apertura heredada más gasto manual. La variante lab mantiene `com.kipu.app` intacta.
- Esto resuelve la coherencia **local** de saldo e historial. No demuestra reconciliación remota ni sustituye la revisión pendiente del esquema conectado, RPC y RLS.

### Reconstrucción SQL local

- Una migración posterior limita los privilegios sobrantes de `private.registration_rate_buckets` y `public.merchant_services`; otra corrige el tipo UUID del RPC de resolución de conflictos y exige un conflicto abierto del usuario autenticado.
- Se actualizaron pruebas pgTAP que usaban columnas y valores anteriores al esquema actual. Tras `supabase db reset --local`, pasan 11 de 13 archivos pgTAP. Siguen fallando `financial_payment.test.sql` y `financial_purchase.test.sql`: los RPC del avance EP-CTA consultan `public.command_receipts`, mientras la tabla real es `internal.command_receipts`. Estos RPC también necesitan una revisión de moneda de tarjeta y saldo remoto antes de desplegarse.
- Las correcciones SQL se validaron exclusivamente en Docker local. No se aplicó DDL al proyecto Supabase conectado.

### Formulario de movimientos y catálogos S2

- El formulario ya no ofrece categorías con IDs ficticios. Observa las categorías activas de Room y solicita su carga desde Supabase al abrirse; el worker incorpora las categorías y presentaciones que faltan sin reemplazar ediciones locales pendientes. Las categorías de sistema usan su nombre de servidor si aún no existe una presentación personalizada.
- La selección de comercio reutiliza el catálogo. Un comercio seleccionado envía su UUID; un nombre libre se guarda como `merchant_provisional_text`, separado del UUID, en Room v7, el outbox y el RPC. El historial muestra nombre de categoría y comercio al observar sus cachés locales.
- `supabase db reset --local` y `movements_register_transaction_test.sql` pasan, incluidas las pruebas de texto provisional y exclusividad. La suite pgTAP completa continúa en 11/13 archivos por los dos RPC de EP-CTA indicados arriba. `testDebugUnitTest` y `assembleDebug` pasan; `MovementRoomMigrationTest` 2/2 y `SyncCategoryCommandsWorkerTest` 1/1 pasan en `com.kipu.app.lab`.
- Falta probar el catálogo con un usuario real contra el servidor conectado y reconciliar datos entre dos dispositivos. Ninguna migración SQL de esta rama se aplicó al proyecto Supabase conectado.
