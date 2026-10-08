# Feature Specification: EP-CCO Categorías, Subcategorías y Comercios

**Feature Branch**: `003-ep-cco-categorias-comercios`

**Created**: 2026-09-22

**Status**: Refined

**Refined**: 2026-10-08 — Se agrega al alcance de EP-CCO el incremento Sprint 5 de HU-16 y HU-17, conservando HU-14/HU-15 como base histórica de Sprint 2 y HU-50 como alcance futuro de Sprint 8.

**Input**: User description: "Épica EP-CCO: Categorías, Subcategorías y Comercios. Alcance exclusivo Sprint 2: HU-14 categorías y subcategorías; HU-15 comercios y servicios."

## Clarifications

### Session 2026-09-22

- Q: ¿Qué debe ocurrir con las subcategorías cuando su categoría raíz se inactiva? → A: La raíz y sus subcategorías se conservan para historial, pero ninguna puede asignarse a nuevos movimientos hasta reactivar la raíz.
- Q: ¿Qué debe pasar si el mismo usuario edita una categoría en dos dispositivos sin conexión y ambos cambios se sincronizan después? → A: Conservar ambas versiones detectadas y pedir al usuario elegir una antes de finalizar la sincronización.
- Q: ¿Qué tipo de coincidencia debe bastar para mostrar un comercio en una búsqueda normalizada? → A: Permitir coincidencias parciales dentro de una palabra, como "star" para "Starbucks".

### Session 2026-09-23

- Aprobación: Las pestañas Gastos e Ingresos filtran categorías por tipo y ambas incluyen las categorías `GENERAL`. Las categorías existentes se migran como `GENERAL` sin inferir su tipo ni cambiar la clasificación histórica. Las raíces nuevas usan el tipo seleccionado; las subcategorías heredan y deben coincidir con su raíz. Las transferencias no llevan categoría.

### Session 2026-10-07 (Resolución de Autoridad sobre Cupo Free de Categorías)

- Aprobación de Autoridad: Conforme a la Arquitectura y Modelado de Datos Kipu V4.2 (sección `categories`) y la resolución del workflow Spec Kit, en plan Free se permiten hasta 5 categorías raíz personalizadas activas de GASTOS (`EXPENSE`) y hasta 5 categorías raíz personalizadas activas de INGRESOS (`INCOME`).
- Las categorías predeterminadas del sistema (`SYSTEM`, como Alimentación, Transporte, Servicios) y las subcategorías están exentas de cupo (incluso cuando se personaliza su nombre, icono o color).
- Toda categoría raíz personalizada de tipo `GENERAL` consume 1 cupo en ambos límites (1 en gastos y 1 en ingresos).
- Al descender de plan (downgrade) o seleccionar cupo (HU-57), el usuario puede elegir hasta 5 raíces de gastos y 5 raíces de ingresos para mantener activas; las excedentes pasan a `LOCKED_BY_PLAN` sin borrarse ni alterar movimientos históricos.

### Session 2026-10-08

- Q: ¿Qué plan permite crear una regla personal de alias nueva en HU-16? → A: Solo Premium; el procesamiento de una nueva señal capturada además requiere entitlement y consentimiento vigentes.
- Decisión Sprint 5: Las reglas alias comparan igualdad exacta después de normalizar mayúsculas/minúsculas, acentos y espacios (recortar extremos y colapsar secuencias); la puntuación se conserva. No se habilitan comodines, prefijos, subcadenas ni coincidencia difusa.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Organizar categorías en dos niveles (Priority: P1)

Como usuario, creo y organizo categorías raíz y sus subcategorías para clasificar mis movimientos con claridad, sin poder formar una jerarquía ambigua o más profunda.

**Why this priority**: La clasificación por categorías es la base para registrar y entender los movimientos; debe conservar una estructura simple y consistente.

**Independent Test**: Se puede probar creando una categoría raíz y una subcategoría, asociando ambas a movimientos distintos y verificando que no se admite una subcategoría de otra subcategoría ni una relación circular.

**Acceptance Scenarios**:

