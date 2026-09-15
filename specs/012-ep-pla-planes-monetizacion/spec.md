# Feature Specification: EP-PLA - Planes, Límites y Monetización Freemium

**Feature Branch**: `001-planes-monetizacion-freemium`

**Created**: 2026-09-14

**Status**: V1.0 - Creación Inicial Sprint 1

**Input**: Especificar la HU-52 para que una persona recién registrada pueda continuar con Kipu Free o conocer voluntariamente la oferta Premium, sin cobros accidentales ni concesión de derechos Premium no verificados.

## Control de la Épica

| Campo | Valor |
|-------|-------|
| Épica | EP-PLA - Planes, Límites y Monetización Freemium |
| Responsable de Épica | Leonidas Garcia (Integrante 1) |
| Sprint de Cierre | Sprint 1 ("Kipu arranca con identidad y privacidad") |
| Puntos de Historia Integrados | 5 Pts (HU-52) |
| Historia incluida | HU-52 - Elegir Free o prueba Premium |
| Prioridad | Alta |
| Estado del Artefacto | V1.0 - Creación Inicial Sprint 1 |

## Objetivo y Alcance del Sprint 1

### Objetivo

Establecer la infraestructura inicial de monetización Freemium en Kipu para que una persona recién registrada pueda elegir el plan permanente Kipu Free o conocer la oferta voluntaria de una prueba Premium de 7 días. La experiencia debe impedir cobros accidentales y no debe conceder derechos Premium ficticios antes de disponer de una compra verificada.

### Alcance Incluido

- Implementación exclusiva de HU-52.
- Maquetación y comportamiento de la Pantalla 1B, Selección de Plan y Onboarding Freemium.
- Presentación transparente de Kipu Free y de las alternativas Premium informativas.
- Registro local-first de la preferencia `FREE`, `TRIAL_INTENT` o `PREMIUM_INTENT`.
- Sincronización diferida e idempotente de la preferencia cuando exista conectividad.
- Aplicación permanente de los límites Kipu Free mientras no exista un entitlement Premium verificado.

### Fuera de Alcance

- Compras y suscripciones mediante Google Play Store, correspondientes a HU-53 en el Sprint 3.
- Verificación remota de compras y concesión de entitlements Premium, correspondientes a HU-54 en el Sprint 3.
- Gestión de cupos, selección de objetos excedentes y downgrade, correspondiente a HU-57 en el Sprint 2.
- Cualquier cobro, renovación, cancelación real o activación efectiva de una prueba durante el Sprint 1.
- La implementación de capacidades futuras relacionadas con HU-58.
- La creación o modificación de un flujo hacia Pantalla 15; HU-52 solo conserva la garantía offline si un flujo anfitrión ya existente suministra ese destino.

### Dependencias y Trazabilidad

| Tipo | Historias | Implicación |
|------|-----------|-------------|
| Bloqueante | HU-01 - Registro e inicio de sesión por correo | La cuenta Kipu debe existir antes de mostrar la Pantalla 1B. |
| Parcial | Ninguna | No aplica una dependencia parcial en este incremento. |
| Relacionadas | HU-53, HU-57, HU-58 | Comparten el dominio de monetización, acceso o límites, pero no forman parte del alcance de Sprint 1. |
| Desbloquea a futuro | HU-53, HU-54, HU-57, HU-58 | La preferencia y los límites establecidos sirven como base para compras, verificación y cambios de plan posteriores. |

**Entrada técnica no bloqueante**: HU-52 define y consume un `TrialEligibilitySnapshot` propiedad del límite de monetización EP-PLA. En Sprint 1, un estado de producción solo puede ser positivo o negativo si incluye evidencia de historial de cuenta verificado; en cualquier otro caso es `UNKNOWN`. Este default conservador permite completar HU-52 sin convertir HU-53 o HU-54 en dependencias parciales ni afirmar compatibilidad con Google Play antes de verificarla.

### Precondiciones

- La persona completó la creación de una cuenta Kipu mediante HU-01.
- La Pantalla 1B está disponible como siguiente paso del onboarding.
- La contratación mediante Google Play todavía se muestra como no habilitada en Sprint 1.
- Ninguna interacción de esta pantalla puede activar una prueba o suscripción ficticia.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Elegir Free o conocer Premium sin riesgo (Priority: P1)

Como usuario de Kipu, quiero elegir Free o conocer la prueba Premium voluntaria, para decidir sin activar cobros por accidente.

**Why this priority**: Es la única historia incluida en la épica durante Sprint 1 y define tanto el acceso inicial a la aplicación como la barrera de seguridad que impide confundir una intención comercial con una suscripción o un entitlement real.

**Independent Test**: Puede probarse con una cuenta recién creada, recorriendo por separado la elección Free, cada alternativa Premium informativa, el caso de una persona no elegible y el abandono de la pantalla. El incremento entrega valor si permite continuar el onboarding y en ningún recorrido solicita pago, genera un cobro o habilita Premium.

**Acceptance Scenarios**:

1. **Escenario 1 - Free (oficial HU-52)**: **Dado que** el usuario no desea suscripción de pago, **Cuando** elige continuar con Kipu Free en la Pantalla 1B, **Entonces** ingresa a la aplicación sin solicitar método de pago ni datos bancarios.
2. **Escenario 2 - Información del Trial (oficial HU-52)**: **Dado que** consulta la oferta en la Pantalla 1B, **Cuando** selecciona la modalidad mensual o anual, **Entonces** observa la duración de la prueba, el precio posterior y las condiciones de renovación antes de aceptar.
3. **Escenario 3 - No elegible (oficial HU-52)**: **Dado que** ya consumió previamente su periodo de prueba de 7 días, **Cuando** consulta la oferta elegible, **Entonces** no se le promete otro Trial.
4. **Escenario 4 - Abandonar Onboarding (oficial HU-52)**: **Dado que** no ha confirmado ninguna compra en Play Store, **Cuando** sale de la pantalla de selección de plan, **Entonces** no se activa ni cobra ninguna suscripción.

