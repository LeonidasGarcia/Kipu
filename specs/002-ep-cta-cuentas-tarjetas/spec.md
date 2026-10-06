# Feature Specification: EP-CTA - Cuentas y Tarjetas

**Feature Branch**: `002-ep-cta-cuentas-tarjetas`

**Created**: 2026-09-21

**Status**: Refined

**Refined**: 2026-09-24 - Sprint 3 refinement of HU-09..HU-13 against the official Product Backlog and dependency matrix; HU-07 and HU-08 remain the closed Sprint 2 increment.

**Refined**: 2026-10-06 - Corrección #21: la consulta desde una tarjeta solo muestra una referencia para una coincidencia exacta de producto, emisor, red y moneda; no muestra ni selecciona coincidencias aproximadas.

**Input**: Especificar funcionalmente EP-CTA completa, con HU-07 a HU-13 y RF-C01 a RF-C12; Sprint 2 entrega cuentas y registro de tarjetas, y Sprint 3 evoluciona crédito, alertas, tasas, pagos y compras en cuotas.

**Refined**: 2026-10-06 - Validacion PR #23 de #21: aliases explicitos entre presets y catalogo, estados exclusivos de tarjeta/catalogo, reintento sin error residual y borrador de TEA por tarjeta.

## Control de la Epica

| Campo | Valor |
|-------|-------|
| Epica | EP-CTA - Cuentas y Tarjetas |
| Incremento Sprint 2 | HU-07 y HU-08, 10 puntos |
| Evolucion Sprint 3 | HU-09 a HU-13, 29 puntos |
| Historias cubiertas | HU-07, HU-08, HU-09, HU-10, HU-11, HU-12 y HU-13 |
| Requisitos cubiertos | RF-C01 a RF-C12 |
| Prioridad | Alta |
| Estado del artefacto | V1.0 - Especificacion funcional completa |

## Objetivo y Alcance

### Objetivo de Negocio

Permitir que cada usuario modele su estructura financiera personal distinguiendo con exactitud el dinero propio disponible de las obligaciones y la capacidad de credito de terceros. Las cuentas y tarjetas deben poder administrarse sin duplicar activos, alterar historia contable ni distorsionar patrimonio, gastos o estadisticas operativas.

### Alcance del Sprint 2

- Alta, consulta, edicion visual y archivo de cuentas de efectivo, ahorro o bancarias y billeteras digitales.
- Registro atomico y auditable del saldo inicial como movimiento de apertura.
- Presets visuales de BCP, BBVA, Interbank, Scotiabank y Banco de la Nacion, ademas de una opcion generica.
- Registro de tarjetas de debito y credito sin solicitar ni conservar PAN completo o CVV.
- Vinculo obligatorio de cada tarjeta de debito con una cuenta de ahorros existente del mismo usuario y moneda.
- Aplicacion del cupo Free de cuatro instrumentos activos, con las exclusiones aprobadas.
- Operacion local, recuperacion tras reinicio, sincronizacion sin efectos duplicados y aislamiento por usuario para HU-07 y HU-08.

### Evolucion Planificada para Sprint 3

- Esta actualizacion incorpora exclusivamente HU-09, HU-10, HU-11, HU-12 y HU-13. HU-07 y HU-08 permanecen cerradas en Sprint 2; no se incorporan historias de sprints futuros.
- Consulta de linea total, credito utilizado, credito disponible y porcentaje de utilizacion.
- Alertas deduplicadas por cruces de 50%, 80% y 100%.
- Catalogo informativo de productos y tasas referenciales de emisores peruanos, y TEA personal.
- Pago de tarjeta como transferencia amortizadora.
- Registro confirmado de compras con credito y simulacion de una a 36 cuotas con redondeo exacto.
- Operacion local, recuperacion tras reinicio, sincronizacion sin efectos duplicados y aislamiento por usuario para HU-09 a HU-13.

### Fuera de Alcance

- Mover dinero, ejecutar pagos reales, emitir credito o conectarse directamente a una entidad financiera.
- Solicitar o almacenar PAN completo, CVV, claves bancarias o credenciales de autenticacion bancaria.
- Open Banking, lectura de SMS o correo, e importacion universal de historiales bancarios.
- Garantizar como contractuales las tasas del catalogo o los intereses de una simulacion.
- Reconocer intereses simulados como deuda o gasto real antes de que exista un cargo confirmado.
- Conversion automatica entre PEN y USD o consolidacion patrimonial multimoneda mediante un tipo de cambio no especificado.
- Eliminar fisicamente instrumentos o movimientos que formen parte de la historia financiera.

### Dependencias y Trazabilidad de Entrega

| Historia | Sprint | Puntos | Requisitos | Dependencias funcionales |
|----------|--------|--------|------------|--------------------------|
| HU-07 | 2 | 5 | RF-C01, RF-C06 | Sesion activa y politica de plan vigente |
| HU-08 | 2 | 5 | RF-C02, RF-C03, RF-C05 | HU-07 para vincular debito |
| HU-09 | 3 | 8 | RF-C04 | Bloqueantes: HU-08, HU-18, HU-19; relacionadas: HU-10, HU-12, HU-13 |
| HU-10 | 3 | 3 | RF-C07 | Bloqueante: HU-09; parcial: HU-42; relacionada: HU-44 |
| HU-11 | 3 | 5 | RF-C08, RF-C09 | Bloqueante: HU-08; relacionadas: HU-09, HU-13 |
| HU-12 | 3 | 5 | RF-C10 | Bloqueantes: HU-07, HU-09, HU-18, HU-19; relacionada: HU-13 |
| HU-13 | 3 | 8 | RF-C11, RF-C12 | Bloqueantes: HU-08, HU-18, HU-19, HU-23; parcial: HU-11; relacionada: HU-48 |

### Precondiciones Generales

- Existe una sesion previamente autenticada y se conoce al usuario propietario.
- La moneda de cada instrumento y operacion es PEN o USD; PEN es la opcion inicial al crear una cuenta.
- El acceso Premium efectivo, si corresponde, ha sido verificado por la politica de monetizacion; una preferencia o intencion comercial no elimina el cupo Free.
- Las capacidades del Sprint 3 solo se consideran entregables cuando su sprint sea implementado, aunque quedan funcionalmente definidas en este artefacto.

## Clarifications

### Session 2026-09-21

