# UI Contract: Screens 4 and 5

## Visual Authority

`docs/stitch-design-system.md` is the sole local source for color, typography, spacing, shapes and accessibility tokens. Screen 4/5 assets are not present with stable Stitch IDs, so implementation may claim token compliance but not pixel-perfect prototype fidelity until those assets are approved.

## Screen 4 - Dashboard

Content order:

1. Screen heading and global balance-mask control.
2. `Dinero real disponible` grouped separately by PEN and USD, including balances of archived non-deleted accounts.
3. Active liquid account cards.
4. Debit-card references nested/associated with their linked account without another balance contribution.
5. `Tarjetas de credito`, visually and semantically separate.
6. Archived instrument rows only through an explicit filter/action; hiding rows does not subtract their balances from financial totals. Archived credit cards with debt remain represented in the current-liability summary until paid.

Credit wording:

- Sprint 2 may show registered credit identity and configured line only.
- Sprint 3 labels unused capacity `Credito disponible, no es dinero propio`.
- Credit line never uses the income semantic color.
- Debt/utilization must include text/icon state and not rely on color.

Currency wording:

- PEN and USD totals remain separate.
- No combined total or implied exchange rate is shown.

States:

- Loading: structure-preserving indicator with no fake values.
- Empty: explain how to add first account/card.
- Content: local Room projection immediately.
- Sync pending/error: non-blocking instrument-level status; financial operation remains visible.
- Fatal local read error: safe retry state without exposing details.

## Screen 5 - Instruments and Form

Main destination groups active Accounts and Cards. Archived items are filterable and clearly labeled.

Creation mode is selected before fields:

- Account
- Debit card
- Credit card

Changing mode discards hidden incompatible fields and validation errors before submission.

### Account Form

- Alias, account type, PEN/USD (PEN initially), preset, icon, color, opening amount and opening date/time.
- After creation, opening amount/currency/date and account type are read-only.
- Financial correction is a separate adjustment action.

### Debit Card Form

- Optional alias, issuer, network, last four and linked active savings/bank account.
- Currency is displayed from linked account and is not independently editable.
- The linked value is labeled `Saldo de cuenta vinculada`.

### Credit Card Form

- Optional alias, issuer, network, last four, currency, line, billing day and due day.
- Day range 1..31; helper copy explains short-month adjustment.
- Preview uses only masked reference.

### Duplicate Warning

Matching issuer+network+last4 presents an explicit warning. Continuation requires a distinguishable alias and acknowledgement; it never changes operation idempotency.

Alias and issuer reject PAN-like or security-code-like input before it enters form state. Ambiguous numeric-only display text is intentionally unsupported.

### Quota

- Show remaining Free slots before confirmation.
- Account and debit card consume separate slots; Cash is exempt.
- Fifth instrument/reactivation is blocked with `FREE_LIMIT_REACHED`; existing data remains usable.

## Presets

- Stable IDs: BCP, BBVA, INTERBANK, SCOTIABANK, BANCO_NACION, GENERIC.
- Preset seeds icon/color/contrast only; manual override changes appearance, not identity/issuer/financial data.
- Banco de la Nacion uses Kipu fallback tokens until approved brand tokens exist.
- BCP uses valid canonical contrast `#002A8F`; malformed `#002A8G` is never used.
- Selected state is semantic and contrast is at least 4.5:1.

## MoneyText

Every displayed amount uses `MoneyText`:

- Tabular numbers (`tnum`).
- Explicit PEN/USD formatting from `Money`; no preformatted untyped amount in domain.
- Visible semantics expose localized amount and currency.
- Masked visual retains currency context and bullets, while semantics expose only `Monto oculto`.
- Raw amount is absent from content descriptions, state descriptions, test tags and invisible nodes when masked.

Card identity uses `MaskedCardReference`, not `MoneyText`.

## Adaptive Layout

- Compact `<600dp`: vertical dashboard and full-screen creation form.
- Medium `600..1023dp`: dashboard two-pane; instruments list/detail.
- Expanded `>=1024dp`: max-width 1200dp, list/detail and navigation rail.
- Base grid, spacing, radii and tonal hierarchy follow Stitch.
- All controls have at least 48x48dp targets and remain usable at 200% font scale.

## Navigation

- `app/dashboard`
- `app/instruments`
- `app/instruments/create?type={ACCOUNT|DEBIT|CREDIT}`
- `app/instruments/{instrumentId}`
- `app/instruments/{instrumentId}/edit`

Only internal IDs and creation mode may be route arguments. No alias, amount, currency, issuer or last four appears in routes.

Onboarding completion and restored authenticated sessions land on dashboard. Local commit emits one navigation effect without waiting for sync. Back from a dirty form requires discard confirmation.

## Accessibility Acceptance

- Headings identify screen and financial sections.
- Instrument row merges alias, institution, type, currency, lifecycle and masked/unmasked amount into coherent semantics.
- Presets expose selected state; archive/sync/error use text plus semantics.
- Focus order follows visual order; IME never hides focused field or Save.
- TalkBack, switch access, keyboard, landscape, RTL-safe icons and 200% font are validated.
