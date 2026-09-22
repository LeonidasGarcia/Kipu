# Research: EP-CTA - Cuentas y Tarjetas

**Date**: 2026-09-21  
**Spec**: [spec.md](spec.md)  
**Plan**: [plan.md](plan.md)

## R-001 - Project Structure

**Decision**: Mantener `:app` como unico modulo y crear `core/finance/domain` mas `feature/accounts/{domain,data,presentation,di}`.

**Rationale**: El repositorio es de modulo unico y `feature/plans` ya demuestra Repository, dominio puro, adaptadores y pruebas de frontera. Esta opcion mejora la separacion sin una migracion modular ajena al alcance.

**Alternatives considered**:

- Modulos Gradle `:domain`, `:data`, `:feature:accounts`: mejor aislamiento de compilacion, pero costo y riesgo desproporcionados ahora.
- Repetir el patron settings con DAOs en ViewModels: rechazado porque viola la Constitucion VII.

## R-002 - Authoritative Financial Truth

**Decision**: Los movimientos/ledger son la unica fuente autoritativa de saldos. `initialBalanceMinorUnits` es un snapshot inmutable de auditoria que debe coincidir con `OPENING`; no existe `currentBalance` persistido.

**Rationale**: RN-10 y la Constitucion I exigen que todo cambio tenga una operacion auditable. Un balance mutable crearia una segunda verdad.

**Alternatives considered**:

- Guardar y editar saldo actual en `AccountEntity`: rechazado por replay, conflictos y correcciones inseguras.
- Omitir `initialBalanceMinorUnits`: valido contablemente, pero se mantiene para satisfacer el contrato de entidad solicitado y facilitar auditoria, siempre subordinado al movimiento.

## R-003 - Money and Rates

**Decision**: `Money` usa `Long` minor units + `Currency`; rango por importe `-99_999_999_999_999..99_999_999_999_999`, con iniciales de activo `>= 0`. Sumas/restas usan operaciones exactas. TEA Sprint 3 usa basis points enteros `0..100_000` (0%..1000%).

**Rationale**: Evita floating point, deja margen amplio frente a SC-010 y permite checks iguales en Kotlin/PostgreSQL.

**Alternatives considered**:

- `Double`/`Float`: prohibido.
- Decimal arbitrario en dominio: innecesario para centimos y mas dificil de alinear con Room `INTEGER`.
- Sin maximo: rechazado porque impediria validar overflow de forma determinista.

## R-003A - Offline Premium Authorization

**Decision**: EP-CTA aplica el cupo Free si no recibe de EP-PLA un lease Premium firmado/verificado, acotado y vigente. EP-CTA no define ni renueva ese lease; EP-PLA debe especificar su duracion maxima, vencimiento no posterior al entitlement, almacenamiento resistente a reinstalacion y reglas ante manipulacion de reloj/dispositivo antes de habilitar bypass offline.

**Rationale**: Mantiene disponible el nucleo manual local sin convertir una preferencia o cache indefinida en autorizacion Premium.

**Alternatives considered**:

- Cache booleana Premium sin vencimiento: rechazada por Constitucion II.
- Elegir una duracion arbitraria en EP-CTA: rechazado; la politica pertenece a EP-PLA.

## R-004 - Card Modeling

**Decision**: Dominio sellado `DebitCard`/`CreditCard`; Room plana con constraints y mapper estricto. Toda tarjeta tiene moneda: debito la copia de su cuenta vinculada y credito la solicita explicitamente. La red admite `VISA`, `MASTERCARD`, `AMEX` y `OTHER`; esta ultima siempre usa identidad visual generica.

**Rationale**: Evita combinaciones nulas invalidas y hace imposible tratar una tarjeta de debito como saldo independiente.

**Alternatives considered**:

- Boolean `isCredit`: rechazado por estados imposibles y baja expresividad.
- Tablas Room separadas: mas puras, pero agregan complejidad sin necesidad; el dominio sellado conserva seguridad.

## R-005 - Atomic Local Commands

**Decision**: Metodos DAO `@Transaction` escriben proyeccion, movimiento y outbox juntos; WorkManager se solicita despues del commit y el startup recupera pendientes.

**Rationale**: Coincide con `PlanPreferencesDao`, garantiza exito local-first y cierra la ventana commit/enqueue mediante reconciliacion.

