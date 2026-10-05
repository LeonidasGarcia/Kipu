# Rediseño Visual Calm Emerald de Kipu

## Extensión: alta de instrumentos y tarjetas

Referencias del segundo lote:

- [Agregar a Mi dinero](../mockups/canonical/add-money/add-to-my-money.png).
- [Seleccionar producto y entidad emisora](../mockups/canonical/cards/select-card.png).
- [Configurar tarjeta y confirmación](../mockups/canonical/cards/configure-card.png).

El alcance visual reorganiza el alta en elección de instrumento, selección de producto de crédito y configuración. Conserva las rutas de cuenta, débito, billetera y efectivo, así como los callbacks de guardado existentes.

La pantalla inicial ofrece accesos para cuenta bancaria, tarjeta de crédito, billetera y efectivo, con información del cupo y privacidad. El alta de crédito presenta entidad emisora, búsqueda/filtros del catálogo, registro personalizado, configuración del ciclo y una confirmación vinculada al guardado real. Se mantuvieron los campos requeridos por el modelo y se adaptaron los chips y controles de expansión a un objetivo táctil mínimo de 48 dp; los campos de corte y pago se envuelven cuando el ancho no permite mostrarlos en una fila.

Los umbrales de utilización se describen como avisos que se activan al alcanzar 50%, 80% o 100%. Las tasas se rotulan como referencia del catálogo, con su fecha disponible, y se aclara que no reemplazan la tasa contractual. La selección manual de una tarjeta permanece explícita y no se repone tras elegir el registro personalizado.

Las imágenes son referencias visuales. No autorizan incorporar precios de PRO, beneficios bancarios ni tasas ficticias. El conteo de instrumentos no determina por sí solo el plan efectivo; las restricciones se validan mediante el contrato existente. Los cuatro dígitos finales continúan siendo obligatorios para crédito, los días de corte y pago pueden cruzar meses y la TEA de catálogo se presenta como referencia. Una confirmación de éxito debe proceder del guardado real.

La restricción histórica de no ejecutar pruebas fue reemplazada por el brief aprobado de implementación final. El avance y la validación en dispositivo se registran en [APPROVED_MOCKUPS_EXECUTION.md](APPROVED_MOCKUPS_EXECUTION.md) y [la evidencia de implementación](IMPLEMENTATION_EVIDENCE.md).

## 1. Visión General y Dirección de Arte

Este documento registra el rediseño visual Calm Emerald en las pantallas de inicio de movimientos, dashboard, historial y alta/configuración de instrumentos, junto con la barra de navegación inferior flotante (`KipuNavigationBar`), basado en las referencias visuales suministradas:

- **Referencia 1 (Hoja de Registro Rápido)**: [register-movement.png](../mockups/canonical/quick-movement/register-movement.png)
- **Referencia 2 (Dashboard / Mi Dinero)**: [dashboard.png](../mockups/canonical/dashboard/dashboard.png)
- **Referencia 3 (Historial de Movimientos)**: [movements.png](../mockups/canonical/movements/movements.png)

### Paleta Visual y Tokens Específicos (`CalmEmeraldTokens`)
- **Fondo Mint**: `#F5FAF8` / `#F1FBF7` (Modo claro), `#0B1220` (Modo oscuro).
- **Tarjetas y Superficies**: `#FFFFFF` (Modo claro), `#131B2E` (Modo oscuro), radio de curvatura de 16–18dp.
- **Bordes Delicados**: `#DFEAE5` (Modo claro), `#26344B` (Modo oscuro).
- **Esmeralda Profundo Primario (`primaryDeep`)**: `#075E52` / `#0B6657` (Modo claro), `#14B8A6` (Modo oscuro).
- **Contenido sobre Esmeralda Primario (`onPrimaryDeep`)**: `#FFFFFF` en modo claro y `#042F2E` en modo oscuro, garantizando contraste accesible sobre botones y FABs.
- **Tipografía Primaria**: `#102522` (Modo claro), `#EFF1F3` (Modo oscuro), familia Inter.
- **Texto Secundario y Muted**: `#617773` (Modo claro), `#94A3B8` (Modo oscuro).
- **Texto de Alerta Accesible (`warningText`)**: `#92400E` (Modo claro), `#FDE68A` (Modo oscuro), evitando contrastes débiles tipo ámbar sobre fondo blanco.
- **Tarjeta Hero**: Esmeralda profundo `#075E52` con arcos geométricos sutiles en `drawBehind`, radio de 22–24dp y tipografía blanca de alto contraste.
- **Roles Financieros Semánticos**:
  - Gasto: Texto coral/rojo `#DC2626`, fondo `#FEF2F2`, borde `#FECACA`.
  - Ingreso: Texto esmeralda `#0B6657`, fondo `#F0FDF4`, borde `#BBF7D0`.
  - Transferencia: Texto azul `#2563EB`, fondo `#EFF6FF`, borde `#BFDBFE`.

