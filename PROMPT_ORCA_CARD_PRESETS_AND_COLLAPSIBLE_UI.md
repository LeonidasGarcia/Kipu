# DIRECTIVA DE ORQUESTACIÓN: CATÁLOGO DE PRESETS VISUALES DE TARJETAS PERUANAS Y MENÚ COLAPSABLE (ACORDEÓN)

> **Rol de Codex (Panel Izquierdo)**: Implementador técnico en Compose y Room. Modifica `InstrumentCardPreview.kt`, crea `CardStylePresets.kt`, refactoriza `UnifiedInstrumentFormScreen.kt` con menús colapsables por familia, ejecuta pruebas unitarias y genera el APK.
> **Rol de AGY (Panel Derecho)**: Oráculo de Dominio. Consulta `C:\Users\Alume\orca\KipuApp` y documenta el catálogo `card_style_presets` con las paletas, gradientes y familias de los bancos peruanos (BCP, Interbank, BBVA, Scotiabank, Falabella, Ripley, BanBif).
> **Rama de trabajo**: `010-ep-not-notificaciones-avisos` (mantenerse estrictamente en esta rama).
> **Bóveda de Documentación**: `C:\Users\Alume\orca\KipuApp`
> **Skills**: `transitions-dev`, `transitions-polish`, `ui-ux-pro-max`, `android-kotlin`.

---

## 1. OBJETIVO DEL PULIDO UI/UX

Transformar la visualización y selección de tarjetas en Kipu:
1. **Eliminar el fondo amarillo plano/monocromático** de las tarjetas y reemplazarlo por un **Catálogo de Presets Visuales nativos en Compose (`CardStylePreset`)**: gradientes multicapa, texturas sutiles, acentos de color realistas y niveles de tarjeta (Clásica, Oro, Platinum, Signature, Infinite, Black) inspirados en las tarjetas físicas reales de Perú (BCP, Interbank, BBVA, Scotiabank, etc.).
2. **Reemplazar la lista vertical plana e interminable** de productos de crédito por un **menú retraíble/colapsable (acordeón tipo categorías y subcategorías)** organizado por familias (ej. BCP -> Débito, Clásica & Light, LATAM Pass, Qore, Amex).
3. **Mini galería horizontal o selector de diseño rápido**: al tocar una tarjeta de la familia o del carrusel, la tarjeta superior con efecto 3D tilt se actualiza en tiempo real con su diseño de marca auténtico.
4. **Documentación formal**: AGY guardará el catálogo de presets en `C:\Users\Alume\orca\KipuApp`.

---

## 2. DETALLE DE TAREAS

### TAREA 1: Investigación y Documentación del Catálogo de Presets en `KipuApp` (AGY)
- AGY consulta `C:\Users\Alume\orca\KipuApp\Tasas de Bancos del Peru\KIPU_CATALOGO_TARJETAS_CREDITO_PERU_2026.md` y las referencias oficiales de los bancos.
- AGY define la especificación técnica de `CardStylePreset` con:
  - `id`: identificador persistible en la columna existente `cards.style_preset_id` (ej. `bcp_visa_classic`, `bcp_qore_sapphire`, `ibk_amex_black`, `bbva_bfree`, `scotia_singular_black`).
  - `institutionCode`: BCP, INTERBANK, BBVA, SCOTIABANK, BANBIF, FALABELLA, RIPLEY.
  - `cardType`: DEBIT, CREDIT.
  - `network`: VISA, MASTERCARD, AMEX, DINERS.
  - `tier`: CLASSIC, GOLD, PLATINUM, SIGNATURE, INFINITE, BLACK.
  - `gradient`: Definición de degradado (ej. Azul Profundo BCP `#002A8F` a `#001244`, Verde Esmeralda/Azul Interbank `#009940` a `#003B70`, Azul Marino BBVA `#004481` a `#041A40`, Negro Grafito mate `#1C1C1E` con ribetes dorados para Black/Infinite).
  - `accentColor` y `textColor`.

### TAREA 2: Creación de `CardStylePresets.kt` en Compose (Codex)
- Crear `app/src/main/java/com/kipu/app/feature/accounts/presentation/components/CardStylePresets.kt`.
- Implementar los gradientes vectoriales `Brush.linearGradient` o `Brush.radialGradient` nativos para cada preset.
- Asegurar que no se dependa de imágenes rasterizadas pesadas (PNGs): todo se renderiza con Canvas, gradientes y tipografía Andean Modernist nativa en Compose.

### TAREA 3: Actualización de `InstrumentCardPreview.kt` con Diseños de Marca y 3D Tilt
- Conectar `InstrumentCardPreview` para recibir `CardStylePreset` o aplicar el gradiente y estética de marca según el preset seleccionado:
  - Mostrar la tipografía estilizada del banco y nivel de tarjeta.
  - Conservar el efecto **3D Tilt interactivo con pointerInput, física de resorte y brillo especular (glare)** ya implementado.
  - Si `rememberReducedMotionEnabled()` es `true`, renderizar la tarjeta plana con su degradado sin animaciones de inclinación.

### TAREA 4: Menú Retraíble / Acordeón por Familias en `UnifiedInstrumentFormScreen.kt`
- En lugar de mostrar 18 tarjetas una debajo de otra en una lista infinita:
  1. Agrupar los productos del banco seleccionado por **Familias**:
     - **BCP**:
       - *Débito* (Débito BCP Clásica)
       - *Línea Clásica & Light* (Visa Clásica, Visa Light, Visa iO)
       - *Línea LATAM Pass* (Oro, Platinum, Signature, Infinite Sapphire, Infinite Iridium)
       - *Línea Qore* (Clásica, Oro, Platinum, Signature, Infinite)
       - *Línea American Express* (Clásica, Oro, Platinum, Black)
     - **Interbank**:
       - *Débito* (Débito Interbank)
       - *Tarjetas Visa* (Clásica, Oro, Platinum, Signature, Infinite, Premia)
       - *Tarjetas Benefit & Cashback*
       - *American Express* (Green, Gold, Platinum, Black, The Platinum Card)
     - **BBVA**:
       - *Débito* (Débito BBVA)
       - *Tarjetas Bfree & Cero* (Bfree Visa/Mastercard, Cero Visa)
       - *Línea Premium* (Platinum, Signature, Infinite, Mastercard Black)
     - **Scotiabank**:
       - *Débito* (Débito Scotiabank)
       - *Línea Singular* (Signature, Infinite, Black)
       - *AAdvantage & Puntos*
  2. Cada familia es un ítem de acordeón colapsable con encabezado estilizado (`Card` o `Row` con flecha `expand_more` / `expand_less`).
  3. Al tocar una familia, se expande con animación suave (`expandVertically` + `fadeIn` usando `transitions-dev`).
  4. Al seleccionar una tarjeta específica dentro de la familia:
     - Se resalta visualmente la opción seleccionada.
     - Se actualiza inmediatamente la **Card Preview superior** con su `CardStylePreset` auténtico (gradiente, red y nivel).
     - Se autocompletan los campos de Red Emisora y TEA referencial conocida.

### TAREA 5: Verificación Técnica y Calidad
- Ejecutar `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug`.
- Asegurar que todas las pruebas unitarias pasen sin regresiones.
- Validar accesibilidad: todos los encabezados de familia y tarjetas seleccionables deben tener touch targets $\ge 48\text{dp}$ y `contentDescription` descriptivo.
