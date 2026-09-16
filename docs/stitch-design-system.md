# Sistema de Diseño de Kipu — Tokens Stitch (Kipu Andean Modernist)

Este documento registra el design system **`Kipu Andean Modernist`** como la **única fuente de verdad visual normativa** del proyecto y es el punto de referencia de tokens para UI nativa en Jetpack Compose y Material 3.

> **Trazabilidad**
> - **Fuente**: design system `Kipu Andean Modernist` — asset `assets/a21e2e45f51e490fa03b92fb8cb83c55` del proyecto Stitch `Kipu V4 Finale` (`projects/5775615138851387862`).
> - **Configuración de tema**: `colorMode: LIGHT`, fuentes `INTER` (headline/body/label), `roundness: ROUND_EIGHT`, color semilla `#0F766E`; `overridePrimaryColor #0F766E`, `overrideSecondaryColor #115E59`, `overrideTertiaryColor #0F172A`, `overrideNeutralColor #F8FAFC`, `colorVariant FIDELITY`, `spacingScale 2`.
> - **Pantalla de referencia EP-PLA**: Pantalla 1B «Selección de Plan» — `projects/5775615138851387862/screens/b8b4bfdcf384409887e54a975c549797`.
> - **Extracción**: MCP de Stitch AI, 2026-09-16 (updateTime `2026-09-16T04:50:40Z`).
> - **Autoridad declarada**: `spec.md` (**Refined** 2026-09-15, §Interfaz → Restricciones visuales), `plan.md` §1 y tareas T042/T056; investigación `research.md` R-002.
> - **Regla de uso**: no regenerar pantallas desde Stitch; usar este archivo como referencia de tokens. La pantalla Stitch es referencia estructural, no prueba de implementación.

---

## 1. Identidad y Concepto de Marca

- **Personalidad**: calmada, precisa, confiable y cercana.
- **Concepto**: modernismo andino que reinterpreta la *quipu* (registro incaico basado en cuerdas estructuradas y claridad numérica) mediante ritmos lineales precisos, ergonomía contable meticulosa y honestidad estructural modular. Sin caricatura folclórica.
- **Movimiento de diseño**: Corporate/Modern (Material 3) + Minimalist Andean Precision.
- **Principios**:
  - Claridad de alto orden: ledger, rendimientos y flujos exigen lectura semántica instantánea sin angustia.
  - Estabilidad serena: neutros pizarra cálidos y superficies teal profundas otorgan autoridad tranquila.
  - Revelación progresiva: la lógica presupuestal y los esquemas multibanco se muestran por incrementos naturales mediante sheets estructurados y contenedores tonales.

---

## 2. Paleta de Colores (Design Tokens)

El canon visual es el set de tokens del tema (`namedColors`/designTheme); la prosa del designMd se usa como guía. Contraste mínimo **4.5:1**.

### 2.1. Tokens Funcionales (Modo Claro)

#### Superficies y texto

| Token | Hex | Uso |
| :--- | :--- | :--- |
| `background` | `#F7F9FB` | Lienzo de la aplicación en modo claro. |
| `on_background` | `#191C1E` | Texto sobre el lienzo. |
| `surface` | `#F7F9FB` | Superficie base de nivel 0. |
| `on_surface` | `#191C1E` | Texto primario del cuerpo. |
| `surface_bright` | `#F7F9FB` | Superficie brillante (elevada). |
| `surface_dim` | `#D8DADC` | Superficie atenuada. |
| `surface_container_lowest` | `#FFFFFF` | Tarjetas y contenedores principales (Card). |
| `surface_container_low` | `#F2F4F6` | Contenedor bajo. |
| `surface_container` | `#ECEEF0` | Contenedor base. |
| `surface_container_high` | `#E6E8EA` | Contenedor alto. |
| `surface_container_highest` | `#E0E3E5` | Contenedor más alto. |
| `surface_variant` | `#E0E3E5` | Variante de superficie (campos, chips). |
| `on_surface_variant` | `#3E4947` | Texto secundario, títulos de sección, fechas. |

