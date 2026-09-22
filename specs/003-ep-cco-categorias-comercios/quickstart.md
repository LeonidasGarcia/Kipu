# Quickstart Validation: EP-CCO

## Prerequisites

- Android SDK and JDK 17 configured for this repository.
- Local Supabase stack or linked project with all migrations applied and two test users.
- A device/emulator with network control to test offline behavior.

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

## Expected evidence

- Unit tests prove hierarchy, quota, eligibility, search normalization and conflict decisions.
- Room migration tests prove v3 data and existing movements survive v4 unchanged.
- Worker tests prove command retry/idempotency and conflict preservation.
- Database tests prove constraints, RLS, grants and no merchant client DML.
- Compose and real-device checks prove no tags, accessible states and local success while offline.
- Timed acceptance evidence records the 2-minute category-to-movement flow for at least 95% of participants and a cached merchant-search result or availability state within 1 second.
