# UI Contract: Categories and Merchants

## Categories route

- Reached from the Categories entry in profile settings.
- Displays roots and their subcategories only; creation controls cannot offer a third level.
- Shows Gastos and Ingresos tabs. Each tab displays its matching type plus `GENERAL` categories; selecting a tab sets the type for a newly created root.
- A subcategory inherits its root type. Parent choices must preserve that type.
- Edit presentation exposes only name, icon and color. It never exposes tags.
- A root marked inactive visibly disables assignments in its branch while historical movement views retain the selected presentation.
- The Free counter concerns active custom roots only. At five, creation/reactivation explains the limit and leaves existing data untouched.
- An open sync conflict shows both complete presentation versions and requires an explicit choice.

## Movement selectors

- `CategoryPicker` and `MerchantPicker` are independent controls and independent state fields.
- `CategoryPicker` hides categories that are inactive or descendants of inactive roots.
- `CategoryPicker` shows `EXPENSE` and `GENERAL` categories for expenses, `INCOME` and `GENERAL` categories for income, and no category selector for transfers.
- `MerchantPicker` normalizes accents, spaces and case, then searches catalog substrings. It shows all matching entries without assigning one automatically.
- **MerchantPicker Filters (UI Taxonomy):** To enhance discovery, catalog merchants are visually grouped into logical macro-categories (e.g., "Restaurantes y Delivery", "Entretenimiento y Streaming", "Supermercados") driven by the presentation layer rather than raw database subcategories.
- An empty or whitespace-only merchant query opens the locally cached catalog for browsing and does not show a no-result or provisional-text action. A non-empty raw query that normalizes to empty shows no catalog matches and may be retained as trimmed provisional text. Selecting a catalog merchant clears provisional text; clearing a merchant permits provisional text.
- Forms and filters contain no tags, label chips, tag controls or tag relations.

## Sprint 5 alias rules (HU-16)

- The alias management flow starts only after the user confirms a canonical merchant from the Kipu catalog. A new rule requires Premium; editing or removing an owned existing rule remains owner-scoped and revision-aware.
- The confirmation surface displays the original source text and the canonical catalog merchant separately. It explains that the saved normalized pattern is compared by exact equality; punctuation is retained and there are no wildcard, prefix, substring or fuzzy alias matches.
- A signal is evaluated only when entitlement and consent are both current. If either gate fails, the signal is not processed and the manual movement/catalog flows remain available.
- A no-match result keeps the source text available for review without assigning a merchant. Different canonical destinations for matching rules show an explicit review choice; the UI does not silently choose by priority.
- Alias edits affect future suggestions only. The screen does not change confirmed movements or the shared merchant catalog.

## Sprint 5 merchant category preference (HU-17)

- The user can set or clear one personal category preference for an identified merchant; it is separate from merchant name/icon/color presentation and from catalog/general rule defaults.
- The category chooser lists only categories compatible with the future operation and currently eligible under owner, active-root and plan rules. The preference itself has no separate Premium gate.
- An eligible preference is shown as the user's first suggestion for future matching operations. If the category becomes inactive, plan-blocked or incompatible, the UI asks for a new selection and does not auto-apply it.
- The UI states that preference changes affect future suggestions only and never recategorize confirmed history.

## Accessibility and adaptive layout

- Every actionable control has a 48dp minimum target and descriptive semantics.
- The hierarchy, inactive state, quota state, empty result and conflict state are announced in text rather than color alone.
- UI remains usable with 200% font scaling and on compact, medium and expanded widths.
