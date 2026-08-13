# Handoff: Mercatto — Marketplace Interface

## Overview

Mercatto is a fictional general-merchandise marketplace built as coursework: an original e-commerce
interface covering the full shopping journey — browse, search, product detail, lists, cart, sign-in,
checkout, orders and reviews. It is **not** a clone of any existing retailer; all branding, copy,
catalogue data and layout are original and must stay that way in the implementation.

Nine screens, desktop-first (1280px content column), UI copy in English.

## About the Design Files

The files in this bundle are **design references created in HTML** — prototypes that show the
intended look and behavior. They are not production code to copy directly.

The task is to **recreate these designs in the target codebase's existing environment** (React, Vue,
SwiftUI, native, etc.) using its established components, routing, state and styling patterns. If the
project has no environment yet, choose the framework that fits the project best and implement there.
`Mercatto.dc.html` is a single-file prototype using a proprietary streaming-template runtime — do not
try to port that runtime. Read it for structure, copy, and exact values only.

## Fidelity

**High fidelity.** Colors, typography, spacing, states and copy are final. Recreate the UI faithfully
using the target codebase's component library, mapping the tokens below onto its own token system.
Layout is desktop-only; responsive behavior was not designed (see Responsive below).

## Design System

The visuals come from the **Industry** design system, included at `design-system/styles.css`
(and `design-system/readme.md` for its rationale). Its character:

- Steel-blue accent on a light technical ground; everything reads like a blueprint drawing.
- Cards, figures and framed panels are **square-cornered, hairline-bordered, transparent** — never
  filled rounded blocks. Each framed object carries four `+` registration marks at its corners
  (`.blueprint` + four `<i class="corner tl|tr|bl|br">` children, drawn at -6px offsets).
- The **primary button is the only solid object**: an accent fill, square corners.
- Photographs go through `.duotone`, which desaturates and washes them in the accent.
- Icons: Lucide, stroke-width 1.5.

If the target codebase has its own design system, keep the *structure and behavior* of these screens
and re-skin with the codebase's tokens — do not mix the two.

## Design Tokens

All values are CSS custom properties in `design-system/styles.css`. Never hard-code these.

### Color

| Token | Value | Use |
| --- | --- | --- |
| `--color-bg` | `#f2f2f3` | page ground |
| `--color-surface` | `#e9e9ea` | table headers, order-card headers, active list row |
| `--color-text` | `#1d1f20` | body text |
| `--color-accent` | `#5980a6` | primary fill, kickers, meter fills, duotone wash |
| `--color-divider` | `rgba(29,31,32,.16)` | every hairline border |

Accent ramp: `100 #eef6ff · 200 #d6ebff · 300 #b5d9fd · 400 #94bce3 · 500 #749dc4 · 600 #597ea3 ·
700 #416180 · 800 #2c455d · 900 #1d2d3d`

Neutral ramp: `100 #f5f5f8 · 200 #e7e7ea · 300 #d4d4d7 · 400 #b7b7ba · 500 #98989b · 600 #7a7a7d ·
700 #5d5d60 · 800 #424244 · 900 #2b2b2d`

Applied roles used across the screens:

- Header bar background `--color-accent-900`; department sub-nav `--color-accent-800`, separated by a
  `1px solid --color-accent-700` rule. Footer uses the same `900` field.
- Header secondary text (`Hello, sign in`, cart count, footer legal line) `--color-accent-400`.
- Body-size accent text (links, star glyphs, stock status, "Helpful" counts, promo lines) uses
  `--color-accent-700` — the base accent does not pass contrast at paragraph size.
- Struck-through list prices `#98989b`; muted meta `#7a7a7d`; secondary body `#5d5d60`; long-form
  review body `#424244`.
- Links: default `--color-accent-700`, hover `--color-accent-800` + underline.

### Typography

