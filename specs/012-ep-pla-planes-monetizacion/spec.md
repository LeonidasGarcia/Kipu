# Feature Specification: EP-PLA - Planes, Límites y Monetización Freemium

**Feature Branch**: `012-ep-pla-planes-monetizacion`

**Created**: 2026-09-14

**Status**: Refined

**Refined**: 2026-09-26 — Resolución de A1: diferenciar el pago `PENDING` informado por Google Play, el fallo temporal de verificación `RETRYABLE` y el estado `PENDING` de la outbox de selección de HU-52; se precisan copy, criterios y cobertura de pruebas.

**Refined**: 2026-09-26 — Incorporación del alcance S3 HU-53/HU-54/HU-56, verificación remota, estados de ciclo de compra y tratamiento explícito de discrepancias del esquema; distinción entre el Trial informativo de HU-52 y las ofertas reales consultadas en Play para HU-53; HU-52 y el addendum S2 se conservan como historial.

**Refined**: 2026-10-02 — Incorporación del alcance S4 de HU-58/HU-59: autorización de capacidades Premium y concesión offline firmada, ligada a usuario/instalación, limitada a 72 horas y a la vigencia comercial verificada; Free local continúa disponible al vencer.

**Refined**: 2026-09-15 — Adopción del design system Stitch "Kipu Andean Modernist" como única fuente de verdad visual de la Pantalla 1B; actualización de tokens de UI y lista de artefactos derivados; sin cambios funcionales (FR/RN/SC y user stories intactos).

**Refined**: 2026-09-15 — Eliminación de las referencias residuales al sistema de diseño previo a Stitch; todas las referencias de diseño apuntan a `docs/stitch-design-system.md` (cleanup post-adopción del design system Stitch; sin cambios funcionales).

**Refined**: 2026-09-16 — Fidelidad total a Pantalla 1B Stitch (Kipu V4 Finale): copy, componentes y layout 1B autorizados; Free informativo no seleccionable, Trial estático, Anual preseleccionado, CTA secundario, badges, sufijos de precio, nota info literal y footer fiscal; los invariantes de dominio permanecen intactos.

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
- La contratación mediante Google Play no se ejecuta en Sprint 1.
- Ninguna interacción de esta pantalla puede activar una prueba o suscripción ficticia.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Elegir Free o conocer Premium sin riesgo (Priority: P1)

Como usuario de Kipu, quiero elegir Free o conocer la prueba Premium voluntaria, para decidir sin activar cobros por accidente.

**Why this priority**: Es la única historia incluida en la épica durante Sprint 1 y define tanto el acceso inicial a la aplicación como la barrera de seguridad que impide confundir una intención comercial con una suscripción o un entitlement real.

**Independent Test**: Puede probarse con una cuenta recién creada, recorriendo por separado la elección Free, cada alternativa Premium informativa, el caso de una persona no elegible y el abandono de la pantalla. El incremento entrega valor si permite continuar el onboarding y en ningún recorrido solicita pago, genera un cobro o habilita Premium.

**Acceptance Scenarios**:

1. **Escenario 1 - Free (oficial HU-52)**: **Dado que** el usuario no desea suscripción de pago, **Cuando** elige continuar con Kipu Free en la Pantalla 1B, **Entonces** ingresa a la aplicación sin solicitar método de pago ni datos bancarios.
2. **Escenario 2 - Información del Trial (oficial HU-52)**: **Dado que** consulta la oferta en la Pantalla 1B, **Cuando** revisa la tarjeta Trial estática y las modalidades Premium, **Entonces** observa antes de confirmar la duración de 7 días, el precio posterior, la frecuencia de renovación y las condiciones de cancelación, sin que el copy visual dependa de la elegibilidad.
3. **Escenario 3 - No elegible (oficial HU-52)**: **Dado que** ya consumió previamente su periodo de prueba de 7 días, **Cuando** consulta la tarjeta Trial estática y confirma una modalidad Premium, **Entonces** conserva el copy informativo de la pantalla, se registra `PREMIUM_INTENT` y no se activa Trial, cobro ni derecho Premium.
4. **Escenario 4 - Abandonar Onboarding (oficial HU-52)**: **Dado que** no ha confirmado ninguna compra en Play Store, **Cuando** sale de la pantalla de selección de plan, **Entonces** no se activa ni cobra ninguna suscripción.

**Acceptance Scenarios complementarios de la especificación**:

5. **Escenario 5 - Confirmar intención de Trial**: **Dado que** la elegibilidad está confirmada y la persona seleccionó Mensual o Anual, **Cuando** presiona "Confirmar Plan", **Entonces** se registra `TRIAL_INTENT`, se encola una única operación de sincronización, continúa el onboarding y el acceso efectivo conserva los límites Kipu Free sin abrir Google Play.
6. **Escenario 6 - Confirmar intención Premium sin Trial**: **Dado que** la persona seleccionó Lifetime o una alternativa Premium sin Trial aplicable, **Cuando** presiona "Confirmar Plan", **Entonces** se registra `PREMIUM_INTENT`, no se activa una prueba, no se programa una renovación y el acceso efectivo permanece en Kipu Free.
7. **Escenario 7 - Confirmar offline y recuperar**: **Dado que** no existe conectividad, **Cuando** la persona confirma cualquier opción y después reinicia la aplicación, **Entonces** la preferencia y su operación pendiente permanecen guardadas, el avance a Pantalla 1C no fue bloqueado y, al recuperar conectividad estable, la selección se sincroniza una sola vez.
8. **Escenario 8 - Reintento, revisión obsoleta o conflicto**: **Dado que** el servicio recibe una operación ya aplicada, una revisión anterior o la misma identidad/revisión con contenido diferente, **Cuando** procesa la solicitud, **Entonces** devuelve respectivamente `DUPLICATE`, `STALE` o `CONFLICT`, conserva la preferencia vigente, no produce efectos adicionales y permite reconciliar el cliente con la revisión aceptada.
9. **Escenario 9 - Aislamiento entre cuentas**: **Dado que** existen dos cuentas autenticadas distintas, **Cuando** una intenta leer o modificar la preferencia de la otra, **Entonces** la operación se rechaza sin revelar ni alterar datos ajenos.
10. **Escenario 10 - Accesibilidad de la elección**: **Dado que** una persona utiliza una ayuda de accesibilidad admitida, **Cuando** consulta, selecciona y confirma un plan, **Entonces** puede identificar la opción seleccionada, su precio, sus condiciones y el botón de confirmación sin perder información esencial.
11. **Escenario 11 - Cupos no destructivos**: **Dado que** una respuesta informa límites Free y existen datos por encima de un cupo, **Cuando** se sincroniza la preferencia, **Entonces** ningún dato se elimina, reescribe o excluye de los cálculos y la gestión del excedente queda diferida a HU-57.
12. **Escenario 12 - Destino suministrado por el flujo anfitrión**: **Dado que** la Pantalla 1B fue abierta por un flujo existente que definió explícitamente Pantalla 1C o Pantalla 15 como siguiente destino, **Cuando** la confirmación local termina, **Entonces** se navega a ese destino sin esperar la red; la entrada de Sprint 1 posterior al registro siempre define Pantalla 1C.
13. **Escenario 13 - Contenido y estructura de Pantalla 1B**: **Dado que** la persona abre Pantalla 1B, **Cuando** consulta la oferta, **Entonces** observa la composición de la Pantalla 1B Stitch: título «Selecciona tu Plan», subtítulo «Configuración inicial de cuenta y suscripción», tarjeta Kipu Free informativa con badge «Permanente», «No requiere método de pago» y seis filas `check_circle` (núcleo manual y cinco límites), tarjeta estática «Prueba Premium Gratis por 7 días» con tag «Completo», Premium Anual preseleccionado con «Recomendado», «Ahorro equivalente a 50%» y «S/ 29.99 / año», Premium Mensual con «S/ 4.99 / mes», Compra Única Lifetime con «S/ 49.99 pago único», nota `info`, CTA «Confirmar Plan», CTA «Continuar con Plan Free» y footer fiscal; la pantalla no incluye Top Bar ni barra de navegación inferior.
14. **Escenario 14 - Error de sincronización seguro**: **Dado que** una solicitud carece de autenticación, usa una versión no soportada, contiene datos inválidos o encuentra el servicio indisponible, **Cuando** se intenta sincronizar, **Entonces** no cambia la preferencia remota ni el acceso efectivo, no se exponen datos ajenos y una operación reintentable permanece pendiente hasta una oportunidad permitida.
15. **Escenario 15 - Motivo de acceso verificable**: **Dado que** se evalúa una capacidad durante Sprint 1, **Cuando** la capacidad pertenece al núcleo Free, supera un cupo Free o exige Premium, **Entonces** la política devuelve respectivamente `Allowed(FREE_CAPABILITY)`, `Denied(FREE_LIMIT_REACHED)` o `Denied(PREMIUM_ENTITLEMENT_REQUIRED)` sin interpretar una intención como entitlement.

