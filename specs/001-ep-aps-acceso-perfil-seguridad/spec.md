# Feature Specification: EP-APS - Acceso, Perfil y Seguridad

**Feature Branch**: `001-ep-aps-acceso-perfil-seguridad`

**Created**: 2026-09-20

**Status**: Refined

**Refined**: 2026-10-05 — Implementación visual de acceso y onboarding autorizada por el usuario; conserva HU-01/HU-02/HU-03 y el destino posterior de HU-52.

**Input**: Especificar EP-APS para el incremento del Sprint 1 con HU-01 a HU-05, manteniendo HU-06 como evolución futura fuera del alcance implementable.

## Control de la Épica

| Campo | Valor |
|-------|-------|
| Épica | EP-APS - Acceso, Perfil y Seguridad |
| Responsable de Épica | Integrante 9 - Desarrollo de pruebas, integración y herramientas |
| Sprint de cierre del incremento | Sprint 1 - "Kipu arranca con identidad y privacidad" |
| Historias incluidas | HU-01, HU-02, HU-03, HU-04 y HU-05 |
| Puntos integrados | 19 puntos |
| Prioridad | Alta, con HU-03 de prioridad Media |
| Estado del artefacto | V1.0 - Aprobado para implementación |

## Objetivo y Alcance del Sprint 1

### Objetivo

Permitir que una persona cree una cuenta Kipu, acceda exclusivamente a su propio espacio, recupere el acceso sin revelar si una identidad existe, conserve de forma segura su sesión, configure protección local y preferencias de privacidad, y decida de forma informada qué permisos opcionales puede usar Kipu. El rechazo de biometría o permisos de automatización no debe impedir el acceso base ni el registro manual permitido.

### Alcance Incluido

- Registro e inicio de sesión mediante correo y contraseña de Kipu.
- Mensajes de autenticación que no revelan información innecesaria sobre las cuentas.
- Recuperación de acceso mediante un enlace temporal y de un solo uso.
- Persistencia y renovación de una sesión propia sin cambiar de propietario.
- Cierre de sesión con advertencia cuando existan cambios locales pendientes.
- Desbloqueo local biométrico opcional, sin almacenar información biométrica.
- Preferencias de moneda principal, inicio de mes, tema y ocultamiento de montos.
- Explicación, solicitud, rechazo y revocación de permisos y fuentes de automatización.
- Continuidad de las operaciones manuales cuando se rechazan permisos opcionales.
- Aislamiento entre cuentas, privacidad de datos y protección de información sensible en mensajes y registros operativos.
- Accesibilidad de los recorridos y controles incluidos.

### Fuera de Alcance

- HU-06 - Portabilidad, eliminación de cuenta y restauración, cuyo cierre corresponde al Sprint 9.
- Inicio de sesión con proveedores sociales o identidades bancarias.
- Open Banking, lectura bancaria directa, lectura de SMS o correos y ejecución de pagos.
- Implementación de captura automática, análisis de contenido u OCR; HU-05 solo configura y explica permisos y fuentes.
- Activación efectiva de capacidades Premium; la decisión de acceso pertenece a EP-PLA.
- Funcionalidades operativas de cuentas financieras, movimientos, categorías, notificaciones o monetización pertenecientes a otras épicas. La pantalla de Ajustes puede mostrar accesos visuales pendientes, sin simular que esas acciones ya funcionan.
- Recuperación de cambios locales que nunca fueron sincronizados después de cerrar una sesión, desinstalar o perder el dispositivo.

### Dependencias y Trazabilidad

| Historia | Bloqueantes | Parciales | Relacionadas | Desbloquea |
|----------|-------------|-----------|--------------|------------|
| HU-01 | Ninguna | Ninguna | Ninguna | HU-02, HU-03, HU-04, HU-05, HU-06, HU-07, HU-14, HU-15, HU-42, HU-52, HU-54 y HU-57 |
| HU-02 | HU-01 | Ninguna | Ninguna | Ninguna |
| HU-03 | HU-01 | Ninguna | HU-02 | Ninguna |
| HU-04 | HU-01 | Ninguna | Ninguna | Ninguna |
| HU-05 | HU-01 | HU-52 y HU-58 | HU-45 | HU-45 |

Las dependencias parciales de HU-05 permiten especificar y probar la consulta, explicación, rechazo y revocación de permisos con estados de acceso controlados. La habilitación final de una fuente Premium debe respetar los contratos aprobados posteriormente por EP-PLA.

### Precondiciones Generales

- La aplicación Kipu está instalada en un dispositivo compatible.
- El servicio de identidad está disponible para el primer registro, primer inicio de sesión y recuperación de acceso.
- La persona conoce y controla el correo que utiliza para registrarse o recuperar acceso.
- Las capacidades de autenticación local y permisos son informadas por el dispositivo; Kipu no presupone que existan.
- La persona puede continuar usando las capacidades manuales admitidas aunque rechace biometría o permisos opcionales.

## Clarifications

### Session 2026-09-20

- Q: Al cerrar sesión, ¿qué debe ocurrir con los datos locales pertenecientes a esa cuenta? → A: Conservarlos aislados y ocultos hasta que vuelva a autenticarse la misma cuenta.
- Q: Cuando la protección biométrica está activa, ¿en qué momento debe volver a bloquearse Kipu? → A: Al iniciar la aplicación y tras 1 minuto continuo en segundo plano.
- Q: ¿Qué preferencias deben seguir a la cuenta entre dispositivos y cuáles deben permanecer locales en cada dispositivo? → A: Moneda, inicio de mes, tema y máscara siguen a la cuenta; biometría y permisos permanecen locales por dispositivo.
- Q: ¿Cómo debe responder Kipu ante múltiples intentos fallidos de inicio de sesión o recuperación desde el mismo origen? → A: Aplicar limitación progresiva configurable, mantener mensajes neutros y no bloquear permanentemente la cuenta solo por intentos fallidos.
- Q: ¿Qué debe mostrar Kipu cuando alguien intenta registrar un correo que ya está asociado a una cuenta? → A: Informar que la cuenta existe y ofrecer iniciar sesión.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrarse e ingresar de forma privada (Priority: P1)

