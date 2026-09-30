# DIRECTIVA DE IMPLEMENTACIÓN: SPRINT DE ESTABILIZACIÓN Y FIXES QA (KIPU)

> **Rama de trabajo activa**: `fix/sprint-3-qa-stabilization`  
> **Auditoría de referencia**: `docs/audits/KIPU_FULL_FUNCTIONAL_UX_AUDIT.md`  
> **Bóveda de Documentación**: `C:\Users\Alume\orca\KipuApp`  
> **Rol de Codex (Panel Izquierdo)**: Desarrollador e Implementador. Modifica código, añade regresiones y ejecuta pruebas.  
> **Rol de AGY (Panel Derecho - `term_34398337-2535-4553-83d6-1929820100ec`)**: Revisor de Especificación y QA. Valida contratos y casos borde antes de dar por cerrado cada bloque.

---

## REGLAS DE TRABAJO
1. Trabaja de forma incremental por bloques (Bloque 1 -> Bloque 2 -> Bloque 3).
2. Cada bloque debe acompañarse de sus pruebas unitarias/instrumentadas de regresión correspondientes.
3. Tras cada bloque, consulta con AGY en `term_34398337-2535-4553-83d6-1929820100ec` para revisión cruzada.
4. No rompas la partida doble ni la invariante de ledger en Room.
5. **Uso de Skills de UI/UX y Animaciones**:
   - Para todo el trabajo visual, diseño de componentes y ergonomía, aplica la skill `ui-ux-pro-max` (heurísticas de Nielsen, targets táctiles mínimos de 48dp, tipografía tabular `tnum` en importes y espaciados atómicos).
   - Para las transiciones, micro-interacciones, expansión de categorías y modales, aplica las skills `transitions-dev` y `transitions-polish` (duraciones asimétricas de apertura/cierre, easings con tokens de `KipuMotionTokens` y soporte para `rememberReducedMotionEnabled()`).

---

## FUENTES DE CONSULTA Y APOYO DISPONIBLES
Si tienes cualquier duda técnica o de especificación durante la implementación, dispones de:
1. **AGY (Panel Derecho - `term_34398337-2535-4553-83d6-1929820100ec`)**:
   - Tu co-auditor y revisor de especificaciones. Consúltale cualquier regla, validación o criterio enviando mensajes con `orca terminal send --terminal term_34398337-2535-4553-83d6-1929820100ec --text "..." --enter` y leyendo su respuesta con `orca terminal read`.
2. **Bóveda de Documentación Oficial (`C:\Users\Alume\orca\KipuApp`)**:
   - Contiene todas las HUs, criterios de aceptación Gherkin, arquitectura y prototipos.
3. **Informe Consolidado de Auditoría (`docs/audits/KIPU_FULL_FUNCTIONAL_UX_AUDIT.md`)**:
   - Contiene la explicación exhaustiva de cada hallazgo, las líneas de código afectadas, las trazas de error y las 460 evidencias de prueba.

---

## BLOQUE 1: ESTABILIDAD CRÍTICA (P1 - CRASH DE NAVEGACIÓN)

### [BUG-NAV-01] Crash al abrir el Centro de Notificaciones
- **Archivo**: `app/src/main/java/com/kipu/app/MainActivity.kt:163-204`
- **Problema**: En `MainActivity.kt`, el bloque `NavHost` registra `authDestinations`, `planSelectionDestination`, `settingsDestinations`, `accountsDestinations`, `movementDestinations` y `movementsDestinations`, pero **omite** registrar `notificationDestinations(navController = navController)`. Al pulsar la campana en el TopAppBar, Navigation lanza `IllegalArgumentException` porque no encuentra la ruta `notifications/center` y la app crashea al launcher de Android.
- **Acción**:
  1. En `MainActivity.kt`, dentro del `NavHost`, agregar la llamada a `notificationDestinations(navController = navController)`.
  2. Verificar que `KipuNavHost.kt` y `MainActivity.kt` queden consistentes.
  3. Crear o actualizar un test de navegación que verifique que la ruta `notifications/center` está registrada en el grafo y navega a la pantalla de Notificaciones sin arrojar excepciones.

