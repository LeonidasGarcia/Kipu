# Evidencia de implementación de mockups aprobados

## Aviso de límite del Plan Básico — 2026-10-04

Refinamiento solicitado sobre el mockup de Mi dinero: tarjeta con cupo usado de
cuentas y tarjetas, porcentaje, «Límite alcanzado», progreso y «Mejorar a PRO».
Comprobado con los datos existentes del Samsung (4 de 4), en Light, Dark y fuente
200 %. La acción abrió la pantalla real Kipu Premium; Google Play mostró ofertas
no disponibles durante el recorrido. No se realizó ninguna compra.

Las capturas de esa comprobaci?n se retiraron tras la revisi?n. Build y lint satisfactorios mediante
`:app:assembleDebug :app:lintDebug`. Escala de fuente restaurada a 0.9.

## Continuación del 2026-10-04

Validación en Samsung SM-A165M, Android nativo, sobre los cambios locales del brief
[de ejecución](../implementation/APPROVED_MOCKUPS_EXECUTION.md). Las capturas sin
prefijo `resumed_` corresponden al recorrido anterior recuperado; no se presentan
como nuevas verificaciones.

Correcciones de esta continuación:

- Dock: destinos de al menos 48 dp; la selección usa icono sobre texto con fuente
  ampliada, reservando espacio para los otros destinos y el FAB.
- Dashboard: el aviso de cupo distribuye texto y acción verticalmente con fuente
  grande; los distintivos bancarios usan icono para evitar siglas cortadas.
- Tarjetas: emisor/red y referencia enmascarada ocupan líneas distintas en la
  disposición ampliada.
- Movimientos: el filtro seleccionado usa `onPrimaryDeep` también en Dark.
- Registro rápido: título del monto y monedas pueden cambiar de fila; PEN/USD
  conservan 48 dp de altura y no estiran el formulario por falta de ancho.

## Recorridos revisados

Se revisaron navegaci?n, cambios de tema, scroll, formularios y estados con tama?o de fuente ampliado. Las capturas locales usadas para esas revisiones se retiraron despu?s; las condiciones y resultados se resumen abajo.

## Límites de aceptación

Verificación final: `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`,
BUILD SUCCESSFUL. 383 pruebas, 0 fallos, 0 errores y 0 omitidas. Lint: 0 errores y
260 advertencias; no se presenta como una base libre de warnings. APK instalada
con éxito en el Samsung. Se restauró la escala de fuente inicial (0.9) y el tema
oscuro que tenía la aplicación al retomar el trabajo.

Estos recorridos complementan las capturas anteriores de catálogo, configuración,
TEA, cuenta, billetera, efectivo, categorías y subcategorías. No sustituyen una
prueba de guardado financiero, una revisión humana con TalkBack ni una medición de
duraciones por fotogramas. Tampoco cierran los gates funcionales de las épicas
(por ejemplo EP-MOV T110). Los sheets conservan el movimiento nativo de Material 3.