- Q: ¿Qué debe ocurrir cuando el usuario intenta pagar un principal mayor que la deuda pendiente de la tarjeta? → A: Rechazar sin efectos y exigir que el usuario ajuste el importe.
- Q: ¿Debe permitirse eliminar físicamente un instrumento que nunca tuvo movimientos, vínculos dependientes ni deuda? → A: Eliminar físicamente solo si no tiene historia, deuda ni dependencias; archivar en cualquier otro caso.
- Q: ¿Qué debe hacer Kipu si el usuario registra otra tarjeta con el mismo emisor, red y últimos cuatro dígitos? → A: Permitir con advertencia y alias distinguible.
- Q: ¿Cómo debe corregirse el importe, la moneda o la fecha del saldo inicial después de crear una cuenta? → A: Apertura inmutable; ajuste auditable, o recreación si cambia la moneda.
- Q: ¿Cómo debe contabilizarse una tarjeta de débito vinculada a una cuenta para el límite de cuatro instrumentos activos del plan Free? → A: La cuenta consume un cupo y la tarjeta vinculada consume otro.

### Session 2026-09-24

- Q: ¿Cómo se calcula el vencimiento de la primera cuota a partir de `closing_day` y `due_day`? → A: La compra ocurrida hasta el `closing_day`, inclusive, pertenece a ese ciclo; la primera cuota vence en el siguiente `due_day`, y las cuotas posteriores vencen mensualmente.
- Q: ¿Qué método de amortización se usa para simular intereses con TEA? → A: Cuota fija (método francés), convirtiendo TEA a TEM con `TEM = (1 + TEA)^(1/12) - 1`; cada cuota es estimada y no genera asientos contables.
- Q: ¿Cómo se asigna un pago parcial de tarjeta entre cuotas pendientes? → A: FIFO por `due_date` ascendente. Se amortiza primero la cuota más antigua; un importe parcial reduce su capital y no pasa a una cuota posterior mientras conserve saldo.
- Q: ¿Qué snapshot y política de vigencia usa el catálogo referencial de Sprint 3? → A: `KIPU_CATALOGO_TARJETAS_CREDITO_PERU_2026.md`, con corte 2026-09-24 y 44 productos confirmados (BCP 18, BBVA 10, Interbank 16). La interfaz muestra `Tasa referencial al 24/09/2026`; no hay caducidad automática por edad y se conservan las advertencias/conflictos de la fuente.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - HU-07 Administrar activos liquidos (Priority: P1)

Como usuario, quiero crear y mantener cuentas que representen mi dinero real, para conocer mis activos liquidos sin perder la trazabilidad de sus saldos.

**Why this priority**: Es la base contable del Sprint 2 y de los pagos futuros. Sin cuentas confiables no puede distinguirse efectivo propio de deuda ni aplicarse una transferencia amortizadora.

**Independent Test**: Puede probarse creando cuentas de cada tipo y moneda, verificando el movimiento de apertura, editando solo su presentacion y archivando una cuenta con historia. Entrega valor si el saldo siempre se reproduce desde movimientos auditables.

**Acceptance Scenarios**:

1. **Alta con saldo inicial**: **Dado que** el usuario tiene cupo disponible, **Cuando** crea una cuenta con alias, tipo, moneda, icono, color, saldo inicial y momento de apertura, **Entonces** se crean de forma indivisible la cuenta y un unico movimiento de apertura por el importe indicado.
2. **Tipos y moneda**: **Dado que** el usuario inicia un alta, **Cuando** elige efectivo, ahorro o bancaria, o billetera digital, **Entonces** puede usar PEN, seleccionada inicialmente, o USD, sin mezclar importes entre monedas.
3. **Preset peruano**: **Dado que** crea una cuenta bancaria, **Cuando** elige BCP, BBVA, Interbank, Scotiabank, Banco de la Nacion o generica, **Entonces** recibe una apariencia inicial editable sin que la marca determine el saldo ni la propiedad.
4. **Edicion no financiera**: **Dado que** una cuenta tiene movimientos, **Cuando** cambia alias, icono o color, **Entonces** sus movimientos, saldo, moneda y resultados historicos permanecen sin cambios.
5. **Archivo con historia**: **Dado que** una cuenta tiene al menos un movimiento, **Cuando** solicita retirarla, **Entonces** queda archivada, deja de admitir operaciones nuevas y conserva todo su historial y participacion correcta en periodos pasados.
6. **Alta offline y reinicio**: **Dado que** no hay conectividad, **Cuando** crea una cuenta y reinicia la aplicacion, **Entonces** la cuenta y su unico movimiento de apertura permanecen disponibles y una sincronizacion posterior no duplica el saldo.
7. **Correccion de apertura**: **Dado que** la cuenta ya fue creada, **Cuando** el usuario detecta un importe o fecha de apertura incorrectos, **Entonces** registra un ajuste auditable sin editar el movimiento original; si la moneda es incorrecta, archiva la cuenta y crea otra en la moneda correcta.

---

### User Story 2 - HU-08 Registrar y vincular tarjetas (Priority: P1)

Como usuario, quiero identificar mis tarjetas de debito y credito sin exponer datos secretos, para asociarlas correctamente a mis activos o pasivos.

**Why this priority**: Completa el incremento del Sprint 2, evita que una tarjeta de debito duplique dinero y establece el limite de privacidad necesario para toda evolucion de credito.

**Independent Test**: Puede probarse registrando una tarjeta de debito vinculada, una tarjeta de credito y entradas prohibidas. Entrega valor si debito comparte el saldo de su cuenta, credito no aumenta activos y nunca se acepta PAN completo ni CVV.

**Acceptance Scenarios**:

