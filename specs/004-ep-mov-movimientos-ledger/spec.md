# Feature Specification: EP-MOV - Movimientos y Ledger

**Feature Branch**: `004-ep-mov-movimientos-ledger`

**Created**: 2026-09-22

**Status**: Refined

**Refined**: 2026-10-02 — Remediación T093/F04: representación inequívoca y versionada para altas nuevas, validación remota y compatibilidad inmutable de outbox S2; no cambia el alcance funcional ni los puntos del Sprint.

**Refined**: 2026-10-02 — Incremento Sprint 4 aprobado: HU-20/HU-21/HU-22, mantenimiento de movimientos estándar, VOIDED y compensación auditable sin DELETE físico; decisiones P1–P5 y dependencia de acceso EP-PLA. Se conserva el incremento S2 y HU-25 en S7.

**Refined**: 2026-10-02 — Correcciones del análisis aprobadas: consumo por periodo verificable sobre revisión vigente, corte temporal exacto y continuidad segura tras reinicio; contexto explícito EP-MOV y serialización de pruebas compartidas en tasks.md.

**Input**: Especificar funcionalmente EP-MOV completa, con HU-18 a HU-25; Sprint 2 entrega registro manual, saldos atómicos y detección de duplicados, y los sprints 4, 6 y 7 amplían correcciones, consulta, obligaciones, metas y reembolsos sin crear una segunda verdad financiera.

## Control de la Épica

| Campo | Valor |
| :--- | :--- |
| Épica | EP-MOV - Movimientos y Ledger |
| Responsable | Integrante 4 - Desarrollo de reglas financieras |
| Historias | HU-18 a HU-25 |
| Puntos totales | 52 |
| Incrementos | S2: 21 pts; S4: 15 pts; S6: 8 pts; S7: 8 pts |
| Prioridad | Alta |

## Objetivo y Alcance

### Objetivo de Negocio

Permitir que una persona registre, consulte y corrija hechos financieros con una única verdad contable, aun sin conexión. Cada operación debe producir efectos completos, auditables e idempotentes sobre saldos y métricas, sin duplicar dinero, gasto o ingreso.

### Alcance Implementable del Sprint 2

- HU-18: registrar manualmente Gasto, Ingreso y Transferencia con referencias propias disponibles; la categoría es obligatoria para Gasto y opcional para Ingreso/Transferencia en Sprint 2.
- HU-19: reflejar saldos inmediatamente y confirmar de forma indivisible el movimiento, sus efectos y su envío pendiente.
- HU-23: impedir el doble efecto del mismo comando y advertir coincidencias sin confundir dos compras reales iguales.
- Integrar los límites Free aplicables sin modificar ni ocultar la verdad financiera ya registrada.
- Conservar el movimiento confirmado después de reiniciar y sincronizarlo mediante reintentos sin duplicación.
- Entregar la Pantalla 11 funcional para los tres tipos básicos y una Pantalla 10 mínima que permita comprobar el alta y su estado de sincronización.
- Diferir edición, anulación, reembolso, filtros avanzados y acciones contextuales hasta las historias y sprints que los definen.

### Evolución Planificada

- **Sprint 4**: HU-20 edición auditable, HU-21 anulación lógica y HU-22 búsqueda/filtros.
- **Sprint 6**: HU-24 aplicación de movimientos a deudas, préstamos y metas.
- **Sprint 7**: HU-25 reembolsos parciales o totales vinculados al movimiento original.

Las capacidades futuras quedan especificadas para preservar contratos e invariantes, pero no se consideran entregadas antes de su sprint de cierre.

### Alcance Implementable del Sprint 4

- HU-20 (5 puntos): corregir importe, fecha, cuentas, categoría, comercio y nota de movimientos estándar propios con revisión conocida; conservar la versión anterior y aplicar compensaciones atómicas.
- HU-21 (5 puntos): anular movimientos estándar mediante revisión `VOIDED`, contrapartidas y conservación de historia; revertir juntas ambas cuentas de una transferencia.
- HU-22 (5 puntos): historial completo, texto, fechas y tipos Gasto/Ingreso/Transferencia disponibles en Free; filtros avanzados por cuenta, tarjeta, categoría, comercio, monto, fuente y estado autorizados mediante EP-PLA.
- EP-MOV aporta 15 puntos al Sprint 4; HU-58/HU-59 aportan 16 puntos desde EP-PLA. El compromiso total se mantiene en 31 puntos.
- La edición y anulación genéricas se limitan a operaciones estándar. Compras/pagos de tarjeta, movimientos con cuotas, dependencias de deuda u otras relaciones especializadas conservan lectura e historia y muestran una advertencia amigable sin mutación genérica.
- Toda confirmación local persiste revisión, efectos, proyecciones, recibo y outbox como una unidad; la reconciliación remota aplica revisión esperada e idempotencia y presenta conflictos sin mezclar importes.
- HU-24 permanece en Sprint 6 y HU-25 en Sprint 7. No se incorpora devolución parcial a la implementación ni a la demo S4.

### Clarificaciones Aprobadas del Sprint 4 (2026-10-02)