---

## 2. Cambios Implementados por Pantalla

### 1. Registro Rápido (`QuickMovementScreen.kt`)
- **Hoja Modal**: Contenedor blanco con esquinas superiores redondeadas a 24dp, manija de arrastre centrada y altura acotada con desplazamiento vertical y pie anclado sobre los insets del sistema y teclado.
- **Cabecera y Cierre**: Título "Registrar movimiento" junto a distintivo de conteo de errores reales (`X error(es)`) cuando existen fallos de validación; botón de cierre con área de toque mínima de 48dp y restricción de ancho para evitar desbordamientos en tamaños de fuente grandes.
- **Separación de Errores**: Se discriminan los errores de validación de campos (`amountError`, `accountError`, `categoryError`, `destinationAccountError`) del error general o transitorio (`generalError`). Solo los errores de campo bloquean el botón de guardado; los errores generales permiten la acción de "Reintentar guardar".
- **Pestañas de Tipo de Movimiento**: Selector segmentado sobre pista `#EEF5F2` con pestañas Gasto, Ingreso y Transferencia, con animación de color sensible a `rememberReducedMotionEnabled()`.
- **Selector de Moneda Deliberado**: Pestañas de moneda (PEN / USD) que reflejan la moneda del instrumento actual y abren el selector de cuentas/tarjetas para una selección explícita del usuario, deshabilitando monedas sin instrumentos disponibles y evitando asignaciones automáticas silenciosas.
- **Entrada de Monto**: Campo grande con prefijo de signo exacto (`−`, `+`), formateo numérico con dígitos tabulares (`tnum`) y teclado decimal.
- **Comercio**: Acción explícita de limpieza vinculada a `onClearMerchant` con botón táctil de 48dp visible cuando hay comercio asignado. Soporta búsqueda/edición en catálogo y entrada de texto libre, sin emitir afirmaciones infundadas de verificación bancaria.
- **Transferencias**: Agrupación vertical de origen y destino con conector central circular y botón de fallback para crear una nueva cuenta en la misma moneda si no existen destinos disponibles. Se eliminó cualquier bloque ficticio de "Sin costo / Misma titularidad", ya que no existe contrato de comisiones ni titularidad en el dominio.
- **Notas y Accesibilidad**: Sección colapsable rotulada como nota o motivo opcional (sin prometer adjuntos de comprobantes inexistentes). Botón de guardado sticky con altura adaptable (`heightIn(min = 52.dp)`), color de texto accesible (`onPrimaryDeep`) e indicador de progreso no bloqueante.

### 2. Dashboard (`DashboardScreen.kt` y `CreditCardSummaryCard.kt`)
- **Cabecera**: Saludo "Bienvenido" sobre "Mi dinero", avatar genérico sin iniciales inventadas, y botones de notificaciones y ajustes con áreas táctiles de 48dp.
- **Tarjeta Hero Multidivisa**: Muestra los saldos disponibles en PEN y USD de forma separada y veraz siempre que existan activos en ambas monedas, evitando conversiones o sumas cruzadas entre divisas. Subtítulo veraz indicando activos líquidos reales.
- **Subpaneles Desglosados**: Totales "En cuentas bancarias" y "En efectivo" calculados por moneda mediante aritmética exacta con `BigInteger` y presentados con `MoneyText`, respetando el enmascaramiento de privacidad (`LocalBalanceMasked`) y mostrando ceros veraces cuando no hay saldos.
- **Banda de Cupo Free**: Muestra el consumo del plan sobre instrumentos activos (cuentas y tarjetas bancarias) con conteos reales y etiqueta veraz "Cupo Free", aplicando color accesible de advertencia al aproximarse o alcanzar el límite.
- **Preservación de Acciones**: Acciones de creación directa ("+ Cuenta" y "+ Tarjeta") en cabeceras de sección con áreas táctiles de 48dp, junto con las opciones de gestión y detalle.
- **Tarjeta de Crédito (`CreditCardSummaryCard.kt`)**: Estados truthful basados en propiedades del modelo ("Activa", "Archivada", "Bloqueada por plan"), fechas de facturación y pago rotuladas como configuradas, maquetación adaptable (`isStacked`) ante pantallas estrechas o texto grande, y enmascaramiento total de límites y porcentajes de utilización cuando el modo privacidad está activo.

