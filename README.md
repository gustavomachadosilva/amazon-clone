# Mercatto

A marketplace project (Amazon-like) being built for a college course, structured as a **modular
monolith**: one Spring Boot deployable, one PostgreSQL database, four business modules
(`users`, `catalog`, `orders`, `sellers`) kept isolated by convention so the codebase doesn't
degrade into a ball of mud — and so it *could* be split into microservices later without a
rewrite.

## Stack

- **Backend:** Java 21 + Spring Boot 3 (Maven), packages-by-module.
- **Frontend:** React + Vite + TypeScript + Tailwind CSS.
- **Database:** PostgreSQL, one schema per module (`users`, `catalog`, `orders`).
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
   (`orders.event.OrderPlacedEvent`); listeners live in the module that owns the reaction
   (`catalog.event.OrderPlacedEventListener`) and run via `@TransactionalEventListener(phase =
   AFTER_COMMIT)`, so each module's transaction commits independently — a failure decrementing
   stock never rolls back the order.

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
`users`, `catalog`, and `orders` schemas before Hibernate touches the database.

### Running without Docker

- Backend: `cd backend && mvn spring-boot:run` (needs a local Postgres matching your `.env`).
- Frontend: `cd frontend && npm install && npm run dev`.

## Contrato de Modularidade

Rules every module must follow. Violating these turns the modular monolith into a monolito de
espaguete — CI/review should reject PRs that break them.

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