| Decisión | Resolución aprobada | Aplicación en EP-MOV |
| :--- | :--- | :--- |
| P1 — Demo y reembolsos | Corregir el guion S4; HU-25 sigue en S7 y se conservan 31 puntos. | Demo de corrección, anulación, filtros y reconexión; sin devolución parcial. La discrepancia con el cronograma del vault queda identificada para su actualización por el responsable. |
| P2 — Operaciones especializadas | Solo editar/anular movimientos estándar Gasto, Ingreso y Transferencia. Cuotas/deudas bloquean la edición genérica con advertencia amigable. | Guardar la restricción en dominio y UI; no tratar compra/pago de tarjeta como estándar por su tipo principal. |
| P3 — Concesión offline | Concesión válida antes del menor límite entre validación +72 h y vencimiento conocido; al alcanzar el límite (incluidas exactamente 72 h), suspender capacidades Premium hasta reconexión. Free y registro local permanecen operativos. | Consumir HU-58/HU-59 sin emitir concesiones desde EP-MOV. Si tras un reinicio no puede demostrarse vigencia con evidencia temporal confiable, requerir revalidación Premium; nunca reiniciar la ventana desde reloj local, backup o reinstalación. |
| P4 — Línea base | Se acepta el main consolidado con cierre de auditoría DB S3. | Baseline `c80ea0ddea80dfe5332971f12418758ea1bc9923`, coincidente con origin/main al crear la rama; Room v16. La aceptación no sustituye evidencia nueva de pruebas/proveedor. |
| P5 — Rechazos financieros | Cero DELETE físico; rechazo o anulación con VOIDED y compensación contable auditable. | Si existen efectos confirmados localmente, conservar evidencia y compensar una sola vez. Una solicitud rechazada antes del commit no crea un movimiento ni asientos ficticios. Un conflicto de edición no anula el hecho vigente ni destruye la propuesta. |

### Guion de Demo del Sprint 4 Refinado

Corregir un gasto de S/20 a S/15 y mostrar revisión y recuperación de S/5; anular una transferencia y mostrar ambas contrapartidas; mostrar búsqueda antigua/filtros básicos Free y rechazo de consulta avanzada no autorizada; caducar concesión Premium, guardar un gasto manual offline y reconectar sin duplicados; demostrar al menos un conflicto y su recuperación explícita. HU-25 se demuestra en S7.

### Fuera de Alcance

- Mover dinero en bancos, ejecutar pagos reales u otorgar crédito.
- Crear un cuarto tipo principal de movimiento denominado Aporte.
- Importar historial bancario universal o leer correos/SMS bancarios no aprobados.
- Autorregistrar una compra con tarjeta de crédito sin confirmación explícita.
- Usar límites de crédito o reservas de metas como activos adicionales.
- Borrar físicamente movimientos con historia financiera.
- Convertir intención de plan, compra pendiente o estado editable por el cliente en autorización Premium.
- Implementar anticipadamente HU-20 a HU-25 dentro del incremento de Sprint 2.

### Dependencias y Trazabilidad de Entrega

| HU | Sprint | Bloqueantes | Parciales | Relacionadas |
| :--- | :---: | :--- | :--- | :--- |
| HU-18 Registro manual | S2 | HU-07, HU-14 | HU-15 | HU-19, HU-23 |
| HU-19 Saldos y atomicidad | S2 | HU-18 | Ninguna | HU-20, HU-21, HU-25 |
| HU-20 Edición | S4 | HU-18, HU-19 | Ninguna | HU-21, HU-22 |
| HU-21 Anulación | S4 | HU-18, HU-19 | Ninguna | HU-20, HU-25 |
| HU-22 Búsqueda y filtros | S4 | HU-18 | Ninguna | HU-39, HU-41 |
| HU-23 Duplicados | S2 | HU-18 | Ninguna | HU-46, HU-49 |
| HU-24 Obligaciones y metas | S6 | HU-18, HU-19, HU-26, HU-27, HU-30, HU-31 | Ninguna | HU-28, HU-32 |
| HU-25 Reembolsos | S7 | HU-18, HU-19, HU-36 | Ninguna | HU-37, HU-41 |

### Precondiciones Generales

- La persona mantiene una sesión propia válida y dispone localmente de sus referencias autorizadas.
- HU-07 aporta las cuentas e instrumentos y HU-14 las categorías requeridas por HU-18.
- Cada incremento posterior inicia únicamente cuando sus dependencias bloqueantes están aceptadas.
- Los criterios de acceso por plan están disponibles, pero nunca cambian el valor contable de operaciones válidas existentes.
- La línea base de S4 es main aceptado por P4. HU-58/HU-59 deben integrarse desde EP-PLA antes de aceptar los escenarios de filtros avanzados, caducidad y reconexión; no se sustituye su autorización por un booleano local.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - HU-18 Registrar movimientos manuales (Priority: P1)

Como usuario de Kipu, quiero registrar gastos, ingresos y transferencias para mantener mis finanzas actualizadas incluso sin internet.

**Why this priority**: Es la entrada manual básica del producto y desbloquea saldos, crédito, obligaciones, metas, presupuestos, reportes y automatización.

**Independent Test**: Con dos cuentas propias y una categoría disponibles, registrar cada uno de los tres tipos y comprobar su efecto después de cerrar y reabrir la aplicación.

**Acceptance Scenarios**:

1. **Given** una cuenta con S/100, **When** se guarda un gasto de S/20 con categoría, **Then** aparece el gasto y quedan S/80.
2. **Given** una cuenta con S/100, **When** se registra un ingreso operativo de S/30, **Then** el saldo queda en S/130 y los ingresos aumentan S/30.
3. **Given** dos cuentas propias de la misma moneda, **When** se transfieren S/40, **Then** el origen baja y el destino sube S/40 sin crear ingreso ni gasto.
4. **Given** un monto inválido o la ausencia de instrumento, **When** se intenta guardar, **Then** se identifican los campos incorrectos y no se crea movimiento.
5. **Given** referencias existentes localmente y ausencia de conexión, **When** se registra y confirma un movimiento y luego se reinicia, **Then** el movimiento y sus efectos completos siguen disponibles.

