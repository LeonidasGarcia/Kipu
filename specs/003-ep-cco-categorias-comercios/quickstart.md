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

## Expected evidence

- Unit tests prove hierarchy, quota, eligibility, catalog substring search, exact alias matching, ambiguity review, Premium/consent gates, preference precedence and conflict decisions.
- Room migration tests prove the historical v3→v4 baseline and the S5 v18→v19 migration preserve existing movements and exact raw merchant text.
- Worker tests prove command retry/idempotency and conflict preservation.
- Database tests prove constraints, RLS, grants, Premium/consent enforcement, preference isolation, soft deletion and no merchant catalog client DML.
- Compose and real-device checks prove no tags, accessible states and local success while offline.
- Timed acceptance evidence records the 2-minute category-to-movement flow for at least 95% of participants and a cached merchant-search result or availability state within 1 second.

These are future validation steps, not evidence that S5 is implemented or tested. Supabase live migration alignment and effective RLS/grant review are release gates. This refinement performs no tests and writes no remote data.