1. **Debito vinculado**: **Dado que** existe una cuenta de ahorros activa del mismo usuario y moneda, **Cuando** registra una tarjeta de debito y la vincula, **Entonces** la tarjeta muestra el saldo de esa cuenta sin crear otro activo ni otro saldo.
2. **Debito sin cuenta valida**: **Dado que** no existe una cuenta de ahorros activa compatible, **Cuando** intenta registrar debito, **Entonces** el alta no se confirma y se explica que debe elegir o crear una cuenta valida.
3. **Credito identificado**: **Dado que** registra una tarjeta de credito, **Cuando** ingresa emisor, red admitida, ultimos cuatro digitos, linea autorizada y dias de corte y vencimiento, **Entonces** queda identificada como pasivo potencial y su linea no se suma al dinero disponible.
4. **Datos prohibidos**: **Dado que** cualquier formulario o entrada contiene un PAN completo o CVV, **Cuando** el usuario intenta continuar, **Entonces** el dato se rechaza, no se conserva y se explica que solo se requieren los ultimos cuatro digitos.
5. **Privacidad visual**: **Dado que** consulta una tarjeta registrada, **Cuando** se muestra su identificacion, **Entonces** solo se presentan emisor, red y una referencia enmascarada terminada en los cuatro digitos guardados.
6. **Registro offline y reinicio**: **Dado que** registra una tarjeta sin conectividad, **Cuando** reinicia y luego recupera la red, **Entonces** la tarjeta mantiene un unico vinculo y no aparece como instrumento duplicado.
7. **Coincidencia de identificacion visible**: **Dado que** ya existe una tarjeta con el mismo emisor, red y ultimos cuatro digitos, **Cuando** registra otra intencionalmente, **Entonces** recibe una advertencia y debe asignarle un alias distinto antes de confirmar; ambas conservan identidades estables independientes.
8. **Cupo de debito**: **Dado que** un usuario Free tiene una cuenta computable activa, **Cuando** registra una tarjeta de debito vinculada, **Entonces** la tarjeta requiere y consume un segundo cupo, aunque no cree otro saldo.

---

### User Story 3 - HU-09 Consultar linea y saldos de credito (Priority: P2)

Como usuario, quiero ver por separado mi deuda, credito disponible y linea total, para entender mi capacidad de endeudamiento sin confundirla con dinero propio.

**Why this priority**: Es la capacidad base de consulta: presenta la linea, la deuda y el credito disponible a partir de efectos confirmados. Sustenta las alertas y los pagos. La validacion E2E de deuda originada mediante una compra en Kipu se completa al integrar HU-13.

**Independent Test**: Puede probarse con lineas positivas, cero, deuda inferior, igual o superior a la linea y cierres en meses cortos. Entrega valor si cada cifra es reproducible y ningun credito aumenta los activos.

**Acceptance Scenarios**:

1. **Utilizacion**: **Dado que** la linea es S/1000 y la deuda S/200, **Cuando** consulta el detalle, **Entonces** ve utilizado S/200 y credito disponible S/800 separados del banco.
2. **Febrero**: **Dado que** el cierre configurado es dia 31, **Cuando** consulta febrero, **Entonces** el evento usa el ultimo dia valido de ese mes.
3. **Linea cero**: **Dado que** no hay linea informada, **Cuando** consulta porcentaje, **Entonces** ve "No disponible" sin error matematico.
4. **Cambio de limite**: **Dado que** la deuda vigente es S/500, **Cuando** reduce la linea a S/400, **Entonces** la deuda no desaparece y se muestra sobreutilizacion.

---

### User Story 4 - HU-10 Recibir alertas de utilizacion (Priority: P3)

Como usuario, quiero recibir avisos al cruzar niveles relevantes de utilizacion, para actuar antes de agotar mi linea de credito.

**Why this priority**: Desarrolla alertas sobre la utilizacion consultada por HU-09. Puede verificarse con deuda confirmada existente; el recorrido E2E que origina una compra en Kipu y luego genera su alerta se completa al integrar HU-13, sin depender de permisos opcionales del dispositivo.

**Independent Test**: Puede probarse elevando, manteniendo, reduciendo y volviendo a elevar la deuda alrededor de cada umbral, con notificaciones permitidas y denegadas.

**Acceptance Scenarios**:

1. **Cruce ascendente**: **Dado que** la utilizacion estaba debajo de 50%, **Cuando** una operacion confirmada la lleva a 50% o mas, **Entonces** se crea un aviso de 50% y queda visible dentro de Kipu.
2. **Multiples umbrales**: **Dado que** una sola operacion pasa de menos de 50% a 100% o mas, **Cuando** se recalcula la utilizacion, **Entonces** se registran una sola vez los cruces de 50%, 80% y 100%.
3. **Deduplicacion**: **Dado que** ya se aviso un umbral y la utilizacion permanece sobre el, **Cuando** ocurren nuevos cambios que no bajan de ese umbral, **Entonces** no se emite otra alerta para ese cruce.
4. **Rearme**: **Dado que** la utilizacion bajo de un umbral previamente alertado, **Cuando** vuelve a alcanzarlo o superarlo, **Entonces** se permite un nuevo aviso para ese umbral.
5. **Permiso denegado**: **Dado que** las notificaciones del dispositivo estan denegadas, **Cuando** se cruza un umbral, **Entonces** el aviso permanece visible dentro de Kipu y ninguna operacion financiera es bloqueada.

---

### User Story 5 - HU-11 Consultar catalogo y registrar TEA (Priority: P3)

Como usuario, quiero consultar tasas referenciales y guardar mi TEA real, para estimar financiamientos con informacion cuyo origen y vigencia sean comprensibles.

**Why this priority**: Mejora las simulaciones sin convertir estimaciones en hechos contables ni alterar compras historicas.

**Independent Test**: Puede probarse consultando los 44 productos del snapshot con su fecha, procedencia, datos por moneda y advertencias; confirmando que no caducan por edad; y cambiando la TEA personal despues de una operacion historica.

**Acceptance Scenarios**:

1. **Catalogo transparente**: **Dado que** existen referencias de emisores peruanos, **Cuando** el usuario consulta un producto, **Entonces** ve emisor, producto, tasa, moneda o alcance aplicable, fecha de verificacion y una advertencia visible de que es una estimacion informativa.
2. **Advertencia de fuente**: **Dado que** la fuente marca un producto o una tasa como pendiente, conflictiva, no publicada o no desglosada por moneda, **Cuando** se muestra, **Entonces** se conserva y presenta esa advertencia, no se presenta como dato confirmado y no se selecciona una TEA ambigua para simulacion.
3. **TEA personal**: **Dado que** el usuario conoce la TEA de su tarjeta, **Cuando** la registra o modifica, **Entonces** las simulaciones nuevas pueden usarla y las transacciones, deudas y simulaciones confirmadas anteriormente no se reescriben.
4. **TEA invalida**: **Dado que** el valor esta fuera del rango admitido o no es numerico, **Cuando** intenta guardarlo, **Entonces** se rechaza con una explicacion y se conserva el ultimo valor valido.
5. **Cobertura y fecha del catalogo**: **Dado** el snapshot oficial con fecha de corte 2026-09-24, **Cuando** el usuario consulta el catalogo, **Entonces** encuentra 18 productos BCP, 10 BBVA y 16 Interbank, ve `Tasa referencial al 24/09/2026`, y cada tasa, moneda, rango, condicion de membresia, fuente y advertencia reproduce lo publicado sin caducidad automatica ni datos inferidos.
6. **Referencia contextual por tarjeta**: **Dada** una tarjeta de credito abierta desde su detalle, **Cuando** consulta tasas, **Entonces** la pantalla muestra tarjeta y producto, recupera su TEA personal guardada y solo presenta la referencia de una coincidencia unica y exacta de producto, emisor, red y moneda; ante identidad incompleta, ausencia o ambiguedad, informa el estado sin elegir una tasa.

