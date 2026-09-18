---
name: Mercatto
description: A cargo-manifest / shipping-ledger marketplace — kraft paper, ink stamps, and tabular readouts standing in for glossy retail chrome.
colors:
  kraft-paper-50: "#ffffff"
  kraft-paper-100: "#ffffff"
  kraft-paper-200: "#f5f1e8"
  kraft-paper-300: "#dcccae"
  kraft-paper-400: "#c3ad86"
  kraft-paper-500: "#a68a63"
  kraft-paper-600: "#83694a"
  kraft-paper-700: "#5f4c37"
  kraft-paper-800: "#3c3226"
  kraft-paper-900: "#211d16"
  manifest-ink-100: "#e7ebf2"
  manifest-ink-200: "#c4cee0"
  manifest-ink-300: "#9fb0cc"
  manifest-ink-400: "#7992b6"
  manifest-ink-500: "#587aa0"
  manifest-ink-600: "#3f5a82"
  manifest-ink-700: "#304463"
  manifest-ink-800: "#232f45"
  manifest-ink-900: "#171e2c"
  consignment-tag-100: "#f6e9d0"
  consignment-tag-200: "#ecd3a1"
  consignment-tag-500: "#b9832f"
  consignment-tag-700: "#714e1c"
  consignment-tag-800: "#4c3513"
  approval-stamp-100: "#e5efdf"
  approval-stamp-200: "#c7dcba"
  approval-stamp-600: "#4a7a3e"
  approval-stamp-700: "#3b6231"
  approval-stamp-800: "#2c4a25"
  carbon-copy-red-100: "#f7e2df"
  carbon-copy-red-200: "#edbdb5"
  carbon-copy-red-600: "#9c3527"
  carbon-copy-red-700: "#7a291f"
  carbon-copy-red-800: "#571d16"
typography:
  display:
    fontFamily: "Oswald, system-ui, sans-serif"
    fontSize: "52px"
    fontWeight: 600
    lineHeight: 1.02
    letterSpacing: "-0.01em"
  headline:
    fontFamily: "Oswald, system-ui, sans-serif"
    fontSize: "26px"
    fontWeight: 600
    lineHeight: 1.1
    letterSpacing: "0"
  title:
    fontFamily: "Oswald, system-ui, sans-serif"
    fontSize: "17px"
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: "0"
  body:
    fontFamily: "IBM Plex Sans, system-ui, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.4
    letterSpacing: "normal"
  label:
    fontFamily: "IBM Plex Mono, ui-monospace, monospace"
    fontSize: "10.5px"
    fontWeight: 500
    lineHeight: 1.2
    letterSpacing: "0.09em"
  readout:
    fontFamily: "IBM Plex Mono, ui-monospace, monospace"
    fontSize: "22px"
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: "-0.01em"
rounded:
  sm: "1px"
  md: "2px"
  lg: "3px"
spacing:
  "1": "4px"
  "2": "8px"
  "3": "12px"
  "4": "16px"
  "6": "24px"
  "8": "32px"
components:
  button-primary:
    backgroundColor: "{colors.manifest-ink-600}"
    textColor: "{colors.kraft-paper-50}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "8px 15.6px"
  button-primary-hover:
    backgroundColor: "{colors.manifest-ink-700}"
  button-secondary:
    backgroundColor: "{colors.kraft-paper-50}"
    textColor: "{colors.kraft-paper-900}"
    rounded: "{rounded.sm}"
    padding: "8px 15.6px"
  button-ghost:
    backgroundColor: "transparent"
    textColor: "{colors.manifest-ink-700}"
    padding: "8px 8px"
  input:
    backgroundColor: "{colors.kraft-paper-50}"
    textColor: "{colors.kraft-paper-900}"
    rounded: "{rounded.sm}"
    padding: "7px 11px"
  card:
    backgroundColor: "{colors.kraft-paper-50}"
    textColor: "{colors.kraft-paper-900}"
    rounded: "{rounded.sm}"
    padding: "12px"
  tag:
    backgroundColor: "{colors.consignment-tag-100}"
    textColor: "{colors.consignment-tag-800}"
    rounded: "{rounded.sm}"
    padding: "4px 10px 4px 16px"
---