---

### User Story 2 - HU-19 Ver saldos inmediatos y completos (Priority: P1)

Como usuario de Kipu, quiero ver saldos actualizados al confirmar para confiar en mis cifras sin esperar la red.

**Why this priority**: Ninguna capacidad financiera posterior es confiable si una operación puede dejar efectos parciales o depender de conectividad para verse reflejada.

**Independent Test**: Confirmar gastos y transferencias con fallos simulados en distintos puntos y verificar que el resultado sea siempre completo o inexistente.

**Acceptance Scenarios**:

1. **Given** una cuenta con S/50, **When** se confirma un gasto de S/10, **Then** se muestran S/40 inmediatamente.
2. **Given** una transferencia que requiere efectos en dos cuentas, **When** ocurre una interrupción antes de confirmar, **Then** ninguna cuenta queda modificada parcialmente.
3. **Given** una operación confirmada, **When** el proceso termina y se reabre, **Then** el movimiento y todos sus efectos permanecen completos.
4. **Given** diez operaciones de S/0.10, **When** se consulta su suma, **Then** el total es exactamente S/1.00.

---

### User Story 3 - HU-23 Evitar duplicados sin perder compras reales (Priority: P1)

Como usuario de Kipu, quiero impedir repeticiones accidentales y recibir advertencias por coincidencias para no descontar dinero dos veces ni perder operaciones legítimas.

**Why this priority**: Los reintentos, el doble toque y la futura captura automática convierten la deduplicación en una condición de integridad, no en una mejora opcional.

**Independent Test**: Reenviar el mismo comando, enviar uno similar y registrar dos compras reales iguales con identidades distintas.

**Acceptance Scenarios**:

1. **Given** un comando previamente guardado, **When** se reenvía con la misma identidad y contenido, **Then** se devuelve su resultado previo sin crear otro efecto.
2. **Given** una compra cercana con importe y referencias similares, **When** se intenta guardar otra, **Then** se advierte la coincidencia antes de confirmar.
3. **Given** dos compras reales del mismo monto, **When** se confirma que son distintas, **Then** ambas se conservan con identidades diferentes.
4. **Given** una cuenta en modalidad Free, **When** se reenvía un comando confirmado, **Then** la misma protección evita duplicados.

---

### User Story 4 - HU-20 Corregir movimientos con trazabilidad (Priority: P2)

Como usuario de Kipu, quiero corregir mis movimientos para mantener saldos, presupuestos e historia coherentes.

**Why this priority**: Las personas cometen errores de importe, fecha o clasificación, pero corregirlos no puede reescribir silenciosamente la historia.

**Independent Test**: Corregir importe y fecha de un gasto y comprobar la diferencia neta, los periodos afectados y la conservación de la revisión anterior.

**Acceptance Scenarios**:

1. **Given** un gasto de S/20 que debió ser S/15, **When** se confirma la corrección, **Then** la cuenta recupera S/5 y el gasto neto queda en S/15.
2. **Given** un gasto ubicado en el periodo equivocado, **When** se corrige la fecha, **Then** se recalculan los consumos de ambos periodos.
3. **Given** una revisión modificada por otro dispositivo, **When** se envía una edición incompatible, **Then** se presenta un conflicto sin mezclar importes.
4. **Given** una categoría histórica bloqueada por plan, **When** solo se corrige la nota, **Then** se conserva esa relación sin habilitar nuevas operaciones sobre el objeto bloqueado.
5. **Given** una operación con tarjeta, cuotas o dependencias de deuda, **When** se solicita edición genérica, **Then** se explica la restricción sin crear revisión, efectos ni outbox.
6. **Given** una corrección estándar confirmada offline, **When** se cierra y reinicia la aplicación, **Then** la revisión, sus efectos y el comando pendiente permanecen completos y se sincronizan sin duplicación.
7. **Given** un único gasto de S/20 en el periodo A y ninguno en B, **When** se cambia su fecha efectiva de A a B, **Then** el gasto confirmado calculado queda en S/0 para A y S/20 para B, sin cambiar el saldo ni contar revisiones antiguas; si se anula, ambos periodos quedan en S/0. Los intervalos y la zona de referencia se fijan en el escenario.

---

### User Story 5 - HU-21 Anular sin destruir historia (Priority: P2)

Como usuario de Kipu, quiero anular movimientos equivocados para corregir mis cifras sin romper relaciones ni borrar evidencia financiera.

**Why this priority**: La reversión explícita protege saldos y auditoría frente a errores y dispositivos atrasados.

**Independent Test**: Anular un gasto y una transferencia, repetir el comando y enviar después una edición antigua.

**Acceptance Scenarios**:

1. **Given** un gasto erróneo de S/20, **When** se confirma su anulación, **Then** se revierte el efecto y se excluye de gastos operativos.
2. **Given** una solicitud de confirmación de anulación, **When** se cancela la acción, **Then** el movimiento no cambia.
3. **Given** una transferencia entre dos cuentas, **When** se anula, **Then** ambos efectos se revierten juntos.
4. **Given** un movimiento ya anulado, **When** llega una edición anterior, **Then** el registro no se reactiva silenciosamente.
5. **Given** una anulación ya aplicada, **When** se reenvía el mismo comando, **Then** se devuelve el resultado previo sin una segunda compensación.
6. **Given** una operación especializada, **When** se solicita anulación genérica, **Then** se muestra una advertencia y se conserva la operación sin modificar relaciones.
7. **Given** una operación con efectos locales confirmados recibe rechazo financiero permanente, **When** se reconcilia el rechazo, **Then** se conserva su evidencia, se registra VOIDED y se compensan solo sus efectos aplicados sin DELETE físico ni duplicación por reintento.

