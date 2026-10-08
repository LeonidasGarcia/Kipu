# Quickstart de validación — EP-DEU

Esta guía reúne la validación ejecutada para HU-26–HU-29 y conserva los escenarios manuales para QA posterior. Las pruebas conectadas usan bases Room en memoria y datos sintéticos; no se crearon movimientos en la cuenta iniciada del teléfono.

## Prerrequisitos

1. Usar el worktree de la rama `013-ep-deu-deudas-prestamos`.
2. Ejecutar las migraciones solo sobre Supabase local/de desarrollo; no probar DDL contra producción.
3. Usar un usuario de prueba aislado con cuenta propia activa, moneda PEN, cupo Free, notificaciones disponibles y otro usuario para verificar aislamiento.
4. Preparar una cuenta con saldo inicial conocido y sin datos financieros reales.

## Evidencia automatizada (2026-10-08)

Desde la raíz del worktree, en PowerShell:

```powershell
.\gradlew testDebugUnitTest
.\gradlew connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.package=com.kipu.app.feature.debts'
.\gradlew connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.core.database.DebtRoomMigrationTest'
npx supabase test db --local supabase/tests/database/debt_sprint5_migration_test.sql supabase/tests/database/debt_sprint5_security_and_idempotency_test.sql supabase/tests/database/debt_payable_opening_test.sql supabase/tests/database/debt_receivable_opening_test.sql supabase/tests/database/debt_details_history_guard_test.sql supabase/tests/database/debt_settlement_test.sql supabase/tests/database/debt_schedule_and_closure_test.sql
.\supabase\tests\database\debt_sprint5_quota_concurrency.ps1
npx supabase migration list --local
```

Resultados:

- `testDebugUnitTest`: 480 pruebas, 0 fallos.
- Pruebas instrumentadas del paquete de deudas en Samsung SM-A165M / Android 16: 30 pruebas, 0 fallos. La migración Room v18→v19 pasó además su prueba aislada: 1/1.
- pgTAP local: 7 archivos, 130 aserciones, todas aprobadas.
- Concurrencia del cupo Free: dos aperturas simultáneas dieron una operación aplicada y una rechazada; quedaron dos obligaciones activas. El script elimina al terminar su usuario y sus filas sintéticas.
- `migration list --local` muestra aplicadas las siete migraciones DEU `20261008120000`–`20261008170000`. La migración Supabase se validó en el stack local; no se aplicó ni modificó el proyecto remoto.

El arnés `debt_sprint5_migration_upgrade.ps1` no se ejecutó: empieza con `supabase db reset --local --version 20261007144500`, que recrearía la base local compartida. Además, el reset general de este repositorio tiene un fallo previo en `20260928110000_credit_card_pull_projection.sql` por la ausencia de `public.recurrence_occurrences`. La prueba pgTAP de preservación y la aplicación local sí pasaron; la ruta aislada de upgrade desde el esquema legacy queda como comprobación previa a cualquier despliegue.

## Escenarios de aceptación manual

### HU-26 — deuda propia

1. Crear PAYABLE nueva de S/500 con desembolso en cuenta de S/1,000.
2. Esperado: caja S/1,500; pasivo S/500; ingresos operativos sin cambio.
3. Crear otra PAYABLE histórica de S/200; esperado: caja sin cambio y pasivo total S/700.
4. Editar contraparte y nota; esperado: capital y pagos sin cambio.
5. Reintentar el mismo comando; esperado: no aparece un segundo desembolso.

### HU-27 — dinero prestado

1. Crear RECEIVABLE nueva de S/100 desde una cuenta de S/300.
2. Esperado: caja S/200; cuenta por cobrar S/100; gastos operativos sin cambio.
3. Crear un préstamo histórico de S/50 ya reflejado en apertura; esperado: la caja continúa en S/200.
4. Intentar crear préstamo sin cuenta origen válida; esperado: rechazo sin objeto ni asiento.

### HU-28 — principal e interés

1. Con PAYABLE de S/200, pagar S/50 de principal y S/5 de interés.
2. Esperado: caja baja S/55; principal pendiente S/150; gasto operativo S/5.
3. Con RECEIVABLE de S/200, cobrar S/50 de principal y S/5 de interés.
4. Esperado: caja sube S/55; por cobrar baja a S/150; ingreso operativo S/5.
5. Intentar principal de S/151 con pendiente S/150; esperado: rechazo sin asiento parcial.
6. Repetir la misma operación; esperado: el mismo recibo y ningún efecto duplicado.

### HU-29 — cuotas y cierre

1. Crear cronograma S/100 en tres cuotas; esperado: suma exacta y sin cambio de caja/pasivo.
2. Programar aviso de una cuota; esperado: una notificación de deuda al anticipo elegido, sin crear movimiento.
3. Cambiar fecha y volver a programar; esperado: se reemplaza el aviso previo, no se duplica.
4. Cerrar con pendiente S/0; esperado: SETTLED y liberación de cupo activo.
5. Cerrar con S/20 pendientes sin ajuste/condonación; esperado: rechazo.
6. Anular un pago que había dejado la deuda en cero; esperado: saldo recalculado y obligación reabierta.

## Verificación de límites

- Intentar leer/editar una deuda con el segundo usuario: denegado.
- Enviar cuenta/cuota ajena, moneda distinta, revisión obsoleta o mismo operation_id con hash distinto: rechazo/conflicto sin efecto parcial.
- Deshabilitar notificaciones: la obligación permanece utilizable y no se interpreta como pagada.
- Consultar movimientos, presupuesto y flujo neto: principal no entra en ingreso/gasto; interés aparece una sola vez.

La matriz de `tasks.md` enlaza cada historia con sus pruebas de dominio, Room, RPC, repositorio y Compose. Los escenarios manuales anteriores siguen disponibles para QA con un usuario aislado; no se ejecutaron en la cuenta iniciada del dispositivo.