Como usuario de Kipu, quiero crear mi cuenta e iniciar sesión con correo y contraseña, para acceder a mis finanzas de forma privada.

**Why this priority**: HU-01 es la puerta de entrada y dependencia bloqueante de las demás historias del incremento y de múltiples épicas. Sin una identidad propia válida no puede garantizarse el aislamiento de los datos.

**Independent Test**: Puede probarse con cuentas nuevas, habilitadas y con credenciales incorrectas. Entrega valor por sí sola si permite registrar e ingresar a la cuenta correcta, rechaza credenciales inválidas y explica la necesidad de conexión en un primer acceso offline.

**Preconditions**:

- La aplicación está instalada.
- La persona se encuentra en Acceso.
- Existe conectividad para autenticar el primer acceso.

**Acceptance Scenarios**:

1. **Registro**: **Dado que** el correo cumple el formato y la contraseña cumple la política configurada, **Cuando** la persona solicita crear la cuenta, **Entonces** se procesa el registro y se indica si debe verificar el correo antes de ingresar.
2. **Acceso correcto**: **Dado que** la cuenta está habilitada y las credenciales son válidas, **Cuando** la persona inicia sesión, **Entonces** accede a su propio espacio y no al de otro usuario.
3. **Credenciales erróneas**: **Dado que** la contraseña no corresponde a la cuenta, **Cuando** la persona intenta acceder, **Entonces** recibe un error neutro y no obtiene acceso.
4. **Primer acceso sin conexión**: **Dado que** no existe una sesión local previa, **Cuando** la persona intenta iniciar sesión sin red, **Entonces** se explica la necesidad de conexión y se conserva el formulario sin guardar la contraseña en registros operativos.
5. **Correo ya registrado**: **Dado que** el correo ya pertenece a una cuenta, **Cuando** la persona intenta registrarlo nuevamente, **Entonces** se informa que la cuenta existe, no se crea un duplicado y se ofrece iniciar sesión.

---

### User Story 2 - Recuperar acceso y controlar la sesión (Priority: P2)

Como usuario de Kipu, quiero recuperar mi acceso y gestionar mi sesión, para volver a entrar sin exponer mi identidad ni mezclar mis datos con otra cuenta.

**Why this priority**: HU-02 completa el ciclo mínimo de identidad iniciado por HU-01 y reduce el riesgo de pérdida de acceso o exposición durante la renovación, reapertura y salida de una sesión.

**Independent Test**: Puede probarse solicitando recuperación con un correo existente y otro inexistente, usando enlaces válidos e inválidos, reabriendo una sesión vigente y cerrando una sesión con cambios pendientes. Entrega valor si todas las respuestas preservan privacidad y propiedad.

**Preconditions**:

- La persona se encuentra en Recuperar acceso o tiene una sesión propia iniciada.
- Para recuperar acceso, puede consultar el correo asociado.

**Acceptance Scenarios**:

1. **Correo registrado**: **Dado que** existe el correo ingresado, **Cuando** la persona solicita recuperación, **Entonces** se envían instrucciones y se muestra una confirmación neutra.
2. **Correo inexistente**: **Dado que** el correo no está registrado, **Cuando** la persona solicita recuperación, **Entonces** se muestra la misma confirmación neutra sin enviar instrucciones.
3. **Enlace válido**: **Dado que** el enlace está vigente y no ha sido usado, **Cuando** la persona confirma una contraseña que cumple la política, **Entonces** la clave se actualiza y permite iniciar sesión.
4. **Enlace inválido**: **Dado que** el enlace venció, ya fue usado o fue alterado, **Cuando** la persona lo abre, **Entonces** se rechaza el cambio y se ofrece solicitar otro enlace.
5. **Sesión persistente**: **Dado que** la sesión propia sigue siendo válida, **Cuando** la persona cierra y abre la aplicación, **Entonces** recupera su espacio sin exponer otra cuenta.
6. **Cierre con pendientes**: **Dado que** hay cambios locales no enviados, **Cuando** la persona solicita cerrar sesión, **Entonces** se advierten los pendientes antes de confirmar y no se muestran sus datos al siguiente usuario.

---

### User Story 3 - Configurar preferencias y privacidad de montos (Priority: P3)

Como usuario de Kipu, quiero configurar moneda, inicio de mes, tema y ocultamiento de montos, para adaptar la presentación a mi uso diario sin modificar mis datos financieros.

**Why this priority**: HU-04 tiene prioridad Alta y establece controles cotidianos de privacidad y presentación después de disponer de una sesión propia.

**Independent Test**: Puede probarse cambiando cada preferencia, reiniciando la aplicación y comprobando que la máscara solo cambia la presentación y que una moneda nueva no convierte registros existentes.

**Preconditions**:

- Existe una sesión propia iniciada.
- La persona accedió a Configuración de su cuenta.

**Acceptance Scenarios**:

1. **Guardar preferencias**: **Dado que** la persona elige un inicio de mes válido, **Cuando** guarda y reabre la aplicación, **Entonces** se mantienen sus preferencias.
2. **Ocultar saldos**: **Dado que** Inicio muestra importes, **Cuando** la persona activa la máscara, **Entonces** los importes se ocultan sin modificar sus valores.
3. **Cambio de moneda**: **Dado que** existen movimientos en PEN, **Cuando** la persona elige otra moneda principal, **Entonces** los registros conservan su moneda original sin conversión ficticia.
4. **Día inválido**: **Dado que** el inicio de mes solicitado no es válido, **Cuando** intenta guardar, **Entonces** se señala el campo y se conserva la configuración anterior.