---

### User Story 6 - HU-22 Encontrar movimientos (Priority: P2)

Como usuario de Kipu, quiero buscar y filtrar movimientos para revisar mi historia y analizar operaciones concretas.

**Why this priority**: La historia financiera pierde utilidad si una persona no puede localizar una operación, pero los filtros avanzados pueden respetar la diferenciación comercial aprobada.

**Independent Test**: Buscar texto en historial antiguo con Free, combinar filtros con Premium y abrir un enlace restringido con Free.

**Acceptance Scenarios**:

1. **Given** un movimiento antiguo de Tambo y modalidad Free, **When** se busca “Tambo”, **Then** se encuentra aunque no pertenezca al mes actual.
2. **Given** una autorización Premium vigente y gastos en distintas cuentas, **When** se combinan cuenta, categoría y rango, **Then** solo se muestran los movimientos que cumplen todas las condiciones.
3. **Given** una consulta sin coincidencias, **When** se aplican filtros, **Then** se muestra un estado vacío con opción de limpiarlos.
4. **Given** modalidad Free y un enlace con filtros avanzados, **When** se intenta ejecutar, **Then** se explica la restricción y se mantiene disponible el historial básico.
5. **Given** una concesión Premium caducada y datos locales, **When** se aplica una consulta avanzada, **Then** se exige revalidación sin ejecutar la consulta protegida y siguen disponibles historial básico y registro manual.
6. **Given** referencias archivadas o movimientos VOIDED, **When** se consulta el historial propio, **Then** siguen legibles con su estado y no reaparecen efectos financieros anulados.
7. **Given** una concesión con límite conocido, **When** se alcanza exactamente el límite o tras reiniciar no existe evidencia temporal confiable, **Then** no se ejecuta consulta avanzada hasta revalidar y se conservan lectura básica y registro manual offline.

---

### User Story 7 - HU-24 Aplicar movimientos a obligaciones y metas (Priority: P3)

Como usuario de Kipu, quiero vincular pagos, cobros y transferencias con mis obligaciones y metas para reflejar el hecho económico sin duplicar gasto, ingreso o patrimonio.

**Why this priority**: Amplía el ledger único a planificación y obligaciones después de que sus módulos base estén disponibles.

**Independent Test**: Aplicar un pago a deuda, un cobro a préstamo y un aporte a meta, verificando saldos y clasificaciones.

**Acceptance Scenarios**:

1. **Given** una deuda con S/100 de principal pendiente, **When** se aplican S/30 desde Gasto al principal, **Then** quedan S/70 sin aumentar el gasto operativo.
2. **Given** S/100 por cobrar, **When** se aplican S/20 desde Ingreso al préstamo, **Then** la cuenta aumenta S/20 y lo pendiente baja a S/80 sin aumentar el ingreso operativo.
3. **Given** una meta y S/200 libres, **When** se transfieren S/50 hacia Cuenta Metas y se elige el objetivo, **Then** se reservan S/50 sin crear gasto ni un activo adicional.
4. **Given** una meta sin cupo activo, **When** se intenta aportar, **Then** se explica cómo seleccionar cupo y no se guarda ningún efecto parcial.

---

### User Story 8 - HU-25 Registrar reembolsos vinculados (Priority: P3)

Como usuario de Kipu, quiero registrar devoluciones totales o parciales para corregir el gasto original sin crear ingresos falsos.

**Why this priority**: Completa el ciclo del gasto cuando ya existe una base presupuestaria capaz de corregir el periodo original.

**Independent Test**: Aplicar reembolsos parcial, total, excesivo y entre periodos sobre una compra propia.

**Acceptance Scenarios**:

1. **Given** una compra original de S/100, **When** se registra una devolución de S/40, **Then** el gasto neto queda en S/60 y la cuenta recupera S/40.
2. **Given** una compra de S/100 sin devoluciones, **When** se reembolsa el total, **Then** el gasto neto queda en cero.
3. **Given** una compra de S/100 con S/40 ya reembolsados, **When** se intentan devolver S/70, **Then** se rechaza porque solo quedan S/60 reembolsables.
4. **Given** una compra de septiembre y un reembolso de octubre, **When** se registra la devolución, **Then** la caja cambia en octubre y el gasto y presupuesto originales se ajustan explicando ambas fechas.

### Edge Cases

- El importe es cero, negativo, excede el rango monetario admitido o contiene más decimales que la moneda.
- La cuenta, categoría, comercio, obligación, meta o presupuesto pertenece a otro usuario, está eliminado o no admite nuevas operaciones.
- Una transferencia usa la misma cuenta en ambos extremos o cuentas de monedas diferentes.
- El proceso se interrumpe entre la validación y la confirmación local, o entre la confirmación local y la sincronización.
- Un comando se reintenta con la misma identidad pero con contenido diferente.
- Dos operaciones legítimas tienen mismo importe, comercio y fecha aproximada.
- Llega una edición antigua después de una corrección o anulación confirmada.
- Un reembolso parcial se repite, excede el remanente o llega desordenado desde otro dispositivo.
- Una relación histórica queda bloqueada por el plan después de registrar el movimiento.
- Una búsqueda combina filtros sin resultados o intenta usar filtros avanzados después de perder Premium.