1. **Given** un usuario con permiso para crear categorías raíz personalizadas, **When** crea una categoría raíz y una subcategoría bajo ella, **Then** ambas quedan disponibles para clasificar movimientos y se muestra su relación de dos niveles.
2. **Given** una subcategoría existente, **When** el usuario intenta asignarle una categoría hija o convertirla en padre de su categoría raíz, **Then** la operación se rechaza y la jerarquía existente no cambia.
3. **Given** un usuario Free con cinco categorías raíz personalizadas activas de un tipo (`EXPENSE` o `INCOME`), **When** intenta activar o crear una sexta categoría raíz personalizada de ese tipo (o una `GENERAL` sin cupo en ambos), **Then** el sistema no permite activarla e informa el límite aplicable.
4. **Given** una categoría raíz personalizada inactiva de un usuario Free, **When** el usuario crea o activa una nueva categoría raíz personalizada, **Then** se permite la acción solo si el total de categorías raíz personalizadas activas para su tipo contable no supera cinco.

---

### User Story 2 - Personalizar catálogo de categorías (Priority: P2)

Como usuario, cambio el nombre, icono y color de mis categorías, incluidas las predeterminadas, para que su presentación refleje mi forma de organizarme sin perder el historial de movimientos clasificados.

**Why this priority**: La personalización mejora la comprensión cotidiana de los movimientos sin alterar su significado financiero ni su trazabilidad.

**Independent Test**: Se puede probar editando la presentación de una categoría predeterminada que ya clasifica movimientos y verificando que los movimientos históricos mantienen su relación con esa categoría.

**Acceptance Scenarios**:

1. **Given** una categoría raíz o subcategoría, **When** el usuario edita su nombre, icono o color, **Then** los cambios se guardan y se muestran en su presentación para usuarios Free y Premium.
2. **Given** una categoría predeterminada con movimientos históricos, **When** el usuario modifica su nombre, icono o color, **Then** los movimientos conservan su categoría y siguen siendo consultables dentro de su historial.
3. **Given** un formulario o filtro de movimientos, **When** el usuario organiza o busca información de clasificación, **Then** solo puede usar categorías y subcategorías; no se ofrecen etiquetas ni tags.

---

### User Story 3 - Buscar y asignar comercios (Priority: P1)

Como usuario, busco un comercio del catálogo general y lo asigno a un movimiento de forma independiente de la categoría, para identificar dónde ocurrió el gasto sin modificar su clasificación.

**Why this priority**: Distinguir el lugar de la transacción de su categoría evita clasificaciones erróneas y permite reconocer gastos recurrentes por comercio.

**Independent Test**: Se puede probar buscando un comercio conocido, asignándolo a un movimiento categorizado y confirmando que cambiar uno de los dos datos no modifica el otro.

**Acceptance Scenarios**:

1. **Given** un movimiento con una categoría seleccionada, **When** el usuario asigna un comercio del catálogo, **Then** el movimiento conserva por separado la categoría y el comercio asignados.
2. **Given** un texto de búsqueda que identifica parcialmente un nombre de comercio tras normalizar diferencias de mayúsculas, minúsculas, espacios y acentos, **When** el usuario realiza la búsqueda, **Then** se muestran únicamente los comercios cuyo nombre normalizado contiene ese texto, sin usar alias ni equivalencias semánticas.
3. **Given** una búsqueda sin coincidencias claras en el catálogo, **When** el usuario la realiza, **Then** se muestra una lista vacía y se conserva el texto ingresado como texto provisional del movimiento si el usuario decide guardarlo.
4. **Given** un movimiento con comercio asignado y categoría seleccionada, **When** el usuario cambia o elimina la categoría, **Then** el comercio permanece sin cambios; y cuando cambia o elimina el comercio, la categoría permanece sin cambios.
5. **Given** que el catálogo de comercios no puede actualizarse, **When** el usuario abre la búsqueda, **Then** se informa si se muestran resultados guardados que pueden estar desactualizados o si el catálogo no está disponible, sin inventar coincidencias.

---

### User Story 4 - Separar categorías de gastos e ingresos (Priority: P1)

Como usuario, organizo categorías de gastos y de ingresos en pestañas separadas, manteniendo disponibles en ambas las categorías históricas cuyo tipo no puede determinarse.

**Independent Test**: Se crean raíces de gastos e ingresos, se verifica el filtro de cada pestaña, se conserva una categoría `GENERAL` en ambas y se comprueba que las subcategorías hereden el tipo raíz.

**Acceptance Scenarios**:

1. **Given** categorías `EXPENSE`, `INCOME` y `GENERAL`, **When** el usuario abre Gastos o Ingresos, **Then** cada pestaña muestra su tipo y las categorías `GENERAL`, sin mostrar el tipo contrario.
2. **Given** una pestaña seleccionada, **When** el usuario crea una raíz, **Then** la categoría queda persistida con el tipo seleccionado.
3. **Given** una raíz tipada, **When** el usuario crea una subcategoría, **Then** esta hereda el tipo raíz y una solicitud con tipo incompatible se rechaza.
4. **Given** una transferencia, **When** el usuario la registra, **Then** no se asigna categoría.

---

### User Story 5 - Conservar texto original y reutilizar alias de comercio (Priority: P1)

Como usuario Premium, quiero conservar el texto exacto de una señal y asociarlo mediante un alias personal a un comercio del catálogo, para reconocerlo sin perder la evidencia de origen.

**Why this priority**: Un alias mejora el reconocimiento futuro sin sustituir el texto observado ni alterar el catálogo compartido.

**Independent Test**: Un usuario Premium guarda el alias normalizado `IZIPAY*TAMBO` para Tambo; al evaluar después una señal autorizada con ese texto, se propone Tambo y la cadena original permanece intacta. El usuario también puede verificar un resultado sin coincidencia y un conflicto que requiere revisión.

**Acceptance Scenarios**:

1. **Given** una señal con texto original y un usuario Premium que confirma manualmente el comercio canónico, **When** acepta guardar un alias, **Then** se muestra y guarda la regla personal vinculada al comercio existente, sin modificar el catálogo general.
2. **Given** un alias cuyo patrón y texto de origen son iguales tras normalizar mayúsculas, minúsculas, acentos y espacios, **When** una nueva señal autorizada se evalúa, **Then** se propone el comercio canónico y se conserva el texto original exacto como dato separado.
3. **Given** que no existe un alias con coincidencia exacta normalizada, **When** se evalúa la señal, **Then** no se inventa ni asigna un comercio y el texto queda disponible para revisión como provisional.
4. **Given** que varias reglas elegibles coinciden con el texto normalizado pero apuntan a comercios distintos, **When** se evalúa la señal, **Then** se solicita revisión humana y no se selecciona un comercio arbitrariamente.
5. **Given** un usuario Free o una señal cuyo procesamiento no está autorizado por plan o consentimiento, **When** intenta crear una regla nueva o procesar esa señal, **Then** se bloquea la creación de la regla o el procesamiento no autorizado; el registro manual y la búsqueda del catálogo siguen disponibles.
6. **Given** una regla personal que se edita o elimina, **When** se consulta una clasificación ya confirmada, **Then** sus datos históricos no cambian y la modificación afecta solo sugerencias futuras.

---

### User Story 6 - Aplicar preferencias personales de categoría por comercio (Priority: P1)

Como usuario de Kipu, quiero definir la categoría que prefiero para un comercio, para reutilizar mi decisión en operaciones futuras sin modificar la historia ni la preferencia de otra persona.

**Why this priority**: Reutilizar una elección explícita reduce trabajo repetido y mantiene la clasificación bajo control de cada usuario.

**Independent Test**: El usuario define Tambo → Alimentación, luego recibe otra operación compatible de Tambo y verifica que su preferencia prevalece sobre una sugerencia general; los movimientos anteriores y las preferencias de otro usuario permanecen intactos.

**Acceptance Scenarios**:

1. **Given** un comercio identificado y una operación futura, **When** el usuario guarda una preferencia personal, **Then** se conserva la relación privada comercio → categoría sin cambiar el catálogo ni la presentación general del comercio.
2. **Given** una preferencia que apunta a una categoría compatible y elegible, **When** una operación futura del mismo usuario y comercio recibe una sugerencia general distinta, **Then** se propone la preferencia personal.
3. **Given** que la categoría preferida fue inactivada, bloqueada por plan o no es compatible con el tipo de la nueva operación, **When** se evalúa la preferencia, **Then** no se aplica automáticamente y se pide elegir una categoría activa y compatible.
4. **Given** una preferencia que se crea, cambia o elimina, **When** el usuario consulta movimientos confirmados antes de ese cambio, **Then** sus comercios y categorías conservan la clasificación histórica original.
5. **Given** dos usuarios que usan el mismo comercio, **When** cada uno guarda o consulta su preferencia, **Then** cada usuario ve y aplica solo su propia relación.

### Edge Cases