#### Líneas y bordes

| Token | Hex | Uso |
| :--- | :--- | :--- |
| `outline` | `#6E7977` | Bordes estructurados (hairlines). |
| `outline_variant` | `#BDC9C6` | Bordes sutiles de contenedores. |

#### Primario (CTA Kipu)

| Token | Hex | Uso |
| :--- | :--- | :--- |
| `primary` | `#005C55` | Estado *pressed* del CTA y acentos primarios de alta intensidad. |
| `on_primary` | `#FFFFFF` | Texto/icono sobre primary. |
| `primary_container` | `#0F766E` | **CTA principal** (base teal). |
| `on_primary_container` | `#A3FAEF` | Texto sobre primary container. |
| `primary_fixed` | `#9CF2E8` | Variante fija clara. |
| `primary_fixed_dim` | `#80D5CB` | Variante fija atenuada. |
| `on_primary_fixed` | `#00201D` | Texto sobre fija. |
| `on_primary_fixed_variant` | `#00504A` | Texto sobre fija variante. |
| `inverse_primary` | `#80D5CB` | Primario inverso (dark). |
| `surface_tint` | `#006A63` | **Anillo de selección/estado activo** (radio, focus). |

#### Secundario

| Token | Hex | Uso |
| :--- | :--- | :--- |
| `secondary` | `#216963` | Secundario / soporte. |
| `on_secondary` | `#FFFFFF` | Texto sobre secondary. |
| `secondary_container` | `#A8ECE5` | Badge/pill tonal (p. ej. «Permanente»). |
| `on_secondary_container` | `#266D68` | Texto sobre secondary container. |
| `secondary_fixed` | `#ABEFE8` | Variante fija clara. |
| `secondary_fixed_dim` | `#8FD3CC` | Variante fija atenuada. |
| `on_secondary_fixed` | `#00201E` | Texto sobre fija. |
| `on_secondary_fixed_variant` | `#00504B` | Texto sobre fija variante. |

#### Terciario

| Token | Hex | Uso |
| :--- | :--- | :--- |
| `tertiary` | `#495167` | Acento terciario. |
| `on_tertiary` | `#FFFFFF` | Texto sobre tertiary. |
| `tertiary_container` | `#616980` | Contenedor terciario. |
| `on_tertiary_container` | `#E4E9FF` | Texto sobre contenedor. |
| `tertiary_fixed` | `#DAE2FD` | Variante fija clara. |
| `tertiary_fixed_dim` | `#BEC6E0` | Variante fija atenuada. |
| `on_tertiary_fixed` | `#131B2E` | Texto sobre fija. |
| `on_tertiary_fixed_variant` | `#3F465C` | Texto sobre fija variante. |

#### Error

| Token | Hex | Uso |
| :--- | :--- | :--- |
| `error` | `#BA1A1A` | Errores y mensajes de fallo. |
| `on_error` | `#FFFFFF` | Texto sobre error. |
| `error_container` | `#FFDAD6` | Contenedor de error. |
| `on_error_container` | `#93000A` | Texto sobre error container. |

#### Inversos

| Token | Hex | Uso |
| :--- | :--- | :--- |
| `inverse_surface` | `#2D3133` | Superficie en dark/inversa. |
| `inverse_on_surface` | `#EFF1F3` | Texto sobre inversa. |

> **Nota de canon (discrepancia de prosa)**: el designMd describe el neutro de fondo como `#F8FAFC`, pero el set de tokens del tema (`namedColors`/designTheme) declara `background`/`surface` = **`#F7F9FB`**. Se adopta el set de tokens como autoritativo.

### 2.2. Colores Semánticos Financieros

| Token | Hex | Uso |
| :--- | :--- | :--- |
| `income` | `#16A34A` | Créditos, ingresos, abonos, avance de metas. Solo semántica financiera positiva. |
| `expense` | `#E85D5D` | Débitos, gastos, deuda, alertas destructivas. |
| `warning` | `#F59E0B` | Cortes de tarjeta, renovaciones, estados de cautela. |
| `surface-light` | `#FFFFFF` | Superficie pura (modo claro). |
| `dark-background` | `#0B1220` | Fondo general en modo oscuro. |