**Acceptance Scenarios complementarios de la especificación**:

5. **Escenario 5 - Confirmar intención de Trial**: **Dado que** la elegibilidad está confirmada y la persona seleccionó Mensual o Anual, **Cuando** presiona "Confirmar Plan", **Entonces** se registra `TRIAL_INTENT`, se encola una única operación de sincronización, continúa el onboarding y el acceso efectivo conserva los límites Kipu Free sin abrir Google Play.
6. **Escenario 6 - Confirmar intención Premium sin Trial**: **Dado que** la persona seleccionó Lifetime o una alternativa Premium sin Trial aplicable, **Cuando** presiona "Confirmar Plan", **Entonces** se registra `PREMIUM_INTENT`, no se promete una prueba, no se programa una renovación y el acceso efectivo permanece en Kipu Free.
7. **Escenario 7 - Confirmar offline y recuperar**: **Dado que** no existe conectividad, **Cuando** la persona confirma cualquier opción y después reinicia la aplicación, **Entonces** la preferencia y su operación pendiente permanecen guardadas, el avance a Pantalla 1C no fue bloqueado y, al recuperar conectividad estable, la selección se sincroniza una sola vez.
8. **Escenario 8 - Reintento, revisión obsoleta o conflicto**: **Dado que** el servicio recibe una operación ya aplicada, una revisión anterior o la misma identidad/revisión con contenido diferente, **Cuando** procesa la solicitud, **Entonces** devuelve respectivamente `DUPLICATE`, `STALE` o `CONFLICT`, conserva la preferencia vigente, no produce efectos adicionales y permite reconciliar el cliente con la revisión aceptada.
9. **Escenario 9 - Aislamiento entre cuentas**: **Dado que** existen dos cuentas autenticadas distintas, **Cuando** una intenta leer o modificar la preferencia de la otra, **Entonces** la operación se rechaza sin revelar ni alterar datos ajenos.
10. **Escenario 10 - Accesibilidad de la elección**: **Dado que** una persona utiliza una ayuda de accesibilidad admitida, **Cuando** consulta, selecciona y confirma un plan, **Entonces** puede identificar la opción seleccionada, su precio, sus condiciones y el botón de confirmación sin perder información esencial.
11. **Escenario 11 - Cupos no destructivos**: **Dado que** una respuesta informa límites Free y existen datos por encima de un cupo, **Cuando** se sincroniza la preferencia, **Entonces** ningún dato se elimina, reescribe o excluye de los cálculos y la gestión del excedente queda diferida a HU-57.
12. **Escenario 12 - Destino suministrado por el flujo anfitrión**: **Dado que** la Pantalla 1B fue abierta por un flujo existente que definió explícitamente Pantalla 1C o Pantalla 15 como siguiente destino, **Cuando** la confirmación local termina, **Entonces** se navega a ese destino sin esperar la red; la entrada de Sprint 1 posterior al registro siempre define Pantalla 1C.
13. **Escenario 13 - Contenido y estructura de Pantalla 1B**: **Dado que** la persona abre Pantalla 1B, **Cuando** consulta Kipu Free, **Entonces** observa S/ 0 de por vida y los cinco límites aprobados, sin publicidad, slogans comerciales, Top Bar ni barra de navegación inferior.
14. **Escenario 14 - Error de sincronización seguro**: **Dado que** una solicitud carece de autenticación, usa una versión no soportada, contiene datos inválidos o encuentra el servicio indisponible, **Cuando** se intenta sincronizar, **Entonces** no cambia la preferencia remota ni el acceso efectivo, no se exponen datos ajenos y una operación reintentable permanece pendiente hasta una oportunidad permitida.
15. **Escenario 15 - Motivo de acceso verificable**: **Dado que** se evalúa una capacidad durante Sprint 1, **Cuando** la capacidad pertenece al núcleo Free, supera un cupo Free o exige Premium, **Entonces** la política devuelve respectivamente `Allowed(FREE_CAPABILITY)`, `Denied(FREE_LIMIT_REACHED)` o `Denied(PREMIUM_ENTITLEMENT_REQUIRED)` sin interpretar una intención como entitlement.

---

### Edge Cases

- Si la elegibilidad para Trial no puede comprobarse, la oferta no promete una prueba y cualquier selección Premium se registra únicamente como `PREMIUM_INTENT`.
- Si una persona ya consumió el Trial, puede consultar las alternativas Premium, pero no vuelve a ver la promesa de 7 días gratuitos.
- Si se elige Lifetime, se informa que es un pago único de S/ 49.99, sin renovación y sin asociarlo al Trial de 7 días.
- Si no hay conectividad al confirmar, la preferencia queda guardada localmente y el onboarding continúa sin esperar la sincronización.
- Si la sincronización se reintenta, una misma operación no crea selecciones duplicadas ni altera los derechos de acceso.
- Si llega una revisión anterior después de una selección más reciente, la revisión anterior se reconoce como obsoleta y no reemplaza la preferencia vigente.
- Si la aplicación se cierra después de la persistencia local y antes de sincronizar, la selección permanece pendiente y recuperable al reiniciar.
- Si la persona abandona antes de confirmar, no se registra una nueva intención comercial, no se abre un flujo de pago y no cambia su acceso efectivo.
- Si se manipula el estado local, el reloj, la instalación o la cola de sincronización, no se obtiene ni se prolonga acceso Premium.
- Si otra cuenta intenta consultar o modificar la preferencia, el acceso se rechaza sin exponer la selección del propietario.
- Si una preferencia Premium informativa entra en conflicto con el acceso efectivo, prevalece Kipu Free hasta que una compra sea verificada por el flujo futuro de HU-54.