## Requirements *(mandatory)*

### Reglas de Negocio

- **RN-MOV-001**: Toda cantidad monetaria autoritativa se expresa en unidades menores enteras y con moneda identificada; no se admite aritmética de coma flotante.
- **RN-MOV-002**: Solo Gasto, Ingreso y Transferencia son tipos principales de movimiento.
- **RN-MOV-003**: Todo cambio de saldo nace de una operación financiera explícita y auditable.
- **RN-MOV-004**: Una transferencia mueve valor entre cuentas propias y no crea ingreso ni gasto.
- **RN-MOV-005**: Evento, efectos financieros, relaciones y envío pendiente se confirman como una unidad indivisible.
- **RN-MOV-006**: Cada comando tiene identidad estable; un reintento idéntico devuelve el resultado previo sin repetir efectos.
- **RN-MOV-007**: Similitud no equivale a identidad; una advertencia no elimina automáticamente una compra legítima.
- **RN-MOV-008**: Correcciones y anulaciones agregan evidencia auditable; no reescriben saldos ni eliminan historia silenciosamente.
- **RN-MOV-009**: Un movimiento anulado no participa en saldos, presupuestos ni métricas.
- **RN-MOV-010**: Principal de deuda o préstamo, aportes a metas y pagos de tarjeta no se reclasifican como ingreso o gasto operativo.
- **RN-MOV-011**: Un reembolso permanece vinculado al movimiento original, nunca excede su saldo reembolsable y no crea ingreso operativo.
- **RN-MOV-012**: La pérdida de Premium o el exceso de cupo preserva movimientos, relaciones y efectos financieros válidos.
- **RN-MOV-013**: Solo el propietario puede leer o modificar un movimiento y todas sus relaciones deben pertenecer al mismo propietario.

### Functional Requirements

- **FR-001**: Kipu MUST permitir registrar manualmente Gasto, Ingreso y Transferencia cuando las referencias obligatorias sean válidas.
- **FR-002**: Kipu MUST exigir importe positivo, moneda e instrumento propio; la categoría MUST ser obligatoria cuando la regla del tipo de movimiento la requiera.
- **FR-003**: Kipu MUST impedir la confirmación cuando una referencia no exista, no esté disponible o pertenezca a otro usuario.
- **FR-004**: Kipu MUST calcular el saldo exclusivamente a partir de efectos financieros confirmados y auditables.
- **FR-005**: Kipu MUST mostrar el efecto local confirmado sin esperar conectividad.
- **FR-006**: Kipu MUST garantizar que una operación con múltiples efectos sea aplicada por completo o no sea aplicada.
- **FR-007**: Kipu MUST conservar una operación confirmada y su envío pendiente tras el cierre inesperado o reinicio.
- **FR-008**: Kipu MUST distinguir un reintento idéntico, un conflicto de identidad con contenido diferente y una coincidencia solo probable. La representación canónica de comandos nuevos MUST distinguir delimitadores dentro de textos, null y cadena vacía, preservando Unicode. Una evolución versionada MUST conservar sin reinterpretación los bytes/hash de comandos S2 ya encolados y permitir su replay compatible; el servidor valida el hash del contrato nuevo.
- **FR-009**: Kipu MUST evitar efectos adicionales ante doble toque, reenvío, reordenamiento o respuesta de red perdida.
- **FR-010**: Kipu MUST permitir confirmar dos operaciones similares como hechos distintos cuando poseen identidades diferentes.
- **FR-011**: Kipu MUST registrar cada corrección como una nueva revisión enlazada a la versión previa.
- **FR-012**: Kipu MUST recalcular todos los efectos dependientes cuando cambien importe, fecha, cuenta, categoría o clasificación. En S4 el consumo base por periodo MUST calcularse sobre el payload vigente de gastos confirmados/revisados, por propietario, moneda e intervalo definido, contando cada movimiento una vez y excluyendo VOIDED. Una corrección de fecha MUST retirar el gasto del periodo anterior y atribuirlo al nuevo; los módulos presupuestarios futuros consumen este cálculo sin implementar su administración en S4.
- **FR-013**: Kipu MUST rechazar o presentar para resolución una edición basada en una revisión incompatible; nunca MUST mezclar importes silenciosamente.
- **FR-014**: Kipu MUST anular lógicamente un movimiento y revertir juntos todos sus efectos relacionados.
- **FR-015**: Kipu MUST impedir que una actualización atrasada reactive silenciosamente un movimiento anulado.
- **FR-016**: Kipu MUST ofrecer a todas las personas historial completo, búsqueda textual, rango de fechas y filtro por Gasto/Ingreso/Transferencia.
- **FR-017**: Kipu MUST limitar la combinación avanzada de cuenta, tarjeta, categoría, comercio, monto, fuente y estado a una autorización Premium verificada.
- **FR-018**: Kipu MUST volver a evaluar el acceso al abrir consultas o enlaces directos con filtros avanzados.
- **FR-019**: Kipu MUST mantener la protección de integridad y deduplicación en Free y Premium.
- **FR-020**: Kipu MUST vincular pagos de principal, cobros y aportes con su obligación o meta dentro de la misma confirmación financiera.
- **FR-021**: Kipu MUST representar un aporte a meta mediante Transferencia hacia la reserva seleccionada, sin crear un cuarto tipo principal ni duplicar activos.
- **FR-022**: Kipu MUST impedir nuevas aplicaciones sobre un objeto sin cupo activo, conservando sus relaciones históricas y sus efectos existentes.
- **FR-023**: Kipu MUST permitir reembolsos parciales y totales únicamente contra una compra propia confirmada.
- **FR-024**: Kipu MUST calcular el saldo reembolsable descontando todas las devoluciones válidas previas.
- **FR-025**: Kipu MUST aplicar el cambio de caja en la fecha real del reembolso y corregir gasto y presupuesto en el periodo de la compra original.
- **FR-026**: Kipu MUST sincronizar operaciones confirmadas mediante reintentos seguros y comunicar conflictos que puedan cambiar la verdad financiera.
- **FR-027**: Kipu MUST mantener disponibles consulta y registro manual para una persona previamente autenticada cuando no haya conexión.
- **FR-028**: Kipu MUST excluir de registros y mensajes diagnósticos los datos financieros sensibles que no sean necesarios para resolver el fallo.
- **FR-029**: En S4 Kipu MUST limitar edición y anulación genéricas a operaciones estándar; una operación especializada MUST conservarse sin cambios y mostrar una advertencia amigable.
- **FR-030**: Kipu MUST conservar los asientos anteriores y agregar compensaciones auditables para anulación o rechazo de efectos locales confirmados; MUST NOT usar DELETE físico. Rechazar antes de confirmar no crea efectos; un conflicto no anula automáticamente el movimiento vigente.
- **FR-031**: Kipu MUST confirmar atómicamente revisión, compensaciones, proyecciones, recibo y outbox, comprobando la revisión esperada y preservando VOIDED frente a sincronización atrasada.
- **FR-032**: EP-MOV MUST revalidar la autorización de HU-58/HU-59 antes de ejecutar filtros avanzados, incluidos enlaces y paginación. La autorización offline solo es válida mientras el tiempo confiable sea estrictamente anterior al menor límite conocido; igualdad implica caducidad. Si un reinicio impide demostrar vigencia, MUST exigir revalidación para Premium. La caducidad MUST preservar Free y registro local y MUST NOT prolongarse por manipulación de reloj.