### 2.3. Presets de Instituciones Financieras (Banking Presets)

Los fondos de tarjetas y badges adoptan presets locales (BCP Gold, Interbank Green, BBVA Navy, Scotiabank Red) con reglas deterministas de contraste (mínimo 4.5:1 entre texto/lecturas y fondo).

| Institución | Token(s) | Hex |
| :--- | :--- | :--- |
| BCP | `bank-bcp-primary` / `bank-bcp-contrast` | `#FFC600` / `#002A8F` |
| Interbank | `bank-interbank` | `#009940` (contraste `#FFFFFF`) |
| BBVA | `bank-bbva` | `#004481` (contraste `#FFFFFF`) |
| Scotiabank | `bank-scotiabank` | `#ED1C24` (contraste `#FFFFFF`) |

Catálogo completo para `AccountCard` y `CreditCardSummary`:

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

### 2.4. Catálogo de Marcas de Servicios

Para suscripciones del hogar e IA, se despliegan logos específicos en el ledger:

- **Streaming**: Netflix, Spotify, HBO Max, YouTube Premium.
- **IA y Productividad**: ChatGPT Plus, Google One, Notion.
- **Fallback**: círculo plano con la inicial en mayúscula del comercio.

---

## 3. Tipografía

- **Fuente única**: **Inter** (headline, body y label). Se empaqueta en `res/font` (pesos estáticos **400 / 500 / 600**).
- **Regla tnum**: cualquier componente que muestre montos, saldos, porcentajes o ledgers debe activar **Números Tabulares** (`fontFeatureSettings = "tnum"`) para alineación vertical estable hasta el decimal.

### 3.1. Escala de Tipos

| Estilo | Tamaño | Peso | Line-height | Letter-spacing | Uso |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `display-lg` | 32px | 600 | 40px | −0.02em | Balances maestros, resúmenes de net-worth. |
| `display-lg-mobile` | 28px | 600 | 36px | −0.02em | Título de pantalla (Pantalla 1B). |
| `headline-md` | 24px | 600 | 32px | −0.01em | Cabeceras de sección. |
| `title-md` | 18px | 500 | 26px | 0em | Anclas de sección, títulos de tarjetas. |
| `title-sm` | 16px | 500 | 24px | +0.01em | Nombres de comercio/cuenta. |
| `body-lg` | 16px | 400 | 24px | 0em | Cuerpo amplio. |
| `body-md` | 14px | 400 | 20px | +0.01em | Metadatos, descripciones, notas. |
| `label-md` | 12px | 500 | 16px | +0.02em | Unidades, etiquetas. |
| `label-sm` | 11px | 600 | 16px | +0.03em | Tags de cuenta, badges, status pills. |

> En Compose se tipa el mismo valor en `sp` (1px ≈ 1sp a densidad de referencia) con `fontFamily = FontFamily(Font(R.font.inter_xxx, weight))`.

---

## 4. Grilla, Espaciado y Formas

### 4.1. Grilla y espaciado

- Base 8dp, medio-paso 4dp.
- Escala de espaciado:

| Token | Valor | Uso |
| :--- | :--- | :--- |
| `spacing-4` | 4dp | Micro-espaciado: iconos con texto, badges. |
| `spacing-8` | 8dp | Espaciado interno de listas. |
| `spacing-12` | 12dp | Gutter interno menor. |
| `spacing-16` | 16dp | Paddings estándar de pantalla y tarjetas grandes. |
| `spacing-24` | 24dp | Separación entre secciones funcionales. |
| `spacing-32` | 32dp | Márgenes superiores de títulos/portadas. |
| `spacing-48` | 48dp | Margen amplio / targets. |