---

### User Story 6 - HU-12 Pagar una tarjeta de credito (Priority: P2)

Como usuario, quiero registrar el pago de mi tarjeta desde una cuenta bancaria, para reducir efectivo y deuda sin duplicar mis gastos.

**Why this priority**: Desarrolla el pago de deuda confirmada y protege los reportes contra la doble contabilizacion. El recorrido E2E desde una compra originada en Kipu hasta su pago se verifica al integrar HU-13.

**Independent Test**: Puede probarse pagando una deuda desde una cuenta con fondos, revisando ambos saldos y los reportes de gasto, y repitiendo la misma orden para verificar que no se aplique dos veces.

**Acceptance Scenarios**:

1. **Pago simetrico**: **Dado que** existe deuda y una cuenta bancaria activa con saldo suficiente en la misma moneda, **Cuando** confirma un pago, **Entonces** una unica transferencia amortizadora reduce por igual el saldo bancario y la deuda principal.
2. **Sin gasto duplicado**: **Dado que** el consumo fue reconocido al comprar, **Cuando** se registra su pago, **Entonces** no aumenta el gasto operativo ni el consumo mensual.
3. **Pago mayor a deuda**: **Dado que** el importe supera la deuda pendiente, **Cuando** intenta confirmar, **Entonces** no se procesa como principal, se informa el excedente y se exige ajustar explicitamente el pago a un importe no mayor que la deuda.
4. **Fondos insuficientes o moneda distinta**: **Dado que** la cuenta no tiene saldo suficiente o su moneda difiere, **Cuando** intenta pagar, **Entonces** la operacion completa se rechaza sin modificar ninguno de los dos saldos.
5. **Reintento**: **Dado que** un pago confirmado se reenvia por reinicio o sincronizacion, **Cuando** se procesa la misma identidad de operacion, **Entonces** no se vuelve a reducir efectivo ni deuda.
6. **Pago parcial FIFO**: **Dado** que hay cuotas pendientes, **Cuando** el usuario confirma un pago menor que la deuda, **Entonces** se aplica primero a la cuota pendiente con menor `due_date`; si no alcanza para cubrirla, reduce parcialmente el capital de esa cuota y no avanza a otra. Si la cubre, continua en orden cronologico. La suma de asignaciones coincide con el pago confirmado; la operacion reduce el activo y el pasivo por igual y no crea gasto.

---

### User Story 7 - HU-13 Registrar compras y simular cuotas (Priority: P2)

Como usuario, quiero confirmar compras con credito y simular su financiamiento, para reconocer el gasto cuando ocurre y proyectar cuotas sin presentar intereses estimados como cargos reales.

**Why this priority**: Origina en Kipu nuevas deudas de consumo y reconoce su principal exactamente una vez. Al integrar HU-13 queda habilitado el recorrido E2E de compra originada en la app, consulta de deuda, alertas de utilizacion y pago, sobre las capacidades base desarrolladas por HU-09, HU-10 y HU-12.

**Independent Test**: Puede probarse confirmando una compra de S/100 en tres cuotas (S/33.34, S/33.33 y S/33.33), rechazando una candidata sin efectos y verificando planes de hasta 36 cuotas.

**Acceptance Scenarios**:

1. **Confirmacion obligatoria**: **Dado que** una compra fue detectada por cualquier fuente, **Cuando** aun no fue confirmada explicitamente, **Entonces** no modifica deuda, gasto, saldo ni estadisticas.
2. **Reconocimiento completo**: **Dado que** el usuario confirma una compra de credito por S/100 en tres cuotas, **Cuando** se registra, **Entonces** se reconoce un gasto de S/100 y un aumento de deuda de S/100 una sola vez en la fecha de compra, y el cronograma distribuye S/33.34, S/33.33 y S/33.33.
3. **Rechazo o correccion**: **Dado que** la compra detectada es incorrecta, **Cuando** el usuario la rechaza o corrige antes de confirmar, **Entonces** no se produce un efecto financiero hasta confirmar los datos corregidos.
4. **Simulacion de cuotas**: **Dado que** el usuario selecciona entre 1 y 36 cuotas y existe una TEA personal valida o una tasa referencial identificada, **Cuando** solicita la simulacion, **Entonces** aplica cuota fija (metodo frances), convierte TEA a TEM con `TEM = (1 + TEA)^(1/12) - 1`, estima cada cuota con `cuota = principal * TEM / (1 - (1 + TEM)^(-n))` (o principal / n si TEM es cero), y muestra metodo, principal, interes estimado, total financiado, importe y fecha de cada cuota, tasa y procedencia, y advertencia visible de estimacion.
5. **Sin tasa utilizable**: **Dado que** no existe una tasa personal ni referencial aplicable, **Cuando** simula, **Entonces** ve una distribucion sin intereses inventados y una advertencia de que debe ingresar la tasa contractual para estimarlos.
6. **Redondeo exacto**: **Dado que** el total financiado no se divide exactamente en centimos entre las cuotas, **Cuando** se genera el cronograma, **Entonces** el residuo de redondeo se asigna a la cuota 1 y la suma de las cuotas coincide exactamente con el total mostrado.
7. **Interes no confirmado**: **Dado que** existe una simulacion, **Cuando** se consulta la deuda real, **Entonces** solo incluye el principal confirmado y cargos reales confirmados; el interes estimado no se convierte por si solo en gasto ni deuda.
8. **Vencimientos por ciclo**: **Dado que** una compra se confirma antes o el mismo dia de cierre, **Cuando** se genera el cronograma, **Entonces** la primera cuota vence en el siguiente dia de vencimiento despues de ese cierre; si se confirma despues del cierre, vence despues del siguiente cierre aplicable. Las demas cuotas vencen en los meses consecutivos, ajustando el dia efectivo al ultimo dia valido sin cambiar el dia preferido.