---

### User Story 4 - Comprender y controlar permisos opcionales (Priority: P4)

Como usuario de Kipu, quiero conocer y configurar los permisos de captura, para decidir qué señales puede procesar Kipu sin perder las operaciones manuales si no los concedo.

**Why this priority**: HU-05 tiene prioridad Alta por su impacto en privacidad y consentimiento, pero depende de una identidad ya establecida y no implementa todavía la captura de HU-45.

**Independent Test**: Puede probarse sin activar captura real, recorriendo la explicación previa, el rechazo, el intento con acceso Free y la revocación de una autorización existente. Entrega valor si cada decisión es comprensible, reversible y no bloquea el uso manual.

**Preconditions**:

- Existe una sesión propia iniciada.
- La persona abrió Configuración.
- La lectura de fuentes opcionales permanece apagada hasta recibir autorización válida.

**Acceptance Scenarios**:

1. **Explicación previa**: **Dado que** no se concedió acceso a una fuente, **Cuando** la persona abre el ajuste de captura, **Entonces** ve qué se leería, para qué se usaría y cómo revocarlo antes de ir a los ajustes del dispositivo.
2. **Rechazo**: **Dado que** el dispositivo ofrece conceder acceso, **Cuando** la persona lo rechaza, **Entonces** Kipu mantiene disponibles las operaciones manuales.
3. **Usuario Free**: **Dado que** el plan no permite captura, **Cuando** la persona intenta activar una fuente, **Entonces** se explica que la capacidad requiere Premium y no se procesa contenido.
4. **Revocación**: **Dado que** existía autorización de una fuente, **Cuando** la persona la desactiva, **Entonces** las señales posteriores se ignoran y se conserva el historial legítimo ya confirmado.

---

### User Story 5 - Proteger el desbloqueo local con biometría (Priority: P5)

Como usuario de Kipu, quiero habilitar un desbloqueo biométrico opcional, para proteger el acceso local a mis saldos sin sustituir mi sesión remota.

**Why this priority**: HU-03 es de prioridad Media y agrega una protección local valiosa después de establecer la identidad, la sesión y los controles de privacidad base.

**Independent Test**: Puede probarse con un dispositivo compatible y otro no compatible, incluyendo activación, desbloqueo, cancelación y fallo. Entrega valor si protege la visualización local sin bloquear permanentemente el uso manual.

**Preconditions**:

- Existe una sesión propia válida.
- El dispositivo informa sus capacidades de autenticación local.

**Acceptance Scenarios**:

1. **Activación**: **Dado que** el dispositivo tiene biometría configurada, **Cuando** la persona activa la protección y se autentica, **Entonces** la preferencia queda guardada.
2. **Desbloqueo**: **Dado que** la protección está activa y Kipu acaba de iniciarse o permaneció al menos 1 minuto continuo en segundo plano, **Cuando** la persona regresa y supera la autenticación local, **Entonces** se muestran los datos de su sesión.
3. **Cancelación o fallo**: **Dado que** aparece el diálogo biométrico, **Cuando** la persona cancela o falla, **Entonces** los saldos permanecen ocultos y puede reintentar.
4. **Sin compatibilidad**: **Dado que** no hay biometría disponible, **Cuando** la persona abre la preferencia, **Entonces** se explica la limitación y se ofrece mantener la protección mediante una credencial admitida por el dispositivo, sin bloquear permanentemente el registro manual.

### Escenarios Complementarios del Incremento

1. **Cambio de cuenta**: **Dado que** una persona cierra sesión y otra inicia sesión en el mismo dispositivo, **Cuando** se presenta el nuevo espacio, **Entonces** no se muestran datos, preferencias privadas ni estados protegidos de la cuenta anterior.
2. **Sesión vencida con datos locales**: **Dado que** la sesión remota dejó de ser válida y existen datos locales de su propietario, **Cuando** la persona abre la aplicación, **Entonces** se protege la información, se solicita restablecer una sesión válida cuando sea necesario y no se reasignan los datos a otra identidad.
3. **Interrupción durante registro**: **Dado que** el servicio deja de responder antes de confirmar el registro, **Cuando** la operación termina con error o resultado incierto, **Entonces** no se afirma que la cuenta fue creada y se ofrece una recuperación segura.
4. **Reenvío de recuperación**: **Dado que** una solicitud de recuperación ya fue realizada, **Cuando** la persona vuelve a solicitarla, **Entonces** recibe la misma respuesta neutra y no se revela si algún envío anterior correspondió a una cuenta.
5. **Revocación externa de permiso**: **Dado que** una autorización fue retirada desde los ajustes del dispositivo, **Cuando** Kipu vuelve a consultar la fuente, **Entonces** refleja el estado revocado y no procesa señales posteriores.
6. **Preferencia por cuenta**: **Dado que** dos cuentas usan el mismo dispositivo, **Cuando** cada una modifica moneda, inicio de mes, tema o máscara de montos, **Entonces** cada espacio presenta únicamente sus propios valores y los recupera en otro dispositivo después de autenticarse, mientras biometría y permisos conservan la configuración local de cada dispositivo.
7. **Registros operativos seguros**: **Dado que** ocurre un error de registro, sesión, recuperación, biometría o permisos, **Cuando** se registra información para diagnóstico, **Entonces** no se incluyen contraseñas, enlaces secretos, tokens, contenido privado de fuentes ni datos financieros sensibles.
8. **Tecnología de asistencia**: **Dado que** una persona utiliza lector de pantalla, navegación por foco o texto ampliado, **Cuando** completa cualquiera de los cinco recorridos, **Entonces** puede identificar campos, errores, acciones, estados de privacidad y decisiones sobre permisos sin perder controles esenciales.