- Headings — **Barlow Condensed** 600 (`--font-heading`).
- Body — **Barlow** 400/500 (`--font-body`). Base size 14px.
- Sizes in use: page H1 32px (40px on the order-confirmation and 52px on the home hero); section H2
  24px; product H1 34px; card title 15–20px; price display 38px (product), 26px (buy box / totals),
  19–22px (cards); body 13–14px; meta 11–12.5px.
- "Kicker" label style: Barlow Condensed 600, 11px, `letter-spacing:.16em`, uppercase,
  `--color-accent`.
- Brand wordmark: Barlow Condensed 600, 26px, `letter-spacing:.06em`, uppercase.
- Nav/section label caps use `letter-spacing:.1em`; order-card column labels 10px uppercase `.1em`.

### Spacing, radius, elevation

- Spacing scale (0.85× density): `--space-1 3.4 · 2 6.8 · 3 10.2 · 4 13.6 · 6 20.4 · 8 27.2` px.
  Page gutters 24px; content max-width 1280px (1080px on checkout/orders, 820px on confirmation,
  760px on review, 400px on sign-in).
- Radius: `--radius-sm 2 · md 4 · lg 7` px. Framed blueprint objects stay square.
- Elevation: `--shadow-sm/md/lg`. Only used as a hover lift on product cards
  (`transition: box-shadow .15s` → `--shadow-md`).

### Component classes (from the design system)

`.btn` + `.btn-primary | .btn-secondary | .btn-ghost | .btn-icon | .btn-block`, `.tag` +
`.tag-accent | .tag-outline | .tag-neutral`, `.field` + `label` + `.input`, `.radio` + `.dot`,
`.card`, `.table`, `.hr`, `.blueprint` + `.corner`, `.duotone`.

### Local classes defined by the prototype

- `.ph` — image placeholder: 45° repeating stripe (`#e7e7ea 0 7px, #f5f5f8 7px 14px`), 1px divider
  border, centered 10px uppercase label in `#7a7a7d`. **Every image in the prototype is a
  placeholder** — see Assets.
- `.prod` — hover elevation for clickable product cards.
- `.stars` — `--color-accent-700`, `letter-spacing:1px`.
- `.navlink` — sub-nav button: transparent, `#e7e7ea` text, 12.5px, 9px/12px padding, hover
  background `--color-accent-700`.

## Global Chrome

### Header (all screens)

Two stacked bars, full-bleed, on `--color-accent-900`.

**Top bar** — max-width 1280px, 12px/24px padding, flex, 20px gap:
1. Brand: wordmark + `MARKETPLACE` in 10px `.2em` uppercase `--color-accent-400`. Click → Home.
2. Search group (flex:1, max-width 720px, 1px `--color-accent-700` border, `#f2f2f3` fill):
   a department `<select>` on `#e7e7ea` with a right divider; a borderless text input
   (placeholder "Search products, brands and categories"); a solid accent Search button
   (Barlow Condensed 600, 14px, `.08em`, uppercase, 18px horizontal padding).
   Enter in the field and the button both run the search.
3. Account cluster (12px): "Hello, sign in" / "Hello, {name}" over "Account & Lists";
   "Returns" over "& Orders"; a CART chip (1px accent-700 border) with the item count in
   Barlow Condensed 20px `--color-accent-400`.

**Sub-nav** — `--color-accent-800`, 1px accent-700 top rule: "All departments" then each department;
"Your Lists" pushed to the far right in `--color-accent-400`.

### Footer (all screens)

`--color-accent-900`, four equal columns (Get to know us · Sell with us · Payment · Help), 38px/24px
padding, 13px items in `#d4d4d7` with 15px uppercase Barlow Condensed headings in `#f2f2f3`.
Bottom rule + centered 11.5px legal line in `--color-accent-400`:
"Academic prototype · Mercatto · Fictional interface built for coursework".

## Screens / Views

Single-page app; `view` drives which screen renders. Every navigation scrolls to top.

### 1. Home (`home`)

Purpose: entry point and discovery.