- Una categoría raíz no puede ser su propia subcategoría, directa ni indirectamente.
- Al inactivar una categoría raíz, esta y todas sus subcategorías dejan de estar disponibles para nuevas asignaciones, pero conservan su jerarquía y los movimientos históricos vinculados.
- Una categoría predeterminada modificada visualmente conserva su identidad y la relación de todos los movimientos históricos.
- Si se detectan ediciones incompatibles de la misma categoría realizadas sin conexión en dispositivos distintos, se conservan ambas versiones hasta que el usuario elija cuál aplicar; ninguna sobrescribe a la otra automáticamente.
- Al bajar de Premium a Free, las categorías raíz personalizadas que excedan el cupo se conservan junto con los movimientos históricos; el usuario no puede activar categorías raíz personalizadas adicionales hasta cumplir el límite.
- La búsqueda de comercios no presenta resultados cuando el texto normalizado no aparece en el nombre normalizado de ningún comercio del catálogo.
- Si el catálogo de comercios no está disponible, el usuario distingue entre resultados guardados potencialmente desactualizados y la ausencia total de catálogo; ninguna de ambas situaciones crea coincidencias ni asignaciones automáticas.
- El texto provisional de comercio no crea, modifica ni incorpora un comercio al catálogo general.
- Los formularios, filtros y datos de clasificación no contienen campos ni relaciones de etiquetas o tags.
- Un alias vacío, ligado a un comercio inexistente/inactivo o que intente modificar el catálogo general se rechaza sin crear una regla.
- La comparación de alias usa igualdad exacta después de normalizar mayúsculas, minúsculas, acentos y espacios; no usa comodines, prefijos, subcadenas, equivalencias semánticas ni coincidencia difusa.
- Si varias reglas aplicables producen comercios canónicos diferentes, el caso queda pendiente de revisión humana aunque una regla tenga mayor prioridad.
- La pérdida o expiración de Premium impide crear nuevas reglas de alias; una nueva señal automática no se procesa si falta entitlement vigente o consentimiento, pero la búsqueda/registro manual continúa disponible.
- Una preferencia deja de ser elegible cuando la categoría o su raíz se inactiva, queda bloqueada por plan o no corresponde al tipo de movimiento; no reclasifica datos existentes.
- Los reintentos o cambios sincronizados fuera de orden no deben crear reglas/preferencias duplicadas ni sobrescribir en silencio una edición más reciente.
- El alcance actual comprende solo HU-16 y HU-17 de Sprint 5. HU-14/HU-15 continúan como entregables históricos de Sprint 2; HU-50, Aprendizaje desde correcciones, continúa en Sprint 8 y no forma parte de este incremento.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir a cada usuario crear, editar, activar e inactivar sus categorías raíz personalizadas y sus subcategorías.
- **FR-002**: El sistema DEBE limitar la jerarquía de categorías a una categoría raíz y, como máximo, un nivel de subcategorías; no DEBE permitir un tercer nivel, ciclos ni una categoría como hija de sí misma.
- **FR-003**: El sistema DEBE permitir editar libremente el nombre, icono y color de categorías raíz y subcategorías tanto para usuarios Free como Premium.
- **FR-004**: El sistema DEBE ofrecer desde la primera disponibilidad de la funcionalidad un catálogo inicial activo de categorías predeterminadas que incluya, como mínimo, Alimentación, Transporte y Servicios.
- **FR-005**: El sistema DEBE permitir que el usuario modifique la presentación visual de una categoría predeterminada sin romper ni reasignar los movimientos históricos vinculados a ella.
- **FR-006**: Para un usuario Free, el sistema DEBE impedir que el total de categorías raíz personalizadas activas supere cinco de gastos (`EXPENSE`) y cinco de ingresos (`INCOME`); las de tipo `GENERAL` consumen un cupo en ambos límites. Las categorías predeterminadas del sistema (`SYSTEM`) y las subcategorías no consumen cupo.
- **FR-007**: El sistema DEBE permitir las subcategorías de una categoría raíz personalizada conforme a la disponibilidad de su categoría raíz, sin aplicarles un cupo Free independiente.
- **FR-008**: Cuando una categoría raíz personalizada supere el cupo Free por un cambio de plan, el sistema DEBE preservar la categoría, sus subcategorías y los movimientos existentes, y DEBE bloquear nuevas activaciones o creaciones que mantengan el exceso.
- **FR-009**: Cuando una categoría raíz se inactiva, el sistema DEBE impedir nuevas asignaciones tanto de la categoría raíz como de todas sus subcategorías, sin alterar su jerarquía ni los movimientos históricos vinculados; al reactivarla, sus subcategorías vuelven a estar disponibles para nuevas asignaciones.
- **FR-010**: El sistema NO DEBE incluir etiquetas ni tags en los formularios de movimientos, filtros de movimientos ni datos de clasificación.
- **FR-011**: El sistema DEBE mantener separadas en cada movimiento la relación de categoría y la relación de comercio; cambiar, eliminar o consultar una no DEBE cambiar la otra.
- **FR-012**: El sistema DEBE ofrecer un catálogo general de comercios administrado por Kipu y permitir al usuario buscar comercios dentro de ese catálogo.
- **FR-013**: El sistema DEBE normalizar el texto de búsqueda de comercios al menos para mayúsculas, minúsculas, espacios y acentos antes de buscar coincidencias.
- **FR-014**: El sistema DEBE mostrar solo comercios respaldados por coincidencia válida con el texto de búsqueda normalizado y NO DEBE inventar ni sugerir coincidencias automáticas sin evidencia.
- **FR-015**: Cuando no haya comercios coincidentes, el sistema DEBE mostrar un resultado vacío y permitir que el usuario conserve el texto ingresado como texto provisional del movimiento.
- **FR-016**: El texto provisional de comercio DEBE permanecer independiente del catálogo general y NO DEBE crear un comercio, alias, texto original ni preferencia personal.
- **FR-017**: El incremento original de Sprint 2 DEBE conservar su límite histórico en HU-14 y HU-15. Este refinamiento agrega únicamente HU-16 y HU-17 para Sprint 5; HU-50 y el aprendizaje desde correcciones permanecen fuera de alcance hasta Sprint 8.
- **FR-018**: Si se sincronizan ediciones incompatibles de una misma categoría realizadas sin conexión en dispositivos distintos, incluidas cambios de presentación o de estado activo/inactivo, el sistema DEBE conservar ambas versiones y solicitar al usuario que elija cuál aplicar; NO DEBE sobrescribir una versión automáticamente.
- **FR-019**: El sistema DEBE considerar como evidencia válida una coincidencia parcial del texto normalizado dentro del nombre normalizado de un comercio, pero NO DEBE usar alias ni equivalencias semánticas para generar resultados.
- **FR-020**: El sistema DEBE obtener un catálogo inicial de comercios, conservar la última versión disponible para búsquedas sin conexión y actualizarlo cuando la conectividad se restablezca; DEBE comunicar si los resultados proceden de una versión potencialmente desactualizada o si no hay catálogo disponible.
- **FR-021**: El formulario de creación o edición de un movimiento DEBE incluir selectores independientes de categoría y comercio, además del texto provisional de comercio cuando no se seleccione una coincidencia del catálogo.
- **FR-022**: El sistema DEBE persistir para cada categoría uno de los tipos `EXPENSE`, `INCOME` o `GENERAL`; las categorías existentes sin evidencia de tipo DEBEN migrar a `GENERAL` sin inferir por el historial.
- **FR-023**: Las pestañas Gastos e Ingresos DEBEN mostrar su tipo y las categorías `GENERAL`; una raíz nueva DEBE tomar el tipo seleccionado.
- **FR-024**: Una subcategoría DEBE heredar el tipo de su raíz y el sistema DEBE rechazar una subcategoría con tipo incompatible.
- **FR-025**: Las transacciones nuevas de tipo Transferencia NO DEBEN tener categoría; las categorías `GENERAL` pueden clasificar tanto gastos como ingresos.
- **FR-026**: Al evaluar una señal con texto de comercio, el sistema DEBE conservar el texto fuente exacto separado del comercio canónico y de cualquier copia normalizada usada para comparar; la normalización NO DEBE reemplazar el texto fuente.
- **FR-027**: El sistema DEBE permitir que un usuario Premium guarde una regla personal de alias únicamente después de confirmar manualmente el comercio canónico; el alias DEBE referenciar un comercio existente y NO DEBE modificar el catálogo general.
- **FR-028**: El sistema DEBE comparar un alias mediante igualdad exacta del patrón y del texto normalizados para mayúsculas/minúsculas, acentos y espacios (recorta espacios extremos y colapsa secuencias); la puntuación se conserva. NO DEBE usar comodines, prefijos, subcadenas, coincidencia difusa ni equivalencias semánticas.
- **FR-029**: Cuando ninguna regla personal de alias coincida exactamente, el sistema NO DEBE inventar ni asignar un comercio; DEBE conservar el texto para revisión como provisional.
- **FR-030**: Cuando las reglas elegibles coincidan con el texto normalizado pero apunten a comercios distintos, el sistema DEBE solicitar revisión humana y NO DEBE escoger un destino automáticamente por orden o prioridad.
- **FR-031**: La creación, edición y eliminación de alias DEBE quedar limitada al propietario; un cambio de regla DEBE afectar solo sugerencias futuras y NO DEBE modificar movimientos ya confirmados.
- **FR-032**: El sistema DEBE impedir que un usuario sin Premium cree reglas nuevas de alias. La evaluación de señales capturadas DEBE requerir entitlement y consentimiento vigentes; su ausencia NO DEBE bloquear la búsqueda manual ni el registro manual.
- **FR-033**: Las reglas personales de alias DEBEN sincronizarse con identidad estable, revisiones y operaciones idempotentes; una actualización incompatible NO DEBE sobrescribir silenciosamente el estado más reciente.
- **FR-034**: El sistema DEBE permitir guardar una preferencia personal por comercio identificado y categoría elegible sin cambiar el catálogo, el valor predeterminado general del comercio ni las preferencias de otros usuarios.
- **FR-035**: Una preferencia personal de categoría DEBE aplicarse solo a una operación futura del mismo usuario y comercio cuando su categoría sea compatible con el tipo de la operación y esté elegible para nuevas asignaciones.
- **FR-036**: Una preferencia elegible DEBE prevalecer sobre una sugerencia general distinta para esa operación futura.
- **FR-037**: Si la categoría preferida o su raíz está inactiva, bloqueada por plan o no es compatible con el tipo de operación, el sistema NO DEBE aplicarla automáticamente y DEBE solicitar una categoría activa y compatible.
- **FR-038**: Crear, cambiar o eliminar una preferencia personal NO DEBE reclasificar movimientos confirmados ni alterar su historia.
- **FR-039**: Las preferencias personales DEBEN ser owner-scoped; un usuario solo puede leer y cambiar sus propias preferencias, y sus comandos de sincronización DEBEN ser idempotentes.