# Design System: Mercatto

## Overview

**Creative North Star: "The Cargo Manifest"**

Mercatto reads as a shipping ledger, not a storefront window: every product is a line item that has been received, weighed, priced, and stamped, not styled for a hero carousel. The ground is a clean white manifest sheet — chosen so photographed cargo (product imagery, which ships on a white background) sits flush against the page with no visible mat — with ink-black body text, a deep indigo-slate "manifest ink" that stands in for a conventional brand blue, and a mustard "consignment tag" accent reserved for small routing labels. A warm kraft-paper scale (`paper-200`→`paper-900`) still carries every border, divider, secondary surface, and ink tone, so the manifest character lives in structure and accent rather than in a tinted ground. A carbon-copy red exists solely for alerts and destructive actions — it is never used decoratively.

The build confirms the thesis holds end to end: registration-corner frames (`<Blueprint>`), rotated ink-stamp badges, dashed consignment tags with a punched grommet dot, and tabular-mono "readout" numerals for every price/SKU/quantity appear consistently across Home, Search, Product, Cart, Checkout, Orders, and the Seller Dashboard, not just on the flagship surface. Corners are sharp to near-square everywhere (1–3px); nothing in the shipped code rounds a surface into a conventional soft-UI pill or card, aside from the circular radio dot and the stamp badge's own oval, which are the system's confirmed exceptions.

**Key Characteristics:**
- White page/card ground (matches white-background product photography) with ink-black text; kraft-paper tones carry borders, secondary surfaces, and mid-tone ink instead of the ground itself.
- Sharp/near-square corners (1–3px) everywhere except radio inputs and stamp badges.
- Tabular-mono "readout" styling reserved for price, quantity, and SKU values.
- Status (stock, order state) is always named in text plus a stamp/tag treatment — never color alone.
- Carbon-copy red is load-bearing only for alerts/destructive actions, never routine accenting.

## Colors

The palette reads as ledger paper and ink: warm neutral kraft tones carry the ground, a cool indigo-slate carries structural/interactive weight, and a small mustard accent is reserved for routing-tag surfaces.

### Primary
- **Manifest Ink** (`#3f5a82`, scale 100–900): the system's structural/interactive color — header and footer chrome (`bg-accent-900`/`800`/`700`), primary buttons (`--color-accent-600` / `700` hover / `800` active), links, focus rings, and stamp-badge ink. Ranges from near-black `#171e2c` (900) to a pale wash `#e7ebf2` (100) used for tag backgrounds.

### Secondary
- **Consignment Tag Ochre** (`#b9832f`, scale 100–900): reserved for the dashed routing-tag component (`.tag-accent-2`) and category-strip chips on Home. It never appears as a button or link color — its role stays confined to small tag-shaped surfaces.

### Tertiary
- **Approval-Stamp Green** (`#4a7a3e`): confirmation/success state only (`.callout-ok`), e.g. order-confirmation and positive inline callouts.
- **Carbon-Copy Red** (`#9c3527`, scale 100–900): alerts and destructive actions only — low-stock stamps (`.stamp-alert`), alert tags (`.tag-alert`), and error callouts (`.callout-alert`). Never used for emphasis or decoration.

### Neutral
- **Kraft Paper** (`#ffffff` → `#211d16`, 9-step scale): `paper-50`/`paper-100` (`#ffffff`) are the page and card ground — pure white, chosen to match the white-background product photography and avoid a visible mat around every image. `paper-200` (`#f5f1e8`) is the surface/secondary-input tone (the product-card code strip, active list rows, info panels) — the first step of visible warmth above white. `paper-300`–`paper-800` carry borders, hover/active feedback, and secondary text; `paper-900` (`#211d16`) is body text and the darkest ink. Divider lines use `rgba(33,29,22,0.18)` (ink at 18% opacity), not a separate gray.

### Named Rules
**The Named-Not-Colored Rule.** Stock and order status are always expressed as text plus a stamp/tag shape (`In stock`, `Low stock`, `Sold out`); color (including the alert red) is a reinforcement, never the sole signal.

**The Red-Is-Alarm Rule.** Carbon-copy red (`alert-*`) is reserved for alerts and destructive/error states. It does not appear in navigation, primary actions, or decorative accents anywhere in the shipped screens.

