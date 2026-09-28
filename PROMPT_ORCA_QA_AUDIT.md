# DIRECTIVA DE ORQUESTACIÓN: QA Y AUDITORÍA INTEGRAL DE PRODUCTO (KIPU)

> **Rol de Codex (Panel Izquierdo - Terminal `term_2791d51d-03e2-4aa1-a698-260c9faf85a2`)**: Orquestador Principal de QA y Auditoría Funcional/UX en runtime. Explora la app abierta en el dispositivo físico conectado (`Samsung SM-S926B`), ejecuta operaciones, captura valores antes/después, utiliza comandos de inspección en terminal (ADB y consultas a Room/SQLite) y registra la evidencia empírica.
> **Rol de AGY (Panel Derecho - Terminal `term_34398337-2535-4553-83d6-1929820100ec`)**: Auditor Independiente de Especificación y Dominio. **Sus conclusiones no son autoridad por sí mismas; deben estar respaldadas por HU, Gherkin, regla de negocio, arquitectura o Constitución.** Consulta la Bóveda de Documentación en `C:\Users\Alume\orca\KipuApp`. Puedes comunicarte con él usando `orca terminal send --terminal term_34398337-2535-4553-83d6-1929820100ec --text "..." --enter` y leerlo con `orca terminal read`.
> **Rama de trabajo**: `010-ep-not-notificaciones-avisos`
> **Bóveda de Documentación Oficial**: `C:\Users\Alume\orca\KipuApp`

---

## REGLAS FUNDAMENTALES Y DE SEGURIDAD
- **NO MODIFIQUES CÓDIGO TODAVÍA**: No hagas refactors, fixes ni commits en esta fase.
- El objetivo exclusivo es: **PROBAR $\rightarrow$ AUDITAR $\rightarrow$ CONTRASTAR CON DOCUMENTACIÓN $\rightarrow$ CONCILIAR MATEMÁTICAMENTE $\rightarrow$ EMITIR INFORME CONSOLIDADO**.
- **NO pruebes**: Registro, login/logout, recuperación de contraseña, compra de Premium, Google Play Billing ni pasarelas externas reales.
- La aplicación YA ESTÁ ABIERTA en el dispositivo conectado con sesión activa. Puedes usar comandos de inspección en terminal (ADB para capturas de pantalla/taps/dumps de jerarquía y consultas SQLite directas a Room para contrastar estado persistido vs UI).
- **AUTORIZACIÓN TOTAL SOBRE DATOS (MOCKUPS)**: Todas las cuentas, tarjetas y movimientos actuales en la aplicación son **datos mockup / de prueba**. Puedes borrar cuentas existentes, crear nuevas, archivarlas, modificarlas o recrear datos según necesites para evaluar flujos y límites. No hay riesgo de perder datos reales.
- **RUTAS DE HERRAMIENTAS EN TU TERMINAL**:
  - Si tu PowerShell no encuentra `orca` o `adb`, agrégalos al inicio del PATH de tu sesión ejecutando:
    `$env:PATH = "C:\Users\Alume\AppData\Local\Programs\orca\resources\bin;C:\Users\Alume\AppData\Local\Android\Sdk\platform-tools;" + $env:PATH`
  - Ejecutable Orca: `C:\Users\Alume\AppData\Local\Programs\orca\resources\bin\orca.cmd`
  - Ejecutable ADB: `C:\Users\Alume\AppData\Local\Android\Sdk\platform-tools\adb.exe`
  - ID del dispositivo físico conectado (Samsung Galaxy S24+): `R5CX10W7CZD`.

---

## 1. REGLA DE EVIDENCIA Y TAXONOMÍA POR SPRINTS

### Regla de Evidencia
- **Ningún hallazgo puede quedar como CONFIRMADO únicamente por inferencia de un agente.**
- **Runtime**: Captura + pasos exactos + valores antes/después cuando sea posible.
- **Documentación**: Archivo + HU/regla/criterio concreto en `C:\Users\Alume\orca\KipuApp`.
- **Discrepancias**: Si runtime y documentación discrepan, registrar la discrepancia con evidencia; no inventar cuál es el correcto.