### Key Entities

- **Categoría**: Clasificación de un movimiento con identidad estable, presentación editable (nombre, icono y color), estado de actividad y tipo raíz o subcategoría.
- **Relación de categoría**: Vínculo opcional de una subcategoría con una categoría raíz que solo admite dos niveles y no admite ciclos.
- **Categoría predeterminada**: Categoría inicial ofrecida por Kipu cuya identidad se conserva aunque el usuario cambie su presentación visual.
- **Movimiento**: Registro financiero histórico que puede mantener, de manera independiente, una relación con una categoría y una relación con un comercio, además de un texto provisional de comercio.
- **Comercio**: Identidad incluida en el catálogo general administrado por Kipu y disponible para asignarse a movimientos.
- **Texto provisional de comercio**: Texto ingresado por el usuario cuando la búsqueda no encuentra un comercio; se conserva en el movimiento sin convertirse en comercio del catálogo.
- **Texto original de comercio**: Cadena exacta recibida desde una señal antes de normalizar o proponer un comercio; se conserva separada de los campos de comercio y de texto provisional.
- **Regla personal de alias**: Relación privada usuario → patrón normalizado → comercio canónico existente; solo opera con igualdad exacta normalizada y no modifica el catálogo.
- **Preferencia personal de categoría**: Relación privada usuario → comercio → categoría elegible; modifica sugerencias futuras, no movimientos históricos ni la categoría predeterminada general del comercio.
- **Plan efectivo**: Condición Free o Premium que determina el cupo de categorías raíz personalizadas activas sin alterar categorías ni movimientos históricos.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En pruebas de aceptación, el 100% de los intentos de crear una jerarquía de tercer nivel, un ciclo o una autorreferencia de categorías son rechazados sin modificar la jerarquía válida existente.
- **SC-002**: En pruebas con usuarios Free, el 100% de los intentos de activar una sexta categoría raíz personalizada son bloqueados, mientras que se permite crear subcategorías bajo categorías raíz disponibles.
- **SC-003**: El 100% de los movimientos históricos vinculados a una categoría predeterminada continúan consultables con la misma identidad de categoría después de editar su nombre, icono o color.
- **SC-004**: Al menos el 95% de los usuarios de prueba puede crear una categoría raíz con una subcategoría y asignarla a un movimiento en menos de 2 minutos, sin ayuda.
- **SC-005**: En un conjunto de pruebas de búsquedas de comercios con coincidencias claras, al menos el 95% muestra el comercio esperado en menos de 1 segundo desde que el usuario ejecuta la búsqueda.
- **SC-006**: El 100% de las búsquedas de comercios sin evidencia de coincidencia muestra una lista vacía, no asigna un comercio automáticamente y permite conservar el texto provisional.
- **SC-007**: En pruebas de edición de movimientos, el 100% de los cambios de categoría conserva el comercio previamente asignado y el 100% de los cambios de comercio conserva la categoría previamente asignada.
- **SC-008**: En pruebas con dos dispositivos sin conexión, el 100% de los conflictos de presentación o de estado activo/inactivo de una categoría conserva ambas versiones y exige una decisión explícita del usuario.
- **SC-009**: En una evaluación de aceptación, al menos el 95% de los participantes completa la creación de una categoría raíz, una subcategoría y su asignación a un movimiento en menos de 2 minutos; la búsqueda de hasta 100 comercios guardados muestra resultados o el estado de disponibilidad correcto en menos de 1 segundo.
- **SC-010**: En el 100% de las evaluaciones de alias, el texto fuente exacto permanece intacto y separado del patrón normalizado y del comercio canónico propuesto.
- **SC-011**: En el 100% de las pruebas, un usuario Free no puede crear una regla de alias; cuando entitlement o consentimiento no autorizan una nueva señal, el 100% de esas señales se detiene mientras el acceso al flujo manual continúa.
- **SC-012**: En el 100% de las pruebas sin coincidencia o con reglas coincidentes que llevan a comercios distintos, no se asigna un comercio automáticamente y se muestra la vía de revisión correspondiente.
- **SC-013**: En el 100% de las pruebas de edición/eliminación de alias y preferencias, los movimientos confirmados conservan la clasificación que tenían antes del cambio.
- **SC-014**: En el 100% de las pruebas con una preferencia vigente, elegible y compatible, la preferencia personal prevalece sobre una sugerencia general; una preferencia no elegible nunca se aplica automáticamente.
- **SC-015**: En las pruebas con dos usuarios, el 100% de las consultas y cambios de alias/preferencias queda aislado por propietario y los reintentos no producen duplicados.

