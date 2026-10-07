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

## Evidencia visual

No se genero evidencia visual en esta ejecucion: `adb` no esta disponible en el entorno. Para completarla, ejecutar la app en un emulador/dispositivo desde Android Studio y capturar la seleccion de Amex BCP/Interbank, su pantalla de tasas, un producto sin TEA publicada y dos TEA aisladas.
