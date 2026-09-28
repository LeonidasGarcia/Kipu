# DIRECTIVA DE ORQUESTACIÓN: REDISEÑO UI/UX "CREAR CUENTA / TARJETA", EFECTO 3D TILT, ENCODING Y CORRECCIONES DE INSTRUMENTOS

> **Rol de Codex (Panel Izquierdo)**: Coordinador e Implementador Técnico Principal. Ejecutas el plan TDD, modificas el código, compilas y corres las suites de pruebas.
> **Rol de AGY (Panel Derecho)**: Oráculo de Dominio, Constitución y Revisor Crítico de Arquitectura/Accesibilidad. **Consulta activamente la Bóveda de Documentación y Prototipos** y provee a Codex los datos validados del negocio.
> **Rama de trabajo**: `010-ep-not-notificaciones-avisos` (mantenerse estrictamente en esta rama).
> **Repositorio de código**: `C:\Users\Alume\orca\Kipu`
> **Bóveda de Documentación Oficial y Prototipos**: `C:\Users\Alume\orca\KipuApp`
> **Skills requeridas**: `transitions-dev`, `transitions-polish`, `ui-ux-pro-max`, `android-kotlin`, `supabase`.

---

## 1. FUENTES DE DOCUMENTACIÓN Y PROTOTIPOS (EN `C:\Users\Alume\orca\KipuApp`)

AGY y Codex deben consultar directamente los archivos de esta ruta para no inventar información ni desviarse de los requerimientos de Kipu:

1. **Catálogo oficial de tarjetas y tasas SBS**:
   - `C:\Users\Alume\orca\KipuApp\Tasas de Bancos del Peru\KIPU_CATALOGO_TARJETAS_CREDITO_PERU_2026.md`
   *(Contiene todos los bancos peruanos: BCP, Interbank, BBVA, Scotiabank, sus familias de tarjetas reales: Clásica, LATAM Pass, Qore, Bfree, Cero, Benefit, y sus rangos de TEA referencial)*.
2. **Diseño, pantallas y tokens Stitch**:
   - `C:\Users\Alume\orca\KipuApp\Prototipo\Stich Prompts.md`
   - `C:\Users\Alume\orca\KipuApp\Prototipo\Esquema de pantallas generales.md`
   - `C:\Users\Alume\orca\KipuApp\Prototipo\Descripción de Pantallas según Historias de Usuario.md`
3. **Modelo de datos y entidades de negocio**:
   - `C:\Users\Alume\orca\KipuApp\Entidades\V4.2_Entidades.md`
   - `C:\Users\Alume\orca\KipuApp\Kipu md\01_Kipu_V4.2_Especificacion_Producto.md`

> **Instrucción para AGY**: Cuando Codex pregunte por listas de tarjetas, jerarquías de entidades bancarias, tasas o esquemas visuales, lee estos archivos de `KipuApp` y responde con los datos exactos.

---

## 2. DETALLE DE TAREAS A EJECUTAR

### TAREA 1: Sanitización de Textos y Codificación UTF-8 (Fix Mojibake)
- **Problema**: En `app/src/main/res/values/strings.xml` y llamadas Compose, textos como `Buscar transacciones…`, `Registrar Transacción`, `Más detalles`, `¿Deseas guardarla de todas formas?` aparecen con caracteres corruptos (`â€¦`, `Ã³`, `MÃ¡s`, `Â¿`).
- **Acción**:
  1. Auditar y sanitizar `app/src/main/res/values/strings.xml` garantizando codificación UTF-8 pura y entidades XML estándar (`&#8230;` para elipsis, tildes correctas en español, signos de interrogación invertidos).
  2. Verificar que ningún archivo `.kt` en `feature/movements` o `feature/accounts` contenga strings hardcodeados con encoding roto.