---

### Edge Cases

- ~~Si la elegibilidad para Trial no puede comprobarse, la oferta no promete una prueba y cualquier selección Premium se registra únicamente como `PREMIUM_INTENT`.~~ *(Sustituido: la tarjeta Trial es informativa y estática; `UNKNOWN` conserva el mapping a `PREMIUM_INTENT`.)*
- ~~Si una persona ya consumió el Trial, puede consultar las alternativas Premium, pero no vuelve a ver la promesa de 7 días gratuitos.~~ *(Sustituido: el copy Trial permanece visible; no se activa Trial y la confirmación genera `PREMIUM_INTENT`.)*
- La tarjeta Trial se muestra siempre; `ELIGIBLE`, `INELIGIBLE` y `UNKNOWN` solo determinan la intención persistida al confirmar Mensual o Anual.
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
- **Aislamiento**: Se prueban `SELECT`, `INSERT` y `UPDATE` con dos cuentas distintas por el límite de servicio y mediante intentos directos controlados contra el almacén remoto; los grants directos de producción permanecen revocados.

## Requirements *(mandatory)*

### Reglas de Negocio

- **RN-001 - Kipu Free Permanente**: Kipu Free DEBE permanecer disponible de por vida, sin solicitar método de pago y con el núcleo manual de registro más los límites aprobados.
- **RN-002 - Trial Voluntario**: La tarjeta informativa «Prueba Premium Gratis por 7 días» DEBE mostrarse estáticamente en Pantalla 1B; la elegibilidad verificada DEBE gobernar exclusivamente si Mensual o Anual se registra como `TRIAL_INTENT` o `PREMIUM_INTENT` y nunca activa una prueba real en Sprint 1.
- **RN-003 - Transparencia Comercial**: Antes de confirmar una intención Premium, la persona DEBE poder consultar en la pantalla la duración informativa del Trial, el precio posterior, la frecuencia de renovación y las condiciones de cancelación.
- **RN-004 - Intención sin Concesión Prematura**: Una elección expresada en la Pantalla 1B DEBE registrar solo una preferencia. La activación real de derechos Premium exige una compra verificada en servidor mediante HU-54.

### Functional Requirements

- **FR-001**: El sistema DEBE presentar la Pantalla 1B secuencialmente después del registro exitoso de una cuenta Kipu.
- **FR-002**: ~~La Pantalla 1B DEBE permitir seleccionar Kipu Free o una única alternativa Premium informativa antes de confirmar el plan.~~ *(Sustituido: Kipu Free es una tarjeta informativa no seleccionable y la persona selecciona una alternativa Premium o continúa mediante su CTA dedicado.)* La Pantalla 1B DEBE mostrar Kipu Free como referencia informativa no seleccionable y permitir una única selección Premium, con Anual preseleccionado por defecto.
- **FR-003**: ~~La tarjeta Kipu Free DEBE mostrar S/ 0 de por vida, ausencia de tarjeta y publicidad, uso manual local y los límites de 4 instrumentos, 5 categorías personalizadas, 2 deudas, 2 metas y 2 presupuestos.~~ *(Sustituido para adoptar la composición Stitch 1B.)* La tarjeta Kipu Free DEBE mostrar el badge «Permanente», «No requiere método de pago» y seis filas con icono `check_circle`: núcleo manual de registro, 4 instrumentos, 5 categorías personalizadas, 2 deudas, 2 metas y 2 presupuestos.
- **FR-004**: ~~La tarjeta Premium DEBE presentar el Trial de 7 días como aplicable únicamente cuando la persona sea elegible. Con elegibilidad desconocida PUEDE informar que existe una oferta sujeta a verificación, sin prometer duración ni aplicación.~~ *(Sustituido: la elegibilidad ya no condiciona el copy visual.)* La Pantalla 1B DEBE mostrar siempre la tarjeta «Prueba Premium Gratis por 7 días», tag «Completo» y «Periodo de prueba voluntario sin cobro inmediato», junto con Premium Anual, Premium Mensual y Compra Única Lifetime.
- **FR-005**: ~~Al seleccionar Mensual o Anual con elegibilidad confirmada, la pantalla DEBE mostrar antes de la confirmación los 7 días de prueba, el precio exacto posterior, la frecuencia de renovación y las condiciones de cancelación.~~ *(Sustituido: la información es estática y previa a cualquier selección.)* La pantalla DEBE mostrar siempre, antes de confirmar, la duración informativa de 7 días, el precio posterior exacto, la frecuencia de renovación y las condiciones de cancelación.
- **FR-006**: Al seleccionar «Compra Única Lifetime», la pantalla DEBE informar «Sin periodo de prueba», «S/ 49.99 pago único» y ausencia de renovación; no se activa Trial en Sprint 1.
- **FR-007**: ~~Si la persona ya consumió el Trial o su elegibilidad no está confirmada, la pantalla NO DEBE prometer otro periodo gratuito.~~ *(Sustituido: la tarjeta Trial se muestra estáticamente para fidelidad con Stitch 1B.)* Sprint 1 NO DEBE activar un periodo gratuito real; la elegibilidad solo determina la intención registrada para Mensual o Anual.
- **FR-008**: El botón primario DEBE rotularse "Confirmar Plan" y DEBE confirmar la alternativa Premium seleccionada, con Anual preseleccionado por defecto.
- **FR-009**: ~~Confirmar Kipu Free DEBE registrar `FREE` y permitir continuar sin solicitar método de pago ni datos bancarios.~~ *(Sustituido: Kipu Free ya no es una tarjeta seleccionable.)* El botón secundario "Continuar con Plan Free" DEBE registrar `FREE` y permitir continuar sin solicitar método de pago ni datos bancarios.
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
- **FR-020**: ~~La Pantalla 1B NO DEBE mostrar publicidad ni slogans comerciales.~~ *(Sustituido: se autorizan los badges, mensajes comerciales y footer fiscal de la Pantalla 1B Stitch.)* La Pantalla 1B NO DEBE mostrar barra superior ni barra de navegación inferior.
- **FR-021**: El estado seleccionado, las condiciones comerciales y la acción de confirmación DEBEN ser perceptibles y operables mediante las ayudas de accesibilidad admitidas por la aplicación.
- **FR-022**: La Pantalla 1B DEBE navegar al destino explícitamente suministrado por su flujo anfitrión después de la confirmación local: la entrada de Sprint 1 posterior al registro DEBE suministrar Pantalla 1C (Biometría); Pantalla 15 solo PUEDE ser suministrada por un flujo preexistente fuera del alcance de HU-52.
- **FR-023**: ~~La interfaz DEBE indicar claramente que la contratación mediante Google Play no está habilitada en Sprint 1 y que confirmar una alternativa Premium solo registra interés.~~ *(Sustituido: la nota visible adopta el copy literal de Stitch; la ausencia de compra se conserva como invariante operativo.)* La interfaz DEBE mostrar la nota con icono `info`: «7 días de acceso completo sin costo. El cobro de S/ 29.99 se realizará al término del periodo de prueba. Cancelación directa desde la configuración de cuenta en cualquier momento sin penalización.»
- **FR-024**: La modalidad Mensual, Anual o Lifetime seleccionada DEBE utilizarse para presentar las condiciones y decidir el tipo de intención, pero NO DEBE persistirse como modalidad de compra en Sprint 1; una compra futura exigirá una nueva selección y confirmación en HU-53.
- **FR-025**: Mostrar o abandonar la Pantalla 1B NO DEBE crear una fila de preferencia. El default `'FREE'` solo aplica cuando se realiza una inserción explícita sin otro valor y no constituye evidencia de confirmación, Trial o compra.
- **FR-026**: Todo precio que se almacene o transporte de forma autoritativa DEBE representarse en unidades monetarias menores enteras junto con la moneda `PEN`; la interfaz DEBE mostrar los importes equivalentes en soles como «S/ 29.99 / año», «S/ 4.99 / mes» y «S/ 49.99 pago único».
- **FR-027**: El límite de monetización EP-PLA DEBE entregar a Pantalla 1B un `TrialEligibilitySnapshot` con estado `ELIGIBLE`, `INELIGIBLE` o `UNKNOWN`, procedencia, momento de verificación y vigencia cuando corresponda. Solo el historial de cuenta verificado y vigente PUEDE producir los dos primeros estados; ausencia, expiración, error o evidencia no confiable DEBE producir `UNKNOWN`. La elegibilidad no condiciona el copy estático de Trial y solo determina la intención registrada al confirmar Mensual o Anual.
- **FR-028**: Todo cambio de esquema local o remoto requerido por esta historia DEBE usar una migración versionada, revisable y no destructiva, validada con datos existentes representativos.
- **FR-029**: Una solicitud sin autenticación, con versión no soportada o payload inválido DEBE rechazarse sin modificar estado ni exponer datos; una indisponibilidad transitoria DEBE conservar la operación local como pendiente y reintentable, siempre sin alterar el acceso Kipu Free.
- **FR-030**: Cada confirmación DEBE establecer `selected_at` al momento de la nueva selección y `updated_at` al mismo momento local; cada aplicación remota exitosa DEBE reemplazar `updated_at` por el momento asignado por servidor.
- **FR-031**: El límite remoto DEBE conservar, durante la vida de la cuenta, un recibo por combinación de usuario y `operation_id`, el hash canónico del payload y la mayor `selection_revision` aceptada; esta metadata DEBE eliminarse con la cuenta y su esquema físico se define en el plan.
- **FR-032**: RLS DEBE estar habilitado y forzado para `plan_preferences` y para toda tabla remota user-owned de esta historia; las operaciones del rol ejecutor que no satisfagan `user_id = auth.uid()` DEBEN ser denegadas. Los clientes no reciben DML directo. Una escritura privilegiada de historial verificado DEBE usar una identidad server-only, alcance mínimo y validación explícita del propietario.

