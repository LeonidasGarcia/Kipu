# Feature Specification: EP-NOT — Centro de Notificaciones y Avisos (HU-42)

**Feature Branch**: `010-ep-not-notificaciones-avisos`

**Created**: 2026-09-26

**Status**: Draft

**Input**: User description: "HU-42 (5 pts): centro único para consultar alertas y recordatorios, abrir el objeto relacionado con navegación resiliente, administrar lectura y borrado lógico, y mostrar el contador de avisos no leídos."

## User Scenarios & Testing

### User Story 1 — Consultar alertas y recordatorios (Priority: P1)

Como usuario de Kipu, quiero consultar mis alertas y recordatorios en un centro único para abrir el contexto que requiere atención.

**Why this priority**: El centro unificado entrega el valor principal de HU-42 y es el destino de la campana de notificaciones.

**Independent Test**: Con avisos del usuario disponibles, abrir el centro permite reconocer alertas y recordatorios, filtrar la lista y distinguir la fecha prevista de un pago realizado.

**Acceptance Scenarios**:

#### Escenario 1: Alerta

- **Dado que** ocurrió cruce de crédito,
- **Cuando** abre el centro,
- **Entonces** ve condición y tarjeta relacionada.

#### Escenario 2: Recordatorio

- **Dado que** se aproxima vencimiento,
- **Cuando** consulta aviso,
- **Entonces** ve fecha prevista distinguida de pago realizado.

### User Story 2 — Abrir el objeto relacionado con seguridad (Priority: P1)

Como usuario, quiero abrir el objeto exacto relacionado con un aviso y recibir una explicación si ya no existe, sin que la navegación falle.

**Why this priority**: El aviso solo es útil si lleva al contexto correcto y no puede provocar un fallo cuando los datos cambian.

**Independent Test**: Abrir un aviso relacionado con una entidad existente llega a su detalle; abrir uno cuyo destino no existe mantiene el centro visible y muestra un mensaje no bloqueante.

**Acceptance Scenarios**:

#### Escenario 3: Navegar

- **Dado que** selecciona un aviso,
- **Cuando** lo abre,
- **Entonces** accede al detalle exacto.

#### Escenario 4: Objeto eliminado

- **Dado que** se eliminó físicamente un presupuesto,
- **Cuando** abre aviso anterior,
- **Entonces** se informa que ya no existe y no falla la navegación.

### User Story 3 — Mantener organizado el centro (Priority: P2)

Como usuario, quiero filtrar, marcar avisos como leídos y retirar los que ya no necesito para mantener claro qué requiere atención.

**Why this priority**: Las acciones de gestión reducen ruido y hacen que el contador represente lo que todavía requiere atención.

**Independent Test**: Marcar un aviso, marcar todos y retirar uno actualiza la lista y el contador solo para la cuenta activa; un aviso retirado deja de aparecer, pero su historial remoto no se borra físicamente.

**Acceptance Scenarios**:

1. **Dado que** hay avisos de distintos tipos, **Cuando** selecciona «Alertas» o «Recordatorios», **Entonces** ve solo los elementos de esa categoría; «Todos» restaura la lista completa.
2. **Dado que** hay avisos no leídos, **Cuando** marca uno o marca todos como leídos, **Entonces** sus estados cambian y el contador refleja el total restante.
3. **Dado que** retira un aviso, **Cuando** vuelve a consultar el centro, **Entonces** el aviso deja de mostrarse sin eliminarse físicamente.
4. **Dado que** el usuario abre el centro sin avisos activos, **Cuando** se completa la carga, **Entonces** ve «Todo al día. No tienes avisos pendientes».
5. **Dado que** el usuario abre la campana de avisos financieros, **Cuando** elige la acción, **Entonces** llega a PNOT y no a la bandeja P19 de importaciones.
6. **Dado que** el dispositivo está sin conexión, **Cuando** el usuario marca o retira un aviso, **Entonces** el cambio se refleja localmente y se sincroniza al recuperar conectividad.
7. **Dado que** el usuario cambia de cuenta, **Cuando** el centro vuelve a observar avisos, **Entonces** solo aparecen los avisos de la nueva cuenta.
8. **Dado que** el usuario tiene activado movimiento reducido, **Cuando** abre el centro o retira un aviso, **Entonces** la información permanece comprensible sin animación decorativa.

## Edge Cases

- La lista está vacía, todos los avisos están leídos o no hay resultados para el filtro seleccionado.
- El usuario cambia de cuenta mientras el centro está abierto; ninguna lista, mutación ni contador de otra cuenta puede aparecer.
- El destino fue eliminado, está marcado con tombstone, usa un tipo desconocido o no tiene una pantalla registrada.
- El usuario marca avisos como leídos o los retira mientras está sin conexión; el estado local queda disponible y la operación se sincroniza al recuperar conectividad.
- Una acción se reintenta después de un timeout o una respuesta duplicada; no se crean avisos ni mutaciones duplicadas.
- La cuenta tiene más de 99 avisos no leídos; el badge permanece compacto y comunica «99+».
- El usuario abre un aviso recordatorio antes de su fecha prevista; el contenido no debe presentarlo como un pago ya realizado.

## Requirements

### Functional Requirements