### TAREA 2: Habilitación de Tarjetas de Crédito como Cuenta Origen en Registro de Gastos
- **Problema**: Al presionar `+` para registrar un **Gasto** en `QuickMovementScreen`, el desplegable "Cuenta origen" solo lista cuentas de ahorro/efectivo (`Sueldo`), pero no muestra las tarjetas de crédito activas del usuario (ej. `BCP VISA •••• 7548`). En la vida real, una compra con tarjeta de crédito es un gasto habitual.
- **Acción**:
  1. En `QuickMovementViewModel.kt`, combinar `observeInstruments.observeAccounts(activeOnly = true)` con `observeInstruments.observeCards(activeOnly = true)`.
  2. Modelar las opciones de origen para que el selector soporte tanto cuentas líquidas como tarjetas de crédito activas, mostrando con claridad el tipo de instrumento (ej. `Sueldo · Ahorros` vs `BCP VISA •••• 7548 · Tarjeta de Crédito`).
  3. Al seleccionar una tarjeta de crédito como origen de un gasto (`EXPENSE`), despachar la transacción como compra a crédito (asociando `card_id`), reconociendo el gasto en el historial y el pasivo correspondiente sin deducir saldo de cuentas líquidas (respetando la Invariante Constitucional I de Kipu).
  4. Actualizar las pruebas unitarias en `QuickMovementViewModelTest.kt`.

### TAREA 3: Resiliencia y Corrección del Error 400 en Tarifario de Tasas y TEA Referencial
- **Problema**: La pantalla de "Tasas y TEA Referencial" muestra en rojo:
  `Credit catalog request failed (400): column credit_products.institution_name does not exist`.
- **Acción**:
  1. En `FinancialInstrumentsApi.kt` (`fetchCreditProducts`), hacer que la petición PostgREST sea tolerante y compatible con el esquema backend actual:
     - Ajustar los parámetros del `select` o realizar un fallback elegante a `referential_rate_catalog` (que contiene las tasas oficiales SBS verificadas).
     - Si la columna `institution_name` no existe en la base de datos remota, mapear el nombre a partir de `institution_code` o de los presets conocidos de entidades bancarias usando `KIPU_CATALOGO_TARJETAS_CREDITO_PERU_2026.md`.
  2. Sustituir el mensaje de error en texto plano rojo por un componente visual de error amigable, alineado al sistema de diseño de Kipu, con botón de reintentar.