## Typography

**Display/Heading Font:** Oswald (with system-ui, sans-serif fallback)
**Body Font:** IBM Plex Sans (with system-ui, sans-serif fallback)
**Label/Mono Font:** IBM Plex Mono (with ui-monospace, monospace fallback)

**Character:** A condensed, stencil-like Oswald for headings reads as cargo-crate lettering; IBM Plex Sans carries calm, legible body copy; IBM Plex Mono renders anything numeric or code-like (price, SKU, quantity, labels) as a fixed-width ledger readout.

### Hierarchy
- **Display** (Oswald, 600, 32px→52px responsive clamp, line-height 1.02–1.05): the Home hero headline only.
- **Headline** (Oswald, 600, ~26px, line-height 1.1): section titles (`h2`, e.g. "Shop by category").
- **Title** (Oswald, 600, 17px, line-height 1.2): card/product titles (`.card-title`).
- **Body** (IBM Plex Sans, 400, 14px base, line-height 1.4): running copy, base document font-size.
- **Label** (IBM Plex Mono, 500, 10.5px, letter-spacing 0.09em, uppercase): field labels, table headers, tag/stamp microcopy.
- **Readout** (IBM Plex Mono, tabular-nums, letter-spacing -0.01em): price, quantity, SKU, and count values everywhere (`.readout`), sized contextually (22px on product-card prices, smaller in tables).

### Named Rules
**The Readout Rule.** Any value that is a price, quantity, count, or SKU renders in `.readout` (IBM Plex Mono, tabular numerals). Oswald and IBM Plex Sans never carry numeric data that needs at-a-glance comparison.

## Layout

Desktop content is capped at a 1280px column (`max-w-[1280px]`), consistent across Header, Footer, Home, and inner pages. Spacing runs on a compact ledger rhythm: 4/8/12/16/24/32px steps (`ds-1`…`ds-8`), tighter than typical consumer e-commerce — card padding is 12px, button padding ~8px/16px. The product grid steps responsively from 2 columns (mobile) to 3 (sm) to 5 (lg) on Home/Search. Header collapses to an icon-only mobile bar with a slide-down department menu below `md`; the desktop header carries a persistent department navlink strip.

## Elevation & Depth

Mercatto is mostly flat: cards and inputs sit on a 1px ink-tinted border (`--color-divider`, ink at 18% opacity) rather than a shadow at rest. A restrained shadow vocabulary exists for genuine elevation moments (hover lift, dialogs) but is not used for routine card/button separation.

### Shadow Vocabulary
- **ds-sm** (`0 1px 2px rgba(33,29,22,0.16)`): primary-button hover feedback.
- **ds-md** (`0 4px 12px rgba(33,29,22,0.18)`): product-card hover lift (`.prod:hover`, paired with a 1px translateY).
- **ds-lg** (`0 14px 34px rgba(33,29,22,0.26)`): modal/dialog and mobile-menu overlay elevation.

### Named Rules
**The Border-Before-Shadow Rule.** Resting surfaces (cards, inputs, tags) are separated by a 1px ink-tinted border, not a shadow. Shadow appears only as a state response — hover or an overlay that must float above the page.

## Shapes

Corners are sharp to near-square everywhere: the radius scale is 1px/2px/3px (`ds-sm`/`ds-md`/`ds-lg`), applied to buttons, inputs, cards, tags, and dialogs. The two confirmed exceptions are the circular radio dot/checkbox and the stamp badge's rotated oval border — both intentionally break the square grid to read as physical hardware (a dial, an ink stamp), not as generic rounded UI. Borders are frequently dashed (routing tags, hero manifest-number strip, blueprint-adjacent framing) to read as ledger/waybill perforation rather than solid card chrome.

## Components

### Buttons
- **Shape:** near-square (1px radius), uppercase Oswald label text, 0.03em letter-spacing.
- **Primary:** manifest-ink-600 background, kraft-paper-50 text; hover darkens to ink-700 with `ds-sm` shadow; active to ink-800.
- **Secondary:** kraft-paper-50 background with a divider border; hover moves to paper-200/paper-500 border.
- **Ghost:** transparent, manifest-ink-700 text; hover applies a translucent ink wash.
- **Icon:** 44×44px square hit target, no padding — used for header cart/account/menu controls.