---

### Edge Cases

- Un saldo inicial de cero crea igualmente un unico movimiento de apertura auditable por cero; un saldo inicial negativo se rechaza para cuentas de activos liquidos.
- El movimiento de apertura no puede editarse despues del alta. Un error de importe o fecha se corrige mediante ajuste auditable; una moneda incorrecta requiere archivar la cuenta y crear otra.
- Dos cuentas pueden compartir alias; su identidad estable y propietario impiden confundir sus movimientos.
- Dos tarjetas pueden compartir emisor, red y ultimos cuatro digitos porque esos datos no prueban unicidad; el segundo registro exige advertencia y alias distinguible, mientras los reintentos de una misma operacion siguen deduplicandose por su identidad estable.
- Archivar una cuenta vinculada a una tarjeta de debito deshabilita nuevas operaciones de la tarjeta, pero conserva el vinculo y el historial.
- Un instrumento archivado deja de consumir cupo Free; reactivarlo requiere cupo disponible. Archivar no altera sus efectos historicos.
- Un instrumento sin movimientos, deuda ni vinculos dependientes puede eliminarse fisicamente; desde que existe cualquiera de ellos, la unica retirada permitida es el archivo no destructivo.
- El efectivo fisico y la Cuenta Metas virtual no consumen cupo; una tarjeta de debito y su cuenta vinculada consumen un instrumento cada una porque ambos son instrumentos activos, aunque solo la cuenta represente el activo monetario.
- Una tarjeta de credito con linea cero puede conservar deuda, mostrar disponible cero y utilizacion no disponible.
- Una reduccion de linea por debajo de la deuda no reduce ni reescribe la deuda; muestra utilizacion superior a 100% y credito disponible cero.
- Si el dia de corte o vencimiento preferido no existe en un mes, solo la fecha efectiva de ese mes se ajusta al ultimo dia; la preferencia original se conserva.
- Una operacion que cruza varios umbrales genera cada cruce aplicable una vez; bajar solo por debajo de 80% rearma 80% y 100%, pero no 50% si aun permanece sobre 50%.
- Las tasas referenciales sin fecha de verificacion no se publican como vigentes; la falta de catalogo no impide registrar una TEA personal.
- Una TEA personal modificada aplica a simulaciones posteriores y no recalcula cronogramas previamente confirmados.
- Un pago no puede usar una cuenta archivada, una cuenta que no sea bancaria o de ahorros, una moneda distinta ni fondos insuficientes.
- Una compra puede superar la linea: conserva el principal real y genera el estado de sobreutilizacion, sin crear credito disponible negativo.
- La cancelacion o correccion posterior de una compra o pago se realiza mediante una operacion auditable de reverso o ajuste, nunca borrando silenciosamente el efecto original.
- Si la aplicacion se cierra entre una confirmacion y su sincronizacion, al reiniciar se recupera una unica operacion confirmada con el mismo efecto financiero.
- Si cambia la sesion activa, los instrumentos del usuario anterior quedan aislados y ocultos; las operaciones pendientes conservan su propietario original.

## Requirements *(mandatory)*

### Reglas de Negocio

- **RN-01 - Separacion matematica absoluta**: El dinero disponible DEBE derivarse solo de activos liquidos propios. La linea total, el credito disponible y cualquier capacidad de endeudamiento NO DEBEN sumarse a activos, efectivo disponible ni patrimonio.
- **RN-02 - Privacidad bancaria absoluta**: Kipu NO DEBE solicitar, aceptar, almacenar, sincronizar ni exponer PAN completo o CVV. Solo puede conservar los ultimos cuatro digitos necesarios para identificar una tarjeta.
- **RN-06 - Pago amortizador**: El pago de una tarjeta DEBE reducir efectivo y pasivo como transferencia interna; NO DEBE reconocer ingreso, gasto ni consumo adicional.
- **RN-10 - Apertura atomica e inmutable**: Toda cuenta DEBE nacer junto con un unico movimiento de apertura por su saldo inicial, moneda y momento de apertura. Ninguna de las dos partes puede quedar confirmada sin la otra y, una vez creada, la apertura NO DEBE editarse: importe o fecha se corrigen mediante un ajuste auditable y una moneda incorrecta exige archivar la cuenta y crear otra.
- **RN-15 - Cuotas exactas y estimaciones honestas**: El principal de una compra en cuotas DEBE reconocerse una sola vez. Los centimos DEBEN distribuirse sin alterar el total y ningun interes estimado puede convertirse en cargo real sin evidencia y confirmacion.
- **RN-CTA-01 - Historia inmutable por apariencia**: Editar alias, icono, color o preset NO DEBE cambiar moneda, saldos, operaciones ni resultados historicos.
- **RN-CTA-02 - Eliminacion y archivo**: Un instrumento solo PUEDE eliminarse fisicamente si nunca tuvo movimientos, no tiene deuda y no posee vinculos dependientes. En cualquier otro caso DEBE archivarse; el archivo impide nuevas operaciones y preserva relaciones y efectos previos.
- **RN-CTA-03 - Cupo Free**: Un usuario Free PUEDE mantener hasta cuatro instrumentos activos que consumen cupo. Cada cuenta computable y cada tarjeta activa consumen un cupo independiente; por ello, una tarjeta de debito y su cuenta vinculada consumen dos cupos aunque compartan saldo. Solo el efectivo fisico y la Cuenta Metas virtual no consumen cupo. Los excedentes historicos se preservan y nunca se eliminan ni excluyen de calculos por una restriccion comercial.
- **RN-CTA-04 - Propiedad exclusiva**: Toda cuenta, tarjeta, movimiento, alerta, tasa personal, pago, compra y simulacion DEBE pertenecer a un solo usuario y solo ser consultable o modificable bajo su sesion autorizada.

### Functional Requirements

