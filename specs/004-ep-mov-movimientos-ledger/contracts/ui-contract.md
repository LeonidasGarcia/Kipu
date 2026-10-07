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
  - Preparación histórica S2: edición, anulación y reembolso. El incremento S4 siguiente fija las acciones habilitadas; no existe eliminación física de hechos contables.

---

## Accesibilidad y Privacidad

- Etiquetas semánticas y objetivos táctiles de al menos 48dp.
- No depender únicamente del color para comunicar tipo o estado financiero.
- Lectura accesible de monto, moneda y signo por TalkBack.
- Respetar el modo de privacidad global de enmascaramiento de cifras numéricas.
- Prohibición estricta de etiquetas (*tags*) en formularios y filtros.

---

## Incremento S4 — T062: Pantallas 10/11

Prevalece para HU-20/21/22; conserva tokens, navegación y accesibilidad S2. Contratos normativos: [comandos](./revise-and-void-transaction-v1.md), [consulta y autorización](./history-query-access.md), [reconciliación](./sync-and-deduplication.md).

### Pantalla 11: edición

- Abrir el snapshot vigente y conservar la revisión de partida; título «Editar movimiento». Guardar usa el comando de revisión, nunca el de alta.
- Permitir importe, fecha, cuentas aplicables, categoría/subcategoría, comercio y nota. Tipo, moneda y naturaleza permanecen fijos. Cuentas de transferencia distintas; mostrar referencias históricas archivadas, pero exigir elegibilidad para nuevas selecciones.
- Mostrar resumen anterior/nuevo antes de guardar. Solo nota conserva categoría y no agrega asientos; fecha actualiza consumo de ambos periodos sin cambiar por sí misma el saldo.
- Validar junto al campo, importes positivos en unidades menores y transferencia completa. Impedir doble envío durante commit; volver al historial tras persistencia durable con «Pendiente de sincronizar».
- Cancelar antes del commit no escribe movimiento, ledger ni outbox; pedir confirmación para descartar cambios sin guardar.
- Tarjeta, cuotas, deuda o relaciones especializadas bloquean edición/anulación genéricas: «Este movimiento tiene cuotas o una deuda asociada. Revísalo desde su gestión específica». Sin mutación ni rutas nuevas que no existan.

### Pantalla 10: anulación e historia

- Acciones S4 «Editar» y «Anular» para estándar vigente. No mostrar «Eliminar». «Reembolso» diferido a HU-25/S7.
- Diálogo «Anular movimiento»: identificar movimiento y consecuencias concretas para ambas cuentas si es transferencia. «Cancelar» no escribe; «Anular» aplica VOIDED/compensación atómicos conservando originales.
- Fila VOIDED visible con texto «Anulado», detalles y revisiones; no admite edición/resurrección. Replay del mismo comando no compensa otra vez.
- Separar estado financiero y sync: «Anulado» puede coexistir con «Pendiente de sincronizar»; un error de red no implica ausencia de efectos.
- Menú accesible además del gesto; 48 dp, TalkBack y texto para estados. Enmascaramiento en lista, editor, resumen, diálogo y conflicto.

### Conflictos y fallos

| Situación | Presentación y acción |
|---|---|
| Validación previa al commit | Error junto al campo; conservar formulario, sin efectos |
| Fallo de red tras commit | Pendiente/reintento sin repetir efecto local |
| Revisión incompatible | «Este movimiento cambió en otro dispositivo»; comparar vigente y propuesta persistida |
| Descartar | Resolver propuesta preservando historia y compensación de efectos optimistas |
| Rehacer | Reabrir sobre versión oficial vigente y validar comando nuevo |
| Oficial VOIDED | Mostrar anulación; impedir rehacer como edición del mismo movimiento |
| Rechazo financiero | Mensaje seguro; compensar solo efectos realmente aplicados, sin borrar historia |
| Evidencia insuficiente | Conflicto pendiente; no calcular compensación por suposición |

### Búsqueda y filtros