### Edge Cases

- Un correo con formato inválido se rechaza antes de solicitar registro, sin alterar el formulario válido restante.
- Un correo ya registrado se identifica como cuenta existente y dirige al inicio de sesión sin crear una identidad duplicada; esta revelación se limita al flujo de registro.
- Una contraseña que no cumple la política vigente se rechaza con reglas comprensibles sin registrar su contenido.
- Los intentos fallidos repetidos de inicio o recuperación activan esperas progresivas según la política vigente, sin revelar si la cuenta existe ni producir un bloqueo permanente por sí solos.
- La verificación de correo pendiente no se presenta como una sesión habilitada cuando la configuración exige verificar antes de ingresar.
- Una respuesta tardía de autenticación después de cancelar no debe abrir el espacio de otra cuenta ni duplicar navegación.
- Un enlace de recuperación vencido, usado o manipulado no cambia la contraseña y permite solicitar uno nuevo.
- Cerrar sesión con cambios pendientes requiere una decisión explícita; cancelar conserva la sesión y confirmar protege los datos frente al siguiente usuario.
- Cerrar sesión conserva los datos locales bajo su propietario original, pero los mantiene ocultos e inaccesibles hasta que vuelva a autenticarse esa misma cuenta.
- Una sesión renovada conserva el mismo propietario; un cambio de identidad exige un nuevo acceso y separación de estado.
- La cancelación o el fallo biométrico mantiene ocultos los saldos y no invalida por sí solo la sesión remota.
- Con la protección biométrica activa, Kipu vuelve a bloquearse en cada inicio y después de permanecer al menos 1 minuto continuo en segundo plano; una interrupción menor no reinicia el bloqueo.
- La eliminación o sustitución de biometría registrada en el dispositivo obliga a reevaluar la protección local sin asumir éxito anterior.
- Cambiar moneda principal modifica la presentación futura, no los importes ni monedas originales.
- El inicio de mes solo acepta días válidos definidos por el producto y nunca desplaza silenciosamente la historia.
- La máscara de montos oculta valores en superficies protegidas sin cambiar cálculos, persistencia ni sincronización.
- Un permiso concedido pero no autorizado por el plan no habilita procesamiento de contenido.
- Una revocación detiene el procesamiento posterior, pero no elimina historia legítima que la persona ya confirmó.
- La falta de conexión después de una sesión previa no impide consultar y gestionar el núcleo manual local permitido; primer acceso, recuperación y renovación que requiera validación informan su dependencia de red.

## Requirements *(mandatory)*

### Reglas de Negocio

- **RN-APS-001 - Identidad propia**: Cada sesión y cada dato privado deben conservar un único propietario identificable; autenticarse no autoriza acceso a datos de otra cuenta.
- **RN-APS-002 - Credenciales limitadas**: Kipu utiliza correo y contraseña propios para este incremento y nunca solicita credenciales bancarias.
- **RN-APS-003 - Respuestas neutras**: Los errores de acceso y las solicitudes de recuperación no deben revelar si un correo pertenece a una cuenta.
- **RN-APS-004 - Recuperación temporal**: Un enlace de recuperación tiene la vigencia configurada, es de un solo uso y no cambia la contraseña si está vencido, usado o alterado.
- **RN-APS-005 - Propiedad de sesión**: Renovar o restaurar una sesión no puede cambiar su propietario; cambiar de cuenta exige establecer una nueva identidad válida.
- **RN-APS-006 - Pendientes antes de salir**: Cerrar sesión no promete recuperar cambios que nunca se sincronizaron y debe advertir su existencia antes de confirmar.
- **RN-APS-007 - Biometría local y opcional**: La biometría protege el desbloqueo local, no sustituye el inicio remoto y Kipu no almacena plantillas biométricas.
- **RN-APS-008 - Preferencias no financieras**: Moneda de presentación, tema, inicio de mes y máscara no modifican hechos financieros ni convierten importes históricos.
- **RN-APS-009 - Consentimiento contextual**: Todo permiso opcional debe explicarse antes de solicitarlo y poder revocarse.
- **RN-APS-010 - Núcleo manual disponible**: Rechazar o revocar un permiso opcional no debe impedir las operaciones manuales permitidas.
- **RN-APS-011 - Acceso por capacidad**: Tener permiso del dispositivo no basta para usar una fuente si la capacidad no está autorizada; sin autorización no se procesa contenido.
- **RN-APS-012 - Diagnóstico privado**: Los registros operativos no deben contener contraseñas, secretos de recuperación, tokens, contenido de fuentes ni datos financieros sensibles.
- **RN-APS-013 - Protección ante abuso**: Los intentos fallidos repetidos deben recibir limitación progresiva configurable y mensajes neutros; no pueden bloquear permanentemente una cuenta solo por su cantidad.
- **RN-APS-014 - Registro existente**: Intentar registrar un correo ya asociado debe informar que la cuenta existe y ofrecer iniciar sesión, sin crear una identidad duplicada; los flujos de acceso y recuperación conservan sus respuestas neutras.

### Functional Requirements

#### Registro e Inicio de Sesión