---

## BLOQUE 2: INTEGRIDAD FINANCIERA Y FLUJOS DE PAGO (P2)

### [BUG-FIN-01] El monto con separador de miles se rechaza ("Ingresa un monto mayor a cero")
- **Archivos**: 
  - `app/src/main/java/com/kipu/app/core/finance/domain/MoneyInputParser.kt:10-22`
  - `app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/PayCardDialog.kt:180-185`
- **Problema**: `MoneyInputParser.parseMinorUnits(input)` ejecuta `val normalized = input.trim().replace(',', '.')`. Cuando se ingresa `1,000.00` o `4,000.00` (con coma de miles), el string se transforma en `1.000.00`, lo cual no coincide con la regex `amountPattern` y devuelve `null`. `PayCardDialog` recibe `null` y muestra el error en rojo `"Ingresa un monto mayor a cero"`.
- **Acción**:
  1. En `MoneyInputParser.kt`, robustecer el parser para admitir formato local: si contiene tanto coma como punto (ej. `1,000.00`), o una coma seguida de 3 dígitos antes del punto/decimal, remover el separador de miles antes de normalizar el separador decimal. Si el string tiene formato latino `1.000,50` o peruano bancario `1,000.50`, parsearlo limpiamente a céntimos (Long) sin pérdida ni falsos nulos.
  2. Añadir tests unitarios en `MoneyInputParserTest.kt` cubriendo: `"1,000.00"`, `"4,000.00"`, `"1000.00"`, `"1000"`, `"0.50"`, `",50"`, `"1,234,567.89"`.

### [BUG-FIN-02 & BUG-UI-01] Selector ofrece Efectivo pero se rechaza y diálogo queda congelado
- **Archivo**: `app/src/main/java/com/kipu/app/feature/accounts/presentation/instruments/PayCardDialog.kt:52-56, 200-210`
- **Problema**: 
  - En `PayCardDialog.kt:53-56`, `matchingAccounts` filtra por moneda, archivado y saldo > 0, pero **no excluye** cuentas `AccountType.CASH`. El usuario elige Efectivo, pero el repositorio (`OfflineFirstFinancialInstrumentsRepository.kt:833-835`) lo rechaza porque solo admite cuentas bancarias (`BANK` o `SAVINGS`).
  - Al ocurrir el error, `isSubmitting` se quedó en `true` y no se resetea a `false`, dejando el botón congelado en `"Procesando..."`.
- **Acción**:
  1. En `PayCardDialog.kt:53-56`, agregar condición para excluir `AccountType.CASH` (solo permitir cuentas bancarias/ahorros elegibles para pagar tarjeta).
  2. En el ViewModel / Dialog, asegurarse de que ante cualquier excepción o fallo en `payCreditCard`, se emita un evento de error que devuelva `isSubmitting = false` y permita al usuario corregir o reintentar sin quedar atrapado.

### [BUG-FIN-03] Corrección repetida de saldo inicial acumula saldo erróneo (+S/ 1.00)
- **Archivo**: `app/src/main/java/com/kipu/app/feature/accounts/data/OfflineFirstFinancialInstrumentsRepository.kt:218-250`
- **Problema**: La función `recordOpeningAdjustment` siempre busca la apertura original por `opening_account_id` y revierte siempre el importe original, en vez de buscar el ajuste vigente previo y revertir ese importe. Al ajustar consecutivamente (ej. 20 -> 21 -> 20), el saldo acumulado en Room queda desviado en S/ 1.00.
- **Acción**:
  1. Actualizar `recordOpeningAdjustment` para que identifique el último ajuste o balance de apertura vigente asociado a la cuenta y revierta el ajuste activo actual antes de insertar el nuevo ajuste auditable.
  2. Agregar test unitario de regresión: apertura 20.00, corregir a 21.00 (saldo 21.00), corregir a 20.00 (saldo vuelve exactamente a 20.00), corregir a 25.00 (saldo 25.00).