## Assumptions

- Los usuarios ya disponen de movimientos y categorías existentes de funcionalidades previas; esta funcionalidad debe preservar las relaciones históricas válidas.
- Las categorías predeterminadas iniciales incluyen Alimentación, Transporte y Servicios, además de otras categorías que Kipu pueda definir dentro del mismo catálogo inicial.
- "Categorías personalizadas" son las creadas por el usuario y se distinguen de las categorías predeterminadas para calcular el cupo Free.
- Para usuarios Free que excedan el cupo tras perder Premium, se aplica un modelo de bloqueo de nuevas activaciones y creaciones, sin eliminar ni alterar datos históricos.
- La búsqueda directa del catálogo continúa usando subcadena normalizada y no consulta alias; las reglas personales de alias se aplican solo al flujo de texto de señal descrito en HU-16.
- La gestión administrativa con la que Kipu actualiza el catálogo general de comercios no forma parte de la experiencia de usuario de este Sprint; el Sprint sí incluye su consulta, conservación local y actualización al recuperar conectividad.
- HU-14 y HU-15 corresponden a la base histórica entregada en Sprint 2; este refinamiento incrementa el alcance de la misma épica con HU-16 y HU-17 únicamente para Sprint 5.
- HU-50 (Aprendizaje desde correcciones) sigue asignada a Sprint 8; este incremento prepara reglas y preferencias reutilizables, pero no agrega ingestión de señales, bandeja, aprendizaje automático de correcciones ni otras historias de EP-AUT.
- Las preferencias personales no agregan una puerta Premium independiente; solo se aplican cuando la categoría ya es elegible para el tipo de operación y no está bloqueada por plan.