- **RF-C01 - Alta de cuentas liquidas**: El sistema DEBE permitir crear cuentas de efectivo, ahorro o bancarias y billeteras digitales con alias, tipo, moneda PEN o USD, icono, color, saldo inicial y fecha y hora de apertura. PEN DEBE aparecer seleccionada inicialmente y el saldo inicial DEBE quedar representado por el movimiento exigido en RN-10. Tras el alta, importe, moneda y momento de apertura NO DEBEN editarse; toda correccion debe seguir el tratamiento auditable de RN-10.
- **RF-C02 - Registro tipado de tarjetas**: El sistema DEBE permitir registrar cada tarjeta como debito o credito, con alias visual opcional, entidad emisora, red Visa, Mastercard, Amex u Otra generica y ultimos cuatro digitos; el tipo confirmado determina si representa acceso a un activo existente o un pasivo potencial. Emisor, red y ultimos cuatro digitos NO DEBEN tratarse como clave unica: una coincidencia intencional se permite tras advertirla y exige un alias distinto para diferenciar las tarjetas. Otra no crea una marca nueva y usa identidad visual generica.
- **RF-C03 - Vinculo de debito sin duplicacion**: Toda tarjeta de debito DEBE vincularse a una cuenta activa de ahorro o bancaria del mismo usuario y moneda, compartir su saldo disponible y NO crear un saldo, activo o movimiento adicional por el mero registro del plastico.
- **RF-C04 - Linea y saldos de credito**: Para cada tarjeta de credito, el sistema DEBE mostrar linea total, deuda actual como credito utilizado, credito disponible igual al mayor valor entre linea menos deuda y cero, y porcentaje de utilizacion. Si la linea es cero, el porcentaje DEBE ser "No disponible". Los dias preferidos de corte y vencimiento entre 1 y 31 DEBEN conservarse, y su fecha efectiva DEBE ajustarse al ultimo dia valido de los meses cortos.
- **RF-C05 - Identificacion segura de tarjetas**: El sistema DEBE solicitar exclusivamente alias visual opcional, emisor, red Visa, Mastercard, Amex u Otra generica, ultimos cuatro digitos y, para credito, linea autorizada y dias de corte y vencimiento. DEBE rechazar PAN completo y CVV en cualquier entrada, incluidos alias y emisor, y mostrar la tarjeta solo mediante referencia enmascarada.
- **RF-C06 - Presets y ciclo de vida de cuentas**: El sistema DEBE ofrecer presets visuales de BCP, BBVA, Interbank, Scotiabank, Banco de la Nacion y generico personalizable; permitir editar alias, icono y color sin efectos financieros; permitir eliminacion fisica solo si la cuenta nunca tuvo movimientos ni vinculos dependientes; y archivar en cualquier otro caso. Una cuenta archivada NO DEBE aceptar operaciones nuevas.
- **RF-C07 - Umbrales de utilizacion**: El sistema DEBE crear avisos al cruzar en ascenso 50%, 80% y 100% de utilizacion; registrar por tarjeta que umbrales permanecen cruzados; evitar repeticiones mientras no se baje de cada umbral; y rearmar individualmente el aviso al bajar y volver a cruzar. El aviso interno DEBE existir aunque las notificaciones del dispositivo esten denegadas.
- **RF-C08 - Catalogo referencial**: El sistema DEBE mostrar el snapshot del catalogo oficial `KIPU_CATALOGO_TARJETAS_CREDITO_PERU_2026.md`, con fecha de corte `2026-09-24`, limitado a productos confirmados de BCP, BBVA e Interbank: 18, 10 y 16 productos respectivamente (44 en total). Cada producto DEBE conservar nombre, TEA de compras por moneda cuando la fuente la publique, rangos y condiciones por perfil, condicion de membresia/costo, fuente y estado editorial de la fuente. Cuando la fuente no publique o no desglose un dato, el sistema DEBE indicarlo expresamente y NO DEBE inferirlo. Los conflictos y estados pendientes DEBEN conservarse como advertencias y NO habilitan una tasa ambigua para simulaciones. La interfaz DEBE mostrar `Tasa referencial al 24/09/2026` y la advertencia de que no es una condicion contractual. El software NO DEBE caducar automaticamente el snapshot por el paso del tiempo; solo una actualizacion editorial explicita puede sustituirlo.
- **RF-C09 - TEA personal**: El usuario DEBE poder registrar y modificar una TEA personal valida para cada tarjeta de credito. El nuevo valor DEBE aplicarse solo a simulaciones posteriores y NO DEBE modificar transacciones, deudas ni simulaciones confirmadas con anterioridad.
- **RF-C09a - Contexto de referencia por tarjeta**: Al abrir tasas desde una tarjeta, el sistema DEBE resolver la tarjeta consultada y su producto persistido. Una referencia solo es aplicable cuando producto, emisor y red coinciden exactamente y la tasa corresponde a la moneda de la tarjeta. Emisor o red por si solos, coincidencias parciales, ausencia de producto y duplicidad de coincidencias NO DEBEN seleccionar una tasa. La TEA personal previamente guardada DEBE recuperarse para esa misma tarjeta.
- **RF-C10 - Pago amortizador**: El sistema DEBE registrar el pago confirmado desde una cuenta bancaria o de ahorros activa y de la misma moneda como una unica transferencia interna que debita dinero real y amortiza el pasivo por igual. NO DEBE aumentar ingreso, gasto operativo ni consumo mensual. El importe DEBE asignarse a cuotas pendientes en orden ascendente estricto por `due_date`; un pago parcial reduce el capital de la cuota mas antigua y no salta a cuotas posteriores mientras aquella tenga saldo. La suma de asignaciones DEBE coincidir con el importe del pago. Un pago superior a la deuda, con fondos insuficientes o moneda incompatible DEBE rechazarse sin efectos.
- **RF-C11 - Compra de credito confirmada**: Toda compra detectada con tarjeta de credito DEBE permanecer como candidata sin efecto hasta confirmacion explicita. Al confirmarla, el sistema DEBE reconocer exactamente una vez el principal completo como gasto y deuda en la fecha de compra, con independencia del numero de cuotas.
- **RF-C12 - Simulacion y cuotas exactas**: El sistema DEBE permitir simular de 1 a 36 cuotas usando primero la TEA personal valida y, si el usuario lo acepta, una tasa referencial identificada. La tasa en bps se convierte a decimal dividiendo entre 10,000; la TEM DEBE calcularse como `(1 + TEA)^(1/12) - 1` y la cuota teorica fija como `principal * TEM / (1 - (1 + TEM)^(-n))`, con `principal / n` si TEM es cero. DEBE mostrar metodo, tasa y procedencia, principal, interes estimado, total financiado, fechas e importes y advertencia visible de estimacion. Los importes DEBEN redondearse y distribuirse deterministamente en unidades menores, asignando el residuo a la cuota 1 y preservando la suma exacta. La primera fecha de vencimiento DEBE seguir el ciclo de cierre y vencimiento indicado en HU-13.8; las cuotas siguientes DEBEN ser mensuales y ajustar el dia efectivo al ultimo dia valido sin cambiar el preferido. Los intereses estimados NO DEBEN crear asientos ni modificar gasto o pasivo real. Sin tasa aplicable, DEBE mostrar solo la distribucion del principal y NO inventar intereses.