### Trazabilidad de Requisitos y Evidencia

| Requisitos | Escenarios o evidencia de aceptación |
|------------|--------------------------------------|
| FR-001, FR-014, FR-022 | Escenarios 1, 5, 7 y 12; SC-001 y SC-005. |
| FR-002 a FR-009, FR-021, FR-023, FR-024 | Escenarios 1, 2, 3, 6, 10 y 13; SC-001, SC-002, SC-004, SC-008 y SC-009. |
| FR-003, FR-020 | Escenario 13; SC-001 y SC-008. |
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
| Encabezado | «Selecciona tu Plan» y «Configuración inicial de cuenta y suscripción». |
| Tarjeta Kipu Free | Tarjeta informativa no seleccionable con badge «Permanente», «No requiere método de pago» y seis filas `check_circle`: núcleo manual de registro, 4 instrumentos, 5 categorías personalizadas, 2 deudas, 2 metas y 2 presupuestos. |
| Tarjeta Trial Premium | Tarjeta estática «Prueba Premium Gratis por 7 días», tag «Completo» y «Periodo de prueba voluntario sin cobro inmediato». |
| Tarjetas Premium | Orden Stitch: Premium Anual preseleccionado con «Recomendado», «Ahorro equivalente a 50%» y «S/ 29.99 / año»; Premium Mensual con «Facturación mensual renovable» y «S/ 4.99 / mes»; Compra Única Lifetime con «Sin periodo de prueba» y «S/ 49.99 pago único». |
| Nota y acciones | Nota `info` literal sobre los 7 días, cobro y cancelación; botón primario "Confirmar Plan" para Premium y botón secundario "Continuar con Plan Free" para registrar `FREE`. Footer: «Validez fiscal y operativa conforme al marco regulatorio en Perú.» |

**Restricciones visuales**:

- La pantalla no contiene Top Bar ni `KipuBottomBar`.
- ~~La pantalla no contiene publicidad ni slogans comerciales.~~ *(Retirado: se autorizan exclusivamente los badges, mensajes comerciales y footer fiscal presentes en la Pantalla 1B Stitch.)*
- ~~La maquetación tomaba como referencia los tokens del documento de diseño previo: Primary `#0F766E`, Ink `#0F172A` y radios de `16dp`/`12dp`.~~ *(Reemplazado por tokens del design system Stitch; la única fuente de verdad visual es `docs/stitch-design-system.md`.)*
- La maquetación adopta el design system Stitch `Kipu Andean Modernist` (Pantalla 1B `b8b4bfdcf384409887e54a975c549797`, proyecto `projects/5775615138851387862`) como única fuente de verdad visual y usa su disposición y componentes como referencia de fidelidad. Tokens registrados en `docs/stitch-design-system.md`: background `#F7F9FB`; surface de tarjetas `#FFFFFF`; onSurface `#191C1E`; onSurfaceVariant `#3E4947`; outline `#6E7977`; outlineVariant `#BDC9C6`; primary (CTA) `#0F766E` con pressed `#005C55`; ring de selección (surface_tint) `#006A63`; secondary `#216963`; secondaryContainer `#A8ECE5`; onSecondaryContainer `#266D68`; error `#BA1A1A`; dark background `#0B1220`. Radios de `16dp` (tarjetas) y `12dp` (controles/CTA). Tipografía Inter obligatoria en toda la pantalla; montos con números tabulares (`tnum`). Grilla base 8dp, márgenes de página 16dp, separación de secciones 24dp, targets mínimos 48dp.
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
| Kipu Free | `amount_minor = 0`, `currency = PEN` | Badge «Permanente» y «No requiere método de pago» | No aplica. |
| Mensual | `amount_minor = 499`, `currency = PEN` | S/ 4.99 / mes | La tarjeta Trial es informativa y estática; la elegibilidad define la intención registrada. |
| Anual | `amount_minor = 2999`, `currency = PEN` | S/ 29.99 / año | La tarjeta Trial es informativa y estática; la elegibilidad define la intención registrada. |
| Lifetime | `amount_minor = 4999`, `currency = PEN` | S/ 49.99, pago único | No aplica y no renueva. |

Estos importes son configuración comercial informativa de Sprint 1. Google Play será la fuente comercial aplicable al iniciar una compra real en HU-53; cualquier diferencia futura exige actualizar y aprobar los artefactos correspondientes antes de mostrar o cobrar nuevos valores.

#### Estructuras Locales Complementarias

- **`feature_access_cache`**: Almacena la política de acceso efectiva y la fecha de expiración conocida localmente. Una intención comercial no puede escribir una autorización Premium en esta estructura.
- **`sync_outbox`**: Cola atómica e idempotente de cambios locales pendientes. Cada operación de selección incluye un `operation_id` UUID estable, una `selection_revision` entera y monotónica por usuario, `contract_version: 1`, el valor de `selection` y `selected_at`. La operación se guarda en la misma transacción Room que `plan_preferences`.
- **DataStore**: Puede conservar únicamente estado efímero de presentación o un checkpoint de onboarding. No es fuente autoritativa de la preferencia, del orden de revisiones ni de derechos de acceso y no reemplaza la transacción Room.

#### Fuente de Elegibilidad

El límite de monetización EP-PLA es propietario del contrato de lectura `TrialEligibilitySnapshot`, compuesto por `status`, `source`, `verified_at` y `valid_until`. `status` admite `ELIGIBLE`, `INELIGIBLE` o `UNKNOWN`; los dos primeros requieren `source = VERIFIED_ACCOUNT_HISTORY`, `verified_at` y una vigencia verificable. Una elegibilidad positiva expirada se degrada a `UNKNOWN`; una inelegibilidad por consumo confirmado puede ser permanente y usar `valid_until = null`. Sin esa evidencia, el productor devuelve `UNKNOWN`.

En Sprint 1, `UNKNOWN` es un resultado válido y no bloqueante: la persona ve la tarjeta Trial informativa estática y puede registrar interés, pero no recibe un Trial real, derechos ni cobros. La integración que alimente este contrato desde Google Play pertenece a HU-53/HU-54. Un cache local puede conservar una denegación verificada, pero nunca transformar `UNKNOWN` en `ELIGIBLE`.

#### Seguridad RLS

RLS debe estar habilitado en `plan_preferences`. Sus políticas `SELECT`, `INSERT` y `UPDATE` deben restringirse estrictamente a filas cuyo `user_id = auth.uid()`. Además, `/plans/selection` deriva el propietario de la sesión autenticada, rechaza cualquier identidad de payload que no coincida y aplica autorización por objeto antes de acceder a datos. La autenticación no sustituye esta autorización y el límite de servicio no depende únicamente de RLS.

