# Research: EP-CTA - Cuentas y Tarjetas

**Date**: 2026-09-24
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

## R-009 - Supabase Baseline Reproducibility and Drift

**Decision**: Keep the checked-in PostgreSQL 17 baseline as the reproducible local source of truth and reconcile the linked Kipu project only with new forward-only migrations. T075 was approved on 2026-09-24 after a clean local reset, a zero-diff comparison of `public,internal` against the checked-in migrations, and a read-only linked-project comparison. Never reset or rewrite the linked project's migration history.

**Rationale**: The local chain is reproducible, while the linked project has a distinct, longer migration history and additive schema/policy drift. Production changes therefore must preserve remote data and legacy clients while converging through additive, compatibility-preserving migrations.

**Alternatives considered**:

- Crear todo con `IF NOT EXISTS`: rechazado porque oculta drift y constraints desconocidos.
- Editar migraciones aplicadas: rechazado porque rompe historia.
- Baseline consolidado nuevo: ultimo recurso que exige aprobacion y reconciliacion explicita de historial.

**T075 evidence (2026-09-24)**: `supabase db reset --local` applied all 23 checked-in migrations successfully. `supabase db diff --local --schema public,internal` returned `No schema changes found`. The linked Kipu project reports 55 applied migration records; its migration history is not a one-to-one match for the 23 checked-in files. The linked schema retains nine legacy alias columns on `public.cards` (`issuer`, `currency`, `currency_code`, `name`, `card_type`, `last_four`, `credit_limit_minor_units`, `billing_cycle_day`, `payment_due_day`), has `public.app_notifications.deleted_at`, and uses `internal.sync_changes.occurred_at` while the local baseline uses `created_at`. The canonical `credit_products`, `credit_installments` and `credit_payment_allocations` columns are present in both. Relevant RLS policies also differ; see R-010. The product owner approved preserving the remote history and using forward-only migration reconciliation.

**Existing RPC compatibility**: Both inspected environments contain `confirm_credit_purchase_v1`, `pay_credit_card_v1` and `register_transaction_v1`, but their function definitions are not byte-identical. Purchase confirmation writes a `CREDIT_PURCHASE` row to `financial_movements` and does not write the canonical ledger or installment schedule. Card payment writes paired cash/liability movement rows and does not allocate installments. `register_transaction_v1` writes transactions and ledger entries for its existing types but does not recognize `CARD_PURCHASE`. Sprint 3 commands must establish one canonical accounting path and keep legacy entry points as delegating wrappers or explicitly retired compatibility endpoints.

## R-010 - RLS and Grants

**Decision**: RLS habilitada con ownership estructural y grants exactos; se revoca DML directo de `PUBLIC`/`anon`. Vistas `security_invoker`; RPC privilegiadas con `search_path=''`, validaciones explicitas y `EXECUTE` minimo.

**T075 evidence**: RLS is enabled on the inspected financial tables in both environments. The remote project grants `anon` table-level SELECT/INSERT on the inspected public tables, while owner policies target `authenticated`; no anonymous row policy was found. Keep RLS as the row boundary and use forward-only revokes/minimum grants to remove unnecessary anonymous DML before relying on those tables through the Data API. The inspected RPCs are `SECURITY DEFINER`, set an empty `search_path`, deny `anon` EXECUTE and grant `authenticated` EXECUTE. Local and remote policies differ for `internal.command_receipts`, `internal.ledger_entries`, `internal.sync_changes` and `public.credit_payment_allocations`; reconcile deliberately without changing historical migrations.

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

## Decisions Added During Sprint 3 Refinement

## R-016 - Credit Cycle Dates

**Decision**: Keep preferred closing_day and due_day values in the inclusive range 1..31. For each month, compute the effective day as the smaller of the preference and the month's last calendar day. A purchase on the effective closing date belongs to that cycle; a purchase after it belongs to the next cycle. The first installment is due on the earliest effective due_day strictly after the selected cycle's effective closing date; later installments are monthly and independently clamp in short months.

**Rationale**: This records the user's clarification for HU-13.8 and preserves the user's preferred day when February or another short month requires a temporary adjustment. It matches RF-C04/RF-C12 and the official backlog's 1..31 preference.

**Alternatives considered**: Mutating the stored preference to 28; assigning purchases on the closing date to the following cycle; choosing a fixed offset from purchase date. These do not match the accepted cycle rule.

## R-017 - Fixed-Installment Interest Estimate and Minor-Unit Rounding

**Decision**: Use the French fixed-payment estimate. Convert a rate in basis points to decimal TEA by dividing by 10,000, calculate TEM = (1 + TEA)^(1/12) - 1, then calculate fixed payment A = P * TEM / (1 - (1 + TEM)^(-n)). When TEM is zero, use P/n. Round displayed monetary values to integer minor units deterministically and assign the principal-split residual to installment 1. A simulation is an estimate only and creates no ledger entry.

**Rationale**: This records the user's clarification and the exact formula now required by RF-C12. It keeps financial authority in integer minor units while permitting a display-only rate estimate.

**Alternatives considered**: Equal principal plus simple interest, annuity due, or treating each simulation as a posted obligation. The French fixed-payment method is the accepted answer; simulations remain non-posting.

**Acceptance fixture**: PEN 100.00 with three zero-interest installments is PEN 33.34, PEN 33.33 and PEN 33.33. For nonzero TEA, the displayed fixed payment is calculated with the formula above and the final displayed installment rows sum exactly to the rounded displayed total.

