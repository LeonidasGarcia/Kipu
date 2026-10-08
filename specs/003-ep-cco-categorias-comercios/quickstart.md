# Quickstart Validation: EP-CCO

## Prerequisites

- Android SDK and JDK 17 configured for this repository.
- Local Supabase stack with all repository migrations applied and two test users. The currently linked/live migration history is behind the local S4/October head; do not run S5 integration or release acceptance against it until the versioned migration history is reconciled through the normal release process.
- A device/emulator with network control to test offline behavior.
- Sprint 5 Room validation must migrate the current v18 schema to v19 while preserving existing movement values and exact source text.

## Build and automated checks

1. Run `./gradlew testDebugUnitTest`.
2. Run `./gradlew connectedDebugAndroidTest` on an emulator/device.
3. Run the project database test workflow for `supabase/tests/database/category_merchant*.sql` after applying migrations.
4. Run `supabase db reset` in the configured local environment and confirm the EP-CCO migration applies from an empty database.
5. Run Supabase security/performance advisors and resolve any EP-CCO finding before release.

## Acceptance scenarios

1. Confirm the initial active category catalog includes Alimentación, Transporte, and Servicios. Create a custom root and a subcategory; attempt a third level, self-parent and cycle. Each invalid attempt is rejected with no valid hierarchy changed.
2. Create five active custom roots as Free; creation/reactivation of a sixth is rejected. Add subcategories to an eligible root and verify the counter remains five.
3. Inactivate a root; verify its subcategories are unavailable for new movement assignments but existing movement history remains classified. Reactivate it and confirm availability returns.
4. Modify a system category name, icon and color; verify existing movements retain the category identity and show the user presentation.
5. Assign category and catalog merchant to one movement. Change/clear each independently and verify the other remains unchanged.
6. Search an accent/case/space variant and a partial name; verify matching catalog entries are shown but not assigned. Search absent text; verify empty state and save provisional text. Disconnect after a catalog refresh and confirm the stale-catalog state; use a first-time offline session and confirm the unavailable-catalog state.
7. On two offline devices, edit the same category presentation differently, then separately edit active/inactive state differently; sync both cases and verify both versions are retained until the user selects one.
8. With two authenticated users, verify neither can read, mutate or resolve the other's categories, conflicts or movement classifications, while both can only read the active Kipu merchant catalog.
9. As Premium, confirm a catalog merchant manually and save alias `IZIPAY*TAMBO` for Tambo. Evaluate a signal with the same normalized text and confirm Tambo is proposed while the original source string is unchanged. Case/accent folding and trimming/collapsing whitespace preserve exact equality; punctuation is retained. A prefix or embedded substring alone must not match.
10. Evaluate a signal without an exact alias and one whose matching rules point to different merchants. Confirm no merchant is assigned and both cases retain their source evidence for review; the ambiguous case requires a human choice regardless of priority.
11. As Free, attempt to create a new alias and confirm it is rejected. Verify an offline Premium lease is accepted only while its verified bounds remain valid and cannot be extended by clock changes, reinstall, or device change. Separately deny/expire entitlement or consent and confirm an unapproved signal is not evaluated while manual catalog search and movement entry remain available.
12. Set a personal Tambo → Alimentación preference, then evaluate a compatible future Tambo operation with a different general suggestion. Confirm the personal preference is proposed only for that user and future operation; confirm inactive, plan-blocked, or incompatible categories require a new choice and confirmed history does not change.
13. With two users and offline edits, verify alias and preference records remain owner-scoped, retry idempotently, and do not restore a tombstoned record or silently overwrite an incompatible revision.

## Sprint 5 implementation evidence (2026-10-08)

- `.\gradlew.bat testDebugUnitTest`: passed, 472 unit tests.
- Targeted `connectedDebugAndroidTest` checks passed on Samsung SM-A165M (Android 16); follow-up cases for blank and normalized-empty merchant queries are recorded below.
- Earlier validation recorded `npx --yes supabase@latest db reset --local` as passing from an empty database. The current revalidation could not reproduce that clean history against the shared `supabase_db_kipu`: `migration list --local` shows the three CCO S5 versions (`20261008161406`, `20261008161540`, `20261008161631`) missing from its applied history and a database-only `20261008170000` version. No reset or migration-history edit was made against this divergent shared database.
- To validate CCO S5 behavior without a reset, the three additive CCO migrations were applied individually in transactions to the local Docker database; no migration-history rows were recorded. Then `npx --yes supabase@latest test db --local supabase/tests/database/category_merchant.test.sql supabase/tests/database/category_type_test.sql supabase/tests/database/merchant_rules_s5.test.sql` passed: **75 pgTAP assertions**. This is targeted schema/contract evidence, not a clean migration replay.
- No remote Supabase writes were made. The linked/live migration history was observed behind the local S4/October head; reconcile it through the approved release process, then review effective RLS/grants before remote integration.

## Remaining release evidence

- Supabase security/performance advisors were not run.
- Timed usability evidence for the 2-minute category-to-movement flow and the 1-second merchant search target was not collected.
- Remote migration application, effective RLS/grant review, and a green full Android instrumentation suite remain release gates. The latest full run and comparison against the clean base are recorded below.

## 2026-10-08 follow-up - merchant query normalization

- `MerchantSearchUseCasesTest`: 12 tests passed, including punctuation-only input whose normalization is empty.
- `MerchantPickerTest`: 7 tests passed on Samsung SM-A165M / Android 16, including whitespace-only catalog browsing and punctuation-only provisional text.
- Full app `connectedDebugAndroidTest`: 267 tests ran; 18 failed in account, movement, notification, and plan packages. A full run at clean `origin/main` (`89a117e`) ran 257 tests and had 21 failures. All 18 failures on the CCO branch also failed on the base; the base additionally failed three category tests that pass on this branch. Every CCO category instrumentation test passed. The broad suite remains red, with no branch-only failing test identified.