### Requisitos Transversales de Aceptacion

- **RT-01 - Persistencia local**: Una operacion confirmada DEBE quedar guardada de forma completa antes de informar exito y DEBE poder consultarse tras reiniciar sin conectividad.
- **RT-02 - Reintentos seguros**: Cada alta, pago, compra, reverso o ajuste DEBE conservar una identidad estable; reintentos, sincronizaciones duplicadas o entregas fuera de orden NO DEBEN repetir efectos financieros ni restaurar estados obsoletos.
- **RT-03 - Conflictos**: Un conflicto que pueda cambiar saldo, deuda, moneda, propiedad o historia NO DEBE resolverse silenciosamente; se debe conservar la ultima verdad confirmada y solicitar una resolucion explicita cuando no pueda reconciliarse sin riesgo.
- **RT-04 - Aislamiento**: Toda lectura y escritura DEBE restringirse al propietario derivado de la sesion activa. Conocer un identificador ajeno NO DEBE revelar existencia, atributos ni historia del instrumento.
- **RT-05 - Precision monetaria**: Todo importe autoritativo DEBE mantener precision de centimo junto con su moneda; los totales deben reproducirse sin diferencias por redondeo.
- **RT-06 - Accesibilidad**: Los estados, importes, moneda, deuda, tasas, vencimientos, advertencias y acciones DEBEN tener semantica accesible y ser perceptibles y operables con TalkBack. La validacion DEBE incluir la tarjeta fisica simulada/Card Preview, la pantalla de compra en cuotas y el flujo de amortizacion de deuda. Confirmar, cancelar y corregir errores debe ser posible sin depender solo del color o de informacion visual.

### Matriz de Trazabilidad

| Requisito | Historia | Evidencia principal |
|-----------|----------|--------------------|
| RF-C01 | HU-07 | Escenarios HU-07.1, HU-07.2 y HU-07.6 |
| RF-C02 | HU-08 | Escenarios HU-08.1 y HU-08.3 |
| RF-C03 | HU-08 | Escenarios HU-08.1, HU-08.2 y HU-08.6 |
| RF-C04 | HU-09 | Escenarios HU-09.1 a HU-09.5 |
| RF-C05 | HU-08 | Escenarios HU-08.3 a HU-08.5 |
| RF-C06 | HU-07 | Escenarios HU-07.3 a HU-07.5 |
| RF-C07 | HU-10 | Escenarios HU-10.1 a HU-10.5 |
| RF-C08 | HU-11 | Escenarios HU-11.1 y HU-11.2 |
| RF-C09 | HU-11 | Escenarios HU-11.3 y HU-11.4 |
| RF-C10 | HU-12 | Escenarios HU-12.1 a HU-12.6 |
| RF-C11 | HU-13 | Escenarios HU-13.1 a HU-13.3 y HU-13.7 |
| RF-C12 | HU-13 | Escenarios HU-13.4 a HU-13.6 y HU-13.8 |
| RT-01 a RT-05 | Todas | Escenarios offline, reinicio, reintento, aislamiento y casos limite |
| RT-06 | Todas | Escenarios de accesibilidad, SC-014 y evidencia de Pantallas 4/5 |

### Key Entities *(include if feature involves data)*

