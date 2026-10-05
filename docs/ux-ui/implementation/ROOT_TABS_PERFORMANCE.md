# Fluidez de Dinero y Movimientos

Fecha: 2026-10-04. Dispositivo: Samsung SM-A165M, Android 16, ADB por USB.

## Estado

Implementado el mantenimiento de las dos pestañas raíz en composición. El objetivo de cero frames con jank **todavía no está acreditado**. Las métricas siguientes son de la APK **debug**, no una garantía de rendimiento en todas las condiciones.

## Cambios realizados

- `MainActivity.kt`: alternar Dinero/Movimientos dentro del dashboard cambia un estado guardable, sin ejecutar una transacción de `NavController`. El dock y el contenido usan esa misma selección. La selección se reinicia al cambiar de usuario.
- `RetainedRootTabs.kt`: mantiene ambas pantallas compuestas, medidas y colocadas en capas. Solo cambia `translationX` y `alpha` de esas capas. La pantalla inactiva queda fuera del contenedor recortado. No se recrea su lista al alternar.
- `AccountsNavigation.kt` y `MovementsNavigation.kt`: reutilizan los contenidos, ViewModels, callbacks y reglas existentes. El registro global se entrega solo a la pestaña activa. Las rutas secundarias y el historial con parámetros/deep links siguen en el NavHost.
- Atrás desde la pestaña raíz Movimientos selecciona Dinero. Los formularios, ajustes y editores conservan su navegación de retorno. La retención está ligada al destino autenticado; no introduce ViewModels globales a la aplicación.
- `KipuNavigationBar.kt`: conserva las variantes visuales de icono/etiqueta y sus medidas. El fondo animado se lee en `drawBehind`, evitando recomposición por cada frame de color. La píldora proporciona el feedback de selección sin superponer otro ripple. Se mantienen la semántica de pestaña, la selección y los targets de 48 dp.
- `MovementHistoryScreen.kt`: crea por adelantado el `QuickMovementViewModel` mientras la raíz se prepara y lo comparte con la hoja del `+`. Sus observaciones de cuentas, categorías y saldos ya pueden cargar mientras la persona usa las pestañas; abrir la hoja no necesita construir por primera vez ese ViewModel. Los datos se vuelven a observar con las mismas fuentes existentes.
- `scripts/measure-root-tabs.py`: benchmark repetible con las coordenadas solicitadas, calentamiento, `gfxinfo reset`, estadísticas crudas y capturas separadas. Rechaza un inicio sin dock y una corrida sin frames.

No se modificaron repositorios, base de datos ni reglas financieras para esta optimización. Conservar un árbol Compose no significa que Android garantice mantener permanentemente sus texturas en GPU.

## Revisión de cambios previos atribuidos a AGY

La revisión corresponde al diff local, sin afirmar autoría individual de cada línea:

| Cambio previo | Evaluación |
| --- | --- |
| Theme y tokens sin interpolación | Evita animaciones tonales masivas; no resuelve por sí solo el montaje de pestañas. Se conserva la decisión solicitada. |
| `SharingStarted.Lazily` | Mantiene el productor activo después de su primera suscripción y hasta cancelar el scope. No mantiene un árbol Compose montado. Tiene un coste de observación en segundo plano durante la vida del ViewModel. |
| Totales/agrupación trasladados al ViewModel | Evita recalcularlos durante recomposiciones de la UI. Moverlos al ViewModel no demuestra por sí mismo que se ejecuten fuera del hilo principal. No se cambiaron sus reglas contables. |
| Anchos estables de las pestañas | Reduce desplazamientos de los targets y permite usar coordenadas constantes. Se conserva. |
| Transiciones raíz desactivadas en NavHost | Reduce duración visual, pero seguía sustituyendo las composiciones. Sustituido por el contenedor retenido para la navegación raíz habitual. |
| `configChanges` del manifest | Afecta recreaciones por configuración; no soluciona el cambio de pestaña. Se dejó el cambio previo sin ampliarlo. |

La captura de Finplan sirve como referencia de experiencia. No se inspeccionó su código: su arquitectura interna y residencia en GPU no están verificadas.

## Mediciones

Evidencias crudas en `../implementation-evidence/tab-performance/`.

| Corrida | Cambios | Frames | Jank | P50 | P95 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Antes, NavHost | 20 | 160 | 44 (27,50%) | 150 ms | 200 ms |
| Primera retención, mismo contenido previo | 20 | 546 | 20 (3,66%) | 28 ms | 65 ms |
| Dock con animación en dibujo, tras nuevo login | 20 | 592 | 20 (3,38%) | 14 ms | 34 ms |
| Capas y variantes del dock retenidas, tras nuevo login | 40 | 1124 | 44 (3,91%) | 13 ms | 24 ms |