### Requisitos Transversales de Aceptación

- Cada historia se acepta solo cuando sus bloqueantes están completos y sus escenarios oficiales pasan.
- Las reglas financieras deben verificarse independientemente de interfaz, conectividad y proveedor remoto.
- Deben probarse persistencia, reinicio, reintento, conflicto, aislamiento por propietario y ausencia de efectos parciales.
- Cualquier cambio de entidad, relación, restricción, aislamiento o sincronización debe mantener alineados la especificación, el modelo de datos, las pruebas y la trazabilidad.
- Una interfaz terminada sin invariantes financieras verificadas no demuestra que la historia esté completa.

### Matriz de Trazabilidad

| Historia | Requisitos principales | Resultado verificable |
| :--- | :--- | :--- |
| HU-18 | FR-001 a FR-005, FR-007, FR-027 | Registro manual local de tres tipos con efectos correctos |
| HU-19 | FR-004 a FR-007, FR-026 | Saldos inmediatos y ausencia de medias operaciones |
| HU-20 | FR-011 a FR-013, FR-029, FR-031 | Correcciones estándar auditables y conflictos explícitos |
| HU-21 | FR-014, FR-015, FR-029 a FR-031 | VOIDED y compensación completa sin DELETE ni resurrección |
| HU-22 | FR-016 a FR-018, FR-032 | Historial básico permanente y filtros avanzados autorizados |
| HU-23 | FR-008 a FR-010, FR-019 | Reintentos sin duplicación y similitudes confirmables |
| HU-24 | FR-020 a FR-022 | Principal, cobros y reservas sin clasificación doble |
| HU-25 | FR-023 a FR-025 | Reembolso limitado, vinculado y conciliado entre periodos |

### Key Entities *(include if feature involves data)*

- **Movimiento financiero**: Hecho registrado por una persona; identifica propietario, tipo, naturaleza económica, importe, moneda, fecha, referencias opcionales, estado y revisión vigente.
- **Efecto contable**: Cambio inmutable y con signo que un movimiento produce sobre una cuenta; es la fuente para derivar saldos.
- **Enlace de movimientos**: Relación explícita entre operaciones que forman una transferencia, amortización, reembolso o reversión.
- **Revisión de movimiento**: Evidencia inmutable del estado anterior, el nuevo estado y el motivo de una corrección o anulación.
- **Recibo de comando**: Resultado asociado a la identidad estable de una solicitud; permite reconocer reintentos idénticos y conflictos de contenido.
- **Operación pendiente de sincronización**: Confirmación local que conserva propietario, identidad, versión y estado de entrega hasta obtener un resultado remoto definitivo.
- **Saldo reembolsable**: Diferencia entre el importe original elegible y la suma de reembolsos válidos ya vinculados.
- **Proyección de consumo**: Resultado derivado que atribuye gastos confirmados y sus correcciones al periodo y jerarquía presupuestaria correspondientes.

### Contratos de Comportamiento