### Matriz Mínima de Validación

- **Recorridos críticos**: Free online, Free offline, Mensual elegible, Anual elegible, Lifetime, Premium no elegible o con elegibilidad desconocida, abandono sin confirmar y confirmación offline con reinicio y sincronización posterior.
- **Rendimiento percibido**: Al menos 40 confirmaciones distribuidas entre las cuatro opciones comerciales, los estados online/offline y tres perfiles representativos de dispositivo de capacidad baja, media y alta.
- **Sincronización**: Al menos 30 operaciones que combinen pérdida y recuperación de red, reinicio, duplicados y entrega fuera de orden. La medición de 15 minutos se realiza con conectividad estable, aplicación no forzada a detenerse, trabajo en segundo plano permitido y sin una restricción de batería que impida al sistema operativo ejecutar la tarea.
- **Restricciones de plataforma**: En un dispositivo Android real se prueban Doze, restricción de batería y detención forzada. Esos estados no están sujetos al plazo de 15 minutos mientras el sistema operativo impida ejecutar trabajo; deben conservar la operación y sincronizarla en la primera oportunidad permitida o al reabrir la aplicación.
- **Comprensión comercial**: Al menos 20 participantes representativos responden preguntas sobre permanencia de Free, voluntariedad del Trial, precio posterior, renovación, cancelación y ausencia de compra en Sprint 1.
- **Accesibilidad**: Los ocho recorridos críticos se completan con lector de pantalla y con texto ampliado al 200%, verificando foco, orden de lectura, estado seleccionado, precios, condiciones y acción principal.
- **Aislamiento**: Se prueban `SELECT`, `INSERT` y `UPDATE` con dos cuentas distintas tanto por el límite de servicio como por el acceso directo permitido al almacén remoto.

## Requirements *(mandatory)*

### Reglas de Negocio

- **RN-001 - Kipu Free Permanente**: Kipu Free DEBE permanecer disponible de por vida por S/ 0, sin tarjeta de crédito y sin anuncios publicitarios.
- **RN-002 - Trial Voluntario**: El Trial de 7 días DEBE ser voluntario, estar sujeto a elegibilidad y vincularse exclusivamente con las modalidades mensual o anual de Google Play Store.
- **RN-003 - Transparencia Comercial**: Antes de confirmar una intención mensual o anual, la persona DEBE conocer la duración del Trial, el precio posterior, la frecuencia de renovación y las condiciones de cancelación.
- **RN-004 - Intención sin Concesión Prematura**: Una elección expresada en la Pantalla 1B DEBE registrar solo una preferencia. La activación real de derechos Premium exige una compra verificada en servidor mediante HU-54.

### Functional Requirements

