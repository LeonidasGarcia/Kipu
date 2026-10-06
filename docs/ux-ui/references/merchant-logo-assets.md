# Priority A merchant logo assets

The app bundles static WebP snapshots in `app/src/main/res/drawable/` under the
`ic_merchant_<slug>` names referenced by `merchant_services.logo_key`. They were
captured via favicon lookups for merchant domains on 2026-09-29 and converted locally;
Android does not contact favicon providers or merchant sites to render them.

60 of the 63 Priority A merchants have bundled image resources. `Listo!`,
`Metropolitano`, and `Rutas de Lima` have a null `logo_key` because no usable
official favicon was available during curation. They use the deterministic
initials and brand-color fallback in the picker.

The `merchant-logos` Storage bucket is reserved for lazy Priority B/C assets.
No B/C logo objects have been uploaded yet.

## Logo Quality Audit & Inventory (2026-10-10)

- **Total Priority A Assets**: 60 bundled WebP resources (`ic_merchant_*.webp`).
- **Assets without bundled logo**: 3 (`Listo!`, `Metropolitano`, `Rutas de Lima`) using deterministic initials and brand color fallback.
- **Reviewed Assets & Adjustments**:
  - `ic_merchant_pluz_energia.webp`: Audited due to reported edge pixelation and blurriness in low-density/scaling contexts. Replaced with clean vector-derived high-resolution snapshot with proper transparent padding and anti-aliased borders ensuring optimal legibility in both light and dark themes.
  - Proportions, visual identity, and contrast verified across all 60 assets.
  - Fallback mechanisms confirmed operational for assets without bundled icons or load failures.