## R-018 - Canonical Credit Catalog and Installment Data

**Decision**: Reuse public.credit_products, public.cards.personal_tea_bps, public.credit_installments and public.credit_payment_allocations. Reconcile referential_rate_catalog and the movement-only payment/purchase RPCs through forward-only migrations and compatibility adapters/delegation to a single ledger command path. Add Room projections for the two existing installment relations and persist personal_tea_bps in CardEntity. Do not create rate_catalog_products, personal_rate_history, installment_simulations, credit_alerts or another notification table for this sprint.

**Rationale**: The canonical entities are already present in the versioned baseline. Creating parallel tables or independent movement-only commands would split catalog or accounting truth. The checked-in app is at Room version 10, so plan the next migration from that version, not the old v3 design.

**Evidence**: Kipu's financial baseline defines credit_products, credit_installments, credit_payment_allocations and app_notifications. The current repo also contains referential_rate_catalog, pay_credit_card_v1 and confirm_credit_purchase_v1, while the existing register_transaction_v1 and worker path do not yet provide the complete CTA canonical command contract. Treat this as a reconciliation gate before implementation.

## R-019 - Utilization Threshold Event Contract

**Decision**: Evaluate committed utilization transitions at 50%, 80% and 100%. Emit one event for each newly crossed threshold, deduplicate retries using the financial operation identity, and permit another crossing only after utilization falls below that threshold. Send an internal event to EP-NOT/HU-42 and reuse its app_notifications store and center.

**Rationale**: HU-10 requires in-app visibility even when OS notification permission is denied. HU-42 is a partial dependency/integration, not permission to add a second notification model within EP-CTA.

**Open integration contract**: Agree the event's stable event ID and fields with EP-NOT before implementation. EP-CTA owns the threshold crossing facts; EP-NOT owns notification persistence and presentation.

## R-020 - Card Preview and Credit UI

**Decision**: Preserve the existing interactive Card Preview in the upper third of the create/edit form. It reflects selected issuer preset, palette, alias and only the last four digits. Show available credit and used credit in the credit-card section, separate from liquid balances. Format all values in PEN with S/ and tabular numerals; keep masking semantics. Never add PAN, expiry or CVV fields.

**Rationale**: The product screen references specify the interactive preview, progressive credit-only fields, real-money/credit separation and Peruvian currency formatting.

**Sources reviewed**: KipuApp/Prototipo/Stich Prompts.md (Prompt 3, Screens 4-6; Prompt 5, Screen 11) and KipuApp/Prototipo/Esquema de pantallas relacionadas con las HU.md (Module 2, HU-09..HU-13; Module 4). The prompts identify BCP, BBVA, Interbank and Scotiabank presets; Banco de la Nación must use the approved generic fallback until brand tokens are approved.

## R-021 - HU-12 FIFO Partial-Payment Allocation

**Decision**: Allocate card payments by strict FIFO on `due_date` ascending. A partial payment reduces the outstanding principal of the oldest installment and does not advance to a later installment while the older one has a balance. For equal due dates, use purchase occurrence time, installment number and stable installment ID as deterministic tie-breaks.

**Evidence**: The official Product Backlog and architecture did not choose an installment allocation priority. The FIFO rule is an explicit product decision approved for Sprint 3 on 2026-09-24 and is recorded in `spec.md`, `plan.md`, `data-model.md` and `contracts/credit-commands.md`. It does not change historical purchase principal or create a second expense.

## R-022 - Official Credit-Card Catalog Snapshot

**Decision**: Use `KipuApp/Tasas de Bancos del Peru/KIPU_CATALOGO_TARJETAS_CREDITO_PERU_2026.md` as the official snapshot with cut-off date 2026-09-24. Import exactly 44 confirmed products into canonical `credit_products`: BCP 18, BBVA 10 and Interbank 16. Preserve source-supported purchase TEA by PEN/USD, ranges/profile conditions, membership costs/terms, references and source status. Display `Tasa referencial al 24/09/2026`; elapsed time alone does not expire the snapshot.

**Rationale**: This fixes the release coverage and presentation date while avoiding unsupported current-rate claims. Missing or conflicting source information remains visible as such and cannot be silently normalized into an estimate rate.

**Evidence and caveats**: The catalog's institutional coverage table reports 18 BCP, 10 BBVA and 16 confirmed Interbank products. The Interbank `American Express Benefit / Blue` candidate is marked pending and excluded from the confirmed count. BCP Visa iO has conflicting official rate statements; preserve those statements and do not select an ambiguous TEA for simulation.

## Earlier S2 Decisions Retained

- Maximo monetario: `99_999_999_999_999` minor units.
- Moneda de tarjeta: obligatoria; debito derivada, credito explicita.
- Cuenta Metas: no se modela como `AccountType` EP-CTA en S2; EP-MET mantiene su objeto virtual exento.
- Status solicitado: tres estados de UI derivados de una maquina interna exhaustiva.
- API remota: SELECT owner-scoped + RPC tipadas; no Edge Function obligatoria.
- Deletion: cuenta confirmada archive-only; tarjeta vacia puede hard-delete con tombstone.
- Catalog snapshot: show the source and verification date for the 2026-09-24 snapshot; elapsed time alone does not expire it. Preserve explicit source end dates, conflicts and pending/unpublished statuses as source data (R-022).
- Pantallas 4/5 sin asset Stitch: tokens/documento local son autoridad; no se afirma pixel fidelity sin IDs aprobados.