- **Registrar movimiento**: Recibe una identidad estable, propietario, tipo, naturaleza económica, importe, moneda, fecha y referencias. Produce una operación confirmada con todos sus efectos o un rechazo sin efectos.
- **Reintentar movimiento**: La misma identidad y el mismo contenido devuelven el resultado previo; la misma identidad con contenido distinto produce conflicto; una identidad nueva con datos similares produce advertencia confirmable.
- **Detectar similitud**: La ventana de cinco minutos y las dimensiones usuario, cuenta origen, tipo, monto y moneda quedan fijadas para Sprint 2. Una coincidencia sólo advierte; no identifica ni rechaza definitivamente una operación.
- **Corregir movimiento**: Requiere la revisión conocida y produce una nueva revisión con efectos compensatorios completos o un conflicto explícito.
- **Anular movimiento**: Conserva la operación original, registra su reversión y excluye sus efectos de cálculos posteriores sin permitir una segunda anulación efectiva.
- **Aplicar a obligación o meta**: Confirma conjuntamente el movimiento, su enlace y el cambio de principal, por cobrar o reserva.
- **Registrar reembolso**: Requiere el origen, valida el remanente reembolsable y produce el cambio de caja y la corrección histórica como una sola decisión financiera.

### Riesgos y Decisiones Abiertas

| Riesgo | Tratamiento requerido |
| :--- | :--- |
| Una operación parcial altera solo una cuenta o relación | Inyectar interrupciones antes de aceptación y comprobar resultado completo o inexistente. |
| Un reintento o doble toque repite el efecto | Usar identidad estable y conservar el resultado de la primera decisión. |
| Una coincidencia heurística elimina una compra real | Separar identidad exacta de similitud y exigir confirmación ante esta última. |
| Un dispositivo atrasado revive una versión anulada | Comparar revisiones y preservar el estado supersedido hasta resolución explícita. |
| Un límite comercial cambia cifras históricas | Bloquear solo nuevas acciones; mantener historia y cálculos existentes. |
| Dependencias de cuentas, categorías u obligaciones llegan tarde | Adelantar contratos y casos de prueba, pero no aceptar la HU hasta integrar el bloqueante real. |

No quedan decisiones funcionales abiertas que impidan planificar el incremento de Sprint 2. Cualquier variación de tipos de movimiento, reglas de clasificación, moneda, ciclo de corrección o dependencias requiere aprobación y actualización de esta especificación.

### Artefactos de Entrega

| Artefacto | Estado esperado |
| :--- | :--- |
| `spec.md` | Fuente funcional vigente de EP-MOV. |
| `plan.md` | Se generará en la fase de planificación y deberá respetar los límites por sprint. |
| `tasks.md` | Se generará después del plan con tareas trazadas a cada HU y escenario. |
| `data-model.md` | Se generará si la planificación necesita detallar entidades, relaciones o migraciones. |
| Contratos y ADR | Se crearán únicamente para decisiones técnicas que necesiten una frontera o justificación durable. |
| Pruebas y PR | Deberán enlazarse antes de aceptar cada incremento y demostrar los criterios correspondientes. |

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Los 33 escenarios oficiales de HU-18 a HU-25 producen exactamente el resultado financiero especificado.
- **SC-002**: En 100 reintentos idénticos de cada operación de prueba se conserva una sola operación económica y un solo conjunto de efectos.
- **SC-003**: En todas las interrupciones inyectadas durante operaciones de múltiples efectos, el resultado observado es completo o inexistente; nunca parcial.
- **SC-004**: Diez operaciones de S/0.10 totalizan exactamente S/1.00 y toda transferencia conserva el valor total entre origen y destino.
- **SC-005**: El 100% de intentos de acceso cruzado del conjunto de pruebas es rechazado sin exponer datos de otra persona.
- **SC-006**: Una persona puede completar un movimiento manual común en menos de 10 segundos en una tarea conocida con datos preparados.
- **SC-007**: El 95% de las consultas locales simples sobre un conjunto de 10,000 movimientos presenta su resultado en menos de 300 ms en el dispositivo de referencia acordado.
- **SC-008**: Después de reiniciar sin red, el 100% de las operaciones que habían sido confirmadas reaparece con los mismos saldos y queda disponible para sincronización.
- **SC-009**: Correcciones, anulaciones y reembolsos dejan una cadena auditable completa y ninguna operación antigua reactiva un estado supersedido.
- **SC-010**: Ninguna pérdida de Premium, reducción de cupo o relación bloqueada elimina o altera el efecto financiero de movimientos válidos existentes.
- **SC-011**: Los escenarios adicionales S4 de HU-20/21/22 y las decisiones P1–P5 se verifican con evidencia de dominio, Room, RPC/sync y UI; las operaciones especializadas no producen mutación genérica.
- **SC-012**: En las pruebas de anulación y rechazo de efectos locales confirmados se conserva el 100% de los asientos originales y se obtiene una sola compensación efectiva por comando.
- **SC-013**: Ninguna consulta avanzada de la matriz Free/concesión caducada/enlace directo se ejecuta sin autorización; el registro manual persiste y sincroniza sin duplicados.

SC-001 conserva los 33 escenarios oficiales de la épica; las adiciones S4 se verifican con SC-011. En S4 se aceptan HU-20/21/22 y regresión del incremento existente; SC-009 para reembolsos y FR-020–025 permanecen sujetos a S6/S7, sin adelantar sus módulos.

## Assumptions