- **Layout**: mobile (≤599dp) 4 columnas fluidas con margen de página 16dp y gutters 12–16dp, agrupación vertical con 24dp entre secciones. Tablet (600–1023dp) 8 columnas con márgenes 24dp y doble panel. Desktop (≥1024dp) 12 columnas con contenedor centrado máx. 1200dp y gutters 32dp.

### 4.2. Radios de curvatura (shapes)

| Token (`rounded`) | Valor | Aplicación |
| :--- | :--- | :--- |
| `sm` | 4dp | Módulos pequeños. |
| `DEFAULT` | 8dp | Base del tema (`ROUND_EIGHT`). |
| `md` | 12dp | **Controles, CTA, campos de entrada**. |
| `lg` | 16dp | **Tarjetas y contenedores (Card)**. |
| `xl` | 24dp | Hojas deslizables (Bottom Sheets) esquinas superiores. |
| `full` | pill (9999px) | Badges, chips y filtros. |

### 4.3. Targets de accesibilidad

Todos los elementos interactivos (botones, chips, toggles, filas de lista) reservan un área mínima de **48×48dp** (límite explícito o padding de expansión de toque balanceado).

---

## 5. Elevación y Jerarquía Tonal

La profundidad se comunica mediante **jerarquía de superficie tonal y hairlines estructurales de 1dp**, no con sombras gruesas.

- **Level 0 (Canvas)**: slate de fondo (`#F7F9FB` claro / `#0B1220` oscuro). Base plana.
- **Level 1 (Surface Containers / Cards)**: blanco puro `#FFFFFF` con límite de 1dp (`rgba(15, 23, 42, 0.08)`). Sin sombra proyectada.
- **Level 2 (Dropdowns, Menús elevados)**: borde 1dp + resplandor suave `0 4px 16px -2px rgba(15, 23, 42, 0.06)`.
- **Level 3 (Modales, Bottom Sheets)**: radio superior 24dp, sombra `0 12px 32px -4px rgba(15, 23, 42, 0.12)`, dim de fondo con tinta 40%.

---

## 6. Componentes (Referencia)

Subconjunto marcado como **[1B]** = utilizado por la Pantalla 1B; el resto queda como catálogo global para futuras pantallas.