### Taxonomía de Hallazgos (Fecha de auditoría: 28/09/2026)
*Contexto: S1 y S2 están oficialmente CERRADOS. Sprint 3 está ACTIVO (23/09 al 29/09). S4 a S10 son sprints futuros.*
- **BUG**: Comportamiento ya implementado que contradice una HU, regla de negocio o criterio de aceptación, independientemente del sprint.
- **NO_IMPLEMENTADO**: Capacidad comprometida en un sprint YA CERRADO (S1, S2) y ausente.
- **PENDIENTE_SPRINT_ACTIVO**: Capacidad de S3 todavía no implementada/completa a fecha de la auditoría. No clasificar automáticamente como bug mientras el sprint siga abierto.
- **FUERA_DEL_SPRINT**: Capacidad cuyo sprint de cierre es posterior al sprint actualmente activo (S4 a S10):
  - *S4*: Edición/borrado de movimientos.
  - *S5*: Deudas y compromisos (EP-DEU).
  - *S6*: Metas y recurrencias (EP-MET).
  - *S7*: Presupuestos e historial avanzado.
  - *S8*: Captura inteligente (OCR, notificaciones SMS/bancarias).
  - *S9*: Analítica financiera, reportes y conciliación Yape (HU-41).
  - *S10*: Cierre y endurecimiento de planes freemium.
  *(NO asumir que "Reportes" es S7; la analítica financiera HU-41 cierra en S9)*.
- **UX**: Funciona técnicamente pero viola heurísticas de Nielsen (flujo confuso, sobrecarga cognitiva, mala jerarquía, exceso de pasos).
- **INCOMPLETO**: La funcionalidad existe pero carece de validaciones límite, estados vacíos o manejo de errores.

---

## 2. ÁREAS CRÍTICAS DE AUDITORÍA Y CASOS TESTIGO

### A. Cuentas y Efectivo
- Creación de cuenta de efectivo, ahorros, etc.
- Edición de presentación/alias, institución, corrección de saldo inicial y archivado.
- Validar que el saldo de cuenta refleje con exactitud la suma matemática de sus movimientos derivados en el ledger de Room.

### B. Tarjetas de Crédito y Compras
- Verificar que una tarjeta de crédito **NO** sea tratada como dinero disponible ni aumente activos líquidos.
- Registrar compras con tarjeta de crédito: debe registrarse el gasto operativo y aumentar la deuda/pasivo sin descontar saldo de cuentas de ahorro/efectivo.
- Distribución de céntimos en compras en cuotas (ej. S/ 100 en 3 cuotas = 33.34 + 33.33 + 33.33).

### C. Pago de Tarjeta de Crédito (Foco Especial - Caso Testigo Detectado)
- Auditar minuciosamente el formulario "Pagar Tarjeta de Crédito":
  - **Caso Testigo A**: Al ingresar un monto válido (ej. `4,000.00`), verificar si el formulario dispara erróneamente en rojo *"Ingresa un monto mayor a cero"* y si bloquea el botón Confirmar Pago.
  - Verificar pagos parciales y totales.
  - Verificar que el pago disminuya la deuda y reduzca el saldo de la cuenta bancaria de origen **SIN generar un doble gasto operativo** en el historial/presupuesto (debe computar como amortización de pasivo).

### D. Transferencias entre Cuentas Propias
- Transferencia entre dos cuentas: Cuenta A resta X, Cuenta B suma X.
- **Invariante estricta**: NO debe computar como gasto en A ni como ingreso en B, ni alterar las métricas de flujo neto general.
- Probar transferencias con saldo insuficiente, misma cuenta como origen/destino, valores límite y doble toque.

### E. Categorías y Subcategorías (Foco Especial UX - Evaluación Objetiva)
- **No asumir automáticamente que el Bottom Sheet anidado es la solución final.**
- **Primero documentar objetivamente los problemas del selector actual**:
  - Jerarquía raíz/subcategoría: el modelo de Kipu establece una jerarquía de máximo dos niveles mediante `parent_id` (raíz $\rightarrow$ subcategoría). Evaluar si en la UI actual categorías padre (ej. `Suscripciones`) y subcategorías (ej. `HBO`, `Crunchyroll`) aparecen mezcladas horizontalmente en chips planos sin distinción jerárquica.
  - Carga cognitiva y escaneabilidad.
  - Número de acciones necesarias para elegir.
  - Relación categoría/comercio: verificar si comercios y categorías se están mezclando visual o conceptualmente.
  - Espacio de pantalla utilizado y propensión a error.
- **Después comparar contra el patrón de referencia** (Bottom Sheet modal + progressive disclosure con chevrons que expanden subcategorías debajo) y determinar qué problemas concretos resuelve.
- *No copiar la estética de la app de referencia; evaluar únicamente el patrón de interacción.*