#### Invariante Crítica de Dominio

`'TRIAL_INTENT'` y `'PREMIUM_INTENT'` representan intención comercial y NUNCA un entitlement. Ninguno de esos valores puede conceder privilegios Premium en `entitlements`, `feature_access_cache`, la interfaz o cualquier evaluación de capacidades. Hasta que HU-54 verifique una compra en servidor, el acceso efectivo es Kipu Free.

#### Contratos de Capacidades y Selección

- **`GET /plans/eligibility`**: Lectura autenticada y no editable por cliente de la proyección de historial verificado. Devuelve `UNKNOWN` cuando no existe evidencia vigente.
- **`POST /plans/selection`**: Límite autenticado que persiste exclusivamente preferencias, nunca compras, suscripciones ni entitlements. La versión inicial del contrato es `1`.
- **`FeatureAccessPolicy`**: Interfaz de dominio determinista que devuelve `Allowed` o `Denied` con un motivo. Una preferencia de intención, por sí sola, siempre debe evaluarse con los derechos efectivos de Kipu Free.

El request de `/plans/selection` contiene `contract_version`, `operation_id`, `selection_revision`, `selection` y `selected_at`. La identidad del usuario se obtiene de la sesión y no de un identificador confiado del cuerpo. Toda respuesta exitosa contiene el resultado `APPLIED`, `DUPLICATE`, `STALE` o `CONFLICT`, la revisión aceptada, la preferencia vigente y una instantánea obligatoria de los cupos Free.

- `APPLIED`: la revisión es posterior a la vigente y reemplaza la preferencia.
- `DUPLICATE`: la combinación de usuario y `operation_id` ya fue aplicada y el payload canónico es idéntico; se devuelve el resultado vigente sin repetir efectos.
- `STALE`: la revisión es anterior a la vigente; se conserva la preferencia más reciente.
- `CONFLICT`: un `operation_id` ya conocido llega con payload diferente, o la misma revisión llega con otra identidad o contenido; se conserva la preferencia vigente, se registra el conflicto para diagnóstico sin datos sensibles y el cliente reconcilia con la respuesta remota.

El servicio conserva durante la vida de la cuenta un recibo único por `(user_id, operation_id)`, su hash de payload canónico, el resultado aplicado y la mayor revisión aceptada por usuario. Esta metadata se elimina con la cuenta. Su esquema físico se decide en `plan.md`, pero su unicidad, retención y comportamiento observable son obligatorios.

Los errores se clasifican sin devolver preferencias ni cupos a una identidad no autorizada:

- `UNAUTHENTICATED`: rechazo sin cambio remoto ni exposición de datos; la operación pasa a esperar una nueva sesión válida del mismo usuario antes de reintentarse con payload idéntico.
- `FORBIDDEN`: rechazo terminal de la operación sin cambio remoto, reintento automático ni exposición de datos.
- `UNSUPPORTED_VERSION` o `INVALID_REQUEST`: rechazo terminal de esa operación; se conserva la preferencia local y se expone un estado de sincronización no sensible para corrección, sin bucle automático infinito.
- `UNAVAILABLE`: fallo reintentable; la operación permanece pendiente con la misma identidad y revisión hasta una oportunidad permitida.

Ningún resultado exitoso o de error modifica el entitlement efectivo ni bloquea el avance de onboarding ya confirmado localmente.

La revisión pertenece a la operación de sincronización y no agrega una modalidad de compra a `plan_preferences`. El contrato puede informar los cupos aprobados, pero en Sprint 1 no bloquea objetos, no selecciona excedentes y no elimina datos; esas conductas pertenecen a HU-57.

#### Flujo Local-First

1. La persona confirma la alternativa Premium preseleccionada o elegida mediante "Confirmar Plan", o presiona "Continuar con Plan Free" para registrar `FREE` en la Pantalla 1B.
2. La aplicación incrementa la revisión del usuario y guarda síncronamente en una transacción Room la selección y la operación correspondiente de `sync_outbox`.
3. La navegación continúa al destino suministrado por el flujo anfitrión aunque no exista red. El onboarding posterior al registro suministra Pantalla 1C; Pantalla 15 solo puede proceder de un flujo preexistente fuera del alcance de HU-52.
4. WorkManager sincroniza la fila con Supabase mediante `/plans/selection` al detectar conectividad.
5. Los reintentos conservan `operation_id`, `selection_revision` y `contract_version`; un resultado `DUPLICATE`, `STALE` o `CONFLICT` no repite ni revierte efectos y reconcilia el cliente con la revisión aceptada.
6. La respuesta actualiza la preferencia remota conocida y los cupos informativos, pero no borra datos ni ejecuta la gestión de downgrade de HU-57.
7. La sincronización de una intención no modifica `entitlements` ni convierte `feature_access_cache` en una fuente de Premium no verificado.

### Key Entities *(include if feature involves data)*

- **Preferencia de Plan (`plan_preferences`)**: Selección comercial confirmada por una persona. Contiene propietario, valor de intención y momentos de selección y actualización; no representa derechos de acceso.
- **Opción Comercial**: Alternativa efímera visible en Pantalla 1B: Free, Mensual, Anual o Lifetime, con importe en unidades menores, moneda, presentación, periodicidad, condiciones y relación aplicable con el Trial. Free se presenta como tarjeta informativa y se registra desde el CTA secundario; no constituye una compra.
- **Elegibilidad de Trial**: Resultado verificado `ELIGIBLE`, `INELIGIBLE` o `UNKNOWN` que determina la intención registrada al confirmar Mensual o Anual. La tarjeta visual de 7 días se muestra de forma estática para todas las personas.
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
- **SC-002**: ~~El 100% de las ofertas Mensual y Anual elegibles muestra, antes de confirmar, los 7 días de prueba, el precio posterior exacto, la frecuencia de renovación y las condiciones de cancelación.~~ *(Sustituido: la tarjeta Trial es estática y no depende de la elegibilidad.)* El 100% de los recorridos presenta antes de confirmar la tarjeta Trial estática con duración, precio posterior, frecuencia de renovación y condiciones de cancelación.
- **SC-003**: Se producen 0 cobros, 0 suscripciones, 0 Trials activos y 0 concesiones Premium en todos los recorridos de Sprint 1, incluidos intención Premium, abandono, uso offline y reintentos.
- **SC-004**: ~~El 100% de las personas identificadas como no elegibles, o cuya elegibilidad sea desconocida, deja de recibir una promesa de Trial de 7 días.~~ *(Sustituido: la tarjeta Trial permanece visible como copy informativo.)* El 100% de las personas no elegibles o con elegibilidad desconocida que confirma una opción Premium se registra como `PREMIUM_INTENT` y no recibe Trial, derechos ni cobros.
- **SC-005**: En al menos el 95% de las 40 confirmaciones de la matriz mínima, la confirmación local y el avance al siguiente paso se completan en 2 segundos o menos, incluso sin conectividad.
- **SC-006**: El 100% de las selecciones confirmadas offline permanece disponible después de reiniciar. Bajo las condiciones de medición de la matriz, al menos el 95% se reconcilia remotamente en un máximo de 15 minutos tras recuperar conectividad estable; el resto permanece íntegro y se procesa en la primera oportunidad permitida.
- **SC-007**: El 100% de los intentos de acceso cruzado entre cuentas es rechazado sin revelar ni modificar preferencias ajenas.
- **SC-008**: ~~Al menos 18 de los 20 participantes de la prueba de comprensión identifican correctamente, en el primer intento, que Free es permanente, que el Trial es voluntario y que confirmar Premium en Sprint 1 no activa una compra.~~ *(Sustituido para validar el copy literal Stitch.)* Al menos 18 de los 20 participantes de la prueba de comprensión identifican correctamente, en el primer intento, que Free es permanente, que la nota `info` describe el modelo comercial futuro y que confirmar Premium en Sprint 1 no activa una compra ni produce un cobro.
- **SC-009**: El 100% de los ocho recorridos críticos definidos en la matriz puede completarse con lector de pantalla y texto ampliado al 200%, conservando perceptible la opción seleccionada y sus condiciones comerciales.
- **SC-010**: En el 100% de las 30 operaciones de sincronización de la matriz, un duplicado no repite efectos, una revisión obsoleta no reemplaza la vigente y un conflicto conserva la última preferencia aceptada.
- **SC-011**: En el 100% de los casos con datos por encima de un cupo Free, sincronizar una preferencia conserva todos los registros y sus efectos financieros sin borrado ni reescritura.
- **SC-012**: En el 100% de los casos de falta de autenticación, autorización denegada, versión no soportada, payload inválido e indisponibilidad, no se concede Premium, no cambia la preferencia remota y no se exponen datos de otra cuenta.
- **SC-013**: El 100% de los datos representativos previos sobrevive a las migraciones requeridas con el mismo propietario, selección y orden temporal observable.