Los porcentajes usan denominadores diferentes porque cambió la cantidad de frames animados. No equivalen a una tasa por toque. La traza de sistema posterior mostró picos de `Choreographer#doFrame` de aproximadamente 23–27 ms durante cuatro alternancias, con coste en animación y grabación del dibujo.

**Limitación de comparabilidad:** la primera corrida y la primera retención tenían cinco movimientos. Después del incidente de QA descrito abajo y del nuevo login, el historial aparece vacío. Las corridas posteriores no son un A/B con el mismo dataset. No se crearon movimientos ficticios para compensarlo.

`after-layers-gfxinfo.txt` es una corrida **inválida**: tuvo cero frames porque estaba en login. Se excluye de cualquier resultado de rendimiento. `after-placement` se tomó antes de terminar una instalación y no representa esa revisión; también se excluye como comparación de versiones.

Las capturas usan `input tap; sleep 0.05; screencap`. Es una espera solicitada de 50 ms después del comando, no una medición exacta touch-to-photon: ADB y screencap añaden latencia. Las imágenes no sustituyen un frame timeline.

## Toque en el FAB global

La captura inmediata después de tocar el `+` muestra la hoja entrando; la captura posterior muestra el formulario disponible. La hoja conserva el movimiento nativo de Material 3 y su medida de entrada de 280–320 ms sigue pendiente de una marca de tiempo visual exacta. El ViewModel del formulario se inicializa junto con la raíz, de modo que solo el árbol visual de la hoja se prepara al abrirla.

Una corrida puntual después del toque devolvió 53 frames, 10 janky, P50 57 ms y P95 400 ms. Incluye la apertura de la hoja, los tiempos del canal ADB y el período de animación; no es una medida aislada de respuesta del click. El objetivo del benchmark repetido aquí es medir solo alternancias entre pestañas. Archivos: `plus-dashboard-50ms.png`, `plus-dashboard-370ms.png` y `plus-dashboard-gfxinfo.txt`.

## Validación automatizada

- `./gradlew compileDebugKotlin`: correcto.
- `./gradlew testDebugUnitTest`: 384 pruebas, cero fallos/errores.
- `./gradlew app:installDebug`: instalado en el Samsung.
- Dos pruebas instrumentadas de `RetainedRootTabsTest` en **com.kipu.app.lab**: ambos árboles se montan una vez tras 20 alternancias; solo la página activa está visible; cada lista mantiene un scroll diferente al volver.

Comando de las pruebas aisladas:

```powershell
./gradlew :app:connectedLabAndroidTest -PisolatedAndroidTests=true '-Pandroid.testInstrumentationRunnerArguments.class=com.kipu.app.navigation.RetainedRootTabsTest'
```

Benchmark con Kipu ya abierto en el dock:

```powershell
python scripts/measure-root-tabs.py --serial DEVICE_SERIAL --label verification --switches 20
```

## Incidente de QA y datos

La primera prueba instrumentada se ejecutó por error con `connectedDebugAndroidTest` sobre el paquete principal, en lugar de la variante aislada ya disponible. Después de esa ejecución y reinstalación, Kipu apareció en login y la carpeta local de bases de datos ya no existía. Se informó al usuario y se solicitó iniciar sesión directamente en el teléfono, sin compartir credenciales. El usuario volvió a iniciar sesión; en el dashboard se vieron cero cuentas, cero tarjetas y cero movimientos. No se pudo determinar si se trata de otra sesión/cuenta o de pérdida de datos locales. **No se afirma que la información previa se haya recuperado.**

Las pruebas posteriores usaron exclusivamente el paquete `com.kipu.app.lab`. No volver a ejecutar suites conectadas sobre la instalación con datos personales. Las instalaciones debug solicitadas se hicieron por actualización, no mediante un comando explícito de borrado de datos.

## Pendiente para cerrar cero jank

El árbol raíz ya se conserva, pero sigue habiendo trabajo costoso en el primer frame de algunos toques. Hace falta verificar con un dataset representativo y una build de rendimiento, usar FrameTimeline/Perfetto para atribuir los deadlines incumplidos y repetir corridas sin otras cargas. No sustituir esta comprobación por desactivar todas las animaciones ni por una corrida con cero frames.