- La identidad y sesión propia provienen de EP-APS y ya están disponibles antes de aceptar el incremento de Sprint 2.
- Cuentas e instrumentos son responsabilidad de EP-CTA; categorías y comercios son responsabilidad de EP-CCO.
- PEN usa dos decimales; otras monedas respetan la escala definida por su catálogo.
- Las transferencias de esta versión se realizan entre cuentas propias de la misma moneda; no existe conversión automática y una transferencia produce una transacción con dos asientos balanceados.
- El aislamiento de datos usa `user_id` derivado de la sesión autenticada; no se introduce un `workspace_id` en este incremento.
- La detección de similitud usa una ventana de cinco minutos y las dimensiones usuario, cuenta origen, tipo, monto y moneda. La confirmación de una coincidencia siempre conserva una identidad nueva.
- Los saldos, presupuestos y métricas son resultados derivados del ledger y no campos editables directamente.
- Free conserva registro manual, historial básico y deduplicación; Premium amplía filtros y automatización sin cambiar la verdad financiera.
- Una referencia histórica bloqueada puede seguir visible y computable, pero no autoriza nuevas operaciones si la política vigente lo impide.
- El reloj y los generadores de identidad usados para validar reglas pueden sustituirse durante las pruebas.
- Las fuentes visuales y contratos de diseño normativos son `docs/stitch-design-system.md` y `Stich Prompts.md` (Pantalla 10 para Historial y Pantalla 11 para Registro en Bottom Sheet), aplicando tokens de color, números tabulares `tnum` y componentes establecidos.

## Dependencies

- **EP-APS**: sesión, propietario e aislamiento de datos.
- **EP-CTA**: cuentas, tarjetas, monedas y saldos derivados.
- **EP-CCO**: categorías, subcategorías y comercios.
- **EP-PLA**: decisión de acceso y selección de objetos bajo límites Free; HU-58/HU-59 autorizan las consultas Premium y su concesión offline. Su implementación pertenece a `specs/012-ep-pla-planes-monetizacion/` y bloquea la aceptación integrada de esos escenarios S4.
- **EP-DEU**: deudas y préstamos requeridos por HU-24.
- **EP-MET**: metas y reservas requeridas por HU-24.
- **EP-PRE**: periodos y consumo presupuestario requeridos por HU-25.
- **EP-INF**: consultas y analítica que consumen movimientos, filtros y reembolsos.
- **EP-AUT**: candidatos que deben reutilizar las mismas reglas de registro y deduplicación.
# Refinamiento UI/UX aprobado — 2026-10-03

**Refined**: 2026-10-03 — Aprobada la mejora integral S4 de consulta, filtros, privacidad, comparación y movimiento mediante UI/UX Pro Max y Compose Animations. Se conserva el alcance financiero y la historia S2/S4.

- **FR-033**: Abrir un movimiento presenta detalle de lectura, también para VOIDED; editar y anular requieren acciones explícitas y conservan los bloqueos de operaciones especializadas.
- **FR-034**: El panel de filtros usa borrador, validación y aplicación explícita. Cancelar conserva la consulta aplicada. Fechas/tipo/texto siguen Free; criterios avanzados requieren autorización efectiva. Importes se presentan en moneda y unidades mayores, se convierten con el parser financiero existente y no mezclan monedas. Criterios aplicados pueden retirarse individualmente; los guardados sin acceso se identifican como pendientes.
- **FR-035**: El enmascaramiento global protege los importes visibles y semánticos en historial, detalle, formularios, resumen, conflicto y anulación.
- **FR-036**: El historial prioriza lectura comparativa de comercio/categoría/cuenta e importes tabulares, distingue estado financiero de sincronización y adapta alturas al texto ampliado.
- **FR-037**: La edición muestra Antes/Después de cada campo modificado; la anulación identifica el movimiento y las consecuencias por cuenta, sin ofrecer resurrección/undo de VOIDED.
- **FR-038**: Vacío inicial, cero coincidencias y fallo de lectura tienen mensajes y acciones diferentes (registrar, retirar/limpiar filtros, reintentar).
- **FR-039**: El movimiento usa tokens compartidos y respeta reducción de animaciones. Feedback y acceso no dependen de terminar una animación; los resultados protegidos se retiran inmediatamente cuando se pierde autorización.
- **SC-014**: Pruebas de privacidad no encuentran importes originales en el árbol semántico de las superficies enmascaradas.
- **SC-015**: Cancelar un borrador no cambia resultados; aplicar rechaza rangos inválidos; las fechas son Free y los montos se interpretan en unidades mayores con moneda explícita.
- **SC-016**: VOIDED admite consulta de detalle sin edición y el resumen identifica los campos modificados.
- **SC-017**: Compilación/pruebas y evidencia de accesibilidad/movimiento documentan límites de dispositivo; no se confunde compilar AndroidTest con ejecutarlo.


## Evidencia de cierre del refinamiento — 2026-10-03

**Refined**: 2026-10-03 — Se preserva FR-017. La auditoría detectó un pendiente heredado: el filtro por **fuente** exigido por HU-22 no tiene un campo de procedencia en `Transaction`/`AdvancedHistoryCriteria`/la proyección Room consumida por esta UI. Sincronización y cuenta de origen son conceptos distintos. Este refinamiento implementa los controles respaldados por los contratos actuales; no fabrica procedencia histórica ni modifica el ledger para inferirla. T110 conserva explícitamente el trabajo restante de contrato y filtro por fuente antes de aceptar HU-22 íntegramente.

Evidencia de autoridad: consulta MCP Obsidian Mind `"HU-22" "fuente" filtros avanzados historial manual SMS notificación`, contrastada con la sección completa de `work/active/kipu/Kipu md/02_Kipu_V4.2_Product_Backlog.md` (HU-22, reglas 1–3). El requerimiento original continúa vigente.