- **FR-001**: El sistema DEBE presentar la Pantalla 1B secuencialmente después del registro exitoso de una cuenta Kipu.
- **FR-002**: La Pantalla 1B DEBE permitir seleccionar Kipu Free o una única alternativa Premium informativa antes de confirmar el plan.
- **FR-003**: La tarjeta Kipu Free DEBE mostrar S/ 0 de por vida, ausencia de tarjeta y publicidad, uso manual local y los límites de 4 instrumentos, 5 categorías personalizadas, 2 deudas, 2 metas y 2 presupuestos.
- **FR-004**: La tarjeta Premium DEBE presentar una oferta informativa de 7 días únicamente cuando la persona sea elegible y DEBE permitir consultar Mensual por S/ 4.99, Anual por S/ 29.99 y Pago Único Lifetime por S/ 49.99.
- **FR-005**: Al seleccionar Mensual o Anual con elegibilidad confirmada, la pantalla DEBE mostrar antes de la confirmación los 7 días de prueba, el precio exacto posterior, la frecuencia de renovación y las condiciones de cancelación.
- **FR-006**: Al seleccionar Lifetime, la pantalla DEBE informar que corresponde a un pago único, sin renovación, y NO DEBE prometer un Trial de 7 días.
- **FR-007**: Si la persona ya consumió el Trial o su elegibilidad no está confirmada, la pantalla NO DEBE prometer otro periodo gratuito.
- **FR-008**: El botón principal DEBE rotularse "Confirmar Plan" y DEBE confirmar solamente la opción que se encuentre seleccionada.
- **FR-009**: Confirmar Kipu Free DEBE registrar `FREE` y permitir continuar sin solicitar método de pago ni datos bancarios.
- **FR-010**: Confirmar Mensual o Anual para una persona elegible DEBE registrar `TRIAL_INTENT`; confirmar una alternativa Premium sin Trial aplicable, incluida Lifetime, DEBE registrar `PREMIUM_INTENT`.
- **FR-011**: Registrar `TRIAL_INTENT` o `PREMIUM_INTENT` NO DEBE crear ni modificar un entitlement, iniciar una compra, abrir un cobro, programar una renovación ni habilitar capacidades Premium.
- **FR-012**: Después de cualquier intención Premium de Sprint 1, el sistema DEBE evaluar el acceso con los límites activos de Kipu Free.
- **FR-013**: La confirmación DEBE guardar en Room, como fuente local autoritativa, la preferencia y la operación pendiente de `sync_outbox` dentro de una única transacción antes de informar éxito.
- **FR-014**: La falta de conectividad NO DEBE impedir la confirmación ni la navegación al siguiente destino del flujo.
- **FR-015**: Cada operación de sincronización DEBE tener un identificador estable y una revisión monotónica por usuario; un duplicado NO DEBE repetir efectos y una revisión obsoleta NO DEBE reemplazar la selección vigente.
- **FR-016**: En Sprint 1, cada respuesta exitosa del contrato DEBE incluir una instantánea informativa de los cupos Free, pero NO DEBE ejecutar downgrade, selección de excedentes ni bloqueos propios de HU-57; si existen datos sobre un cupo, DEBE conservarlos sin alterar sus efectos financieros.
- **FR-017**: Las lecturas y escrituras remotas de preferencias DEBEN derivar el propietario de la sesión autenticada, validar la autorización en el límite de servicio y restringirse además a la fila cuyo `user_id` coincide con dicho propietario.
- **FR-018**: La evaluación de capacidades DEBE producir de forma determinista `Allowed(FREE_CAPABILITY)`, `Denied(FREE_LIMIT_REACHED)` o `Denied(PREMIUM_ENTITLEMENT_REQUIRED)` para los estados posibles de Sprint 1. Otros motivos requieren ampliar el contrato en una historia posterior.
- **FR-019**: Salir de la Pantalla 1B sin confirmar NO DEBE registrar una nueva selección, iniciar una compra, activar un Trial, conceder Premium ni producir un cobro.
- **FR-020**: La Pantalla 1B NO DEBE mostrar publicidad, slogans comerciales, barra superior ni barra de navegación inferior.
- **FR-021**: El estado seleccionado, las condiciones comerciales y la acción de confirmación DEBEN ser perceptibles y operables mediante las ayudas de accesibilidad admitidas por la aplicación.
- **FR-022**: La Pantalla 1B DEBE navegar al destino explícitamente suministrado por su flujo anfitrión después de la confirmación local: la entrada de Sprint 1 posterior al registro DEBE suministrar Pantalla 1C (Biometría); Pantalla 15 solo PUEDE ser suministrada por un flujo preexistente fuera del alcance de HU-52.
- **FR-023**: La interfaz DEBE indicar claramente que la contratación mediante Google Play no está habilitada en Sprint 1 y que confirmar una alternativa Premium solo registra interés.
- **FR-024**: La modalidad Mensual, Anual o Lifetime seleccionada DEBE utilizarse para presentar las condiciones y decidir el tipo de intención, pero NO DEBE persistirse como modalidad de compra en Sprint 1; una compra futura exigirá una nueva selección y confirmación en HU-53.
- **FR-025**: Mostrar o abandonar la Pantalla 1B NO DEBE crear una fila de preferencia. El default `'FREE'` solo aplica cuando se realiza una inserción explícita sin otro valor y no constituye evidencia de confirmación, Trial o compra.
- **FR-026**: Todo precio que se almacene o transporte de forma autoritativa DEBE representarse en unidades monetarias menores enteras junto con la moneda `PEN`; la interfaz DEBE mostrar los importes equivalentes en soles.
- **FR-027**: El límite de monetización EP-PLA DEBE entregar a Pantalla 1B un `TrialEligibilitySnapshot` con estado `ELIGIBLE`, `INELIGIBLE` o `UNKNOWN`, procedencia y momento de verificación. Solo el historial de cuenta verificado PUEDE producir los dos primeros estados; ausencia, error o evidencia no confiable DEBE producir `UNKNOWN` sin promesa de Trial.
- **FR-028**: Todo cambio de esquema local o remoto requerido por esta historia DEBE usar una migración versionada, revisable y no destructiva, validada con datos existentes representativos.
- **FR-029**: Una solicitud sin autenticación, con versión no soportada o payload inválido DEBE rechazarse sin modificar estado ni exponer datos; una indisponibilidad transitoria DEBE conservar la operación local como pendiente y reintentable, siempre sin alterar el acceso Kipu Free.
- **FR-030**: Cada confirmación DEBE establecer `selected_at` al momento de la nueva selección y `updated_at` al mismo momento local; cada aplicación remota exitosa DEBE reemplazar `updated_at` por el momento asignado por servidor.
- **FR-031**: El límite remoto DEBE conservar, durante la vida de la cuenta, un recibo por combinación de usuario y `operation_id`, el hash canónico del payload y la mayor `selection_revision` aceptada; esta metadata DEBE eliminarse con la cuenta y su esquema físico se define en el plan.
- **FR-032**: RLS DEBE estar habilitado para `plan_preferences`; operaciones `SELECT`, `INSERT` y `UPDATE` que no satisfagan `user_id = auth.uid()` DEBEN ser denegadas incluso si omiten el límite `/plans/selection`.

### Trazabilidad de Requisitos y Evidencia

| Requisitos | Escenarios o evidencia de aceptación |
|------------|--------------------------------------|
| FR-001, FR-014, FR-022 | Escenarios 1, 5, 7 y 12; SC-001 y SC-005. |
| FR-002 a FR-008, FR-021, FR-023, FR-024 | Escenarios 2, 3, 6 y 10; SC-002, SC-004, SC-008 y SC-009. |
| FR-003, FR-020 | Escenario 13; SC-001. |
| FR-009 a FR-012, FR-025, FR-027 | Escenarios 1, 3, 4, 5 y 6; SC-001, SC-003 y SC-004. |
| FR-013, FR-015 | Escenarios 7 y 8; SC-005, SC-006 y SC-010. |
| FR-016 | Escenario 11; SC-011. |
| FR-017, FR-032 | Escenario 9; SC-007. |
| FR-018 | Escenario 15; SC-003. |
| FR-019 | Escenario 4; SC-003. |
| FR-026 | Inspección de contratos y prueba de presentación de precios; SC-002. |
| FR-028 | Evidencia de migración definida en Definition of Done; SC-013. |
| FR-029 | Escenario 14; SC-003, SC-006 y SC-012. |
| FR-030, FR-031 | Escenarios 7 y 8; SC-006 y SC-010. |