**Alternatives considered**:

- Transaccion en repositorio: Room no garantiza atomicidad entre llamadas separadas.
- Esperar Supabase para confirmar: viola operacion offline.

## R-006 - Outbox and Sync Status

**Decision**: Crear `instrument_sync_outbox` especializada. Estados internos ricos se proyectan a `PENDING`, `SYNCED`, `ERROR` para UI.

**Rationale**: La tabla `sync_outbox` existente, pese al nombre, contiene columnas de planes. Los comandos financieros necesitan leasing, hash, aggregate, revision y conflicto.

**Alternatives considered**:

- Generalizar la outbox de planes: migracion amplia y riesgosa sin consumidores suficientes.
- Solo tres estados persistidos: no distingue auth pendiente, lease, conflicto y error terminal.

## R-007 - Conflict Strategy

**Decision**: Comandos inmutables usan `operationId` + SHA-256 canonico; metadata mutable usa `expectedRevision`; cada mutacion local posterior referencia `predecessorOperationId`; cursor de sync usa secuencia de servidor. Nunca se usa reloj para resolver verdad.

**Rationale**: Soporta reintentos ambiguos y multiples dispositivos sin convertir una escritura tardia en verdad automatica. La cadena causal permite crear, editar y archivar el mismo aggregate offline: el worker espera el recibo predecesor y el servidor usa su revision aceptada como base, evitando comparar la sucesora contra una revision remota que aun no existe.

**Alternatives considered**:

- Last-write-wins: viola RT-03.
- Revision global emitida por cada cliente: colisiona entre dispositivos offline.
- Enviar create/edit/archive solo por `createdAt`: rechazado porque orden de transporte no demuestra dependencia ni evita falsos conflictos.

## R-008 - Remote Mutation Boundary

**Decision**: PostgREST para proyecciones SELECT y RPC PostgreSQL estrechas para mutaciones; no Edge Function obligatoria en S2.

**Rationale**: Las operaciones necesitan una transaccion en el mismo limite que locks, cupo, ledger, recibo y sync. Una Edge Function solo agregaria un salto sin mejorar esa atomicidad.

**Alternatives considered**:

- DML directo con RLS: insuficiente para efectos compuestos e idempotencia.
- Dispatcher RPC JSON generico: demasiado privilegiado y dificil de validar.
- Edge Function como autoridad financiera: no puede reemplazar la transaccion PostgreSQL.

## R-009 - Supabase Baseline Recovery

**Decision**: Recuperar las migraciones financieras originales antes de una migracion EP-CTA forward-only; comparar reconstruccion limpia contra el esquema vinculado.

**Rationale**: `20260920162500_align_domain_schema_names.sql` altera tablas que ninguna migracion versionada crea. Agregar otra migracion no arregla clones limpios.

**Alternatives considered**:

- Crear todo con `IF NOT EXISTS`: rechazado porque oculta drift y constraints desconocidos.
- Editar migraciones aplicadas: rechazado porque rompe historia.
- Baseline consolidado nuevo: ultimo recurso que exige aprobacion y reconciliacion explicita de historial.

**Linked-schema evidence**: La inspeccion del proyecto vinculado confirma que existe un modelo financiero remoto no reproducible desde las migraciones locales, pero no equivale al modelo objetivo: `accounts` no conserva apertura/snapshot, `cards` usa `is_credit` y `account_id NOT NULL`, `transactions` no tiene `OPENING` ni identidad de operacion, `ledger_entries` exige importe distinto de cero, y las referencias financieras observadas no son owner-composite. La migracion EP-CTA debe reconciliar estas diferencias forward-only despues de recuperar el baseline; no debe asumir que los nombres actuales ya cumplen el contrato.

## R-010 - RLS and Grants

**Decision**: RLS habilitada/forzada, ownership estructural, `PUBLIC`/`anon` revocados, DML financiero directo revocado y grants exactos. Vistas `security_invoker`; funciones privilegiadas con owner dedicado, `search_path=''` y checks explicitos.

**Rationale**: `TO authenticated` solo autentica; no autoriza filas. La Constitucion exige objeto correcto, y Supabase ya no expone automaticamente tablas nuevas.

**Alternatives considered**:

- Confiar en filtros del cliente: IDOR/BOLA.
- `SECURITY DEFINER` publico para resolver permisos: rechazado por bypass y superficie de ataque.