### 6.1. Botones
- **Primario [1B]**: base teal `#0F766E` (`primary_container`), texto blanco, alto 48dp, radio 12dp, tipografía Inter `title-sm` (500). Estado activo/*pressed*: **`#005C55`** (`primary`).
  > **Nota de canon (discrepancia de prosa)**: el designMd prosa indica activo `#115E59`, pero el set de tokens del tema declara `primary` = **`#005C55`**. Se adopta el set de tokens como autoritativo.
- **Secundario/Tonal [1B]**: fill teal suave `rgba(15, 118, 110, 0.08)`, texto `#0F766E`, alto 48dp, sin outline.
- **Destructivo**: fill expense `#E85D5D` o wash tonal; reservado a desvincular cuentas / borrar transacciones.

### 6.2. Campos de entrada
- Alto 56dp, radio 12dp. Inactivo: borde 1dp `rgba(15, 23, 42, 0.12)`, fondo blanco, label en `body-md`. Foco: anillo primario 2dp `#0F766E`, label flotante en `label-sm`. Variante cantidad: `tnum` automático, `display` 24sp, prefijo `S/.` o `$` en tinta.

### 6.3. Tarjetas y listas de ledger [1B]
- **Account Cards**: radio 16dp, contorno de 1dp; pill de institución, números enmascarados (•••• 4821) y balance `tnum`.
- **Ledger Items**: filas de 56dp de alto (48dp de toque); tile de icono con radio 10dp; comercio en `title-sm`; categoría/fecha en `label-sm`; monto alineado a la derecha con `tnum` en Ingreso Verde (`+#16A34A`) o Tinta (`-#0F172A`).

### 6.4. Chips y selectores [1B]
- Alto 36dp, padding horizontal 6dp (límite de toque extendido a 48dp con padding transparente).
- Default: superficie blanca, borde 1dp. Seleccionado: fondo `#0F766E`, label blanco. En la Pantalla 1B el anillo de selección del estado activo usa `surface_tint #006A63`.

### 6.5. Checkboxes y radio buttons [1B]
- Caja visual 20×20dp dentro de un target interactivo 48×48dp.
- Marcado: sólido `#0F766E` con glifo blanco. No marcado: borde 1.5dp de `#0F172A` al 40% de opacidad.

### 6.6. Bottom Sheets
- Curvatura 24dp en esquinas superiores izquierda/derecha.
- Pill de arrastre 32×4dp centrado en tinta `#0F172A` al 20%.

---

## 7. Mapeo Token Stitch → Material 3 / Compose

Puente entre el designMd y la implementación (`Color.kt`, `Type.kt`, `Theme.kt`). Referencia de implementación de T042/T056.

| Token Stitch | Rol Material 3 | Uso en la Pantalla 1B |
| :--- | :--- | :--- |
| `background` `#F7F9FB` | `background` / `surface` | Lienzo de pantalla. |
| `on_background` / `on_surface` `#191C1E` | `onBackground` / `onSurface` | Título y texto primario. |
| `surface_container_lowest` `#FFFFFF` | `surfaceContainerLowest` | Tarjetas (Free / Premium) y contenedores. |
| `on_surface_variant` `#3E4947` | `onSurfaceVariant` | Subtítulos, fechas, texto secundario. |
| `primary_container` `#0F766E` | `primaryContainer` | **CTA «Confirmar Plan»** (base). |
| `primary` `#005C55` | `primary` | Estado *pressed* del CTA. |
| `surface_tint` `#006A63` | `primary` (tint)/`outline` reforzado | Anillo de selección / ring del chip activo. |
| `outline` `#6E7977` · `outline_variant` `#BDC9C6` | `outline` / `outlineVariant` | Bordes de 1dp de tarjetas y chips. |
| `secondary_container` `#A8ECE5` · `on_secondary_container` `#266D68` | `secondaryContainer` / `onSecondaryContainer` | Pill/badge «Permanente». |
| `secondary` `#216963` | `secondary` | Uso de soporte. |
| `error` `#BA1A1A` | `error` | Mensajes de error. |
| `income` `#16A34A` / `expense` `#E85D5D` / `warning` `#F59E0B` | (semánticos) | Estado financiero. |
| `dark-background` `#0B1220` | `background` (dark) | Modo oscuro. |
| `display-lg-mobile` 28/600 | Display Medium (28-30sp) | Título de pantalla. |
| `headline-md` 24/600 | Headline Medium | Título de sección. |
| `title-md` 18/500 | Title Medium | Títulos de tarjeta. |
| `title-sm` 16/500 | Title Large | Nombres/opciones. |
| `body-md` 14/400 | Body Medium | Notas y pies. |
| `label-md` 12/500 · `label-sm` 11/600 | Label Large / Label Small | Unidades, badges, límites. |
| `rounded.md` 12dp | `Shapes.Medium` | CTA, chips y controles. |
| `rounded.lg` 16dp | `Shapes.Large` | Tarjetas. |
| Inter 400/500/600 | `FontFamily(Font(R.font.inter_*, weight))` | Tipografía global. |

---

## 8. Evidencia y Trazabilidad

- **IDs estables para re-extracción**: proyecto `projects/5775615138851387862`; design system `assets/a21e2e45f51e490fa03b92fb8cb83c55`; pantalla `b8b4bfdcf384409887e54a975c549797`.
- **Método**: consulta mediante el MCP de Stitch AI (`get_project` / `get_screen`). Los valores de este documento se tomaron del `designTheme` y el `designMd` del proyecto.
- **Actualización**: si el design system cambia, re-extraer y revisar tokens (paleta, tipografía, radios). Los cambios funcionales requieren re-evaluación de la spec. No se regeneran pantallas.
- **Nota de scope EP-PLA**: la Pantalla 1B se implementa según este archivo + `spec.md` (copy aprobado e invariantes: 5 límites Free, CTA único, sin barras/promoción/filler).