### Interfaz de Usuario y Navegación

**Pantalla asociada**: Pantalla 1B - Selección de Plan y Onboarding Freemium, maquetada en Jetpack Compose.

| Componente | Contenido y comportamiento requerido |
|------------|--------------------------------------|
| Tarjeta Kipu Free | S/ 0 de por vida, uso manual local, sin tarjeta, sin publicidad y resumen de límites: 4 instrumentos, 5 categorías personalizadas, 2 deudas, 2 metas y 2 presupuestos. |
| Tarjeta Trial Premium | Información de 7 días para personas elegibles y chips interactivos para Mensual S/ 4.99, Anual S/ 29.99 y Pago Único Lifetime S/ 49.99. Lifetime se diferencia como compra futura sin Trial ni renovación. |
| Botón principal | "Confirmar Plan"; persiste la selección y continúa el flujo sin iniciar pagos en Sprint 1. |

**Restricciones visuales**:

- La pantalla no contiene Top Bar ni `KipuBottomBar`.
- La pantalla no contiene publicidad ni slogans comerciales.
- La maquetación toma como referencia los tokens de `DESIGN.md`: Primary `#0F766E`, Ink `#0F172A` y radios de `16dp`/`12dp`.
- La referencia de tooling es el MCP de Stitch AI y las pautas del Prompt 1 de `Stich Prompts.md`.

**Flujo de navegación**:

| Momento | Origen | Destino | Condición |
|---------|--------|---------|-----------|
| Entrada | Pantalla 1, registro exitoso | Pantalla 1B | Cuenta creada y autenticada mediante HU-01. |
| Salida primaria | Pantalla 1B | Pantalla 1C, Biometría | Selección guardada localmente como Free o intención Premium. |
| Salida alternativa | Pantalla 1B en un flujo previamente definido | Pantalla 15 | La selección local se confirma sin bloquear por conectividad. |

### Arquitectura de Datos, Contratos y Sincronización

**Resumen funcional**: Kipu recuerda la preferencia en el dispositivo antes de continuar, la envía más tarde si no hay red, impide que una intención habilite Premium y aísla la selección de cada cuenta. Ningún fallo de sincronización bloquea el núcleo manual Free ni modifica la historia financiera.

Esta sección formaliza las restricciones técnicas proporcionadas para EP-PLA. Se vuelven normativas al aprobar esta especificación; el diseño detallado debe registrarse después en `plan.md`, `data-model.md` y `ADR-012-freemium-intent` sin debilitar los invariantes aquí establecidos. La existencia de esta sección no constituye evidencia de que la integración ya esté implementada.

#### Entidad Principal: `plan_preferences`

La entidad lógica debe existir tanto en Supabase como en Room. El siguiente contrato físico corresponde a la tabla remota Supabase:

| Campo | Tipo y restricciones | Significado |
|-------|----------------------|-------------|
| `user_id` | UUID, PK, FK `auth.users.id` | Identificador del usuario autenticado y propietario de la preferencia. |
| `selection` | TEXT, NOT NULL, default `'FREE'` | Valor permitido: `'FREE'`, `'TRIAL_INTENT'` o `'PREMIUM_INTENT'`. |
| `selected_at` | TIMESTAMPTZ, NOT NULL, default `now()` | Momento en que se confirmó la selección. |
| `updated_at` | TIMESTAMPTZ, NOT NULL, default `now()` | Momento de la actualización más reciente. |

La entidad Room conserva los mismos cuatro nombres y significados lógicos, pero usa tipos físicos compatibles con la plataforma local:

| Campo lógico | Restricción local |
|--------------|-------------------|
| `user_id` | UUID normalizado, clave primaria; no replica la FK remota a `auth.users`. |
| `selection` | Texto no nulo restringido a `'FREE'`, `'TRIAL_INTENT'` o `'PREMIUM_INTENT'`. |
| `selected_at` | Instante UTC no nulo, convertible a TIMESTAMPTZ sin perder orden. |
| `updated_at` | Instante UTC no nulo, actualizado en cada confirmación y reconciliación remota. |

En cada confirmación, `selected_at` identifica la selección nueva y `updated_at` recibe inicialmente el mismo instante local. Cuando el servidor aplica la operación, asigna el `updated_at` remoto autoritativo y el cliente lo adopta al reconciliar. El plan define los convertidores y tipos Room concretos.

Mostrar la Pantalla 1B no crea esta fila. El default `'FREE'` es una defensa del contrato para una inserción explícita que omita `selection`; no prueba que la persona haya confirmado un plan. Sin fila o sin entitlement verificado, la política efectiva continúa siendo Kipu Free.

`selection` conserva deliberadamente la categoría de intención, no la modalidad de facturación. Mensual y Anual elegibles convergen en `'TRIAL_INTENT'`; Lifetime y cualquier alternativa sin Trial aplicable convergen en `'PREMIUM_INTENT'`. La opción exacta vive solo durante la presentación de Sprint 1 y debe seleccionarse de nuevo al iniciar una compra futura en HU-53.