### F. Historial y Formato
- Agrupación por fechas (Hoy, Ayer, anteriores).
- Búsqueda y filtros funcionales (Todos, Gasto, Ingreso, Transferencia).
- **Formato monetario**: verificar consistencia de signos (ej. `S/ -4,000.00` vs convención bancaria consistente `-S/ 4,000.00`).
- **Estados de sincronización**: auditar el significado y feedback de los íconos de advertencia o exclamación roja en movimientos (ej. caso testigo en `HBO / Sueldo`).

---

## 3. CONCILIACIÓN FINANCIERA ESTRICTA (UI vs ROOM/SQLITE LEDGER)

> **Invariante de Arquitectura**: Las cuentas no mantienen un saldo mutable arbitrario en almacenamiento; el saldo debe derivar de las entradas del ledger (`internal.ledger_entries` / transacciones).

Para **TODA** operación financiera probada, registrar obligatoriamente:
1. **Estado antes**:
   - Saldo cuenta origen
   - Saldo cuenta destino (si aplica)
   - Deuda tarjeta (si aplica)
   - Gasto acumulado (si puede observarse)
2. **Acción ejecutada** (monto, tipo, instrumento).
3. **Estado después**.
4. **Resultado matemático esperado**.
5. **Diferencia esperado vs real**.
6. **Contraste UI vs Room/SQLite**:
   - Una discrepancia de visualización es un hallazgo de **UI**.
   - Una discrepancia persistida en el ledger es **DATA / CONSISTENCIA FINANCIERA** y un potencial **P0**.

---

## 4. PROTOCOLO DE REVISIÓN CRUZADA (CODEX $\leftrightarrow$ AGY)

1. **Codex**: Ejecuta las pruebas en el dispositivo físico, toma capturas de valores antes y después, e inspecciona la base de datos Room.
2. **Consulta a AGY**: Codex envía sus observaciones a AGY en el panel derecho.
3. **AGY**:
   - Contrasta los hallazgos contra las especificaciones en `C:\Users\Alume\orca\KipuApp`.
   - Verifica si los comportamientos reportados violan la Constitución o las HUs de S1/S2/S3.
   - Señala inconsistencias o bugs que Codex pudo haber omitido.
4. **Confrontación con Evidencia**:
   - Codex cuestiona interpretaciones teóricas de AGY con evidencia empírica de runtime.
   - AGY cuestiona clasificaciones erróneas de Codex usando los criterios Gherkin y contratos de datos.
   - Ambos resuelven discrepancias con evidencia y consensúan la severidad (P0, P1, P2, P3).

---

## 5. INFORME FINAL CONSOLIDADO

Generar obligatoriamente el archivo:
`docs/audits/KIPU_FULL_FUNCTIONAL_UX_AUDIT.md`

Estructura obligatoria:
1. Resumen ejecutivo y diagnóstico general.
2. Matriz de hallazgos por severidad:
   - **P0**: Errores que corrompen dinero, duplican movimientos, calculan mal saldos o rompen la app.
   - **P1**: Funcionalidades centrales que no funcionan.
   - **P2**: Flujos incompletos, validaciones incorrectas (ej. bug de pago de tarjeta) y problemas UX fuertes.
   - **P3**: UI, consistencia visual y mejoras menores.
3. Tabla de conciliación contable de las pruebas realizadas (con valores antes/después y contraste con Room).
4. Evaluación objetiva del selector de categorías (análisis del problema actual vs patrón progressive disclosure).
5. Discrepancias resueltas entre Codex y AGY.
6. Backlog de correcciones priorizado para la siguiente fase.

Cada hallazgo debe estructurarse con:
`ID | Severidad | Tipo | Pantalla | HU | Pasos para reproducir | Resultado observado | Resultado esperado | Impacto | Evidencia | Recomendación`.

---

## 6. REGLA DE CIERRE DE AUDITORÍA

**No terminar cuando se haya recorrido la UI.**

La auditoría termina **ÚNICAMENTE** cuando:
1. Todas las capacidades correspondientes a S1 y S2 hayan sido clasificadas;
2. Las capacidades disponibles de S3 hayan sido verificadas;
3. Todas las operaciones financieras ejecutadas hayan sido conciliadas matemáticamente;
4. Todos los P0/P1 hayan sido reproducidos por segunda vez;
5. Codex y AGY hayan realizado la revisión cruzada con confrontación de evidencia;
6. `docs/audits/KIPU_FULL_FUNCTIONAL_UX_AUDIT.md` exista y contenga evidencia suficiente para que otro desarrollador pueda reproducir cada P0/P1 sin contexto adicional;
7. **No se haya modificado ningún archivo de código de la aplicación.**