- **Hero** — `.blueprint` panel, 34px padding, grid `1.15fr 1fr`, 34px gap, centered.
  Left: kicker "2026 catalogue · Free shipping over $49"; H1 52px/1.02, max-width 15ch,
  "Everything the workshop, the desk and the kitchen need."; 46ch lede in `#5d5d60`;
  buttons "See today's deals" (primary → search sorted by price low→high) and
  "Browse catalogue" (secondary → search, all departments).
  Right: 16:10 duotone placeholder, "Campaign image".
- **Shop by category** — H2 24px; 6-column grid, 16px gap. Each cell is a `.blueprint` card with a
  1:1 placeholder, category name (15px), and "{n} items" in 11.5px `#7a7a7d`. Click → filtered search.
- **Recommended for you** — H2 + "See all" ghost button; grid of N columns (default 5, tweakable
  3–6). Card: 1:1 duotone placeholder, 13.5px title (min-height 36px), stars + review count,
  price 22px next to struck list price, delivery line, full-width primary "Add to cart".
  Card click opens the product; the button stops propagation and adds to cart.

### 2. Search results (`search`)

Purpose: filter and compare.

Grid `236px 1fr`, 28px gap.

**Sidebar** — kicker "Filters", then:
- Department: radio list over `All` + 6 departments.
- Price up to: range input, `min 20 max 600 step 10`, accent-colored; formatted value beneath.
- Customer rating: radios `4.5 & up`, `4 & up`, `3 & up`, `all ratings`, each showing star glyphs.
- Delivery: single checkbox "Arrives tomorrow".

**Results** — a header row (result count + query + department, and a Sort select: Relevance /
Price: low to high / Price: high to low / Avg. customer review) above a vertical stack of 16px-gapped
`.blueprint` rows, each a grid `180px 1fr 210px`:
1. 1:1 duotone placeholder (click → product).
2. Title 20px (click → product), "Sold by {brand}", stars + rating + count, 52ch description,
   two tags: department (`.tag-outline`) and delivery badge (`.tag-accent`).
3. A left-ruled buy column: price 26px + struck list price, "or 4 interest-free payments of $X",
   delivery line, stock line, primary "Add to cart", secondary "View details".

Empty state: `.blueprint` panel, "No results", helper copy, "Clear filters" secondary button.

### 3. Product (`product`)

Purpose: evaluate and buy one item.

Breadcrumb `Home / {Department} / {name}` in 12.5px `#7a7a7d`.

Main grid `420px 1fr 300px`, 28px gap, top-aligned.

- **Gallery** — `.blueprint` frame with 12px padding around a 1:1 duotone "Main photo", plus a
  4-up thumbnail row (8px gap): "Angle 2", "Angle 3", "Detail", "In use".
- **Detail column** — H1 34px/1.08; "Brand: {brand}" with the brand as a link; a rule-terminated
  rating row (stars, numeric rating, "{n} ratings"); a price block (38px accent-800 price, struck
  list price, discount `.tag-accent` like "-31%", installment line); "About this item" kicker with a
  4-bullet list at 13.5px; a `.blueprint` "Product details" panel wrapping a `.table` of
  Brand / Department / Item model number / Warranty / Sold by.
- **Buy box** — sticky (`top:16px`) `.blueprint` panel: price 28px, delivery line, "Ships from and
  sold by {store}", stock status in 17px accent-700, a Qty select (1–5), primary "Add to cart",
  secondary "Buy now", then the **Add to a list** module (see Lists), a `.hr`, and the 12px
  reassurance line "Free returns within 30 days · Secure payment · 12-month warranty".

Below the fold, each section separated by a 28px-padded top rule:

- **"Customers who viewed this item also viewed"** — subtitle "Based on browsing sessions that
  included {name}"; 6-column grid of compact cards (placeholder, 12.5px title, stars, 19px price,
  and a co-view share line: 38% / 24% / 19% / 14% / 11% / 9%).
