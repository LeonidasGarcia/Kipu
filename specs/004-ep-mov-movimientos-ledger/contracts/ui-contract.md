# Contract: UI Sprint 2 — EP-MOV

## Fuentes Visuales y Sistema de Diseño Normativo

1. **Tokens y Sistema Visual**: [`docs/stitch-design-system.md`](../../../docs/stitch-design-system.md) (`Kipu Andean Modernist`).
   - Tokens de color: Base teal/CTA `#0F766E`, fondo `#F7F9FB`, tarjetas `#FFFFFF`, borde contenedor 16dp, esquinas de bottom sheet 24dp.
   - Colores semánticos financieros: Ingresos `#16A34A`, Gastos `#E85D5D`, Alertas/Warning `#F59E0B`.
   - Tipografía: Inter con números tabulares obligatorios (`fontFeatureSettings = "tnum"`) en todo monto, saldo o balance.
2. **Prototipo Figma**: [`Kipu_Documentacion/Prototipo/Stich Prompts.md`](../../../../Kipu_Documentacion/Prototipo/Stich%20Prompts.md) (Prompt 5 de 9, líneas 280-355).

---

## Pantalla 11 — Formulario de Acción Rápida (Bottom Sheet)

- **Estructura**: Bandeja inferior deslizable independiente (BottomSheet) cubriendo hasta el 85% de la pantalla, esquinas superiores redondeadas a 24dp y barra de arrastre (*drag handle*).
- **Cabecera**: Título destacado **"Registrar Transacción"** usando tipografía Inter Medium. Barra de navegación inferior (KipuBottomBar) oculta por completo durante el proceso.
- **Pestañas de Tipo de Movimiento (TabRow)**:
  - Estrictamente tres pestañas interactivas horizontales de igual ancho: **"Gasto"**, **"Ingreso"** y **"Transferencia"**.
- **Campos del Formulario**:
  - **Monto**: Caja de entrada destacada con tipografía Inter y números tabulares (`tnum`).
  - **Cuenta origen**: Selector de cuenta líquida (efectivo, ahorros, billeteras).
  - **Categoría/Subcategoría**: Selector con favoritos y árbol taxonómico (obligatorio en Gasto, opcional en Ingreso; oculto en Transferencia).
  - **Comercio**: Selector que enlaza al catálogo o permite ingresar nombres personalizados/provisionales.
  - **Cuenta destino (solo Transferencia)**: Selector de cuenta de destino (fondos propios, pago de tarjeta o deuda).
  - **Sección colapsable "Más detalles"**: Revela fecha mediante DatePicker y campo de notas.
- **Detección de Duplicados (HU-23)**:
  - Al presionar "Guardar", si se detecta un movimiento similar (monto, cuenta y fecha en rango < 5 min), despliega diálogo de advertencia en español:
    - Título: *"Posible Movimiento Duplicado"*.
    - Mensaje: *"Se ha detectado una transacción idéntica registrada recientemente en esta cuenta. ¿Deseas guardarla de todas formas?"*.
    - Botones: *"Cancelar"* (volver a editar) y *"Confirmar"* (guardar de forma atómica).

---

## Pantalla 10 — Historial de Movimientos (Ledger)

- **Estructura**: Layout vertical Portrait de scroll continuo sobre fondo claro.
- **Barra Superior (KipuTopAppBar)**:
  - Título fijo **"Historial"** en tipografía Inter Medium.
  - Extremo derecho con exactamente dos botones de acción: campana (Notificaciones) y engranaje (Ajustes).
  - Extremo izquierdo vacío (sin icono de navegación/retroceso).
- **Barra de Navegación Inferior (KipuBottomBar)**:
  - Visible, activa y con la opción **"Historial"** seleccionada.
- **Componentes**:
  1. **Barra de búsqueda de texto**: OutlinedTextField compacto para filtrar la lista en tiempo real.
  2. **Panel de filtros rápidos (Chips)**: Fila horizontal con scroll (`Período`, `Categoría`, `Cuenta`, `Tarjeta`, `Comercio`, `Monto`, `Estado`). En plan Free permite filtrar por tipo de movimiento (Gasto, Ingreso, Transferencia).
  3. **Lista cronológica agrupada por fechas**: Cabeceras ("Hoy, 3 de Setiembre", "Ayer, 2 de Setiembre", etc.).
  4. **Fila de Transacción (`TransactionRow`)**:
     - Icono: Logotipo oficial de marca del comercio o icono de categoría con fondo tonal.
     - Textos: Nombre del comercio o categoría, y debajo el alias de la cuenta afectada.
     - Monto: Formateado con números tabulares (`tnum`) de tipografía Inter (ingresos en verde `#16A34A`, gastos en neutro oscuro).
     - Estado de sincronización: Indicador sutil de sincronizado / pendiente de sincronizar.
     - Soporte para ocultamiento dinámico de saldos mediante `LocalBalanceMasked` (MoneyText).
- **Menú Contextual (Preparación para Sprints 4 y 7)**:
  - Al mantener presionado o deslizar la fila: acciones rápidas "Editar", "Anular", "Eliminar", "Reembolso".

---

## Accesibilidad y Privacidad

- Etiquetas semánticas y objetivos táctiles de al menos 48dp.
- No depender únicamente del color para comunicar tipo o estado financiero.
- Lectura accesible de monto, moneda y signo por TalkBack.
- Respetar el modo de privacidad global de enmascaramiento de cifras numéricas.
- Prohibición estricta de etiquetas (*tags*) en formularios y filtros.
