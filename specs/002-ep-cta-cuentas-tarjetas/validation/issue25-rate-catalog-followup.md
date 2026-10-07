# Evidencia actual: seguimiento #25 de identidad de productos

**Fecha**: 2026-10-06  
**Rama**: `fix/identidad-amex-y-trazabilidad`  
**Base**: `8e55946` (`main` actualizado; incluye PR #23 en `3240043`)

## Alcance verificado

- `OfficialCreditProductMappings` es compartido por la seleccion de productos y `RateCatalogContext`.
- American Express/Amex se resuelve solo mediante aliases explicitos; Sapphire e Iridium conservan preset y nombre canonico distintos.
- Una tarjeta existente sin `stylePresetId` se recupera solo desde alias oficial exacto mas emisor y red; una identidad incompleta queda sin referencia.
- El catalogo se consulta sin cambiar sus tasas publicadas. Ledger y calculos financieros no fueron modificados.

## Comando y resultado

```text
./gradlew.bat :app:testDebugUnitTest \
  --tests com.kipu.app.feature.accounts.presentation.components.CardStylePresetsTest \
  --tests com.kipu.app.feature.accounts.presentation.instruments.RateCatalogContextTest \
  --tests com.kipu.app.feature.accounts.domain.TeaCatalogTest \
  --console=plain
```

Resultado: `BUILD SUCCESSFUL` (2026-10-06): `CardStylePresetsTest` 5/5, `RateCatalogContextTest` 19/19 y `TeaCatalogTest` 4/4, sin fallos ni errores. Cubre seleccion y consulta BCP/Interbank Amex, Sapphire/Iridium, coincidencias ambiguas, producto sin TEA publicada, PEN/USD y aislamiento de TEA entre dos tarjetas.

Tambien se ejecuto `./gradlew.bat :app:assembleDebug --console=plain` con resultado `BUILD SUCCESSFUL`.

## Validacion final equivalente al CI

```text
./gradlew.bat testDebugUnitTest --console=plain
```

Resultado: `BUILD SUCCESSFUL` (2026-10-06). Los 87 reportes JUnit registraron 437 pruebas, 0 fallos, 0 errores y 0 omitidas. Este es el mismo task sin filtros que usa `.github/workflows/android-ci.yml` para las pruebas unitarias.

## Evidencia visual de la ejecucion inicial

No se genero evidencia visual en esta ejecucion: `adb` no esta disponible en el entorno. Para completarla, ejecutar la app en un emulador/dispositivo desde Android Studio y capturar la seleccion de Amex BCP/Interbank, su pantalla de tasas, un producto sin TEA publicada y dos TEA aisladas.

## Correccion y validacion posterior del PR #27 (2026-10-06)

La revision detecto que un preset existente podia resolver una referencia de otro
banco: tarjeta Interbank AMEX con preset BCP Oro. El resolver ahora exige que el
emisor almacenado de la tarjeta coincida tambien con el emisor del producto.
Las regresiones cubren el rechazo de esa combinacion y la aceptacion de
`Interbank`/`INTERBANK`. Los nombres de las pruebas unitarias de seleccion ya no
afirman probar persistencia. El encabezado de tareas queda reconciliado a 93/93.

### Comandos y resultados actuales

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class com.kipu.app.feature.accounts.RateCatalogPersistenceTest com.kipu.app.test/androidx.test.runner.AndroidJUnitRunner
```

- Build debug, APK de pruebas y suite unitaria: `BUILD SUCCESSFUL`, **439 pruebas, 0 fallos y 0 errores**.
- Galaxy A16 (SM-A165M), Android 16/API 36: **OK (3 tests)**, 7.903 segundos. [Salida completa](issue25/android-tests.txt).
- Despues de agregar aserciones de los textos USD/no publicado, se recompilo el APK de pruebas y se repitieron las tres pruebas en el mismo dispositivo; el resultado anterior corresponde a esa ultima ejecucion.

### Limites ejercitados

`RateCatalogPersistenceTest` utiliza el repositorio de produccion y una base Room
en archivo, unica por prueba. Obtiene los 44 productos mediante el mapeo de
catalogo de produccion, selecciona el preset con el mismo mapeo usado por el
formulario, llama a `registerCreditCard`, cierra la base, la reabre y lee la
tarjeta con `observeCardById` antes de consultar su referencia.

1. BCP American Express Oro LATAM Pass: identidad conservada y referencia USD 65.00%-76.90% intacta tras reabrir Room.
2. Interbank American Express Gold: identidad conservada; PEN/USD continúan sin TEA numerica publicada, sin inferencias.
3. TEA personal: BCP se actualiza de 53.25% a 55.00%; Interbank conserva 61.00%. Ambas sobreviven al cierre/reapertura. Cambiar la tarjeta en la pantalla restaura su propio valor; el catalogo permanece igual.

La respuesta de API es un fixture exacto de las 44 filas publicas del snapshot
versionado en `20260925022130_s3_credit_canonical_purchase_payment.sql`, con UUIDs
de prueba. Sesion y programador de sincronizacion son dobles. El registro,
persistencia, relectura y renderizado de `RateCatalogScreen` son reales. Esta
prueba verifica los limites locales; no acredita una consulta remota, una
sincronizacion del servidor ni la navegacion del formulario mediante toques.
La base de prueba se elimina al terminar y las capturas contienen datos sinteticos.

### Capturas actuales revisadas en dispositivo

| Caso | Evidencia |
| --- | --- |
| BCP Amex, referencia USD | [Captura](issue25/bcp-amex-usd.png) |
| Interbank Amex, tasa no publicada | [Captura](issue25/interbank-amex-unpublished.png) |
| TEA BCP 55.00% despues de reabrir | [Captura](issue25/bcp-amex-personal-tea.png) |
| Cambio a Interbank: TEA 61.00% aislada | [Captura](issue25/interbank-amex-personal-tea.png) |
