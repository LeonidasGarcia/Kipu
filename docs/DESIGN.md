# Sistema de Diseño de Kipu

Este documento especifica los tokens de diseño, paleta de colores, tipografía y componentes clave del sistema de diseño de **Kipu** (Versión 2.0). Está optimizado para ser procesado por herramientas de diseño asistido por IA (como Stitch AI) y para servir de guía en la implementación de la UI nativa con Jetpack Compose y Material 3.

---

## 1. Identidad Visual y Concepto de Marca

- **Concepto Central**: Moderno, confiable, sereno y cercano.
- **Inspiración**: Incorpora abstracciones sutiles del "quipu" (nudos andinos tradicionales) en forma de detalles lineales, separadores de sección o microilustraciones minimalistas, evitando sobrecargar la interfaz temática histórica.
- **Enfoque de UI**: Limpieza visual, priorización de legibilidad de datos numéricos y revelación progresiva de información para reducir la carga cognitiva.

---

## 2. Paleta de Colores (Design Tokens)

La paleta está estructurada para soportar de manera nativa los modos claro (Light) y oscuro (Dark) de Material 3, garantizando un contraste accesible (mínimo 4.5:1).

### 2.1. Colores Semánticos y de Marca

| Token de Color | Valor Hex | Uso Principal |
| :--- | :--- | :--- |
| **Primary** | `#0F766E` | Acciones principales, selección activa de menús y branding general de Kipu. |
| **Primary Dark** | `#115E59` | Títulos principales, textos en botones activos y estados interactivos de realce. |
| **Ink** | `#0F172A` | Texto primario del cuerpo, etiquetas secundarias de navegación y bordes oscuros. |
| **Income** | `#16A34A` | Indicadores de saldo positivo, ingresos confirmados, abonos y avance de metas. |
| **Expense** | `#E85D5D` | Salidas de dinero, gastos en el ledger, tarjetas de deuda y botones de peligro. |
| **Warning** | `#F59E0B` | Recordatorios de alertas de pago inminente, días de corte de tarjetas y advertencias. |
| **Background (Light)** | `#F8FAFC` | Fondo general de la aplicación en modo claro. |
| **Surface (Light)** | `#FFFFFF` | Fondo de contenedores principales (Cards, Bottom Sheets, Diálogos, Inputs). |
| **Dark Background** | `#0B1220` | Fondo general de la aplicación en modo oscuro. |

---

## 3. Tipografía y Escala de Texto

- **Fuente Principal**: *Inter* o tipografía predeterminada del sistema Android.
- **Regla Crítica de Números**: Se debe habilitar obligatoriamente la propiedad de **Números Tabulares** (`Tabular Numbers` / `fontFeatureSettings = "tnum"`) en cualquier componente que represente montos financieros, asegurando que las columnas numéricas se alineen perfectamente sin variaciones de ancho.
- **Escala de Tipos**:
  - *Display Large*: 32sp, SemiBold (Ej: Balances de saldos en Dashboard).
  - *Title Medium*: 18sp, Medium (Ej: Cabeceras de tarjetas o secciones).
  - *Body Medium*: 14sp, Regular (Ej: Detalles de transacciones, notas).
  - *Label Small*: 11sp, Bold/Medium (Ej: Indicador de cuenta, categoría o etiquetas).

---

## 4. Grilla, Espaciado y Formas

El sistema de diseño sigue estrictamente los principios de espaciado adaptativo de Material 3.

- **Unidad de Medida**: Base de `8dp`.
- **Escala de Espaciado (Paddings/Margins)**:
  - `4dp` (Micro espaciado: etiquetas internas, iconos con texto).
  - `8dp` (Espaciado interno de elementos de lista, márgenes estrechos).
  - `12dp` (Espaciado de tarjetas pequeñas).
  - `16dp` (Paddings estándar de pantallas y tarjetas grandes).
  - `24dp` (Separación de bloques y secciones principales).
  - `32dp` (Márgenes superiores de títulos y portadas).

### 4.1. Radios de Curvatura (Bordes)
- **Tarjetas / Contenedores (`Cards`)**: `16dp` de redondeo.
- **Hojas Deslizables (`Bottom Sheets`)**: `20dp` a `28dp` en las esquinas superiores.
- **Campos de Entrada de Datos (`Inputs`)**: `12dp` de redondeo uniforme.

---

## 5. Elevación y Estilo de Contenedores

Para evitar la carga visual de sombras pesadas en dispositivos de gama baja y media, Kipu prioriza **superficies tonales** y bordes finos.
- **Bordes estándar**: Contenedores sobre fondo claro usan un borde sutil de `1dp` con un color tonal claro en lugar de elevación física.
- **Elevación de Diálogos / Sheets**: Máximo `Elevation = 3` (sombra ligera difuminada).

---

## 6. Catálogos Visuales Preestablecidos

Kipu utiliza estructuras estables para el reconocimiento visual inmediato de los elementos financieros peruanos.

### 6.1. Catálogo de Instituciones Financieras (Financial Presets)
Cada banco está definido por sus atributos de marca autorizados para renderizar de manera dinámica las tarjetas de los usuarios (`AccountCard` y `CreditCardSummary`):

```json
{
  "financial_institutions": [
    {
      "id": "bcp",
      "display_name": "BCP",
      "aliases": ["banco de credito", "bcp"],
      "primary_color": "#FFC600",
      "contrast_color": "#002A8G",
      "is_dark_preset": false
    },
    {
      "id": "interbank",
      "display_name": "Interbank",
      "aliases": ["interbank", "ibk"],
      "primary_color": "#009940",
      "contrast_color": "#FFFFFF",
      "is_dark_preset": true
    },
    {
      "id": "bbva",
      "display_name": "BBVA",
      "aliases": ["bbva", "continental"],
      "primary_color": "#004481",
      "contrast_color": "#FFFFFF",
      "is_dark_preset": true
    },
    {
      "id": "scotiabank",
      "display_name": "Scotiabank",
      "aliases": ["scotiabank", "patria"],
      "primary_color": "#ED1C24",
      "contrast_color": "#FFFFFF",
      "is_dark_preset": true
    },
    {
      "id": "generic",
      "display_name": "Personalizado",
      "aliases": [],
      "primary_color": "#0F766E",
      "contrast_color": "#FFFFFF",
      "is_dark_preset": true
    }
  ]
}
```

### 6.2. Catálogo de Marcas de Servicios
Para suscripciones del hogar e inteligencia artificial, Kipu despliega logos específicos en el ledger para mejorar la carga cognitiva sin rigidizar la taxonomía de la app:
- **Streaming**: Netflix, Spotify, HBO Max, YouTube Premium.
- **IA y Productividad**: ChatGPT Plus, Google One, Notion.
- **Fallback**: Si el comercio no está en el catálogo, se aplica un círculo plano con la inicial en mayúscula de la primera letra del comercio.

---

## 7. Directrices de Accesibilidad (A11y)

1. **Tamaño de Toque (Touch Targets)**: Todos los elementos interactivos, botones de acción rápida, selectores y chips horizontales deben tener un área mínima de toque de **`48dp x 48dp`** (cumpliendo RNF-05).
2. **Textos con Iconos**: Ningún color de icono sustituye al texto. El nombre de la categoría, comercio o cuenta siempre debe estar presente en texto claro y legible.
3. **Escalabilidad**: El diseño de todas las pantallas debe respetar el escalado de fuente del sistema Android sin cortar montos financieros ni encabalgar contenedores.