**Sources**:

- https://supabase.com/docs/guides/api/securing-your-api
- https://supabase.com/docs/guides/database/functions
- https://supabase.com/docs/reference/kotlin/rpc
- Supabase changelog 2026-04-28: nuevas tablas dejan de exponerse automaticamente al Data API.

## R-011 - Lifecycle

**Decision**: Archivo es el lifecycle normal tras cualquier historia. Una cuenta confirmada siempre tiene `OPENING`, por lo que no es fisicamente eliminable. Hard delete queda limitado a tarjeta sin historia/deuda/dependencias y debe emitir tombstone.

**Rationale**: Reconcilia RN-10 con la aclaracion de delete solo sin historia y evita resurreccion desde dispositivos desactualizados.

**Alternatives considered**:

- Borrar apertura cero: rechazado porque sigue siendo historia auditable.
- Soft delete unico: ambiguo frente a archivo, cupo y sincronizacion.

## R-012 - UI Architecture

**Decision**: Pantalla 4 separa dinero real y credito; Pantalla 5 usa estado sellado por tipo. Route/Screen/ViewModel, layouts compact/medium/expanded y navegacion autenticada nueva.

**Rationale**: Previene doble conteo visual, stale fields y rutas con datos sensibles. Sigue Navigation Compose y el design system existente.

**Alternatives considered**:

- Carrusel unico de cuentas/tarjetas: confunde linea con efectivo.
- Formulario plano con muchos nullables: deja payload incompatible al cambiar tipo.
- Migrar libreria de navegacion: fuera de alcance.

## R-013 - MoneyText and Card Privacy

**Decision**: `MoneyText` centraliza `tnum`, moneda y masking semantico; `MaskedCardReference` se ocupa de `•••• last4`. Entradas mayores a cuatro digitos se rechazan antes de estado.

**Rationale**: El componente existente enmascara visualmente, pero no garantiza tipografia ni semantica privada. Separar identificacion de tarjeta evita confundir masking monetario.

**Alternatives considered**:

- Enmascarar strings en ViewModel: duplica logica y facilita leaks.
- Truncar PAN pegado a cuatro digitos: conserva parcialmente un dato que nunca debio aceptarse.

## R-014 - Presets

**Decision**: Persistir IDs `BCP`, `BBVA`, `INTERBANK`, `SCOTIABANK`, `BANCO_NACION`, `GENERIC`. Los presets siembran apariencia; overrides no cambian identidad ni finanzas. Banco de la Nacion usa fallback Kipu hasta que el design system apruebe tokens.

**Rationale**: El documento Stitch no contiene tokens de Nacion y tiene un typo BCP `#002A8G`; no se inventan colores normativos.

**Alternatives considered**:

- Inferir preset desde alias: inestable.
- Copiar colores de fuentes externas sin aprobar: contradice la fuente de verdad visual.

## R-015 - Test and Release Gates

**Decision**: Exigir tests de dominio, Room/migracion, worker, Compose, navegacion y pgTAP; CI debe agregar lint y Supabase reset/test/advisors. Android real valida plataforma/accesibilidad.

**Rationale**: CI actual solo ejecuta unit tests y assemble; no prueba migraciones, RLS, WorkManager real ni accesibilidad.

**Alternatives considered**:

- Confiar en mocks/documentos: prohibido por Constitucion IX.

## Resolved Unknowns

- Maximo monetario: `99_999_999_999_999` minor units.
- Moneda de tarjeta: obligatoria; debito derivada, credito explicita.
- Cuenta Metas: no se modela como `AccountType` EP-CTA en S2; EP-MET mantiene su objeto virtual exento.
- Status solicitado: tres estados de UI derivados de una maquina interna exhaustiva.
- API remota: SELECT owner-scoped + RPC tipadas; no Edge Function obligatoria.
- Deletion: cuenta confirmada archive-only; tarjeta vacia puede hard-delete con tombstone.
- Freshness de tasas: Sprint 3 adopta 90 dias por defecto editorial; siempre muestra `verifiedAt` y puede marcar stale antes si la fuente lo exige.
- Pantallas 4/5 sin asset Stitch: tokens/documento local son autoridad; no se afirma pixel fidelity sin IDs aprobados.
