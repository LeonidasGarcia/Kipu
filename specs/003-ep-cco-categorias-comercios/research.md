# Research: EP-CCO Categorías, Subcategorías y Comercios

## Canonical movement attachment

**Decision**: Adjuntar categoría, comercio y texto provisional a `financial_movements`, no a la tabla heredada `transactions`.

**Rationale**: `financial_movements` es el ledger activo en Android y ya soporta cuentas, tarjetas, operaciones estables y sincronización. Una clasificación en `transactions` excluiría movimientos actuales y crearía dos verdades financieras.

**Alternatives considered**: Extender solo `transactions` se rechaza por duplicación. Crear una tabla de clasificaciones sin referencia al movimiento se rechaza porque no garantiza una única asociación vigente por movimiento.

## Presentation of system categories

**Decision**: Conservar el catálogo Kipu inmutable en identidad y guardar una `CategoryPresentation` owner-scoped para el nombre, icono y color elegidos por cada usuario.

**Rationale**: Una superposición conserva movimientos históricos y evita que una edición de un usuario cambie la presentación de los demás.

**Alternatives considered**: Mutar la fila de categoría del sistema se rechaza por ser compartida. Duplicar categorías del sistema por usuario se rechaza porque pierde identidad común y complica migración/historial.

## Hierarchy and Free quota

**Decision**: Validar jerarquía, owner, estado y cupo tanto en dominio/Room como en RPC PostgreSQL. El cupo cuenta solamente raíces personalizadas activas.

**Rationale**: La validación local permite operar sin red; la remota impide que dispositivos concurrentes, datos manipulados o reintentos excedan el límite. Las subcategorías heredan elegibilidad de la raíz y no consumen cupo propio.

**Alternatives considered**: Validar solo en UI o Room se rechaza por no proteger la sincronización. Limitar todas las categorías activas se rechaza por contradecir FR-007.

## Conflict handling

**Decision**: Una edición de presentación o un cambio de estado activo/inactivo usa revisión esperada; al detectar versiones incompatibles se guardan ambas en un conflicto tipado y el usuario elige cuál aplicar.

**Rationale**: FR-018 y el principio local-first prohíben la sobrescritura silenciosa. Esta regla afecta la presentación o elegibilidad de categoría, nunca importes ni efectos de ledger.

**Alternatives considered**: Last-write-wins y first-write-wins se rechazan porque eliminan una edición válida sin elección del usuario. Fusionar campos automáticamente se rechaza por producir una presentación o estado no elegido.

## Merchant search and catalog authority

**Decision**: El cliente busca solo entradas Kipu por subcadena de `normalized_name` tras normalizar mayúsculas, espacios y acentos. El catálogo es read-only para usuarios autenticados.

**Rationale**: FR-019 permite coincidencia parcial pero prohíbe alias, equivalencias semánticas e invención de resultados. La autoridad del catálogo queda en Kipu.

**Alternatives considered**: Búsqueda difusa/semántica se rechaza por falta de evidencia. Permitir alta de comercios por usuarios se rechaza porque pertenece al alcance diferido y contradice el catálogo controlado por Kipu.

## Sprint 5 alias matching and entitlement

**Decision**: HU-16 personal aliases use exact equality after trimming outer whitespace, collapsing whitespace sequences, folding case and removing diacritics; punctuation is preserved. The source string is retained unchanged. Catalog search remains normalized substring search and does not consult aliases. No wildcard, prefix, substring or fuzzy rule matching is in S5. If eligible rules with the same normalized input resolve to different canonical merchants, the system requests human review regardless of priority.

**Rationale**: The backlog's `IZIPAY*TAMBO` example requires reuse of a personal association while preserving source evidence. Exact equality limits false merchant assignments; ambiguity still needs review rather than a priority-based guess.

**Decision**: Every new alias rule requires Premium and follows a manual confirmation of the canonical merchant. Processing a captured signal separately requires current entitlement and consent. When either processing gate fails, that signal is not evaluated; manual catalog search and movement entry remain available. HU-17 category preferences do not add a separate Premium gate.

**Alternatives considered**: Broad pattern matching is deferred until a future product decision defines its false-positive and precedence behavior. Treating the existing general `merchant_rules` table as proof that HU-16 is complete is rejected; its fields are only a partial inventory and do not prove the acceptance rules or command contract.

## Sprint 5 personal category preference

**Decision**: Store merchant-to-category preference separately from merchant catalog presentation and from any `category_id` carried by a merchant rule. Resolve it only for a future operation owned by that user, after validating merchant identity, movement type, category/root activity and plan eligibility. A valid user preference takes precedence over a general suggestion, but does not update an already confirmed movement.

**Rationale**: A preference belongs to the user and expresses a future classification choice. Reusing `category_presentations` would confuse display name/icon/color with classification behavior; reusing `merchant_rules.category_id` would conflate two independently scoped concepts.

**Alternatives considered**: Updating old movements when a preference changes is rejected because historical classifications must remain stable. Treating an ineligible preference as a suggestion is rejected because it could assign a blocked or incompatible category.

## Remote authorization and observed readiness

**Decision**: Aplicar RLS forzada y grants mínimos; los comandos usan RPCs con `auth.uid()` y validación de owner. El catálogo solo concede `SELECT` a `authenticated` sobre entradas activas de sistema.

**Rationale**: Las filas privadas deben aislarse por objeto. La actualización directa de `merchant_services` por clientes existentes debe retirarse para cumplir el catálogo general controlado por Kipu.

**Alternatives considered**: Confiar en que el cliente incluya `user_id` se rechaza por BOLA/IDOR. Mantener DML directo de comercio se rechaza por permitir alterar el catálogo.

**Observed live inventory (read-only, 2026-10-08)**: Supabase exposes `merchant_rules`, `merchant_services`, `transactions.merchant_raw_text`, private categories and `category_presentations`. The latter stores presentation fields only. No separate merchant-category preference relation or dedicated alias/preference RPC was found. Observed SELECT policies expose system/own rows for the merchant/category tables; INSERT/UPDATE require the authenticated owner and no DELETE policy was present in the inspected tables. `transactions` has owner-only ALL policy. This is partial readiness evidence, not a complete RLS/security audit and not proof of HU-16/HU-17 implementation.

**Local implementation evidence (2026-10-08)**: Sprint 5 adds dedicated `merchant_alias_rules` and `merchant_category_preferences` relations, owner-scoped read policies, command RPCs, idempotent receipts, and the immutable `financial_movements.merchant_raw_text` field. A clean local Supabase reset applied the migrations and `merchant_rules_s5.test.sql` passed 39 pgTAP checks. The Android unit suite passed 471 tests; targeted Room, worker, end-to-end, and Compose checks passed on the connected Samsung SM-A165M running Android 16. This evidence applies only to the local feature worktree and device; it does not certify the live Supabase project or full release readiness.

**Release gate**: Live Supabase migrations lag local S4/October migrations. Before S5 integration or release, reconcile migration history, inspect effective RLS/grants and apply the approved versioned migration through the normal release process. No remote database change is authorized by this refinement.