- **FR-001**: El sistema DEBE permitir crear una cuenta mediante un correo con formato válido y una contraseña que cumpla la política vigente.
- **FR-002**: El sistema DEBE informar si la cuenta requiere verificar el correo antes de poder ingresar.
- **FR-003**: El sistema DEBE impedir una sesión válida cuando las credenciales son incorrectas o la cuenta todavía no está habilitada.
- **FR-004**: Los errores de autenticación DEBEN usar mensajes neutros que no confirmen la existencia de una cuenta.
- **FR-005**: Un primer inicio de sesión sin conectividad DEBE explicar que se necesita conexión y DEBE conservar los campos no secretos del formulario para reintentar.
- **FR-006**: El sistema NO DEBE guardar ni registrar la contraseña introducida fuera del procesamiento estrictamente necesario para autenticar.
- **FR-007**: Después de un acceso correcto, la persona DEBE ver únicamente el espacio asociado a la identidad autenticada.

#### Recuperación y Ciclo de Sesión

- **FR-008**: El sistema DEBE aceptar una solicitud de recuperación por correo y mostrar la misma confirmación observable tanto si la cuenta existe como si no.
- **FR-009**: Solo una solicitud correspondiente a una cuenta existente PUEDE producir instrucciones dirigidas a su correo, sin que la interfaz revele esa diferencia.
- **FR-010**: Un enlace vigente y no usado DEBE permitir establecer una contraseña que cumpla la política.
- **FR-011**: Un enlace vencido, usado o alterado DEBE rechazarse sin cambiar credenciales y DEBE ofrecer solicitar uno nuevo.
- **FR-012**: Una sesión propia todavía válida DEBE poder recuperarse al reabrir la aplicación sin pedir credenciales innecesariamente.
- **FR-013**: Toda renovación de sesión DEBE conservar el mismo propietario y DEBE fallar de forma segura si no puede comprobarlo.
- **FR-014**: Antes de cerrar una sesión con cambios locales no enviados, el sistema DEBE advertir qué riesgo existe y solicitar confirmación explícita.
- **FR-015**: Cancelar el cierre ante la advertencia DEBE conservar la sesión y sus pendientes sin cambios.
- **FR-016**: Confirmar el cierre DEBE retirar los datos privados de las superficies visibles, conservarlos aislados bajo su propietario original y evitar que otra cuenta herede o acceda al estado anterior.
- **FR-017**: Los datos y pendientes conservados localmente DEBEN permanecer asociados a su propietario original, NO DEBEN enviarse bajo la sesión de otra cuenta y solo PUEDEN reactivarse cuando vuelva a autenticarse la misma cuenta.

#### Desbloqueo Local

- **FR-018**: Una persona con sesión válida DEBE poder consultar si el dispositivo admite protección biométrica o una credencial local compatible.
- **FR-019**: La protección biométrica DEBE permanecer desactivada hasta que la persona la active y complete una autenticación local válida.
- **FR-020**: Cuando la protección está activa, Kipu DEBE bloquear y ocultar saldos y datos privados en cada inicio y después de permanecer al menos 1 minuto continuo en segundo plano, hasta superar el desbloqueo local requerido.
- **FR-021**: Cancelar o fallar el desbloqueo DEBE mantener la información oculta y permitir un nuevo intento o una alternativa admitida.
- **FR-022**: Si no existe biometría compatible, el sistema DEBE explicar la limitación y ofrecer la protección local admitida por el dispositivo sin bloquear permanentemente el núcleo manual.
- **FR-023**: Kipu NO DEBE recopilar, almacenar, sincronizar ni registrar plantillas o muestras biométricas.
- **FR-024**: Superar el desbloqueo local NO DEBE crear, renovar ni sustituir una sesión remota inválida.

#### Preferencias y Privacidad de Saldos

- **FR-025**: La persona DEBE poder guardar moneda principal, inicio de mes, tema y estado de ocultamiento de montos como preferencias de su cuenta, recuperables en otro dispositivo después de autenticarse.
- **FR-026**: Las preferencias guardadas DEBEN conservarse al cerrar y reabrir la aplicación.
- **FR-027**: Activar la máscara DEBE ocultar los importes en las superficies protegidas sin modificar sus valores ni cálculos.
- **FR-028**: Cambiar la moneda principal DEBE conservar la moneda y el importe originales de todos los registros existentes y NO DEBE ejecutar una conversión ficticia.
- **FR-029**: El inicio de mes DEBE validarse contra el rango aprobado; un valor inválido DEBE señalarse y conservar la configuración anterior.
- **FR-030**: Moneda principal, inicio de mes, tema y máscara de montos DEBEN mantenerse aislados por cuenta y acompañarla entre dispositivos; la activación biométrica y los permisos DEBEN permanecer locales al dispositivo donde fueron autorizados y NO DEBEN activarse por sincronización.
- **FR-031**: La personalización ordinaria incluida en esta historia DEBE permanecer disponible en el acceso Free.

#### Permisos y Fuentes de Automatización

- **FR-032**: Antes de dirigir a la persona a conceder un permiso, el sistema DEBE explicar el beneficio, el tipo de información que podría procesarse, sus límites y cómo revocarlo.
- **FR-033**: El sistema DEBE distinguir claramente el permiso para avisos propios de Kipu del acceso a contenido producido por otras aplicaciones.
- **FR-034**: Rechazar un permiso opcional DEBE mantener disponibles el acceso y las operaciones manuales permitidas.
- **FR-035**: Una fuente DEBE permanecer inactiva mientras no exista autorización válida del dispositivo y autorización de capacidad.
- **FR-036**: Si el acceso vigente no permite captura, el intento de activar una fuente DEBE explicar la restricción y NO DEBE procesar contenido.
- **FR-037**: Revocar una fuente DEBE impedir el procesamiento de señales posteriores desde el momento en que la revocación se detecta.
- **FR-038**: La revocación NO DEBE borrar ni invalidar historia legítima previamente confirmada por la persona.
- **FR-039**: El sistema DEBE reflejar una revocación realizada fuera de Kipu cuando vuelva a consultar el estado del permiso.
- **FR-040**: HU-05 NO DEBE crear movimientos, candidatos de captura ni efectos financieros; esas capacidades pertenecen a historias posteriores.

