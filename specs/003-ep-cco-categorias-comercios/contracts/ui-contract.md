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
- A no-result merchant query shows an empty list and lets the user retain the entered provisional text. Selecting a catalog merchant clears provisional text; clearing a merchant permits provisional text.
- Forms and filters contain no tags, label chips, tag controls or tag relations.

## Accessibility and adaptive layout

- Every actionable control has a 48dp minimum target and descriptive semantics.
- The hierarchy, inactive state, quota state, empty result and conflict state are announced in text rather than color alone.
- UI remains usable with 200% font scaling and on compact, medium and expanded widths.
