# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Two primary audiences, both central to the product:

- **Buyers** — shop a general-merchandise catalogue: browse the home page, search/filter, view
  product detail, maintain lists, manage a cart, check out, track orders, and write reviews.
- **Sellers** — manage their own storefront: view a dashboard of their products and sales metrics
  (seller-side of the `sellers`/`catalog`/`orders` modules).

## Product Purpose

Mercatto is a fictional general-merchandise marketplace (Amazon-like) built as coursework. It
exists to demonstrate a correctly built full-stack application — a Java/Spring Boot modular
monolith (`users`, `catalog`, `orders`, `cart`, `sellers` modules, one Postgres schema each, see
`README.md`'s "Contrato de Modularidade") paired with a React/Vite/TypeScript/Tailwind frontend —
through a complete, working shopping journey end to end.

Success means: the modular-monolith architecture stays correct (no cross-module JPA
relationships, no cross-module transactions, module boundaries respected), and the full journey
works — browse, search, product detail, lists, cart, sign-in, checkout, orders, reviews, and the
seller dashboard. This is an architecture and completeness bar, not a commercial
differentiation bar.

## Positioning

Not a commercial product competing for market position — an original (non-cloned) marketplace
interface and backend built to evaluate architectural and full-stack craft for a college course.
All branding, copy, and catalogue presentation are original and must stay that way; the product
is explicitly not a clone of any existing retailer.

## Operating Context

- Local dev via Docker Compose (`docker compose up --build`): frontend on `:5173`, backend API on
  `:8080`, Postgres on `:5432`.
- `SPRING_PROFILES_ACTIVE=dev` seeds data automatically and idempotently so `Home.tsx` and
  `SellerDashboard.tsx` never render empty in a fresh environment: 500 products from a curated
  sample of the Kaggle "Amazon Products 2023" dataset (`catalog.service.AmazonProductSeeder`,
  reading `backend/src/main/resources/seed/amazon-products-sample.csv`) distributed round-robin
  across a fixed list of seller and buyer accounts (`users.service.UserSeeder`). This seed never
  runs in production.
- GitHub-based workflow: `dev` is the integration/default branch (no `main`); all work lands via
  PR from a feature branch into `dev`, gated by `mvn test` (backend) and `npm run lint` / `npm run
  build` (frontend) before opening a PR.

## Capabilities and Constraints

- Backend: Java 21 + Spring Boot 3 (Maven), packages-by-module (`users`, `catalog`, `orders`,
  `cart`, `sellers`). Cross-module communication only through a module's public `service` interface or
  `ApplicationEvent`s — never direct repository/entity access or a shared transaction (full rules
  in `README.md`).
- Third-party integrations are ports with mock implementations until real integration exists
  (e.g. `orders.service.PaymentGateway` / `MockPaymentGateway`) — no real payments today.
- Database: one PostgreSQL instance, one schema per module (`users`, `catalog`, `orders`, `cart`).
  Cross-module entity references are bare foreign-key ids, never JPA `@ManyToOne`.
- Frontend: React + Vite + TypeScript + Tailwind CSS.

## Evidence on Hand

- `frontend/design-reference/` — a high-fidelity HTML design reference (`Mercatto.dc.html`, read
  for structure/copy/values only, not to be ported as code) covering all nine screens (home,
  search, product, lists, cart, sign-in, checkout, orders, review), plus the "Industry" design
  system/token set (`_ds/`) the reference visuals are built on. Desktop-first (1280px content
  column); responsive behavior was not designed in the reference.
- Catalogue content is real (imported from a curated sample of the Kaggle "Amazon Products 2023"
  dataset) but the branding, storefront copy, and seller/buyer accounts are original fixtures
  created for this coursework, not real business data. No real testimonials, press, or case
  studies exist and none should be fabricated.

## Product Principles

1. Architecture correctness is a product requirement, not a background concern — the Contrato de
   Modularidade constrains what any feature is allowed to touch.
2. The shopping journey must stay complete and coherent end to end (browse through review), not
   just individually impressive in isolated screens.
3. Originality matters: Mercatto must read as its own marketplace, never as a copy of an existing
   retailer's branding or layout.
4. Never render empty for a fresh environment — seeded data and empty-state handling both matter.
5. Integrations are mocked behind ports until real; don't let a mock's limitations leak into
   product-facing claims (e.g. no real checkout/payment claims).