### TAREA 4: Rediseño Unificado de "Crear Cuenta / Tarjeta" (Alineado al Prototipo Andean Modernist)
- **Problema**: Actualmente "Nueva cuenta" y "Nueva tarjeta" están separados en pantallas inconexas (`AccountFormScreen` y `CardFormScreen`), y el selector actual de tipos de cuenta es un modal genérico poco intuitivo.
- **Acción**:
  Unificar el flujo en una única pantalla de alta calidad según el prototipo oficial de `KipuApp`:
  
  #### A. Card Preview Interactiva Superior con Efecto 3D Tilt y Glare
  - Componente visual de tarjeta Andean Modernist en la parte superior:
    - Logotipo/nombre del banco (ej. BCP) y tipo de instrumento.
    - Red emisora (VISA, Mastercard, etc.).
    - Chip EMV e ícono contactless.
    - Saldo a la fecha o Línea de crédito en tiempo real.
    - Alias dinámico y últimos 4 dígitos (`•••• XXXX`).
  - **Efecto 3D Tilt (Pointer Tilt with Glare)**:
    - Utilizar las pautas de `transitions-dev`:
      - Implementar en Compose con `pointerInput` y `graphicsLayer`: cálculo de inclinación en los ejes X e Y (`rotationX`, `rotationY`) según la posición del toque/drag respecto al centro de la tarjeta.
      - Efecto de reflejo de luz / brillo especular (*specular glare overlay*) con gradiente radial o lineal que se desplaza orgánicamente con el ángulo de inclinación.
      - Retorno suave al estado neutro con física de resorte (`spring` o `tween` corto) al soltar el dedo.
      - **Accesibilidad**: Si `rememberReducedMotionEnabled()` es `true`, suprimir el tilt dinámico y mostrar la tarjeta en posición plana neutra.

  #### B. Selector de Tipo de Cuenta (4 opciones en cuadrícula/chips elegantes)
  - `Ahorros / Débito`
  - `Tarjeta de Crédito`
  - `Billetera (Yape/Plin)`
  - `Efectivo`
  - Transición fluida (`AnimatedContent` / `fadeIn` + `expandVertically`) al alternar entre tipos.

  #### C. Lógica Dinámica y Lista Anidada / Acordeón por Banco
  - **Si es Billetera (Yape/Plin)**:
    - Habilitar campo para afiliarla a una cuenta de ahorros o tarjeta de débito existente del usuario.
    - Sugerir alias automático (ej. "Yape BCP", "Plin Interbank").
  - **Si es Ahorros / Débito o Tarjeta de Crédito**:
    - Habilitar selector de **Entidad Bancaria**: BCP, Interbank, BBVA, Scotiabank.
    - Al seleccionar el banco, mostrar una **lista anidada / acordeón elegante de productos reales del mercado peruano** extraídos de `KIPU_CATALOGO_TARJETAS_CREDITO_PERU_2026.md`:
      - **BCP**: Clásica Débito BCP, Tarjetas LATAM Pass (Oro, Platinum, Signature, Infinite), Visa Light, Visa Clásica, Tarjetas Qore.
      - **BBVA**: Débito BBVA, Tarjetas Bfree, Cero, Signature, Platinum, Infinite.
      - **Interbank**: Débito Interbank, Tarjetas Cashback, Millas Benefit, Clásica, Oro, Platinum, Black.
      - **Scotiabank**: Débito Scotiabank, Tarjetas AAdvantage, Scotia Puntos, Clásica.
    - Al seleccionar una tarjeta específica:
      - Autocompletar la red (Visa / Mastercard).
      - Configurar los colores base y presets visuales en la Card Preview superior.
      - Si es crédito, precargar la TEA referencial conocida según la documentación.
  - **Si es Efectivo**:
    - Ocultar bancos y dígitos; pedir únicamente alias y saldo inicial.

  #### D. Formulario Inferior Unificado
  - *Alias de la Cuenta o Tarjeta*.
  - *Saldo a la fecha* (para Débito/Ahorros/Billetera/Efectivo) o *Línea de crédito* (para Crédito).
  - *Personalización (Color e Ícono)*: Paleta Andean Modernist y selector de ícono.
  - *Últimos 4 dígitos* (con máscara y límite de 4 caracteres numéricos).
  - *Red Emisora* (Visa, Mastercard, Amex, Diners).
  - *Día de corte y Día de pago* (visibles únicamente para Tarjeta de Crédito).
  - *Insignia de Seguridad*: "Kipu nunca solicita ni almacena el número de 16 dígitos, fecha de vencimiento ni código CVV." con ícono de escudo.
  - *Botón Guardar*: Mínimo 48dp de altura, estado deshabilitado si faltan campos obligatorios y animación de guardado.

  #### E. Navegación en Dashboard
  - El botón de agregar instrumento en el Dashboard debe abrir directamente este formulario unificado.

---

## 3. PROTOCOLO DE ORQUESTACIÓN Y VERIFICACIÓN CRUZADA CON AGY

1. **Consulta inicial a AGY (Panel Derecho)**:
   - AGY consulta `C:\Users\Alume\orca\KipuApp` y valida con Codex el catálogo exacto de tarjetas y entidades para la lista anidada.
   - Confirma que la estructura unificada cumple con las invariantes contables de la Constitución (no mezclar saldo líquido con líneas de crédito).
2. **Implementación y pruebas unitarias**:
   - Escribe o actualiza las pruebas unitarias para el ViewModel y los use cases.
   - Ejecuta `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug` para garantizar que todo compila y pasa al 100%.
3. **Auditoría de Accesibilidad y Motion con AGY**:
   - Pide a AGY revisar que los touch targets cumplan $\ge 48\text{dp}$, los contrastes sigan WCAG AA y `rememberReducedMotionEnabled()` esté correctamente implementado en el 3D tilt.
4. **Entrega final**:
   - Presenta un resumen estructurado de los cambios y confirma que la suite de pruebas queda en verde. No realizar commits sin instrucción expresa del usuario.
