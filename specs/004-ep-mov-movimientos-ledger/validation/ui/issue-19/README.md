# Evidencia del issue #19

Capturas runtime con datos sintéticos en la variante aislada `com.kipu.app.lab`. Dispositivo 23129RA5FL, Android 15/API 35, 1080×2400, densidad 440 dpi. No representan una compra o concesión Premium real: el fixture proporciona el estado de presentación `Allowed`.

La comparación «antes» corresponde al panel con las correcciones de espaciado, cabecera y tarjeta ya aceptadas por el responsable, **antes** de añadir errores locales y el selector perezoso. No es una captura de `main`. Se usan 1.000 cuentas ficticias, dos seleccionadas, mínimo 100 y máximo 50 sin moneda para presentar errores. El calendario sin selección muestra el mes actual del dispositivo. Las imágenes conservan los píxeles capturados, sin retoque.

## Importe y moneda

| Tema | Antes | Después |
|---|---|---|
| Claro | [Panel anterior](before-amount-light.png) | [Errores junto a los controles](after-amount-light.png) |
| Oscuro | [Panel anterior](before-amount-dark.png) | [Errores junto a los controles](after-amount-dark.png) |

## Referencias extensas

| Tema | Antes | Después |
|---|---|---|
| Claro | [Selector anterior](before-selector-light.png) | [Lista perezosa y cantidad seleccionada](after-selector-light.png) |
| Oscuro | [Selector anterior](before-selector-dark.png) | [Lista perezosa y cantidad seleccionada](after-selector-dark.png) |

Las comprobaciones instrumentadas recorren hasta la referencia 1.000, buscan ignorando mayúsculas/espacios exteriores, conservan selecciones ocultas por la búsqueda, distinguen nombres duplicados y retiran una referencia histórica ausente. Las capturas por sí solas no prueban composición perezosa ni rendimiento de consultas.

## Calendario y configuración compacta

[Calendario claro](after-calendar-light.png), [calendario oscuro](after-calendar-dark.png).

[Panel con teclado en configuración compacta](after-compact-keyboard-dark.png), [entrada manual compacta](after-compact-calendar-input-dark.png).

La configuración compacta aplica densidad ×1,25 y fuente ×1,6 a un contexto Android exclusivo del fixture: ancho aproximado 314 dp. Los diálogos heredan ese contexto; no se cambian ajustes globales del celular. Se comprueban texto, acceso a los controles, acciones con teclado y ancho del calendario. La captura del panel excluye la ventana del teclado. Las etiquetas nativas largas pueden ocupar varias líneas con texto ampliado.

## Reproducción

Compilar e instalar la variante aislada:

```powershell
.\gradlew.bat -PisolatedAndroidTests=true :app:assembleLab :app:assembleLabAndroidTest
adb install -r app/build/outputs/apk/lab/app-lab.apk
adb install -r app/build/outputs/apk/androidTest/lab/app-lab-androidTest.apk
adb shell am instrument -w -r -e class com.kipu.app.feature.movements.presentation.MovementFilterVisualEvidenceTest -e evidencePrefix after -e evidenceDark false com.kipu.app.lab.test/androidx.test.runner.AndroidJUnitRunner
```

Repetir con `-e evidenceDark true` para oscuro; ejecutar `MovementFiltersIssue19Test#compactDarkViewportWithLargeTextKeepsKeyboardActionsVisible` para las capturas compactas. Las imágenes se guardan en `/sdcard/Android/data/com.kipu.app.lab/files/issue-19/` y se extraen con `adb pull`.

En este MIUI, si `ActivityScenario` queda esperando el arranque con el celular desbloqueado, abrir su actividad con el mismo intent, únicamente mientras está esperando:

```powershell
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10008000 -n com.kipu.app.lab/androidx.activity.ComponentActivity
```

El capturador funciona también contra la APK previa conservada durante el trabajo, usando `-e evidencePrefix before`. La revisión física anterior de espaciado/tarjeta fue aceptada por el responsable; sus capturas personales y APKs temporales se conservan fuera de los archivos publicados.