## Assumptions

- HU-01 entrega una cuenta autenticada y un identificador estable antes de entrar a Pantalla 1B.
- Los precios S/ 4.99 mensual, S/ 29.99 anual y S/ 49.99 Lifetime son la configuración comercial aprobada para mostrar en Sprint 1; su cobro y validación pertenecen a historias futuras.
- Anual es la alternativa Premium preseleccionada al abrir Pantalla 1B; Kipu Free se confirma exclusivamente mediante el CTA secundario.
- La tarjeta Trial de 7 días se presenta estáticamente junto a las alternativas Premium. La elegibilidad decide si Mensual o Anual se registra como `TRIAL_INTENT` o `PREMIUM_INTENT`; Lifetime se registra como `PREMIUM_INTENT`, no activa Trial y no renueva.
- Una persona elegible que confirma Mensual o Anual genera `TRIAL_INTENT`; una persona no elegible o con elegibilidad desconocida genera `PREMIUM_INTENT` si conserva su interés.
- El historial remoto verificado de la cuenta es la fuente autoritativa de elegibilidad. Cuando no pueda consultarse o no sea confiable, la experiencia conserva el copy Trial estático, registra `PREMIUM_INTENT` para Mensual o Anual y no concede Trial ni Premium.
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

## Sprint 2 Addendum: HU-57 — cupos, selección y downgrade

Este addendum amplía EP-PLA para el Sprint 2 sin cambiar el alcance comercial de HU-52 ni tratar `FREE`, `TRIAL_INTENT` o `PREMIUM_INTENT` como un entitlement.

### Invariantes de HU-57

- Límites Free: 4 instrumentos computables, 5 raíces personalizadas, 2 deudas, 2 metas y 2 presupuestos activos.
- Efectivo, cuenta virtual de metas y cuenta interna de pasivo de crédito no consumen un cupo de instrumento adicional.
- Una selección es una **instantánea completa por usuario y grupo**. Cada revisión representa el estado local vigente y puede saltar revisiones intermedias coalescidas offline; el servidor solo acepta revisiones estrictamente mayores que la cabecera aceptada. Reintentar la misma identidad/payload devuelve el recibo previo; revisiones iguales o anteriores se reconcilian como `STALE`.
- La aceptación, el downgrade y los conflictos nunca eliminan ni alteran movimientos, historial, categorías, instrumentos o saldos. Solo cambia qué recursos pueden usarse para nuevas operaciones bajo Free.
- Premium retira `LOCKED_BY_PLAN` únicamente con entitlement verificado por una autoridad confiable; una intención del cliente nunca concede acceso.
- En S2, la UI de selección cubre grupos ya implementados (instrumentos y categorías); los demás grupos conservan la política/domain contract y se integran al llegar sus épicas. Sus pantallas no se adelantan.

### Aceptación técnica del addendum

1. Un usuario excedido puede elegir qué categorías raíz e instrumentos mantiene disponibles; los excedentes siguen en sus listas/historial con estado de plan distinto de archivo/inactividad.
2. Efectivo y los recursos de otros grupos dentro de límite siguen disponibles aunque un grupo esté excedido.
3. Cambios locales rápidos/offline se sincronizan como la última instantánea con una identidad determinista; un timeout ambiguo no crea otra operación.
4. `STALE`/`CONFLICT` conservan la instantánea local vigente, avanzan a una revisión válida con backoff y no marcan el trabajo como sincronizado por error.
5. El worker comprueba que el token corresponde al propietario de la cola antes de enviar y no incluye propietario en el payload confiado por el servidor.
6. Pruebas SQL cubren revisiones omitidas, stale, duplicate, conflicto de idempotency key, propiedad, tipo de recurso y conservación de datos.

### Gate de integración remota

La verificación local de migrations/pgTAP no demuestra compatibilidad del proyecto Supabase remoto. El estado actual y los errores de catálogo están registrados en [`docs/s1-s2-remote-backend-readiness.md`](../../docs/s1-s2-remote-backend-readiness.md). No se declara habilitada la sincronización remota hasta que un despliegue ordenado y validado en un entorno desechable/staging confirme el contrato y RLS; ninguna intención de plan habilita Premium.

## Sprint 3 Addendum: HU-53, HU-54 y HU-56

Este refinamiento agrega 21 puntos de Sprint 3 a EP-PLA sin reemplazar los criterios, las decisiones ni la evidencia histórica de HU-52 (Sprint 1) y HU-57 (Sprint 2). Las secciones anteriores describen el alcance de sus sprints originales; este addendum define el alcance vigente de facturación y reconocimiento de compras para Sprint 3.

### Control del incremento S3

| Historia | Puntos | Dependencia y condición de cierre |
| :--- | :---: | :--- |
| HU-53 — Compra mensual, anual y Lifetime | 8 | HU-52 bloqueante; HU-54 parcial. Compra integrada solo cierra después de verificación de servidor. |
| HU-54 — Verificación y reconocimiento de compras | 8 | HU-01 y HU-52 bloqueantes; HU-53 parcial; backend autenticado y Google Play consultable. |
| HU-56 — Estados de compra y cancelación | 5 | HU-54 bloqueante; requiere una proyección de compra verificada. |
| **Total Sprint 3 de EP-PLA** | **21** | Verificación bajo demanda. RTDN y restauración integral permanecen en HU-55 (Sprint 5). |

### Reglas comunes y decisiones normativas

- El producto contratado, el estado devuelto por Google Play y el entitlement efectivo de Kipu son dimensiones independientes. El tipo de producto procede del catálogo; el estado de compra y vigencia se toman de la respuesta verificada de Play; el acceso efectivo se calcula exclusivamente desde compras verificadas por servidor.
- La interfaz o un callback de Google Play en Android solo informa que hubo una interacción o entrega un token candidato. Ningún callback, estado local, preferencia, selección ni respuesta no verificada concede Premium.
- Mensual y Anual son suscripciones recurrentes con precios objetivo de referencia S/ 4.99 y S/ 29.99. Lifetime es una compra única no consumible con precio objetivo de referencia S/ 49.99. La fuente cobrable y el texto de precio son los detalles localizados devueltos por Google Play; si no existe oferta, no se inventa ni se presenta un importe como cobrable.
- Si Google Play ofrece prueba gratuita, esta puede pertenecer únicamente a Monthly/Annual y se muestra solo según la oferta y elegibilidad que devuelva Play. El precio, la duración y la fecha exacta del primer cobro se explican antes de confirmar. Lifetime no tiene prueba propia.
- La tarjeta estática de Trial de HU-52 pertenece al onboarding y solo registra una intención; no representa una oferta disponible ni inicia Billing. La compra de HU-53 ocurre en la superficie de compra de planes y muestra únicamente las ofertas/trials que Google Play devuelve como disponibles y elegibles; si no hay oferta, explica indisponibilidad.
- Una compra `PENDING` conserva el acceso previo y no crea ni amplía Premium. La compra puede verificarse de nuevo cuando Play reporte el cambio a `PURCHASED`; Sprint 3 no depende de RTDN para reconocer ese cambio.
- El backend deriva el `user_id` de la sesión autenticada de Supabase; nunca confía en un `user_id` suministrado por el cliente. El purchase token es opaco: se envía al backend por el canal autenticado, se resume mediante SHA-256 sobre sus bytes UTF-8 exactos y no se persiste ni registra en claro. La unicidad del hash y la asociación al propietario rechazan el uso del mismo token por otra cuenta sin reasignarlo.
- La fuente de dominio documenta `billing_products`, `billing_purchases` e `internal.billing_events`, pero no una tabla independiente `subscriptions` ni una tabla remota `entitlements`. En S3 la compra recurrente se representa con `billing_purchases`; el entitlement efectivo es una proyección derivada de registros verificados y puede almacenarse localmente solo como cache aislado por cuenta. No se crea otra tabla de suscripciones o entitlements en este alcance.
- Existe una discrepancia documental que debe resolverse con una migración aditiva antes de aceptar el producto Lifetime: `billing_products.plan_type` admite actualmente `FREE`, `PRO_MONTHLY` y `PRO_ANNUAL`, aunque los requisitos incluyen Lifetime. El catálogo debe admitir `PRO_LIFETIME`. La entidad actual tampoco permite `REVOKED` en `entitlement_state`; la migración debe admitir ese estado. `CANCELED_ACTIVE` es el estado de ciclo de vida normalizado de Kipu, derivado de compra cancelada más `expires_at` vigente; no reemplaza el estado bruto de compra `CANCELLED` de la fuente.
- `internal.billing_events` conserva eventos de verificación saneados y append-only. La recepción asíncrona de RTDN, la reconciliación periódica y la restauración multidispositivo completa no se adelantan desde HU-55.