- Free: texto, fechas/periodo y tipo sobre todo el historial local. Premium: cuenta/tarjeta, categoría/subcategoría, comercio, monto, origen y estado conforme al contrato tipado. Sin etiquetas.
- Chips expresan valores y permiten retirar criterios. Criterios distintos se intersectan; varios valores del mismo criterio se unen conforme al contrato. Rango inválido muestra error sin corrección silenciosa.
- Limpiar reinicia criterios y cursor; cero coincidencias conserva búsqueda y ofrece limpiar. Distinguir vacío inicial, fallo de consulta y fin de paginación.
- Autorizar al abrir/aplicar, cargar página, resolver enlace y reanudar. El bloqueo evita ejecutar consulta avanzada, además de controlar UI.
- Caducidad o arranque sin continuidad confiable: «Reconecta para verificar tu acceso Premium». Continuar con texto/fecha/tipo, conservar selección avanzada como borrador y retirar resultados protegidos. Registro y edición/anulación estándar siguen disponibles en Free.
- Cambio de propietario, criterios, revisión del conjunto o autorización invalida cursor; reiniciar primera página autorizada. No registrar filtros, notas ni datos financieros en logs.
# UX aprobado 2026-10-03 (prevalece sobre interacción S4 anterior)

Tocar una fila abre detalle de lectura, también para VOIDED; las acciones explícitas siguen sujetas a elegibilidad y no resucitan movimientos. Filtros editan un borrador: aplicar valida montos en unidades mayores/moneda y fechas; cerrar/cancelar no modifica criterios aplicados. Referencias históricas se presentan por nombre. Estado financiero, sincronización, criterios efectivos y criterios retenidos sin acceso tienen etiquetas distintas. Todo importe respeta el enmascaramiento visual y semántico global.

Edición compara cada cambio Antes/Después y confirma persistencia local/pending sync; anulación conserva consecuencias por cuenta y no ofrece undo. Avisos, resúmenes y feedback usan tokens/reduced motion; no animar resultados protegidos de salida tras caducidad. Vacío inicial ofrece registrar, cero resultados ofrece retirar/limpiar, fallo ofrece reintentar. Targets 48 dp, layout adaptable, tonos semánticos y anuncios discretos.

### Corrección visual del issue #19 — 2026-10-06

Hallazgos reportados por el responsable durante prueba física del panel (FR-034/039): los botones y filas de chips deben tener separación vertical explícita; el calendario debe dejar margen superior al título y asignar igual espacio a inicio/fin. Las etiquetas compactas «Inicio» y «Fin» conservan el significado del rango. El cambio de calendario a entrada manual sigue disponible en la cabecera.

El aviso «Filtros avanzados · Premium» y su descripción forman una tarjeta informativa no clicable, con borde tonal y fondo `surfaceContainerHighest` para distinguirla del panel (ajuste de contraste solicitado al revisar el dispositivo). «Ver Premium» conserva su callback explícito; recuperación y controles deshabilitados siguen sujetos al acceso existente. Esta corrección no cambia aplicar/cancelar/limpiar, fechas, modelos ni autorización, y no cierra los restantes criterios del issue ni T110.

### Errores y selectores del issue #19

Los errores se muestran junto a fechas, moneda y cada límite de importe, distinguiendo fin anterior al inicio de máximo menor al mínimo. Corregir el borrador refresca los errores tras un intento inválido sin aplicar filtros ni modificar validación del dominio. Los mensajes no incluyen valores privados.

Cada selector presenta nombres/resumen de selección y abre búsqueda con lista acotada y composición perezosa por ID. Buscar no elimina selecciones ni modifica filtros aplicados. Referencias seleccionadas ausentes del catálogo actual conservan su ID y ofrecen una etiqueta histórica segura para retirarlas; nunca se muestra el ID. Diferenciar catálogo vacío de búsqueda sin coincidencias. Cerrar el selector conserva sus cambios en el borrador; cerrar el panel conserva filtros aplicados. La pérdida de acceso cierra el selector y bloquea controles, sin conceder acceso por restauración de UI.

El responsable confirmó que el resumen de filtros aplicados y «Limpiar» ya cumplen lo esperado; esas implementaciones se conservan.

En ventanas menores de 360 dp, el contenedor local del calendario se limita al ancho disponible y sus acciones se ajustan a varias filas. Conserva `DateRangePicker` y su modo manual/calendario; no sustituye la validación ni agrega criterios. En anchos mayores se conserva el diálogo nativo.