### [BUG-FIN-04] Alta de tarjeta falla con error de propietario/sesión
- **Archivos**: `app/src/main/java/com/kipu/app/feature/accounts/presentation/AccountsViewModel.kt` y `AccountFormScreen.kt`
- **Problema**: Al dar de alta una nueva tarjeta de crédito, el formulario o ViewModel genera un ID de propietario provisional que no coincide con el `userId` de la sesión autenticada activa, provocando el rechazo: `"Credit card owner does not match the active session"`.
- **Acción**:
  1. Garantizar que el alta de tarjeta use el `userId` de la sesión activa obtenido del SessionManager / Repository.
  2. Añadir prueba unitaria de creación de tarjeta con sesión autenticada.

---

## BLOQUE 3: REDISEÑO UI/UX Y PRESENTACIÓN (P2/P3)

### [UX-01] Selector de Categorías con Progressive Disclosure (No chips planos)
- **Archivo**: `app/src/main/java/com/kipu/app/feature/movements/presentation/QuickMovementScreen.kt`
- **Problema**: El selector actual muestra categorías padre (`Suscripciones`) y categorías hijas (`HBO`, `Crunchyroll`) en una sola fila horizontal plana de chips, aplanando la jerarquía y causando confusión.
- **Acción (Aplicando `ui-ux-pro-max` + `transitions-dev` + `transitions-polish`)**:
  1. Implementar un selector jerárquico mediante Bottom Sheet modal con *progressive disclosure*:
     - **Estructura visual**: Muestra las categorías raíz (`parent_id == null`) con sus iconos y etiquetas claras, en áreas táctiles ergonómicas (mínimo 48dp de altura).
     - **Animación y Micro-interacciones**:
       - Chevron de rotación animada (0° a 180° mediante `animateFloatAsState` con `tween(durationMillis = 200, easing = FastOutSlowInEasing)`).
       - Despliegue de subcategorías con `AnimatedVisibility` utilizando `expandVertically` + `fadeIn` para la apertura, y `shrinkVertically` + `fadeOut` para el colapso (tokens asimétricos de `transitions-dev`).
       - Subcategorías indentadas visualmente, con fondo distintivo o pill sutil para diferenciar nivel jerárquico.
       - Soporte para `rememberReducedMotionEnabled()` (saltar animaciones si el usuario tiene reducción de movimiento activa).
     - **Selección flexible**: El usuario puede tocar directamente la categoría raíz para seleccionarla, o abrirla y tocar una subcategoría específica. Al seleccionar, el modal se cierra suavemente y muestra en el formulario la selección legible (ej. `Suscripciones > HBO` o simplemente `HBO`).
     - **Separación de Comercio**: El campo de comercio (Merchant) debe permanecer en su propio bloque visual independiente, sin mezclarse ni competir con las categorías.

### [UX-02 & UX-03] Etiquetas de Historial y Feedback en Transferencias
- **Archivos**: `app/src/main/java/com/kipu/app/feature/movements/presentation/MovementHistoryScreen.kt`
- **Acción**:
  1. En `MovementHistoryScreen.kt`, rotular los movimientos de pago de tarjeta (`CARD_PAYMENT`) explícitamente como `"Pago de tarjeta"`, manteniendo el subtítulo `Cuenta -> Tarjeta`.
  2. Al guardar una transferencia entre cuentas, emitir un Snackbar de confirmación inmediata y/o conmutar el filtro a `"Todos"` o `"Transferencia"` para que el movimiento sea visible de inmediato.
  3. En Dashboard, mostrar badge visual `"Archivada · Deuda pendiente"` en tarjetas de crédito archivadas que aún conservan pasivo.

---

## CRITERIO DE VERIFICACIÓN
- Ejecutar `./gradlew test` para asegurar que todas las pruebas unitarias pasen.
- Compilar la app con `./gradlew assembleDebug`.
- Documentar las soluciones en `docs/audits/CHECKLIST_FIXES_AUDITORIA.md`.