#### Configuración Comercial de Sprint 1

| Opción | Importe autoritativo | Presentación | Trial aplicable en Sprint 1 |
|--------|----------------------|--------------|-----------------------------|
| Kipu Free | `amount_minor = 0`, `currency = PEN` | S/ 0 de por vida | No aplica. |
| Mensual | `amount_minor = 499`, `currency = PEN` | S/ 4.99 por mes | 7 días si la elegibilidad está verificada. |
| Anual | `amount_minor = 2999`, `currency = PEN` | S/ 29.99 por año | 7 días si la elegibilidad está verificada. |
| Lifetime | `amount_minor = 4999`, `currency = PEN` | S/ 49.99, pago único | No aplica y no renueva. |

Estos importes son configuración comercial informativa de Sprint 1. Google Play será la fuente comercial aplicable al iniciar una compra real en HU-53; cualquier diferencia futura exige actualizar y aprobar los artefactos correspondientes antes de mostrar o cobrar nuevos valores.

#### Estructuras Locales Complementarias

- **`feature_access_cache`**: Almacena la política de acceso efectiva y la fecha de expiración conocida localmente. Una intención comercial no puede escribir una autorización Premium en esta estructura.
- **`sync_outbox`**: Cola atómica e idempotente de cambios locales pendientes. Cada operación de selección incluye un `operation_id` UUID estable, una `selection_revision` entera y monotónica por usuario, `contract_version: 1`, el valor de `selection` y `selected_at`. La operación se guarda en la misma transacción Room que `plan_preferences`.
- **DataStore**: Puede conservar únicamente estado efímero de presentación o un checkpoint de onboarding. No es fuente autoritativa de la preferencia, del orden de revisiones ni de derechos de acceso y no reemplaza la transacción Room.

#### Fuente de Elegibilidad

El límite de monetización EP-PLA es propietario del contrato de lectura `TrialEligibilitySnapshot`, compuesto por `status`, `source` y `verified_at`. `status` admite `ELIGIBLE`, `INELIGIBLE` o `UNKNOWN`; los dos primeros requieren `source = VERIFIED_ACCOUNT_HISTORY` y un `verified_at` vigente según la configuración de producto. Sin esa evidencia, el productor devuelve `UNKNOWN`.

En Sprint 1, `UNKNOWN` es un resultado válido y no bloqueante: la persona puede conocer la oferta marcada como sujeta a elegibilidad y registrar interés, pero no recibe una promesa ni derechos. La integración que alimente este contrato desde Google Play pertenece a HU-53/HU-54. Un cache local puede conservar una denegación verificada, pero nunca transformar `UNKNOWN` en `ELIGIBLE`.

#### Seguridad RLS

RLS debe estar habilitado en `plan_preferences`. Sus políticas `SELECT`, `INSERT` y `UPDATE` deben restringirse estrictamente a filas cuyo `user_id = auth.uid()`. Además, `/plans/selection` deriva el propietario de la sesión autenticada, rechaza cualquier identidad de payload que no coincida y aplica autorización por objeto antes de acceder a datos. La autenticación no sustituye esta autorización y el límite de servicio no depende únicamente de RLS.

#### Invariante Crítica de Dominio

`'TRIAL_INTENT'` y `'PREMIUM_INTENT'` representan intención comercial y NUNCA un entitlement. Ninguno de esos valores puede conceder privilegios Premium en `entitlements`, `feature_access_cache`, la interfaz o cualquier evaluación de capacidades. Hasta que HU-54 verifique una compra en servidor, el acceso efectivo es Kipu Free.

#### Contratos de Capacidades y Selección

- **`/plans/selection`**: Límite autenticado que persiste exclusivamente preferencias, nunca compras, suscripciones ni entitlements. La versión inicial del contrato es `1`.
- **`FeatureAccessPolicy`**: Interfaz de dominio determinista que devuelve `Allowed` o `Denied` con un motivo. Una preferencia de intención, por sí sola, siempre debe evaluarse con los derechos efectivos de Kipu Free.

El request de `/plans/selection` contiene `contract_version`, `operation_id`, `selection_revision`, `selection` y `selected_at`. La identidad del usuario se obtiene de la sesión y no de un identificador confiado del cuerpo. Toda respuesta exitosa contiene el resultado `APPLIED`, `DUPLICATE`, `STALE` o `CONFLICT`, la revisión aceptada, la preferencia vigente y una instantánea obligatoria de los cupos Free.

- `APPLIED`: la revisión es posterior a la vigente y reemplaza la preferencia.
- `DUPLICATE`: la combinación de usuario y `operation_id` ya fue aplicada y el payload canónico es idéntico; se devuelve el resultado vigente sin repetir efectos.
- `STALE`: la revisión es anterior a la vigente; se conserva la preferencia más reciente.
- `CONFLICT`: un `operation_id` ya conocido llega con payload diferente, o la misma revisión llega con otra identidad o contenido; se conserva la preferencia vigente, se registra el conflicto para diagnóstico sin datos sensibles y el cliente reconcilia con la respuesta remota.

El servicio conserva durante la vida de la cuenta un recibo único por `(user_id, operation_id)`, su hash de payload canónico, el resultado aplicado y la mayor revisión aceptada por usuario. Esta metadata se elimina con la cuenta. Su esquema físico se decide en `plan.md`, pero su unicidad, retención y comportamiento observable son obligatorios.

Los errores se clasifican sin devolver preferencias ni cupos a una identidad no autorizada:

- `UNAUTHENTICATED` o `FORBIDDEN`: rechazo terminal sin cambio remoto ni exposición de datos; la operación espera una nueva sesión válida antes de reintentarse.
- `UNSUPPORTED_VERSION` o `INVALID_REQUEST`: rechazo terminal de esa operación; se conserva la preferencia local y se expone un estado de sincronización no sensible para corrección, sin bucle automático infinito.
- `UNAVAILABLE`: fallo reintentable; la operación permanece pendiente con la misma identidad y revisión hasta una oportunidad permitida.

Ningún resultado exitoso o de error modifica el entitlement efectivo ni bloquea el avance de onboarding ya confirmado localmente.

La revisión pertenece a la operación de sincronización y no agrega una modalidad de compra a `plan_preferences`. El contrato puede informar los cupos aprobados, pero en Sprint 1 no bloquea objetos, no selecciona excedentes y no elimina datos; esas conductas pertenecen a HU-57.

#### Flujo Local-First

1. La persona selecciona una opción y presiona "Confirmar Plan" en la Pantalla 1B.
2. La aplicación incrementa la revisión del usuario y guarda síncronamente en una transacción Room la selección y la operación correspondiente de `sync_outbox`.
3. La navegación continúa al destino suministrado por el flujo anfitrión aunque no exista red. El onboarding posterior al registro suministra Pantalla 1C; Pantalla 15 solo puede proceder de un flujo preexistente fuera del alcance de HU-52.
4. WorkManager sincroniza la fila con Supabase mediante `/plans/selection` al detectar conectividad.
5. Los reintentos conservan `operation_id`, `selection_revision` y `contract_version`; un resultado `DUPLICATE`, `STALE` o `CONFLICT` no repite ni revierte efectos y reconcilia el cliente con la revisión aceptada.
6. La respuesta actualiza la preferencia remota conocida y los cupos informativos, pero no borra datos ni ejecuta la gestión de downgrade de HU-57.
7. La sincronización de una intención no modifica `entitlements` ni convierte `feature_access_cache` en una fuente de Premium no verificado.

### Key Entities *(include if feature involves data)*

- **Preferencia de Plan (`plan_preferences`)**: Selección comercial confirmada por una persona. Contiene propietario, valor de intención y momentos de selección y actualización; no representa derechos de acceso.
- **Opción Comercial**: Alternativa efímera visible en Pantalla 1B: Free, Mensual, Anual o Lifetime, con importe en unidades menores, moneda, presentación, periodicidad, condiciones y relación aplicable con el Trial. No constituye una compra.
- **Elegibilidad de Trial**: Resultado verificado `ELIGIBLE`, `INELIGIBLE` o `UNKNOWN` que determina si se puede prometer la oferta de 7 días. Solo `ELIGIBLE` permite mostrarla como aplicable.
- **Entitlement Efectivo**: Fuente separada y verificada de derechos Premium. Su creación o modificación queda fuera de HU-52 y no puede derivarse de una preferencia.
- **Política de Acceso**: Evaluación determinista de una capacidad que devuelve permiso o denegación con un motivo y mantiene límites Free en Sprint 1.
- **Operación de Sincronización (`sync_outbox`)**: Cambio local con identificador estable, revisión monotónica y versión de contrato que permite sincronizar la preferencia de manera idempotente sin bloquear la experiencia offline.
- **Recibo de Sincronización**: Metadata remota por usuario y operación que conserva el hash canónico, el resultado y la mayor revisión aceptada durante la vida de la cuenta para distinguir duplicados, obsolescencia y conflictos.

## Riesgos y Decisiones Arquitectónicas

| Identificador | Riesgo o decisión | Tratamiento obligatorio |
|---------------|-------------------|-------------------------|
| RSK-001 | Activación prematura de permisos Premium antes de integrar la verificación remota de Play Billing en Sprint 3. | Restringir Sprint 1 al registro de `'TRIAL_INTENT'` o `'PREMIUM_INTENT'` y mantener siempre los límites Kipu Free. |
| DEC-001 | Separar preferencia, compra, ciclo de suscripción y entitlement efectivo. | Ningún estado editable por cliente o derivado de UI puede autorizar Premium. |
| DEC-002 | Operación local-first. | Persistir preferencia y outbox atómicamente; continuar offline y sincronizar de forma idempotente. |
| DEC-003 | Preservación de datos frente a límites. | La evaluación de cupos puede bloquear acciones futuras, pero no borrar datos excedentes ni alterar la verdad financiera. |