- **FR-001**: El centro MUST mostrar en una lista unificada las alertas y los recordatorios del usuario autenticado, ordenados del más reciente al más antiguo.
- **FR-002**: El centro MUST distinguir visual y textualmente una condición ocurrida de un evento futuro con fecha prevista. MUST reconocer `BUDGET_ALERT`, `BILLING_DUE`, `GOAL_REACHED`, `CREDIT_THRESHOLD` y `SYSTEM`, además del tipo histórico de cruce de crédito que ya emite el servidor.
- **FR-003**: El centro MUST ofrecer los filtros «Todos», «Alertas» y «Recordatorios» y un estado vacío con el copy «Todo al día. No tienes avisos pendientes».
- **FR-004**: Al abrir un aviso, el sistema MUST resolver el objeto exacto indicado por su tipo e identificador de referencia (`card`, `budget`, `debt` o `goal`) cuando el objeto y su pantalla estén disponibles.
- **FR-005**: Si el objeto referido fue eliminado, no existe o no tiene un destino registrado, el sistema MUST conservar el centro en pantalla, mostrar un mensaje amigable y no lanzar una excepción de navegación.
- **FR-006**: El sistema MUST permitir marcar un aviso como leído y marcar como leídos todos los avisos activos de la cuenta actual.
- **FR-007**: El sistema MUST permitir retirar un aviso mediante borrado lógico; las consultas del centro MUST excluir avisos retirados y la operación MUST conservar su registro remoto.
- **FR-008**: La campana del TopAppBar MUST mostrar el total de avisos activos no leídos de la cuenta actual y actualizarse tras las acciones individuales o masivas.
- **FR-009**: La lectura, el contador y las acciones de gestión MUST estar aislados por usuario; cambiar de cuenta MUST sustituir la observación local por los avisos de la cuenta nueva.
- **FR-010**: El usuario MUST poder consultar la última copia local y ejecutar acciones de lectura o retirada sin conexión; los cambios pendientes MUST converger con el estado remoto cuando se recupere la conexión.
- **FR-011**: La interfaz MUST ofrecer objetivos táctiles de al menos 48dp, etiquetas semánticas comprensibles y contraste mínimo WCAG AA.
- **FR-012**: Las transiciones del badge, apertura del centro y retirada de una fila MUST respetar la preferencia de movimiento reducido del sistema operativo.
- **FR-013**: El centro de avisos financieros (PNOT) MUST permanecer separado de la bandeja de revisión de capturas/importaciones (P19).

### Key Entities

- **Aviso**: Mensaje dirigido a una cuenta, con título, cuerpo, tipo, fecha de emisión, estado de lectura y estado de retirada.
- **Referencia de destino**: Vínculo opcional y débil a una tarjeta, presupuesto, deuda o meta; relaciona tipo e identificador sin asumir que el objeto seguirá existiendo.
- **Estado de interacción**: Estado de lectura y retirada que el usuario puede cambiar y que debe conservarse para su cuenta.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Desde la campana, el usuario llega al centro y ve la última lista local en menos de 2 segundos en el dispositivo de referencia.
- **SC-002**: En todos los escenarios automatizados, el contador coincide exactamente con los avisos activos no leídos de la cuenta seleccionada; los avisos retirados no cuentan.
- **SC-003**: El 100% de las pruebas de navegación para destinos presentes llega al objeto indicado y el 100% de los casos sin destino mantiene el centro visible sin excepción.
- **SC-004**: Las pruebas de aislamiento confirman que ningún aviso ni acción de otra cuenta aparece tras cambiar de cuenta.
- **SC-005**: Las pruebas de interacción confirman que lectura y retirada se reflejan en la copia local de inmediato y se sincronizan después de recuperar conectividad.
- **SC-006**: Todos los controles accionables cumplen el objetivo táctil mínimo establecido por el producto y no dependen exclusivamente del color o del movimiento para comunicar estado.

## Assumptions

- La sesión autenticada de HU-01 aporta el identificador de cuenta activo; HU-42 no implementa autenticación.
- PNOT es el centro financiero de HU-42. P19 sigue siendo la bandeja de revisión de elementos importados y no es el destino de la campana.
- La pantalla de detalle de tarjeta existe en el repositorio. Presupuesto, deuda y meta pertenecen a historias posteriores; mientras su destino no esté registrado, se muestra el mensaje de indisponibilidad de forma segura.
- «BILLING_DUE» se presenta como recordatorio. Los tipos de condición ocurrida y los avisos de sistema se presentan como alertas mientras no exista otra regla de clasificación aprobada.
- `app_notifications` no define una columna dedicada de vencimiento. Cuando el productor la conoce, la fecha prevista se entrega en `event_payload.due_date` como `YYYY-MM-DD`; si falta, el texto preformateado del productor en `body` es la fuente visible. El cliente etiqueta `BILLING_DUE` como fecha prevista, conserva `body`, no deriva vencimientos de `created_at` ni presenta un recordatorio como pago realizado.
- Los nombres de tipos y referencias aceptan mayúsculas de productores existentes y minúsculas de documentación; el modelo de presentación los normaliza sin reescribir avisos históricos.
- Al no existir una instrucción normativa para marcar automáticamente un aviso al fallar su destino, el fallo de navegación no cambia su estado de lectura.
- La hora de vencimiento se presenta como fecha prevista del recordatorio y no como confirmación de pago.