#### Seguridad, Privacidad, Offline y Accesibilidad

- **FR-041**: Toda lectura o modificación de información privada DEBE comprobar que el propietario coincide con la sesión vigente, además de requerir autenticación.
- **FR-042**: Los intentos de acceso cruzado entre cuentas DEBEN rechazarse sin revelar si los datos objetivo existen.
- **FR-043**: Los errores y registros operativos NO DEBEN contener contraseñas, secretos de recuperación, tokens, contenido privado de fuentes ni datos financieros sensibles.
- **FR-044**: Una persona previamente autenticada DEBE poder consultar y gestionar el núcleo manual local permitido sin conectividad continua; las acciones que requieran validación remota DEBEN explicar la necesidad de conexión sin simular éxito.
- **FR-045**: Los formularios DEBEN conservar de forma segura la información no secreta necesaria para reintentar después de errores recuperables, sin retener contraseñas.
- **FR-046**: Todos los recorridos incluidos DEBEN ofrecer etiquetas comprensibles, orden de foco coherente, errores perceptibles, controles táctiles suficientes y continuidad con texto ampliado y lector de pantalla.
- **FR-047**: La máscara de montos y el bloqueo local DEBEN proteger valores en vistas previas y transiciones donde una exposición momentánea pueda revelar información.
- **FR-048**: Ninguna acción de EP-APS DEBE crear o modificar hechos financieros, conceder Premium, mover dinero o habilitar captura sin los contratos de las historias propietarias.
- **FR-049**: Los intentos fallidos repetidos de inicio de sesión desde un mismo origen DEBEN activar una espera progresiva conforme a la política vigente, conservar mensajes neutros y NO DEBEN bloquear permanentemente la cuenta por sí solos.
- **FR-050**: Las solicitudes de recuperación repetidas desde un mismo origen DEBEN someterse a la misma política de limitación progresiva sin variar la confirmación pública ni revelar si el correo existe.
- **FR-051**: Si un correo ya está asociado a una cuenta, el registro DEBE informar esa condición, NO DEBE crear una identidad duplicada y DEBE ofrecer continuar al inicio de sesión; esta revelación NO DEBE extenderse a los mensajes de error de acceso ni a la recuperación.
- **FR-052**: Ajustes DEBE organizarse en Mi suscripción, Preferencias financieras, Tema de la aplicación, Notificaciones y alertas, y Seguridad y copias de seguridad, conforme a las referencias visuales aprobadas.
- **FR-053**: La moneda principal DEBE ofrecer PEN y USD como opciones visibles; el inicio del ciclo mensual DEBE poder ajustarse entre los días 1 y 28 mediante controles de incremento y decremento. La máscara de montos DEBE conservar su comportamiento existente.
- **FR-054**: Ajustes DEBE mantener operativos los accesos existentes a permisos, biometría y cierre de sesión. El acceso a permisos DEBE aparecer en Notificaciones y alertas; el de biometría, en Seguridad y copias de seguridad.
- **FR-055**: Estado de suscripción, vigencia, facturación, restauración de compras, cupos, categorías, anticipación de vencimientos, exportación, restauración local y eliminación de cuenta PUEDEN mostrarse como interfaz preparada para futuras épicas, pero DEBEN identificarse como pendientes y NO DEBEN presentar datos inventados como reales ni ejecutar acciones no implementadas.

### Key Entities *(include if feature involves data)*

- **Identidad de Cuenta**: Representa la cuenta Kipu asociada a un correo y administrada por el servicio de identidad. Determina el propietario estable de sesiones y datos privados; no contiene credenciales bancarias.
- **Sesión**: Autorización temporal de una identidad para acceder a su propio espacio. Tiene propietario, vigencia y estado; su renovación nunca cambia de propietario.
- **Solicitud de Recuperación**: Petición privada para recuperar acceso. Produce una respuesta pública neutra y, cuando corresponde, un enlace temporal de un solo uso.
- **Perfil y Preferencias**: Configuración propia de la cuenta para moneda principal, inicio de mes, tema y ocultamiento de montos. Acompaña a la cuenta entre dispositivos y cambia presentación, no hechos financieros.
- **Preferencia de Protección Local**: Configuración propia de cada dispositivo que indica si la persona solicitó desbloqueo local. No se activa por sincronización, no contiene plantillas biométricas ni sustituye la sesión.
- **Estado de Permiso o Fuente**: Configuración propia de cada dispositivo que refleja si una fuente opcional fue explicada, autorizada, rechazada o revocada y si la capacidad está permitida. No se activa por sincronización ni contiene por sí misma señales capturadas.
- **Cambio Local Pendiente**: Operación aún no enviada que conserva la identidad de su propietario y debe advertirse antes de cerrar sesión.

### Estados y Transiciones Relevantes

| Concepto | Estados observables | Transiciones permitidas |
|----------|---------------------|-------------------------|
| Cuenta | Pendiente de verificación, habilitada, no habilitada | Registro puede dejarla pendiente o habilitada según la política; solo una cuenta habilitada puede iniciar sesión. |
| Sesión | Ausente, válida, requiere renovación, cerrada | Acceso válido crea sesión; reapertura restaura la propia; renovación conserva propietario; cierre retira acceso visible. |
| Recuperación | Solicitada, vigente, usada, vencida o inválida | Una solicitud válida puede emitir enlace; usarlo una vez lo vuelve no reutilizable; vencimiento o alteración impiden cambios. |
| Protección local | Desactivada, activa, bloqueada temporalmente | Activación exige autenticación local; retorno protege datos; éxito desbloquea; fallo o cancelación mantiene ocultamiento. |
| Permiso o fuente | No solicitado, explicado, concedido, rechazado, revocado | Solo se solicita después de explicar; rechazo conserva núcleo manual; revocación detiene señales posteriores. |