La aprobación de esta especificación autoriza los invariantes funcionales y técnicos aquí enumerados. Antes de implementar el diseño detallado, `ADR-012-freemium-intent` debe registrar y recibir aprobación explícita para la separación entre intención y entitlement, el versionado de selección y el tratamiento de conflictos.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los recorridos Free permite continuar sin solicitar tarjeta, datos bancarios ni acceso a una pasarela de pago.
- **SC-002**: El 100% de las ofertas Mensual y Anual elegibles muestra, antes de confirmar, los 7 días de prueba, el precio posterior exacto, la frecuencia de renovación y las condiciones de cancelación.
- **SC-003**: Se producen 0 cobros, 0 suscripciones, 0 Trials activos y 0 concesiones Premium en todos los recorridos de Sprint 1, incluidos intención Premium, abandono, uso offline y reintentos.
- **SC-004**: El 100% de las personas identificadas como no elegibles, o cuya elegibilidad sea desconocida, deja de recibir una promesa de Trial de 7 días.
- **SC-005**: En al menos el 95% de las 40 confirmaciones de la matriz mínima, la confirmación local y el avance al siguiente paso se completan en 2 segundos o menos, incluso sin conectividad.
- **SC-006**: El 100% de las selecciones confirmadas offline permanece disponible después de reiniciar y, bajo las condiciones de medición de la matriz, se sincroniza una sola vez en un máximo de 15 minutos tras recuperar conectividad estable.
- **SC-007**: El 100% de los intentos de acceso cruzado entre cuentas es rechazado sin revelar ni modificar preferencias ajenas.
- **SC-008**: Al menos 18 de los 20 participantes de la prueba de comprensión identifican correctamente, en el primer intento, que Free es permanente, que el Trial es voluntario y que confirmar Premium en Sprint 1 no activa una compra.
- **SC-009**: El 100% de los ocho recorridos críticos definidos en la matriz puede completarse con lector de pantalla y texto ampliado al 200%, conservando perceptible la opción seleccionada y sus condiciones comerciales.
- **SC-010**: En el 100% de las 30 operaciones de sincronización de la matriz, un duplicado no repite efectos, una revisión obsoleta no reemplaza la vigente y un conflicto conserva la última preferencia aceptada.
- **SC-011**: En el 100% de los casos con datos por encima de un cupo Free, sincronizar una preferencia conserva todos los registros y sus efectos financieros sin borrado ni reescritura.
- **SC-012**: En el 100% de los casos de falta de autenticación, autorización denegada, versión no soportada, payload inválido e indisponibilidad, no se concede Premium, no cambia la preferencia remota y no se exponen datos de otra cuenta.
- **SC-013**: El 100% de los datos representativos previos sobrevive a las migraciones requeridas con el mismo propietario, selección y orden temporal observable.

## Assumptions

- HU-01 entrega una cuenta autenticada y un identificador estable antes de entrar a Pantalla 1B.
- Los precios S/ 4.99 mensual, S/ 29.99 anual y S/ 49.99 Lifetime son la configuración comercial aprobada para mostrar en Sprint 1; su cobro y validación pertenecen a historias futuras.
- El Trial de 7 días solo aplica a Mensual y Anual. Lifetime se registra como `PREMIUM_INTENT`, no ofrece Trial y no renueva.
- Una persona elegible que confirma Mensual o Anual genera `TRIAL_INTENT`; una persona no elegible o con elegibilidad desconocida genera `PREMIUM_INTENT` si conserva su interés.
- El historial remoto verificado de la cuenta es la fuente autoritativa de elegibilidad. Cuando no pueda consultarse o no sea confiable, la experiencia falla de forma segura: no promete Trial ni concede Premium.
- Pantalla 1C es el destino obligatorio del onboarding iniciado tras registro. Pantalla 15 solo puede ser un destino entregado explícitamente por un flujo anfitrión ya existente; crear o modificar ese flujo no forma parte de HU-52.
- La fila `plan_preferences` se crea o actualiza al confirmar. Su default `'FREE'` es una defensa de persistencia y no implica una elección cuando la persona solo abrió o abandonó la pantalla.
- Mensual, Anual y Lifetime son opciones efímeras de presentación en Sprint 1. Solo se persiste la categoría de intención y una futura compra exige volver a elegir modalidad.
- La revisión de selección aumenta monotónicamente por usuario y `contract_version` comienza en `1`; el plan técnico debe definir su persistencia remota sin alterar el esquema de negocio aquí establecido.
- Para SC-006, conectividad estable significa que el dispositivo puede alcanzar continuamente el límite de sincronización durante la ventana de 15 minutos, la aplicación no fue forzada a detenerse, el trabajo en segundo plano está permitido y no existe una restricción de batería que impida al sistema operativo ejecutar la tarea.
- Si el sistema operativo difiere el trabajo por Doze, restricción de batería o detención forzada, no aplica el plazo de SC-006 durante el bloqueo; la operación debe permanecer íntegra y procesarse en la primera oportunidad permitida o al reabrir la aplicación.
- El acceso manual local de Kipu Free continúa disponible sin conectividad y sin depender de la sincronización de la preferencia.
- Ninguna preferencia comercial modifica transacciones, balances, deudas, metas, presupuestos ni otra historia financiera válida.

## Definition of Done

- Los cuatro escenarios Gherkin oficiales de HU-52 están validados mediante pruebas unitarias y de integración.
- Los escenarios complementarios 5 a 12 y la matriz mínima de validación cuentan con evidencia de aceptación.
- La persistencia en Room prueba la transacción atómica entre `plan_preferences` y `sync_outbox`.
- Las pruebas demuestran que ninguna intención concede Premium y que los límites Free permanecen activos.
- La autorización por propietario y el rechazo de acceso cruzado cuentan con evidencia verificable.
- El comportamiento offline, los reintentos idempotentes, los estados de error y la accesibilidad están validados.
- El contrato `/plans/selection` demuestra los resultados `APPLIED`, `DUPLICATE`, `STALE` y `CONFLICT`, incluida la entrega fuera de orden.
- Las migraciones local y remota son versionadas, revisables, no destructivas y están validadas con datos representativos existentes.
- WorkManager se verifica en un dispositivo Android real con pérdida y recuperación de red, Doze, restricción de batería, detención forzada y reapertura; la evidencia distingue los casos sujetos y no sujetos al plazo de SC-006.
- Los artefactos de producción y release no contienen entitlements Premium ficticios, compradores de prueba, estados falsos de billing ni secretos de servidor.
- Otro integrante del equipo aprueba la revisión cruzada de código.
- El flujo operativo se demuestra en la Review de Sprint 1.

## Artefactos Derivados

- `plan.md`
- `tasks.md`
- `data-model.md`
- `ADR-012-freemium-intent`
- `DESIGN.md`
- `Stich Prompts.md`