### 3. Historial de Movimientos (`MovementHistoryScreen.kt`)
- **Cabecera y Búsqueda**: Barra de búsqueda con limpieza rápida y acceso al panel de filtros avanzados, junto a la píldora de contexto del período temporal seleccionado.
- **Filtros por Tipo**: Chips de selección ("Todos", "Gastos", "Ingresos", "Transf.") con indicadores cromáticos y opción de limpiar filtros.
- **Tarjeta de Resumen Financiero**:
  - Título veraz: **"INGRESOS − GASTOS VIGENTES"** con subtítulo **"Resultados filtrados"**, evitando presentar el neto como patrimonio consolidado o métrica global.
  - Cálculo robusto por divisa mediante `BigInteger` y formateo con `BigDecimal`, desglosando líneas independientes para PEN y USD si ambas están presentes (o valor neutro en PEN si no hay movimientos).
  - Filtro estricto a movimientos en estado `ACTIVE` o `CONFIRMED`, excluyendo registros `VOIDED`, `FAILED` o `REVISED`.
  - Exclusión de transferencias internas y operaciones no monetarias o de ajuste (`OPENING`, `ADJUSTMENT`, `REVERSAL`, `CARD_PAYMENT_CASH`), e inclusión de compras con tarjeta (`CARD_PURCHASE`).
- **Estados de Carga, Error y Vacío**: Botones de reintento y registro con áreas táctiles de 48dp y texto accesible contrastado (`onPrimaryDeep`).

### 4. Barra de Navegación Flotante (`KipuNavigationBar.kt`)
- **Estructura Flotante**: Cápsula blanca flotante centrada con elevación suave y padding respecto al borde inferior, conteniendo estrictamente las dos rutas de navegación reales del producto: "Dinero" (`Dashboard`) y "Movimientos" (`History`).
- **Botón de Acción Rápida (FAB)**: Botón circular independiente de 56dp con esmeralda primario y glifo `+`, con canal de eventos desacoplado (`savedStateHandle["open_register_movement"]`) que invoca la hoja de registro rápido en la pantalla activa.
- **Adaptabilidad Tipográfica**: Distribución equitativa mediante ponderación proporcional y disposición vertical adaptable (icono sobre texto) ante factores de escala tipográfica (`fontScale >= 1.25f`), con retroalimentación táctil nativa de Compose.

---

## 3. Límites de Implementación y Declaraciones Técnicas

1. **Destinos de Navegación Reales**: La barra de navegación conserva únicamente los dos destinos vigentes en el sistema (`Dinero` y `Movimientos`). No se introdujeron pestañas simuladas (p. ej. "Planificación").
2. **Sin Contratos Financieros Ficticios**: No se asumen gratuidades de comisión en transferencias ni validaciones bancarias oficiales de comercios; se utiliza la terminología exacta de catálogo y entradas libres.
3. **Aritmética Segura**: Todas las sumas monetarias en componentes de presentación se realizan por divisa independiente con tipos enteros de precisión arbitraria (`BigInteger`), eliminando riesgos de desbordamiento en enteros de 64 bits y conversiones con coma flotante (`Double`).
4. **Declaración de Verificación**:
   > **Estado de verificación**: Se hizo una revisión manual estática de los cambios Kotlin/Jetpack Compose y `git diff --check`. A solicitud expresa del usuario, **no se ejecutó compilación vía Gradle (`./gradlew`), no se ejecutaron pruebas automatizadas y no se realizaron pruebas en emulador/dispositivo mediante ADB**.
