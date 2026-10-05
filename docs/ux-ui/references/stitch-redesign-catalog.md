# Catálogo de Rediseño UI/UX — Kipu V4.2 (Stitch AI)

> **Proyecto Stitch:** `projects/8741755512585500557`  
> **Título del Proyecto:** *Kipu V4.2 Rediseño UI/UX*  
> **Design System Asset:** `assets/9ef07f90f66d4e0b991e015000a05ca4` (*Kipu Andean Modernist*)  
> **Alcance Funcional Congelado:** Hasta cierre de Sprint 3  
> **Plataforma Objetivo:** Android Nativo, Kotlin, Jetpack Compose, Material 3 (390 x 844 px)  

---

## Índice de Pantallas Generadas (R1 – R10)

| ID Pantalla | Nombre Funcional | Tipo / Rol | Identificador en Stitch |
| :---: | :--- | :--- | :--- |
| **R1** | [Historial de Movimientos](#r1--historial-de-movimientos) | Pantalla principal (Lista cronológica) | `79c7df74150542e0867c223138c607d0` |
| **R2** | [Registrar Transacción](#r2--registrar-transacción-modal-bottom-sheet) | Modal Bottom Sheet (~90% altura) | `97da5eea52b742d58716d95e335c0e11` |
| **R3** | [Selector de Cuenta](#r3--selector-de-cuenta-modal-bottom-sheet) | Modal Bottom Sheet (~70% altura) | `5d09ee3b5e8f4c49b2ccacd4ff0825aa` |
| **R4** | [Selector de Categoría (Cerrado)](#r4--selector-de-categoría-estado-cerrado) | Modal Bottom Sheet (~72% altura) | `a31e6be489a04a42983c7f7ee40e74dd` |
| **R5** | [Selector de Categoría (Expandido)](#r5--selector-de-categoría-estado-expandido) | Modal Bottom Sheet (~80% altura) | `6e8ee3d8ed9644efbbac39c42fc04341` |
| **R6** | [Mi Dinero Real (Cuentas e Instrumentos)](#r6--mi-dinero-real-cuentas-e-instrumentos) | Pantalla principal de Saldos y Cuentas (Perfeccionada) | `bdfaf17638a74a00be738d1ce132d745` |
| **R7** | [Detalle de Cuenta "Sueldo"](#r7--detalle-de-cuenta-sueldo) | Vista de Detalle y Configuración | `fa5bdc233da8494597f60325ea78029b` |
| **R8** | [Nuevo Instrumento: Ahorros / Débito](#r8--nuevo-instrumento-ahorros--débito-kibo-ui-card) | Formulario de Alta con Kibo UI Card | `8a78a330c3a64e378fffcef572c1af02` |
| **R9** | [Nuevo Instrumento: Tarjeta de Crédito](#r9--nuevo-instrumento-tarjeta-de-crédito-kibo-ui-card) | Formulario de Alta con Kibo UI Card | `d68dd3078d4b43d28b3e4b3501070e4f` |
| **R10** | [Detalle de Tarjeta de Crédito](#r10--detalle-de-tarjeta-de-crédito-kibo-ui-flip--movimientos) | Vista de Detalle, Flip Kibo y Movimientos | `d089d45d2b734d0991d1643ab8a2bd0f` |

---

## Galería Completa y Decisiones de Diseño

### R1 — Historial de Movimientos
- **Identificador Stitch:** `79c7df74150542e0867c223138c607d0`
- **Propósito:** Reemplazar las cards pesadas individuales por una lista escaneable agrupada por fecha (*HOY*, *AYER*) con separadores finos de 1px.
- **Tokens Semánticos:** Gastos en coral semántico (`- S/ 142.50`), Ingresos en verde (`+ S/ 3,850.00`), Transferencias y Pagos de tarjeta en tono neutral/teal (`S/ 850.00`).
- **Navegación:** Kipu Bottom Bar M3 con *Movimientos* activo y FAB central `+`.


---

### R2 — Registrar Transacción (Modal Bottom Sheet)
- **Identificador Stitch:** `97da5eea52b742d58716d95e335c0e11`
- **Propósito:** Sheet modal al 90% con esquinas superiores de 28dp sobre scrim translúcido.
- **Componentes:**
  - Control segmentado de 3 pestañas: `Gasto` (activo en `#E85D5D`), `Ingreso`, `Transferencia`.
  - Hero del Monto en 34sp bold con cifras tabulares (`S/ 142.50`).
  - Selectores táctiles de 64dp para Cuenta, Categoría, Comercio y Fecha.
  - Acordeón colapsable para notas y comprobante.
  - Botón Sticky Primary `#0F766E` *"Guardar gasto"*.


---

### R3 — Selector de Cuenta (Modal Bottom Sheet)
- **Identificador Stitch:** `5d09ee3b5e8f4c49b2ccacd4ff0825aa`
- **Propósito:** Sustituye el dropdown de escritorio por un sheet modal táctil al 70%.
- **Componentes:**
  - Fila activa en Surface Variant (`#F1F5F9`) con borde turquesa, tag `PRINCIPAL`, icono BCP y checkmark circular.
  - Cuentas líquidas (*Cuenta Sueldo*, *Efectivo*, *BBVA*).
  - Tarjetas de crédito con mini CardVisual que separa estrictamente lo disponible de la deuda devengada.


---

### R4 — Selector de Categoría (Estado Cerrado)
- **Identificador Stitch:** `a31e6be489a04a42983c7f7ee40e74dd`
- **Propósito:** Selección ágil de categorías principales con filas táctiles de 64dp.
- **Componentes:**
  - Iconos tonales temáticos (Alimentación, Suscripciones, Transporte, Salud, Hogar, Educación).
  - Estado actual seleccionado con acento `#0F766E` y check.
  - Chevron derecho anticipando subcategorías.


---

### R5 — Selector de Categoría (Estado Expandido)
- **Identificador Stitch:** `6e8ee3d8ed9644efbbac39c42fc04341`
- **Propósito:** Despliegue jerárquico de subcategorías (*Suscripciones* expandido con *HBO*, *Crunchyroll*, *Spotify*, *Netflix*).
- **Componentes:**
  - Chevron rotado 180° hacia arriba (`expand_less`).
  - Sangría de 24dp con guía estructural.
  - Subcategoría *Crunchyroll* en estado activo con borde y checkmark turquesa Kipu.


---

### R6 — Mi Dinero Real (Cuentas e Instrumentos — Perfeccionada)
- **Identificador Stitch:** `bdfaf17638a74a00be738d1ce132d745`
- **Propósito:** Vista consolidada con jerarquía visual de alto impacto, separación estricta de crédito vs disponible y tarjeta física virtual completa y proporcionada (sin recortes).
- **Componentes:**
  - **Indicador de Cupo Plan Free:** Píldora discreta superior (*2 de 4 cuentas activas*) con microbarra de progreso.
  - **Hero Card Total Disponible:** Contenedor teal `#0F766E` refinado con `S/ 5,210.00` en 34sp tabular-nums, control de privacidad (ojo) y leyenda de activos líquidos.
  - **Cuentas y Efectivo Unificadas:** Contenedor único con separadores limpios para *Cuenta Sueldo BCP* (`S/ 3,850.00`), *BBVA Ahorros Libre* (`S/ 1,120.00`) y *Efectivo* (`S/ 240.00`).
  - **Tarjeta Kibo UI Íntegra y Espaciosa:** Proporción áurea completa (1.586:1, 340x205dp), chip EMV dorado, ondas NFC, logo BCP, `•••• 7548`, corte/vencimiento y VISA GOLD.
  - **Panel de Métricas de Crédito:** 3 columnas (*Deuda actual*, *Línea libre*, *Próximo pago 05 Dic*), barra de uso al 14.2% (rango óptimo) y botones rápidos (*Registrar gasto* y *Pagar tarjeta*).


---

### R7 — Detalle de Cuenta "Sueldo"
- **Identificador Stitch:** `fa5bdc233da8494597f60325ea78029b`
- **Propósito:** Configuración de la cuenta bancaria sin abarrotar la pantalla.
- **Componentes:**
  - Hero card con saldo contable (`S/ 3,850.00`).
  - Selector en cuadrícula 2x2 para entidades bancarias con BCP activo.
  - Bloque explicativo de Saldo Inicial (`S/ 3,500.00`) con acción outline para corrección atómica.
  - Zona de riesgo inferior separada con borde sutil `#FCA5A5` para archivar la cuenta.


---

### R8 — Nuevo Instrumento: Ahorros / Débito (Kibo UI Card)
- **Identificador Stitch:** `8a78a330c3a64e378fffcef572c1af02`
- **Propósito:** Alta de cuentas y tarjetas de débito con previsualización física interactiva.
- **Componentes:**
  - Card Preview BCP Débito con textura física, chip EMV dorado, ondas NFC, numeración enmascarada `•••• •••• •••• 7548` y logo oficial VISA.
  - Selector de banco, acordeón de catálogo de producto y campos ergonómicos para alias y saldo inicial (`S/ 1,500.00`).


---

### R9 — Nuevo Instrumento: Tarjeta de Crédito (Kibo UI Card)
- **Identificador Stitch:** `d68dd3078d4b43d28b3e4b3501070e4f`
- **Propósito:** Alta de tarjetas de crédito con previsualización física Kibo UI y revelación progresiva de parámetros financieros.
- **Componentes:**
  - Card Preview BCP Visa Oro en acabado grafito oscuro/azul noche con chip dorado y hint interactivo de giro 3D.
  - Parámetros de crédito: Línea total (`S/ 10,000.00`), día de corte (15), día de pago (05), TEA referencial (49.50%) y toggle de alerta de consumo al 80% (HU-10).


---

### R10 — Detalle de Tarjeta de Crédito (Kibo UI Flip + Movimientos)
- **Identificador Stitch:** `d089d45d2b734d0991d1643ab8a2bd0f`
- **Propósito:** Vista integral de tarjeta de crédito activa con interacción flip Kibo UI y movimientos vinculados.
- **Componentes:**
  - CardVisual Hero interactiva con efecto flip 3D (reverso informativo con fechas de ciclo y límites).
  - Resumen métrico: Deuda devengada (`S/ 1,420.00`), Disponible (`S/ 8,580.00`), Próximo pago (05 Dic) y barra de utilización semántica al 14.2% (rango óptimo HU-10).
  - Acciones rápidas: *Registrar consumo* (outline) y *Pagar tarjeta* (primary `#0F766E`).
  - Historial de movimientos filtrado: compras en coral y abono de tarjeta en teal neutral (sin considerarlo gasto operativo).