### User Story Sprint 3.1 — HU-53: Compra mensual, anual y Lifetime

Como usuario de Kipu, quiero contratar un plan mostrado por Google Play, para habilitar Premium con precio y duración claros.

**Precondición**: Productos configurados en el canal de pruebas y verificación HU-54 integrada en el mismo sprint. HU-52 debe estar disponible. La compra solo se considera completa tras respuesta de verificación de backend.

**Reglas de negocio HU-53**:

1. Mensual y Anual son recurrentes; Lifetime es una compra única no consumible.
2. Los precios de referencia son S/ 4.99, S/ 29.99 y S/ 49.99; la UI presenta precio y moneda localizados por Play.
3. El retorno de UI sin verificación remota no habilita acceso.
4. Lifetime no tiene periodo de prueba propio.

**Criterios de aceptación oficiales**:

#### Escenario 1: Mensual

- **Dado que** el producto mensual está disponible,
- **Cuando** completa compra y verificación,
- **Entonces** se muestra Premium con vigencia y renovación.

#### Escenario 2: Anual

- **Dado que** elige oferta anual,
- **Cuando** confirma en Play,
- **Entonces** se usa precio y condiciones reales mostrados.

#### Escenario 3: Lifetime

- **Dado que** no existe renovación recurrente activa,
- **Cuando** compra y se verifica Lifetime,
- **Entonces** queda acceso sin caducidad comercial ni consumo de producto.

#### Escenario 4: Cancelar

- **Dado que** está en la hoja de compra,
- **Cuando** cancela,
- **Entonces** mantiene su acceso previo.

#### Escenario 5: Sin productos

- **Dado que** Play no devuelve oferta,
- **Cuando** abre planes,
- **Entonces** se explica indisponibilidad y no se inventa un precio cobrable.

### User Story Sprint 3.2 — HU-54: Verificación y reconocimiento de compras

Como usuario de Kipu, quiero obtener Premium solo cuando mi compra sea válida, para evitar pérdidas o activaciones indebidas.

**Precondición**: Compra reportada; backend autenticado y proveedor consultable. HU-01 y HU-52 deben estar disponibles; HU-53 puede avanzar con contrato y fixtures, y se cierra integrada.

**Reglas de negocio HU-54**:

1. El servidor valida usuario, aplicación, producto, estado y vigencia consultando Google Play Developer API con el token recibido.
2. `PENDING` no desbloquea Premium.
3. El reconocimiento de una compra inicial es idempotente. Lifetime nunca se consume; los productos no consumibles ni las suscripciones no se procesan mediante una operación de consumo.
4. Un token asociado a otra cuenta Kipu se rechaza sin cambiar el propietario.
5. Una repetición de la misma verificación no duplica compra, entitlement ni reconocimiento. Una falla temporal del proveedor produce `RETRYABLE`, conserva el entitlement previo y permite reintentar; no se presenta al usuario como una compra `PENDING` de Google Play.

**Contrato lógico de verificación**: `POST /billing/verify`, expuesto por la Edge Function Supabase `verify-purchase`, requiere JWT de usuario en `Authorization` y recibe `productId` y `purchaseToken`. La identidad de usuario procede exclusivamente del JWT. El resultado normalizado informa `VERIFIED`, `PENDING`, `REJECTED` o `RETRYABLE`, además de producto, estado de compra, ciclo de vida y vigencia aplicables. En este contrato de Billing, `PENDING` significa que Play confirma que el pago aún está pendiente; `RETRYABLE` significa que Kipu no pudo completar temporalmente la verificación con el proveedor. El `PENDING` de la outbox de selección HU-52 es un estado local de sincronización distinto y no forma parte del contrato de Billing. El contrato nunca devuelve el token ni permite escribir directamente en tablas de facturación desde Android. Token asociado a otra cuenta se rechaza con conflicto de propiedad.

**Criterios de aceptación oficiales**:

#### Escenario 1: Válida

- **Dado que** Play confirma compra del usuario,
- **Cuando** se verifica,
- **Entonces** se concede acceso y reconoce entrega una vez.

#### Escenario 2: Pendiente

- **Dado que** la compra sigue PENDING,
- **Cuando** solicita activar,
- **Entonces** se mantiene el acceso previo y se informa que el pago sigue pendiente en Google Play; no se concede Premium.

#### Escenario 3: Repetida

- **Dado que** ya se reconoció el token,
- **Cuando** reintenta verificación,
- **Entonces** no duplica compra ni permiso.

#### Escenario 4: Otra cuenta

- **Dado que** el token pertenece a otro usuario Kipu,
- **Cuando** se presenta desde esta sesión,
- **Entonces** se rechaza sin reasignar propietario.

#### Escenario 5: Fallo

- **Dado que** el proveedor no responde,
- **Cuando** se solicita verificar,
- **Entonces** se informa que no fue posible verificar temporalmente (`RETRYABLE`), se conserva el acceso previo y se permite reintentar, sin presentarlo como pago `PENDING` de Google Play.

### User Story Sprint 3.3 — HU-56: Estados de compra y cancelación

Como usuario de Kipu, quiero consultar mi vigencia aunque cancele renovación, para usar el periodo que ya tengo autorizado.

**Precondición**: Existe proyección de compra verificada para el usuario.

**Separación y matriz de ciclo de vida**:

| Estado de ciclo de vida Kipu | Evidencia normalizada de Play | Premium efectivo | Comportamiento |
| :--- | :--- | :---: | :--- |
| `ACTIVE` | Compra `PURCHASED`, vigencia recurrente activa | Sí | Acceso hasta la vigencia verificada; mostrar próxima renovación cuando Play la informe. |
| `IN_GRACE_PERIOD` | Play confirma periodo de gracia | Sí, mientras Play lo autorice | Conservar acceso y mostrar estado de pago pendiente de resolver. |
| `ACCOUNT_HOLD` | Play confirma hold o pausa que suspende acceso | No | Aplicar Free temporalmente y conservar datos. |
| `CANCELED_ACTIVE` | Compra `CANCELLED` y `now() < expires_at` | Sí | No renueva; mantiene acceso hasta `expires_at` y muestra «No se renovará». |
| `EXPIRED` | Play confirma vencimiento o `expires_at` cumplido | No | Volver a Free sin borrar datos ni historial. |
| `REVOKED` | Play confirma revocación | No para esa compra | Retirar el acceso que dependía de esa compra, sin borrar datos. |

Lifetime verificado se representa como acceso `ACTIVE` con `expires_at = NULL`. Al calcular permisos, una compra Lifetime verificada vigente prevalece sobre la expiración, cancelación o revocación de otra suscripción. Un periodo `PENDING` nunca aparece como estado con Premium.

**Criterios de aceptación oficiales**:

#### Escenario 1: Cancelada vigente

- **Dado que** canceló renovación pero no terminó el periodo,
- **Cuando** abre Kipu,
- **Entonces** mantiene Premium hasta vencimiento y ve No se renovará.

#### Escenario 2: Expirada

- **Dado que** el proveedor confirma fin del periodo,
- **Cuando** revalida,
- **Entonces** pasa a Free sin borrar datos.

#### Escenario 3: Gracia

- **Dado que** el proveedor mantiene acceso durante gracia,
- **Cuando** consulta permiso,
- **Entonces** conserva Premium según vigencia autorizada.

#### Escenario 4: Revocación parcial

- **Dado que** se revoca una compra pero hay Lifetime válido,
- **Cuando** recalcula acceso,
- **Entonces** conserva el permiso procedente de Lifetime.

#### Escenario complementario 5: Cuenta en hold

- **Dado que** Play confirma `ACCOUNT_HOLD` o una pausa que suspende el derecho,
- **Cuando** Kipu revalida la compra,
- **Entonces** deniega Premium de esa compra y conserva los datos del usuario.