### Matriz de Trazabilidad

| Requisitos | Historias y evidencia de aceptación |
|------------|-------------------------------------|
| FR-001 a FR-007 | US1, escenarios Registro, Acceso correcto, Credenciales erróneas y Primer acceso sin conexión |
| FR-008 a FR-017 | US2, seis escenarios oficiales y complementarios Cambio de cuenta, Sesión vencida y Reenvío de recuperación |
| FR-018 a FR-024 | US5, cuatro escenarios oficiales y casos de cancelación, fallo y cambio de biometría |
| FR-025 a FR-031 | US3, cuatro escenarios oficiales y complementario Preferencia por cuenta |
| FR-032 a FR-040 | US4, cuatro escenarios oficiales y complementario Revocación externa |
| FR-041 a FR-048 | Todas las historias; complementarios Cambio de cuenta, Registros seguros y Tecnología de asistencia |
| FR-049 y FR-050 | US1 y US2; caso límite de intentos repetidos y SC-015 |
| FR-051 | US1, escenario Correo ya registrado y SC-016 |

## Riesgos y Decisiones Abiertas

| Identificador | Riesgo o decisión | Tratamiento requerido en este incremento |
|---------------|-------------------|-------------------------------------------|
| RSK-APS-001 | Revelar si un correo existe mediante diferencias de mensaje o flujo. | Mantener respuestas neutras equivalentes en acceso y recuperación y validarlas con cuentas existentes e inexistentes. |
| RSK-APS-002 | Mostrar datos de una cuenta anterior después de cerrar o cambiar sesión. | Retirar superficies privadas, conservar pendientes con su propietario y validar el cambio de cuenta en el mismo dispositivo. |
| RSK-APS-003 | Confundir biometría local con una sesión remota válida. | Mantener ambos controles separados y exigir sesión válida cuando corresponda. |
| RSK-APS-004 | Solicitar permisos sin consentimiento informado o seguir procesando tras revocación. | Explicar antes de solicitar, reflejar estados reales y detener señales posteriores al detectar revocación. |
| RSK-APS-005 | Prometer persistencia o recuperación de cambios nunca sincronizados. | Advertir pendientes antes de cerrar y no afirmar que podrán restaurarse desde otro dispositivo. |
| RSK-APS-006 | El registro de un correo existente permite inferir que la cuenta existe. | Limitar esa revelación al registro, aplicar protección ante abuso y mantener neutros los flujos de acceso y recuperación. |
| DEC-APS-001 | HU-06 pertenece a la épica, pero no al incremento. | Mantenerla trazable como evolución del Sprint 9 sin requisitos implementables en esta versión. |
| DEC-APS-002 | La política exacta de contraseña, verificación y vigencia de enlaces es configurable. | La especificación exige cumplir y comunicar la política vigente; sus parámetros concretos se documentarán y validarán en el plan. |

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los intentos con credenciales incorrectas, cuentas no habilitadas o identidades ajenas termina sin crear una sesión válida ni mostrar datos privados.
- **SC-002**: En una prueba con al menos 20 solicitudes distribuidas entre correos existentes e inexistentes, el 100% muestra la misma confirmación pública de recuperación y ningún participante puede determinar la existencia de la cuenta por el mensaje mostrado.
- **SC-003**: El 100% de los enlaces válidos de la matriz permite un único cambio de contraseña, y el 100% de enlaces usados, vencidos o alterados se rechaza sin modificar credenciales.
- **SC-004**: En el 100% de las pruebas de reapertura y renovación, una sesión válida recupera únicamente el espacio de su propietario y nunca cambia de identidad.
- **SC-005**: En el 100% de los cierres con cambios pendientes se presenta una advertencia antes de confirmar; después del cierre, otra cuenta observa 0 datos privados de la sesión anterior.
- **SC-006**: El 100% de los inicios y retornos después de al menos 1 minuto continuo en segundo plano presenta el bloqueo cuando está activo; el 100% de los fallos o cancelaciones mantiene los montos ocultos y el 100% de los desbloqueos válidos permite continuar con la sesión propia.
- **SC-007**: El 100% de los cambios de moneda, tema, inicio de mes y máscara sobrevive a una reapertura cuando fue guardado correctamente; 0 registros históricos cambia de importe o moneda como efecto de esas preferencias.
- **SC-008**: El 100% de los rechazos o revocaciones de permisos opcionales conserva disponibles las operaciones manuales permitidas y procesa 0 señales posteriores no autorizadas.
- **SC-009**: El 100% de los intentos de acceso cruzado de la matriz de dos cuentas es rechazado sin revelar ni modificar datos ajenos.
- **SC-010**: En la revisión de registros producidos por todos los errores de la matriz se encuentran 0 contraseñas, enlaces secretos, tokens, contenidos privados de fuentes o datos financieros sensibles.
- **SC-011**: Al menos 18 de 20 participantes completan en el primer intento el registro, la recuperación o el ajuste de privacidad asignado y explican correctamente qué información protege cada control.
- **SC-012**: El 100% de los recorridos críticos puede completarse con lector de pantalla y texto ampliado al 200%, sin perder campos, mensajes de error, acciones ni estado de privacidad.
- **SC-013**: En al menos el 95% de 40 mediciones sobre dispositivos representativos, ocultar o revelar montos, guardar una preferencia local o presentar el bloqueo local responde visualmente en menos de 1 segundo.
- **SC-014**: Con una sesión previa válida y sin conectividad, el 100% de los recorridos de consulta y gestión manual local permitidos continúa disponible; las acciones que requieren red informan la limitación y registran 0 éxitos ficticios.
- **SC-015**: El 100% de las matrices de intentos fallidos repetidos activa la limitación progresiva configurada, conserva mensajes neutros y permite recuperar el acceso legítimo posteriormente sin bloqueos permanentes causados solo por esos intentos.
- **SC-016**: El 100% de los intentos de registrar un correo existente informa que la cuenta existe, crea 0 cuentas duplicadas y ofrece iniciar sesión; el 100% de las pruebas de acceso y recuperación mantiene sus mensajes neutros.