- **Cuenta liquida**: Activo monetario del usuario. Contiene identidad estable, propietario, alias, tipo, moneda, apariencia, preset opcional, momento de apertura y estado activo o archivado. Su saldo se deriva de movimientos.
- **Movimiento financiero**: Hecho auditable que cambia o explica un saldo. Incluye propietario, identidad estable, tipo, importe, moneda, fecha efectiva, estado y relaciones con las operaciones que origina, revierte o ajusta.
- **Movimiento de apertura**: Primer movimiento inseparable de una cuenta; representa exactamente su saldo inicial y momento de apertura.
- **Tarjeta**: Instrumento con identidad estable propia, propietario, tipo debito o credito, emisor, red, ultimos cuatro digitos, alias, apariencia y estado. Emisor, red y ultimos cuatro digitos no garantizan unicidad. Nunca contiene PAN completo ni CVV.
- **Vinculo de debito**: Relacion entre una tarjeta de debito y una cuenta de ahorro o bancaria activa del mismo propietario y moneda; no posee saldo independiente.
- **Cuenta de credito**: Condiciones y estado financiero asociados a una tarjeta de credito: linea autorizada, dias preferidos de corte y vencimiento, deuda derivada de movimientos y TEA personal opcional.
- **Estado de umbral**: Memoria por tarjeta y umbral de si 50%, 80% o 100% permanece cruzado, con los momentos de cruce y rearme necesarios para deduplicar avisos.
- **Referencia de tasa**: Informacion editorial no contractual de un emisor o producto, con tasa, alcance, moneda cuando aplique, fecha de verificacion, vigencia y advertencia.
- **TEA personal**: Tasa contractual declarada por el usuario para una tarjeta y vigente desde un momento; no reescribe hechos anteriores.
- **Pago amortizador**: Operacion compuesta y auditable que relaciona una salida de una cuenta propia con una reduccion equivalente del pasivo, sin efecto de gasto.
- **Candidato de compra**: Deteccion revisable sin efecto financiero hasta su confirmacion o rechazo explicito. Conserva identidad/propietario, tipo y referencia trazable de fuente, momento de captura, hechos extraidos, evaluacion de confianza, clave de deduplicacion entre fuentes y estado de revision; nunca adivina un instrumento sin evidencia suficiente.
- **Compra con credito**: Operacion confirmada que reconoce principal completo como gasto y deuda una sola vez en la fecha de compra.
- **Simulacion de cuotas**: Proyeccion informativa asociada a una compra o importe, con tasa y procedencia, numero de cuotas, fechas, principal, interes estimado, total y distribucion exacta de centimos; no constituye por si sola un cargo real.
- **Instrumento activo computable**: Cuenta o tarjeta activa que consume cupo del plan Free, salvo las exclusiones expresas de efectivo fisico y Cuenta Metas virtual.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Al menos 95% de usuarios de prueba completa el alta de una cuenta con movimiento de apertura en menos de 2 minutos y sin asistencia.
- **SC-002**: En el 100% de 100 escenarios representativos de altas, ediciones, archivos, pagos, compras, reversos y reintentos, los saldos reproducidos coinciden al centimo con los movimientos auditables.
- **SC-003**: En el 100% de los escenarios de debito, registrar, editar o archivar una tarjeta no duplica el activo ni modifica el saldo de su cuenta vinculada.
- **SC-004**: En el 100% de los escenarios de credito, linea total y credito disponible quedan excluidos del dinero real disponible y del patrimonio; solo la deuda confirmada participa como pasivo.
- **SC-005**: Cero entradas de PAN completo o CVV son aceptadas, persistidas, sincronizadas o visibles en una muestra de 100 intentos por formularios y flujos admitidos.
- **SC-006**: El 100% de los cierres configurados en dias 29, 30 y 31 produce una fecha valida en los doce meses de anos comunes y bisiestos, conservando el dia preferido original.
- **SC-007**: En el 100% de las secuencias que cruzan, mantienen, bajan y vuelven a cruzar 50%, 80% y 100%, se genera exactamente un aviso por cruce valido y el aviso interno permanece accesible sin permiso del sistema.
- **SC-008**: El 100% de los 44 productos del snapshot muestran `Tasa referencial al 24/09/2026`, fuente, datos disponibles por moneda y advertencia de estimacion; ningun dato pendiente, conflictivo o no publicado se presenta como confirmado ni se inventa, y el snapshot no caduca automaticamente por edad.
- **SC-009**: En el 100% de los pagos aceptados, la disminucion de efectivo coincide al centimo con la disminucion del principal y el gasto o consumo mensual no cambia.
- **SC-010**: Para importes desde 0.01 hasta 100,000.00 y planes de 1 a 36 cuotas, el 100% de los cronogramas suma exactamente el total financiado mostrado, sin centimos perdidos o creados.
- **SC-011**: Tras 100 secuencias combinadas de operacion offline, cierre, reinicio, reintento y sincronizacion, se conserva el 100% de operaciones confirmadas y se observan cero efectos financieros duplicados.
- **SC-012**: En pruebas cruzadas con dos usuarios, el 100% de intentos de lectura o modificacion de instrumentos ajenos es rechazado sin revelar atributos financieros del propietario.
- **SC-013**: Al menos 90% de participantes identifica correctamente, al primer intento, que el credito disponible no es dinero propio, que una compra genera el gasto y que pagar la tarjeta no genera otro gasto.
- **SC-014**: Al menos 90% de participantes completa los recorridos principales con las ayudas de accesibilidad admitidas. En la tarjeta fisica simulada, la compra en cuotas y el flujo de amortizacion, TalkBack anuncia correctamente etiquetas, roles, importes, moneda, estados, errores y acciones; los recorridos de revision, confirmacion y cancelacion se completan sin perdida de informacion esencial.
- **SC-015**: En el plan Free, el 100% de intentos de crear o reactivar un quinto instrumento computable se bloquea sin perdida de historia; cada cuenta y tarjeta activa ocupa un cupo separado, mientras efectivo fisico y Cuenta Metas permanecen exentos.

## Assumptions

- PEN y USD se administran por separado. Esta epica no define tipos de cambio ni una suma patrimonial comun entre monedas.
- Los importes tienen precision de centimo; el rango monetario maximo permitido se definira en planificacion sin debilitar las pruebas de exactitud.
- El saldo inicial de una cuenta de activo puede ser cero, pero no negativo.
- Una tarjeta de debito y su cuenta vinculada cuentan como dos instrumentos activos para el cupo Free, aunque solo la cuenta represente dinero. Efectivo fisico y Cuenta Metas virtual son las unicas exclusiones de esta epica.
- Archivar libera cupo para nuevas altas; reactivar vuelve a consumirlo y requiere disponibilidad.
- Solo cuentas de ahorro o bancarias activas pueden financiar pagos de tarjeta. Efectivo y billeteras digitales no son origen de RF-C10.
- Un pago mayor a la deuda debe corregirse hasta la deuda pendiente. El tratamiento de saldos acreedores o pagos adelantados excedentes queda fuera de alcance hasta contar con una regla aprobada.
- La deuda puede superar la linea autorizada; esto no borra principal ni crea credito disponible negativo.
- La misma regla de ultimo dia valido se aplica a las fechas efectivas de corte y vencimiento, conservando las preferencias originales.
- La utilizacion se evalua contra deuda confirmada y linea vigente. Con linea cero se informa "No disponible" y no se disparan umbrales porcentuales.
- El catalogo Sprint 3 es un snapshot editorial con fecha de corte 2026-09-24. El paso del tiempo no lo caduca automaticamente; discrepancias y estados publicados por la fuente se conservan y se muestran.
- La TEA personal es la fuente preferida para nuevas simulaciones. Usar una tasa referencial requiere que el usuario acepte expresamente su caracter estimado.
- Los intereses de una simulacion no afectan deuda ni gasto hasta que un cargo real sea registrado y confirmado por un flujo aprobado.
- Una compra detectada sigue el modelo de candidato exigido por la constitucion y requiere confirmacion expresa con independencia de su fuente o confianza.
- La aplicacion administra registros financieros; no ejecuta pagos reales ni consulta saldos directamente en entidades financieras.

## Dependencies

- EP-APS debe proporcionar una sesion autenticada, propiedad estable y aislamiento de datos por usuario.
- EP-PLA debe proporcionar la decision efectiva de plan y la politica no destructiva del cupo Free.
- El dominio comun de movimientos debe admitir aperturas, transferencias, gastos, pasivos, reversos y ajustes auditables sin duplicar efectos.
- RF-C08 usa el snapshot oficial `KIPU_CATALOGO_TARJETAS_CREDITO_PERU_2026.md` al corte 2026-09-24 para 44 productos confirmados de BCP, BBVA e Interbank. Su indisponibilidad no bloquea cuentas, tarjetas ni TEA personal.
- Las fuentes automaticas futuras solo pueden entregar candidatos de compra y no pueden confirmar efectos financieros en nombre del usuario.