#### Escenario complementario 6: Revocación sin Lifetime

- **Dado que** Play revoca la compra y no existe otra compra Premium verificada vigente,
- **Cuando** Kipu recalcula el acceso,
- **Entonces** aplica Kipu Free sin borrar datos ni conceder vigencia por el tiempo restante del periodo revocado.

### Requisitos funcionales y criterios de éxito S3

- **RN-005 — Verdad del proveedor**: Solo la verificación remota con Google Play puede confirmar una compra; el cliente no escribe compras ni entitlements.
- **RN-006 — Propiedad e idempotencia**: El hash SHA-256 único del token se vincula a una sola cuenta Kipu; reintentos devuelven el resultado canónico y no reasignan propietario.
- **RN-007 — Acceso temporal**: Cancelar renovación no acorta la vigencia ya pagada. Gracia conserva acceso solo mientras Play lo autorice; hold, expiración y revocación no conceden acceso de esa compra.
- **FR-033**: El catálogo mensual/anual/Lifetime proviene de productos y ofertas consultados a Google Play; la app muestra precio y moneda localizados y no cobra desde un precio de referencia estático.
- **FR-034**: La pantalla anual distingue visualmente la recomendación sin afirmar ahorro no calculado a partir de precios localizados; Lifetime se identifica como pago único permanente, sin renovación ni trial propio.
- **FR-035**: La compra mensual/anual puede mostrar trial solo cuando Play devuelve una oferta elegible y debe presentar duración y fecha exacta del primer cobro antes de confirmar.
- **FR-036**: Una notificación/callback local de compra pasa a estado de verificación y no cambia el acceso efectivo hasta la respuesta verificada del backend.
- **FR-037**: `verify-purchase` valida JWT, identidad del usuario, paquete/aplicación, producto, token, estado y vigencia contra Google Play Developer API antes de persistir compra y derivar permiso.
- **FR-038**: Un pago `PENDING` confirmado por Play no crea ni extiende Premium; una falla temporal de consulta produce `RETRYABLE`, conserva el entitlement previo y permite reintento sin confundirlo con el estado de pago.
- **FR-039**: El backend calcula un hash SHA-256 del purchase token, aplica unicidad y rechaza un token ya asociado a otro `user_id`; el token en claro no se almacena ni aparece en logs.
- **FR-040**: Tras persistir y conceder el entitlement de una compra inicial que Play confirme como `PURCHASED`, el backend consulta acknowledgement state y reconoce de forma idempotente solo si falta reconocimiento. Lifetime no se consume; la app cliente no hace reconocimiento autoritativo.
- **FR-041**: `billing_products`, `billing_purchases` e `internal.billing_events` aplican el acceso mínimo y RLS; el cliente no inserta, actualiza ni elimina compras/eventos y secretos de Google/Supabase permanecen solo en servidor.
- **FR-042**: Producto, estado bruto de compra, ciclo de vida normalizado y entitlement efectivo se mantienen separados; las reglas de los seis estados y Lifetime siguen la matriz HU-56.
- **FR-043**: El estado `CANCELED_ACTIVE` conserva Premium solo hasta el fin de la vigencia verificada y comunica «No se renovará»; `EXPIRED` o `REVOKED` retiran acceso sin borrar datos.
- **FR-044**: Mientras no exista RTDN en S3, Kipu vuelve a consultar y verificar compras bajo demanda al reabrir la experiencia de compra/estado; no declara soporte de actualización asíncrona en tiempo real.
- **FR-045**: La UI distingue el pago `PENDING` confirmado por Play (pago pendiente, sin Premium) de la falla de verificación `RETRYABLE` (no se pudo verificar temporalmente, acceso previo conservado y acción de reintento); también distingue procesamiento, verificación, éxito verificado e indisponibilidad. Usa la hoja/superficie de compra existente y no inventa una nueva ruta de resultado fuera del prototipo.
- **FR-046**: Los precios conservan su espacio durante la consulta; selección, carga y confirmación tienen feedback perceptible y respetan la preferencia del sistema por movimiento reducido.
- **FR-047**: Las tarjetas y botones interactivos reservan mínimo 48×48dp, exponen rol/selección/estado semántico, usan Inter y tokens Stitch, mantienen contraste WCAG AA 4.5:1 para texto normal y buscan AAA 7:1 cuando la combinación de tokens lo permita.
- **SC-014**: En las pruebas de callback UI, pago `PENDING`, token inválido, token de otra cuenta y falla temporal del proveedor, el cliente no habilita Premium ni altera el entitlement efectivo; la UI distingue `RETRYABLE` de pago `PENDING`.
- **SC-015**: Los tres productos muestran exclusivamente precios localizados de Play; catálogo vacío presenta indisponibilidad y no un precio cobrable inventado.
- **SC-016**: Repetir la verificación de un token no duplica registro, permiso ni acknowledge; una compra Lifetime verificada nunca es consumida.
- **SC-017**: Pruebas de dominio cubren `ACTIVE`, `IN_GRACE_PERIOD`, `ACCOUNT_HOLD`, `CANCELED_ACTIVE`, `EXPIRED`, `REVOKED`, `PENDING` y la precedencia de Lifetime.
- **SC-018**: La cancelación conserva acceso hasta el `expires_at` verificado, luego pasa a Free; expiración y revocación no eliminan movimientos, historial ni datos del usuario.
- **SC-019**: Las pantallas de plan cumplen targets 48dp y contraste WCAG AA; los tests de UI verifican selección, carga sin saltos, fecha del primer cobro si hay trial y acceso al administrador de suscripciones de Google Play.

### Contrato de interfaz y estados visuales

- Selector de tarjetas con Anual destacado como opción recomendada, Mensual y Lifetime diferenciados; el badge del Anual usa «Más popular» para evitar prometer un porcentaje de ahorro cuando el precio localizado varía.
- La selección usa fondo blanco sobre `background`, anillo activo de 2dp y `surface_tint`; CTA usa `primary_container` y estado pressed `primary`. Lifetime presenta «Pago único para siempre» y ausencia de renovación/trial sin letra pequeña.
- Durante la consulta de catálogo, shimmer conserva las dimensiones del precio y no desplaza contenido. La transición de compra abierta a verificación y resultado preserva continuidad; el check de éxito aparece solo después de confirmación backend.
- Éxito verificado, pago pendiente e indisponibilidad/error se expresan con copy directo y acciones adecuadas dentro de la superficie existente: confirmación sobria; instrucciones para completar/reintentar pago pendiente; explicación amigable y reintento para error recuperable. No hay pantalla independiente de éxito/fallo en los prototipos actuales.
- La interacción de selección usa microescala hasta `1.02` y elevación con spring corto; shimmer usa pulso lineal; el check verificado usa transición breve y sobria. El movimiento no esencial se reduce o se sustituye por feedback estático cuando el usuario solicita movimiento reducido.
- El detalle de condiciones muestra la fecha real del primer cobro si hay trial, renovación/vigencia y enlace de gestión de Google Play. El enlace no amplía Sprint 3 a la pantalla completa «Mi Plan» de HU-60.

**Trazabilidad de fuente y discrepancias**: criterios y dependencias proceden de `KipuApp/Kipu md/02_Kipu_V4.2_Product_Backlog.md` (secciones HU-53/HU-54/HU-56); entidades de `KipuApp/Kipu md/03_Kipu_V4.2_Arquitectura_y_Datos.md` §13 y `KipuApp/Entidades/V4.2_Entidades.md` §11.1–11.3; pantallas de los documentos de Prototipo y `Stich Prompts.md`. AGY confirmó que S3 verifica bajo demanda en `/billing/verify`, que RTDN pertenece a HU-55 y que el constraint de producto actual omite Lifetime. La Edge Function física se denomina `verify-purchase`; la ruta lógica conserva `/billing/verify` para mantener la convención documentada.

## Requisitos funcionales y criterios de éxito S4 — HU-58 / HU-59

### Historias de usuario

#### HU-58 — Aplicar capacidades Free y Premium

Como persona usuaria de Kipu, quiero que cada capacidad respete mi derecho efectivo y verificado para conservar Kipu Free y saber cuándo una función requiere Premium.

**Prioridad**: Alta · **Estimación**: 8 puntos · **Dependencias**: HU-52, HU-57 y verificación HU-54.

**Criterios de aceptación**