## Assumptions

- La política concreta de complejidad de contraseña y la necesidad de verificar correo pueden evolucionar mediante configuración; este incremento siempre aplica y comunica la política vigente.
- La recuperación requiere conectividad y acceso al correo asociado; su respuesta visible permanece neutra aunque el correo no exista.
- El enlace de recuperación tiene una vigencia finita definida por la configuración aprobada y deja de ser válido después de usarse una vez.
- Las sesiones pueden renovarse cuando existe una credencial de sesión válida; una renovación fallida no convierte una sesión ajena o vencida en válida.
- Los cambios locales pendientes permanecen ligados a la cuenta que los creó y no se envían bajo otra identidad.
- La moneda inicial es PEN. Cambiar la preferencia no convierte ni reescribe registros existentes.
- El día de inicio de mes acepta el rango aprobado por el producto; la definición concreta se formalizará en el plan sin cambiar el escenario de rechazo de valores inválidos.
- La biometría y credenciales locales son proporcionadas y verificadas por el dispositivo; Kipu solo conserva la preferencia necesaria para aplicar el bloqueo.
- Moneda principal, inicio de mes, tema y máscara de montos acompañan a la cuenta entre dispositivos; biometría y permisos deben configurarse por separado en cada dispositivo.
- HU-05 administra consentimiento y estado de permisos, pero no implementa el procesamiento de señales de HU-45.
- La evaluación final de acceso Premium para fuentes de automatización depende de los contratos de HU-52 y HU-58; ante ausencia o incertidumbre se aplica el comportamiento conservador de no procesar contenido.
- La máscara de montos es una protección visual y no reemplaza autenticación, autorización ni desbloqueo local.
- Una sesión previa permite el núcleo manual local definido por el producto; primer login, recuperación y validaciones remotas requieren conectividad.

## Definition of Done

### Refinamiento visual de acceso — 2026-10-05

- VIS-APS-001: Login, registro y recuperación usan componentes Compose reales y adaptables según las ocho referencias de `docs/ux-ui/mockups/Sesion/`, sin usar pantallas rasterizadas.
- VIS-APS-002: Introducción de exactamente tres páginas con swipe, siguiente, atrás, indicador y salida final/omitir. La tercera página usa la referencia aprobada `Omboarding 3 - Corregido.png`: ilustración de privacidad, «Tus finanzas son tuyas» y su descripción, CTA «Empezar» y «Ya tengo una cuenta». Ambas acciones finales completan el checkpoint y llevan al acceso existente.
- VIS-APS-003: Persistir por instalación página y finalización de la introducción. Una sesión restaurable y los callbacks de Auth continúan por su flujo existente; completar la introducción lleva al acceso. No sustituye la selección de plan ni biometría posteriores al registro.
- VIS-APS-004: Conservar la política aprobada de 8–72 caracteres, letra y número; confirmar contraseña en registro. La recuperación nunca revela existencia de cuentas: adaptar el texto del PNG de error a formato inválido o fallo de solicitud y usar confirmación neutra para correos válidos.
- VIS-APS-005: Insets/IME, scroll, controles de al menos 48dp, semántica accesible, estados loading/error/success y motion de 150–350ms que respeta Reduce Motion. Reutilizar Inter y tokens existentes sin modificar globalmente el Design System. Ajustar decoración y espaciados al alto disponible para que el registro quepa con teclado cerrado en A16 y tamaños similares; mantener scroll con teclado, errores o texto ampliado. El cambio de pestaña conserva un único formulario y anima la aparición/retiro de los campos adicionales sin sustituir la pantalla.
- VIS-APS-006: Validar dominio/ViewModels, navegación, pager y persistencia, compilar, ejecutar lint y comparar las pantallas renderizadas con referencias, registrando límites de proveedor/dispositivo.

- Los 22 escenarios Gherkin oficiales de HU-01 a HU-05 pasan con evidencia reproducible.
- Los escenarios complementarios de cambio de cuenta, sesión vencida, interrupciones, revocación externa, aislamiento de preferencias, registros seguros y accesibilidad cuentan con evidencia.
- Los recorridos de correo existente e inexistente demuestran respuestas neutras de recuperación.
- Los enlaces válidos, usados, vencidos y alterados se validan sin cambios no autorizados de credenciales.
- La reapertura, renovación, cierre con pendientes y cambio de cuenta demuestran propiedad estable y ausencia de exposición cruzada.
- La biometría se valida como protección local opcional, incluyendo cancelación, fallo y dispositivo no compatible, sin almacenar muestras biométricas.
- Las preferencias sobreviven una reapertura y ninguna cambia importes, monedas originales o cálculos.
- El rechazo y la revocación de permisos conservan el núcleo manual y detienen señales posteriores no autorizadas.
- La matriz offline distingue operaciones locales permitidas de acciones que requieren conectividad y no registra éxitos ficticios.
- La inspección de errores y registros operativos confirma ausencia de secretos y datos sensibles prohibidos.
- Los recorridos afectados cumplen los criterios de accesibilidad y se verifican en dispositivos representativos.
- Existe revisión cruzada de trazabilidad entre HU, requisitos, escenarios, pruebas y evidencia.
- HU-06 permanece fuera de la implementación y está identificada como evolución del Sprint 9.
