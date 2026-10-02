# Mercatto

[![CI](https://github.com/gustavomachadosilva/amazon-clone/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/gustavomachadosilva/amazon-clone/actions/workflows/ci.yml)

A marketplace project (Amazon-like) being built for a college course, structured as a **modular
monolith**: one Spring Boot deployable, one PostgreSQL database, business modules (`users`,
`catalog`, `orders`, `cart`, `reviews`, `lists`, plus the read-only composition modules `sellers`
and `recommendations`) kept isolated by convention so the codebase doesn't
degrade into a ball of mud — and so it *could* be split into microservices later without a
rewrite.

## Stack

- **Backend:** Java 21 + Spring Boot 3 (Maven), packages-by-module.
- **Frontend:** React + Vite + TypeScript + Tailwind CSS.
- **Database:** PostgreSQL, one schema per module (`users`, `catalog`, `orders`, `cart`, `reviews`,
  `lists`). The composition modules (`sellers`, `recommendations`) own no data and no schema.
- **Infra:** Docker Compose for local dev.

## Architecture: how modules talk to each other

Two channels only, chosen deliberately per use case:

1. **Direct interface calls**, for synchronous reads a request can't proceed without (e.g. Orders
   needs a product's current price at checkout). The caller depends only on the callee module's
   public `service` interface (e.g. `catalog.service.ProductService`), never on its repository or
   entities. See `orders.service.OrderServiceImpl`, which calls `catalog.service.ProductService`.

2. **Spring `ApplicationEvent`s**, for side effects that belong to another module's data and don't
   need to block the triggering request (e.g. decrementing stock after an order is paid). The
   publishing module defines the event as part of its public contract
   (`orders.event.OrderPlacedEvent`); listeners run via `@TransactionalEventListener(phase =
   AFTER_COMMIT)` (e.g. `orders.event.OrderPlacedEventListener`, which decrements stock through
   `catalog.service.ProductService`), so each module's transaction commits independently — a
   failure decrementing stock never rolls back the order. Events also break what would otherwise
   be a dependency cycle: Catalog reads ratings from Reviews, so Reviews never calls Catalog —
   it publishes `reviews.event.ReviewCreatedEvent`, and `catalog.service.ReviewRatingSyncListener`
   refreshes the product's denormalized rating (used to filter/sort search results).

Reads that combine two modules' data live in the module that already depends on the other. The
product page's "Frequently bought together" (#224) needs order history and product details, but
Catalog may not depend on Orders (`ArchitectureBoundaryTest`), so Orders composes it:
`orders.service.BoughtTogetherService` counts co-purchases with a live query over Orders' own tables
(`OrderService.coPurchasedWith`, PAID orders only) and reads product details through
`catalog.service.ProductService`, falling back to Catalog's similar products when there isn't enough
history. A projection table fed by `OrderPlacedEvent` was rejected for now — there is no
cancel/refund event to decrement it, it would need a backfill, and an `AFTER_COMMIT` listener
without an outbox can silently lose increments. Revisit if the query gets slow or orders gain a
cancellation/refund flow; a projection can replace the query behind the same interface.

The Home's "Recommended for you" / "Top rated" shelf (#225) combines four modules — purchases
(Orders), cart lines (Cart), wish lists (Lists) and product details (Catalog) — so none of them can
host it without new cross-module dependencies. It lives in its own **composition module**,
`recommendations`, like `sellers`: no schema, entities, repository or `@Transactional`; it only reads
`catalog.service`, `orders.service`, `cart.service` and `lists.service`, and nothing depends on it
(`ArchitectureBoundaryTest` enforces both). `GET /api/recommendations/home` is **optionally
authenticated** (`config.JwtAuthenticationFilter`): without an `Authorization` header it serves
anonymous visitors (the "Top rated" layer); with one, the token is validated as on any protected
endpoint — an invalid token is a 401, never a silent downgrade to anonymous — and the shelf is
personalized from that user's history. Algorithm and numbers: `docs/search-recommendation-baseline.md`.

This is also why every cross-module reference in an entity is a bare foreign-key id
(`Product.sellerId`, `Order.buyerId`, `OrderItem.productId`) and never a JPA `@ManyToOne` — no
entity ever joins across a schema boundary.

## Running locally

1. Copy the env file and adjust if needed:
   ```bash
   cp .env.example .env
   ```
2. Start everything:
   ```bash
   docker compose up --build
   ```
3. Open:
   - Frontend: http://localhost:5173
   - Backend API: http://localhost:8080/api/catalog/products
   - Postgres: `localhost:5432` (credentials from `.env`)

Postgres runs the SQL in `backend/src/main/resources/db/init/` on first boot, creating the
`users`, `catalog`, `orders`, and `cart` schemas before Hibernate touches the database.
Objects that need the tables to exist (extensions, SQL functions, expression indexes) go in
`backend/src/main/resources/db/post-ddl/` instead: Spring runs those scripts on **every** startup,
right after Hibernate's `ddl-auto` (`spring.sql.init.mode: always` +
`spring.jpa.defer-datasource-initialization: true`), so they must be idempotent
(`CREATE ... IF NOT EXISTS`, `CREATE OR REPLACE`) and never contain `;` inside a function body.

### Product search

`GET /api/catalog/products?query=…` (#220) searches name, brand, category and description with
PostgreSQL full-text search (`english` configuration, `db/post-ddl/catalog-search.sql`): every
term must match, in any field and any order; case and accents are ignored, English plurals and
inflections are stemmed ("laptops" finds "Laptop") and terms of 3+ characters match as prefixes.
A term containing `%` or `_` is literal text. Only when a search finds nothing, words of 4+
letters that aren't in the catalog are corrected to the closest catalog word (1 edit, 2 from 8
letters) and the search runs once more. `sort=relevance` (the default) orders by full-text rank
(name > brand/category > description). Details and measurements in
[`docs/search-recommendation-baseline.md`](docs/search-recommendation-baseline.md).

### Running without Docker

- Backend: `cd backend && mvn spring-boot:run` (needs a local Postgres matching your `.env`).
- Frontend: `cd frontend && npm install && npm run dev`.

> **Note (macOS/Homebrew):** if `mvn` resolves to a JDK newer than the project's Java 21
> (common on a Mac with `brew install openjdk`, which installs the latest version),
> `mvn test` fails in `ArchitectureBoundaryTest` with
> `java.lang.IllegalArgumentException: Unsupported class file major version 70` — ArchUnit
> 1.2.1 can't read bytecode from a JVM newer than it supports; it is not a real architecture
> rule violation. Run `java -version` to check, and point `JAVA_HOME` at the locally installed
> JDK 21 (e.g. `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` on macOS) before running
> `mvn`. CI (`.github/workflows/ci.yml`) already uses Temurin 21 and is not affected.

### Simulated payment (MockPaymentGateway) and payment retry

Without a real Stripe key (`STRIPE_API_KEY` empty or `replace-me`, the default — and
`docker-compose.yml` doesn't even pass that variable to the backend), checkout uses
`orders.service.MockPaymentGateway`, which makes no network calls. By default it **approves**
every charge; to reproduce a declined payment locally, set `PAYMENT_MOCK_DECLINE` in `.env`
(property `payment.mock.decline`):

| Value | Behavior |
|---|---|
| `none` (default) | approves every charge |
| `always` | declines every charge (the order always ends up `FAILED`) |
| `first-attempt` | declines the **first** charge of each order and approves the following ones |

An unknown value prevents the backend from starting. With a real Stripe key configured, the
variable is ignored (with a warning in the log). The `first-attempt` memory ("this order has
already been declined once") is kept in memory, per JVM: restarting the backend resets it.

To see the declined order → new payment → paid flow:

1. Set `PAYMENT_MOCK_DECLINE=first-attempt` in `.env` and run `docker compose up --build`.
2. Check out (`POST /api/orders/checkout`): the response is `200` with `"status": "FAILED"`
   and stock is not decremented.
3. Retry the payment as the buyer who owns the order:
   ```bash
   curl -X POST http://localhost:8080/api/orders/{id}/payment \
     -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"paymentMethod": "CARD"}'
   ```
   This time the charge is approved: the order becomes `PAID` and stock is decremented (exactly
   once).

Contract of `POST /api/orders/{id}/payment` (body `{ "paymentMethod": "CARD" | "STORE" | "GIFT" }`,
which replaces the payment method stored on the order; the amount charged is the original
`totalAmount`):

- `200` with the order (`OrderResponse`): `PAID` if approved; `FAILED` if declined again (you can
  try again).
- `400` with no body, no `paymentMethod`, or an invalid value.
- `403` if the order doesn't belong to the authenticated user.
- `404` if the order doesn't exist.
- `409` if the order isn't `FAILED` (e.g. already `PAID`), if another retry of the same order is
  already in progress (double click — only one of them charges), or if some item no longer has
  enough stock (nothing is charged).

### Integration tests (Testcontainers)

`mvn test` also runs the suite in `backend/src/test/java/com/mercatto/integration/`, which boots
the whole application against a real PostgreSQL 16 via Testcontainers (checkout flow: paid order
→ event → stock decremented, per-module schemas, `AFTER_COMMIT`). **Docker must be running**;
without it these classes are skipped, not failed. With Colima instead of Docker Desktop, export
first:
`DOCKER_HOST=unix://$HOME/.colima/default/docker.sock` and
`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`.

The search evaluation (`SearchEvalIT`: reference queries, metrics and latency) is excluded from
`mvn test` and runs on its own with `mvn test -Dtest=SearchEvalIT`; methodology and baseline in
[`docs/search-recommendation-baseline.md`](docs/search-recommendation-baseline.md).

## Data seed (development environment)

So that `Home.tsx` and `SellerDashboard.tsx` never render empty in a fresh environment, the backend
seeds data automatically on startup:

- **Users** (`users.service.UserSeeder`): a fixed list of varied accounts — 3 sellers (the anchor
  seller `seller.demo@mercatto.dev` / `Seller123!`, owner of the seeded products, plus 2 extra
  sellers) and 6 buyers, each with their own name and email.
- **Products** (`catalog.service.AmazonProductSeeder`): 500 products from a curated sample of the
  Kaggle "Amazon Products 2023" dataset (file `backend/src/main/resources/seed/amazon-products-sample.csv`),
  distributed round-robin among the seeded sellers.

This seed only runs when `SPRING_PROFILES_ACTIVE=dev` (the default in `.env.example` and in
`docker-compose.yml`) — **never in production**. It is also idempotent: on every startup it checks
what already exists (by email for users; by product count for the catalog) and only creates
what's missing, so running `docker compose up` repeatedly neither duplicates data nor fails.

To reset and repopulate from scratch (deletes all Postgres data, including the `db_data`
volume):

```bash
docker compose down -v   # removes the Postgres volume, deletes ALL data
docker compose up --build
```

## Modularity Contract

Rules every module must follow. Violating these turns the modular monolith into a spaghetti
monolith — CI/review should reject PRs that break them.

1. **No cross-module JPA relationships.** A module may only reference another module's aggregate
   by its id (a plain `Long` column). Never `@ManyToOne`/`@OneToMany` across module packages —
   that's how schemas end up implicitly coupled and unsplittable later.

2. **No transaction spans two modules.** A single `@Transactional` method must only write to its
   own module's tables. Cross-module side effects go through `ApplicationEvent`s
   (`@TransactionalEventListener(phase = AFTER_COMMIT)`), not direct calls inside the same
   transaction. If you find yourself calling another module's service to *mutate* its state from
   inside your own `@Transactional` method, stop — publish an event instead.

3. **Only `service` (and published `event`) packages are public API.** `repository`, and entities'
   setters/persistence details are implementation. Other modules must depend on interfaces in
   `<module>.service`, never reach into `<module>.repository` or query another module's entity
   directly. Package-private implementation classes (see `ProductServiceImpl`,
   `UserServiceImpl`) enforce this at compile time within a module.

4. **New third-party integrations are ports, not direct calls.** Follow the
   `orders.service.PaymentGateway` / `MockPaymentGateway` pattern: define an interface owned by
   the module that needs the capability, inject it, and provide a mock or stub implementation
   until the real integration (Stripe, a shipping calculator, JWT/OAuth2 auth) is ready. Business
   logic must never import a vendor SDK directly.

5. **One module, one schema.** Tables for module `X` live in Postgres schema `X`
   (`@Table(schema = "x")`). Don't create tables in `public` or borrow another module's schema.

6. **Module boundaries are packages, not just folders.** `com.mercatto.<module>.*` is the unit of
   ownership. If a class needs something from another module, import its `service` interface —
   don't move the class into the other module's package to "make it easier."
