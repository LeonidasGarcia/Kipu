# UI Contract: Categories and Merchants

## Categories route

- Reached from the Categories entry in profile settings.
- Displays roots and their subcategories only; creation controls cannot offer a third level.
- Edit presentation exposes only name, icon and color. It never exposes tags.
- A root marked inactive visibly disables assignments in its branch while historical movement views retain the selected presentation.
- The Free counter concerns active custom roots only. At five, creation/reactivation explains the limit and leaves existing data untouched.
- An open sync conflict shows both complete presentation versions and requires an explicit choice.

## Movement selectors

- `CategoryPicker` and `MerchantPicker` are independent controls and independent state fields.
- `CategoryPicker` hides categories that are inactive or descendants of inactive roots.
- `MerchantPicker` normalizes accents, spaces and case, then searches catalog substrings. It shows all matching entries without assigning one automatically.
- A no-result merchant query shows an empty list and lets the user retain the entered provisional text. Selecting a catalog merchant clears provisional text; clearing a merchant permits provisional text.
- Forms and filters contain no tags, label chips, tag controls or tag relations.

## Accessibility and adaptive layout

- Every actionable control has a 48dp minimum target and descriptive semantics.
- The hierarchy, inactive state, quota state, empty result and conflict state are announced in text rather than color alone.
- UI remains usable with 200% font scaling and on compact, medium and expanded widths.