### Chips / Tags
- **Style:** dashed 1px border, a punched "grommet" dot rendered via `::before`, IBM Plex Mono microcopy. Variants: `tag-accent` (manifest-ink wash), `tag-accent-2` (consignment-tag ochre, used for category chips), `tag-neutral`, `tag-outline`, `tag-alert`.
- **State:** no separate selected/unselected variant observed; tags are used as static routing labels and clickable category filters sharing the same visual treatment.

### Cards / Containers
- **Corner Style:** 1px radius (near-square).
- **Background:** white (`paper-50` / `--color-card`).
- **Shadow Strategy:** `ds-sm` at rest is used sparingly (product-card default has none beyond the divider border; `.prod:hover` promotes to `ds-md` with a 1px lift).
- **Border:** 1px solid divider (ink at 18% opacity).
- **Internal Padding:** 12–14px (`space-3` / `p-3.5` on the flagship product card).

### Inputs / Fields
- **Style:** kraft-paper-50 background, 1.5px divider border, 1px radius, IBM Plex Mono uppercase field labels at 10.5px.
- **Focus:** border shifts to manifest-ink, no glow/ring beyond `:focus-visible` outline.
- **Error / Disabled:** disabled buttons drop to 45% opacity; no dedicated input-error style was found in the shipped CSS (not yet a confirmed pattern — do not invent one).

### Navigation
- **Style:** header/footer chrome sit on manifest-ink-900/800/700 (dark, inverted from the kraft body), with `.navlink` items in uppercase Oswald, 12.5px, hovering to a filled ink-700 block. Mobile collapses to a hamburger-triggered slide-down panel reusing the same `.navlink` treatment at a 44px min-height tap target.

### Ink-Stamp Badge (signature component)
`.stamp`: a rotated (-3deg) oval with a dashed-adjacent solid currentColor border, `mix-blend-mode: multiply` so it reads as ink pressed onto paper beneath it. Used for stock status on every product card (`In stock` / `Low stock` / `Sold out`, switching to `stamp-alert` red for low stock) and for the wordmark mark in the header (rendered with `mixBlendMode: normal` there, since it sits on dark chrome rather than paper).

### Crate-Stencil Registration Corners (signature component)
`<Blueprint>`: wraps a container in four absolutely-positioned corner brackets (ink-tinted crosshair marks) via a shared `.blueprint`/`.corner` CSS pair. Used on the Home hero panel and every `ProductGridCard`, giving media/content blocks a stenciled-crate frame instead of a card shadow.

### Ledger Placeholder (signature component)
`.ph`: a graph-paper grid background (two overlaid linear-gradients at 14px pitch) with a dashed-border caption chip, used by `<Placeholder>` for any un-photographed product image — an intentional "not yet documented" state rather than a generic gray box.

## Do's and Don'ts

### Do:
- **Do** render price, quantity, SKU, and count values in the mono `.readout` treatment (IBM Plex Mono, tabular numerals) everywhere they appear.
- **Do** pair every stock/order status with both a text label and a stamp/tag shape; never signal status by color alone.
- **Do** keep corners at 1–3px (`ds-sm`/`ds-md`/`ds-lg`) on buttons, inputs, cards, tags, and dialogs; reserve fully circular shapes for the radio dot and the stamp badge's oval.
- **Do** use dashed borders for routing/ledger-adjacent elements (tags, the manifest-number strip, the perforated `.perf-top` totals edge).

### Don't:
- **Don't** use carbon-copy red (`alert-*`) outside alerts, errors, and destructive actions — it is not a decorative or navigational color in this system.
- **Don't** apply drop shadows to resting cards/inputs; shadow is reserved for hover-lift and overlay elevation (dialogs, mobile menu), per the Border-Before-Shadow Rule.
- **Don't** introduce rounded/pill buttons, cards, or chips — the near-square radius scale is the system's confirmed form language.
- **Don't** add kicker/eyebrow labels above headings, unicode glyph icons, or hard-offset "neobrutalist" shadows: none of these appear anywhere in the shipped build, and their absence is deliberate, not an oversight to fill in on new surfaces.