- **"Recommended based on this item"** — subtitle "Frequently bought with or instead of this
  {Department} pick"; two columns:
  - *Frequently bought together*: a `.blueprint` panel with an 84px placeholder row joined by `+`
    separators, a checkbox list of the bundle items (the first prefixed "This item: "), a live
    "Total price:" in 24px accent-800 with "{k} of {n} items selected", and a primary
    "Add selected to cart".
  - Four related rows (`88px 1fr 150px`): placeholder, title, stars, and an italic-free reason line
    in accent-700 — "Highest rated in {Department}", "Similar item at a lower price",
    "Most reviewed by customers like you", "Arrives tomorrow with this order" — plus price and a
    secondary "Add to cart".
- **Customer reviews** — H2 with a secondary "Write a review" button; grid `260px 1fr`:
  - Summary panel: 40px rating, star row, "{n} global ratings", and a 5-row histogram
    (68/21/7/2/2%) drawn as 9px hairline-bordered bars filled with `--color-accent`.
  - Review list: stars + 15px headline, "{author} · {date} · Verified purchase",
    70ch body, a small secondary "Helpful" button and its running count.
    User-submitted reviews are prepended to the seeded ones.

### 4. Write a review (`review`)

Purpose: rate a purchased item.

760px column. H1 "Create a review". A `.blueprint` item strip (82px placeholder + name + brand ·
department). Then a `.blueprint` form panel: **Overall rating** as five 30px star buttons
(filled `--color-accent-700`, empty `#b7b7ba`) with a label — "Select a rating", "I hate it",
"I don't like it", "It's OK", "I like it", "I love it"; a Headline field ("What is most important to
know?"); a Written review textarea ("What did you like or dislike? What did you use this product
for?"); a "Add a photo or video" drop placeholder (96px); primary "Submit review" and secondary
"Cancel". Submitting requires a rating; it returns to the product page with the new review on top.

### 5. Your lists (`lists`)

Purpose: save products across multiple named lists.

Grid `250px 1fr`.

- **Sidebar** — kicker "Your lists"; one row per list: name + item count, left border
  `2px --color-accent` and `--color-surface` background when active, transparent otherwise. Below,
  a "New list name" input + primary "Add" (Enter also submits).
- **Detail** — H1 = list name, subtitle "{n} item(s) · private list"; header actions
  "Add all to cart" (secondary) and "Delete list" (ghost). Item rows are `.blueprint`
  `110px 1fr 190px` grids: placeholder, title, stars + count, "{delivery} · {stock}", then price
  21px, primary "Add to cart", ghost "Remove from list".
- Empty state: "This list is empty" + "Open a product and use 'Add to list' to save it here." +
  primary "Browse products".

**Add to a list module** (product buy box): a 1px-bordered block with the kicker "Add to a list",
a select of `"{list name} ({count})"`, a secondary "Add to list", a ghost "+ Create a new list" that
swaps in a name input + primary "Save", and a 12px accent-700 feedback line
("Added to {list}." / "Already in {list}." / "List \"{name}\" created."). If the customer has deleted
every list, "Add to list" creates a "Shopping List" and adds the item to it.

Seeded state: "Shopping List" (empty) and "Workshop wishlist" (1 item).

### 6. Cart (`cart`)

Grid `1fr 300px`.

- H1 "Shopping cart", subtitle "{n} product(s) · prices and availability may change", divider.
- Line rows `130px 1fr auto`, separated by hairlines: placeholder; title (click → product), stock in
  accent-700, delivery, then a control row — a bordered `− {qty} +` stepper, ghost "Delete", ghost
  "Save for later"; right-aligned line subtotal at 20px.
- Right-aligned "Subtotal ({n} items): $X" beneath the list.
- **Saved for later** (only when non-empty): H2 + 4-column grid of compact cards with
  "Move to cart".
- Empty state: "Your cart is empty" + "Continue shopping".
- **Summary** — sticky `.blueprint`: a shipping-progress line ("Your order qualifies for FREE
  shipping." or "$X away from FREE shipping."), "Subtotal ({n} items):", the amount at 26px,
  a gift checkbox, primary "Proceed to checkout", secondary "Continue shopping".

### 7. Sign in / Create account (`signin`)

400px column, single `.blueprint` panel. Title "Sign in" / "Create account"; subtitle
"Use your email and password to continue." / "One account for orders, lists and reviews."
Fields: Your name (register only), Email, Password. Errors render as a 12.5px accent-800 line with a
2px accent left border. Primary CTA "Continue" / "Create your account"; 11.5px terms line; `.hr`;
secondary toggle "New to {store}? Create an account" / "Already have an account? Sign in".

Validation: email must contain `@`; password ≥ 6 characters; name required on register.
On success the display name is the entered name, or the email local-part capitalized on sign-in;
the user lands on Home and the header greets them.

### 8. Checkout (`checkout`)

1080px, grid `1fr 300px`. H1 "Checkout", then four numbered `.blueprint` panels (kickers
"1 · Shipping address" … "4 · Review items"):

1. Address — two-column fields: Full name, ZIP code, Street address (spans 2), City, State.
   Prefilled with 1578 Union Street, Apt 92 · Seattle WA 98104.
2. Delivery option — radios with right-aligned prices: Standard — 3 to 5 business days (FREE);
   Express — arrives tomorrow ($9.99); Pick up at a partner locker (FREE).
3. Payment method — radios: Credit card ending in 4417; Store card — 5% back; Gift card balance.
4. Review items — one 56px-thumb row per cart line with name, "Qty {n}" and line subtotal.

**Summary** (sticky `.blueprint`): "Order summary"; Items ({n}) · Shipping · Estimated tax ·
Promotion rows; `.hr`; "Order total" with the amount at 26px accent-800; primary
"Place your order"; an 11.5px terms line.

### 9. Order placed (`confirmation`) and Your orders (`orders`)

**Confirmation** — 820px `.blueprint`: kicker "Order {id}", H1 40px "Order placed, thanks.",
"A confirmation was sent to {email}. Arriving Thursday, August 13.", a `.table` of Order total /
Payment / Delivery / Address, then primary "View your orders" and secondary "Back to the store".

**Your orders** — 1080px. H1 + "{n} order(s) placed in the last 6 months". Each order is a
`.blueprint` card with a `--color-surface` header strip (Order placed · Total · Ship to, and a
right-aligned Order #) above a body showing the status in 19px accent-700
("Arriving tomorrow" / "Arriving Thursday, August 13") and one `86px 1fr 190px` row per item:
placeholder, title (click → product), "Qty {n} · {price}", primary "Buy it again",
secondary "Write a product review" (opens the review form for that item).
Empty state: "No orders yet" + "Start shopping".

## Interactions & Behavior

- **Navigation** is state-driven, no URLs in the prototype. In the target app, map each view to a
  route: `/`, `/s?k=`, `/product/:id`, `/lists`, `/cart`, `/checkout`, `/order/:id`, `/orders`,
  `/signin`, `/product/:id/review`.
- **Search** filters on name + brand + department + description, case-insensitive, combined (AND)
  with department, max price, minimum rating and the fast-delivery flag. Sorting is applied after
  filtering.
- **Add to cart** increments quantity if the product is already in the cart, then navigates to the
  cart. "Buy now" adds and goes straight to checkout. Bundle add adds every checked item at once.
- **Cart** stepper never drops below 1; "Save for later" moves the line to a saved list;
  "Move to cart" moves it back and adds 1.
- **Checkout** recalculates on every option change: shipping $9.99 for Express else free;
  a 5% promotion applies when the subtotal exceeds $150; tax is 8.9% of (subtotal − promotion);
  total = subtotal + shipping + tax − promotion.
- **Place order** creates an order (id pattern `114-3829174`, +613 per order), empties the cart and
  routes to the confirmation screen. Orders persist in session state and appear on Your orders.
- **Reviews** prepend to the product's list; "Helpful" increments that review's counter.
- **Lists** support create, select, add product, remove product, add-all-to-cart, delete.
- **Hover**: product cards raise to `--shadow-md`; sub-nav links tint to `--color-accent-700`;
  buttons follow the design system's built-in accent-ramp hover/active states.
- **Focus**: `:focus-visible { outline: 2px solid var(--color-accent); outline-offset: 2px }` —
  never the browser default.
- **Loading / error states** were not designed. The only error surface is sign-in validation.
  Add skeletons matching the `.ph` placeholder geometry when wiring real data.
- **Responsive**: desktop-only (1280px). If mobile is in scope, it needs a design pass —
  do not naively collapse the three-column product grid.

## State Management

Single store; suggested shape:

```
view            string   which screen is rendered
query           string   search text
category        string   'All' | department
maxPrice        number   20..600
minRating       number   0 | 3 | 4 | 4.5
fastOnly        boolean
sort            'relevance' | 'low' | 'high' | 'rating'
productId       number   product on the detail screen
qty             number   1..5, buy-box quantity
cart            [{ id, qty }]
saved           [productId]
bundleOff       [productId]        unchecked bundle items
lists           [{ id, name, items: [productId] }]
activeList      listId             selected on the lists screen
listTarget      listId             selected in the buy-box module
newListName     string
creatingList    boolean
listFeedback    string
shipping        'standard' | 'express' | 'pickup'
payment         'card' | 'store' | 'gift'
signedIn        boolean
authMode        'signin' | 'register'
user            { name, email }
form            { name, email, pass }
authError       string
orders          [{ id, date, total, status, items: [{ id, qty }] }]
lastOrder       order | null
reviews         { [productId]: [{ stars, title, author, date, text, helpful }] }
draft           { rating, title, text }
```

Derived, never stored: filtered results, cart totals, recommendation sets, bundle totals.

**Data fetching** — the prototype ships a hard-coded 13-item catalogue (see `PRODUCTS` in
`Mercatto.dc.html`: id, name, brand, category, price, list price, rating, review count, fast flag,
description, stock string). In the real app these become API calls: catalogue/search, product detail,
recommendations ("also viewed", "bought together"), cart, lists, orders, reviews, auth. Currency is
formatted with `Intl.NumberFormat('en-US')` at two decimals with a `$` prefix.

## Assets

**There are no real images in this design.** Every image slot is a striped `.ph` placeholder labelled
with what belongs there — "Campaign image", "Main photo", "Angle 2", "Detail", "In use", "Product",
"Item". Product photography must be supplied before implementation; wrap each in the `.duotone`
treatment (or drop the treatment if the target design system prefers untreated photography).

Icons: none are drawn in the prototype. Where the implementation needs them, use **Lucide** at
stroke-width 1.5 (search, cart, star, chevron, trash, plus/minus).

Star ratings are typographic (`★` / `☆`) — replace with a proper accessible rating component
(`role="img"` + label) in production.

Fonts: Barlow and Barlow Condensed, loaded from Google Fonts in the prototype; self-host in
production.

## Accessibility notes

- The prototype uses `div`s with click handlers in several places (cards, breadcrumbs, header
  clusters). Implement these as `<a>` or `<button>` with real semantics and keyboard support.
- Star ratings, the histogram and the placeholder tiles need text alternatives.
- Accent-on-ground meets ~3:1 — fine for chrome and large text; use `--color-accent-700` or darker
  for any body-size accent text.

## Files

- `Mercatto.dc.html` — the full prototype (all nine screens, all state and interaction logic).
  Open it in a browser to click through the flows. The markup uses a streaming-template runtime;
  read it for structure and exact values, do not port the runtime.
- `design-system/styles.css` — the Industry design system: token sheet + component layer.
- `design-system/readme.md` — the design system's own guide (direction, do's and don'ts).

Legal note: this is an original interface built for coursework. It deliberately does not copy any
existing retailer's branding, layout or proprietary UI patterns; keep it that way when implementing.
