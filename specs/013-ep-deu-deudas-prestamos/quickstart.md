# Quickstart de validación — EP-DEU

Esta guía define cómo validar HU-26–HU-29 durante implementación. Es futura; en esta fase no se ejecutaron builds, pruebas ni operaciones sobre la cuenta del teléfono.

## Prerrequisitos

1. Implementar las tareas de `tasks.md` y revisar los cambios de código.
2. Ejecutar las migraciones sobre una base local/de desarrollo. Antes de aplicar la migración DEU, reconciliar la deriva local/remota indicada en `research.md`; no probar DDL contra producción.
3. Usar un usuario de prueba aislado con cuenta propia activa, moneda PEN, cupo Free, notificaciones disponibles y otro usuario para verificar aislamiento.
4. Preparar una cuenta con saldo inicial conocido y sin datos financieros reales.

## Validación automatizada prevista

Desde la raíz del repositorio, en PowerShell:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
supabase test db
```

Las pruebas instrumentadas requieren emulador o dispositivo de prueba. Supabase local requiere CLI y la base levantada. Los comandos no se han ejecutado en esta fase.

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