1. La operación manual, el registro local, la sincronización y las consultas básicas de historial siguen disponibles en Free.
2. La automatización de captura/OCR/categorización, el análisis histórico y los filtros avanzados solo se autorizan con un entitlement Premium efectivo y verificado.
3. Para historial, búsqueda por texto, rango de fechas y tipo (Gasto, Ingreso o Transferencia) es básica; los criterios por cuenta, tarjeta, categoría, comercio, monto, fuente o estado son avanzados. Una consulta con cualquier criterio avanzado requiere Premium.
4. La decisión se aplica en dominio antes de acceder a datos o ejecutar una operación protegida; la UI no es autoridad de acceso.
5. Una consulta avanzada sin permiso conserva los criterios básicos compatibles, ofrece el resultado básico y comunica que Premium requiere verificación o reconexión según corresponda. No pierde ni oculta movimientos.
6. Preferencias comerciales, callback de compra, estado pendiente o cache no autenticado no conceden capacidades Premium.

#### HU-59 — Usar temporalmente capacidades Premium sin conexión

Como persona usuaria con Premium verificado, quiero seguir usando capacidades Premium sin conexión durante una concesión acotada y resistente a cambios del reloj del dispositivo.

**Prioridad**: Alta · **Estimación**: 8 puntos · **Dependencias**: HU-54 y HU-58.

**Criterios de aceptación**

1. Solo una verificación autenticada de servidor que confirme un entitlement efectivo puede emitir una concesión offline verificable criptográficamente.
2. La concesión identifica al usuario y a la instalación, incluye versión de política, instante de verificación del servidor y vigencia comercial conocida, y no puede renovarse editando el reloj civil, restaurando una copia de seguridad o copiando el cache a otra instalación.
3. Su límite estricto es `notAfter = min(serverVerifiedAt + 72 horas, knownEntitlementEnd)`; Lifetime no elimina el límite de 72 horas.
4. La app calcula el tiempo transcurrido usando continuidad monotónica del mismo arranque. Si reinicia y no puede probar continuidad, requiere reconexión para revalidar antes de permitir Premium offline.
5. Al llegar a `notAfter`, o al detectar una concesión inválida, Kipu suspende temporalmente solo las capacidades Premium y solicita reconexión; Free manual, historial básico, persistencia local y outbox siguen operativos.
6. Una verificación fallida no extiende la concesión. Mientras el plazo previo siga siendo demostrablemente válido, una falla de red por sí sola no revoca esa concesión; una denegación/revocación autenticada de servidor sí la reemplaza por Free inmediatamente.
7. La pérdida de concesión nunca elimina, modifica ni excluye datos financieros ya registrados.

### Requisitos funcionales

- **FR-048**: La política de dominio distingue capacidades Free, Free limitadas y Premium-only. HU-58 mantiene en Free operación manual, registro local, sincronización y filtros básicos, y protege las capacidades avanzadas definidas en sus criterios.
- **FR-049**: El entitlement efectivo se deriva de compras verificadas y vigentes según HU-54/HU-56. Intenciones de plan, respuesta local de Billing, estado `PENDING` y cache sin concesión válida no autorizan Premium.
- **FR-050**: Tras verificación autenticada satisfactoria, el backend puede emitir una concesión firmada que contiene usuario, huella de clave de instalación, política, `serverVerifiedAt`, `knownEntitlementEnd` nullable, `notAfter`, identificador de concesión y `keyId`; `notAfter` usa el mínimo de 72 horas y el fin comercial conocido.
- **FR-051**: Android valida la firma y las claims, compara el usuario con la sesión actual y la huella con una clave no exportable de Android Keystore. Un grant ausente, alterado, de otra cuenta/instalación, de política incompatible o con firma desconocida se trata como Free y requiere reconexión para restaurar Premium.
- **FR-052**: La vigencia offline se evalúa con `elapsedRealtime` desde el ancla del mismo arranque; la hora civil del dispositivo no crea, extiende ni renueva acceso. Una regresión del contador monotónico/reinicio sin continuidad exige revalidación.
- **FR-053**: El cache de concesión y las claves de instalación no se incluyen en backup o transferencia de dispositivo. No se persisten purchase tokens ni secretos de firma en Android.
- **FR-054**: Al vencer la concesión, la app niega capacidades Premium, conserva Kipu Free, registros locales, consultas básicas y outbox. Reintentos de red no alteran datos contables ni la concesión previa sin respuesta autenticada.
- **FR-055**: Una verificación online que confirma revocación, expiración u otro estado no-entitling sustituye el cache con Free; una falla transitoria no crea un nuevo límite temporal ni extiende `notAfter`.

### Criterios de éxito

- **SC-020**: Pruebas de política demuestran que las capacidades básicas/manuales permanecen permitidas en Free y toda capacidad avanzada requiere entitlement efectivo verificable.
- **SC-021**: Vectores de firma válidos verifican; firma o claims alteradas, propietario distinto, clave de instalación distinta, versión/política incompatible y key ID desconocido deniegan Premium.
- **SC-022**: Con tiempo monotónico controlado, se permite justo antes de `notAfter` y se exige reconexión exactamente en `notAfter`; cambios hacia adelante/atrás del reloj civil no cambian el resultado.
- **SC-023**: La concesión se limita a 72 horas desde el instante firmado por servidor, y una suscripción con fin anterior se limita a ese fin; Lifetime también vence la concesión a las 72 horas.
- **SC-024**: Reinicio/regresión monotónica sin continuidad, cache restaurado/copiado, error de red y revocación autenticada producen los estados especificados; ninguna ruta bloquea Free, outbox o consulta básica.
- **SC-025**: Migración Room preserva todas las filas existentes y niega Premium a caches legacy sin grant firmado; migración Supabase local valida permisos/RLS y la API no expone secretos ni emite grants para pagos pendientes.

**Decisiones S4 aprobadas**: tras más de 72 horas sin ancla confiable, se exige reconexión y se mantienen Kipu Free/registro local; no se usa `DELETE` en movimientos ni se altera historia financiera; el estado de entitlement y las concesiones no son fuente de verdad contable. Para cuentas con cuotas/deudas, la política de movimiento permanece fuera de esta épica.

**Trazabilidad S4**: HU-58/HU-59 y reglas de dependencia/capacidades se contrastan con Obsidian Mind `Kipu md/02_Kipu_V4.2_Product_Backlog.md` y `Procesos/31-aplicar-cupos-accesos-y-vigencia-offline.md`; los filtros y la concesión consumidora con `specs/004-ep-mov-movimientos-ledger/contracts/history-query-access.md`. Los gates se rigen por Obsidian Mind `Spec Kit in Kipu.md` y `Spec Kit — Referencia Global.md`.

## Artefactos Derivados

- `plan.md`
- `tasks.md`
- `data-model.md`
- `ADR-012-freemium-intent`
- ~~El documento de diseño de marca previo~~ *(Sustituido por `docs/stitch-design-system.md` — design system Stitch `Kipu Andean Modernist` como autoridad.)*
- `stitch-design-system.md`
- `Stich Prompts.md`
# Refinamiento UI/UX aprobado — 2026-10-03

**Refined**: 2026-10-03 — Aprobados aviso contextual Free/Premium, revalidación explícita con estados terminales, feedback accesible y movimiento reducido. Integra el refinamiento consumidor de EP-MOV sin alterar concesión/contabilidad.

- **FR-056**: El historial diferencia Premium requerido, revalidación requerida, verificando, éxito, fallo temporal y ausencia de compra recuperable. Ver Premium navega a las ofertas; Verificar acceso invoca la restauración/verificación autenticada existente sin depender de cargar catálogo. Se impiden envíos simultáneos y se ofrece reintento; un resultado local de Billing nunca autoriza por sí solo.
- **FR-057**: La tarjeta contextual emplea copy comprensible, color informativo/advertencia según la situación, conserva la operación Free y distingue los filtros efectivos de los borradores protegidos.
- **FR-058**: Avisos anuncian cambios relevantes discretamente a TalkBack; feedback respeta tokens, texto ampliado, targets 48 dp y movimiento reducido. Ni animación ni error de transporte renuevan concesiones o retrasan la denegación.
- **SC-026**: Restauración directa tiene resultados verificables para éxito autenticado, sin compras, compra pendiente, fallo y timeout; pulsaciones repetidas no duplican solicitudes.
- **SC-027**: Pruebas y previews cubren estados del aviso, enmascaramiento y reducción de movimiento; la ejecución en dispositivo se registra aparte.
