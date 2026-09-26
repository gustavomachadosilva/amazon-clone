# Mercatto

[![CI](https://github.com/gustavomachadosilva/amazon-clone/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/gustavomachadosilva/amazon-clone/actions/workflows/ci.yml)

A marketplace project (Amazon-like) being built for a college course, structured as a **modular
monolith**: one Spring Boot deployable, one PostgreSQL database, five business modules
(`users`, `catalog`, `orders`, `cart`, `sellers`) kept isolated by convention so the codebase doesn't
degrade into a ball of mud — and so it *could* be split into microservices later without a
rewrite.

## Stack

- **Backend:** Java 21 + Spring Boot 3 (Maven), packages-by-module.
- **Frontend:** React + Vite + TypeScript + Tailwind CSS.
- **Database:** PostgreSQL, one schema per module (`users`, `catalog`, `orders`, `cart`).
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

### Running without Docker

- Backend: `cd backend && mvn spring-boot:run` (needs a local Postgres matching your `.env`).
- Frontend: `cd frontend && npm install && npm run dev`.

> **Nota (macOS/Homebrew):** se `mvn` resolver para um JDK mais novo que o Java 21 do
> projeto (comum em Mac com `brew install openjdk`, que instala a versão mais recente),
> `mvn test` falha em `ArchitectureBoundaryTest` com
> `java.lang.IllegalArgumentException: Unsupported class file major version 70` — o
> ArchUnit 1.2.1 não lê bytecode de uma JVM mais nova do que ele suporta, não é uma
> violação real de regra de arquitetura. Rode `java -version` para conferir e aponte
> `JAVA_HOME` para o JDK 21 instalado localmente (ex.: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`
> no macOS) antes de rodar `mvn`. O CI (`.github/workflows/ci.yml`) já usa Temurin 21 e
> não é afetado.

### Pagamento simulado (MockPaymentGateway) e refazer pagamento

Sem uma chave Stripe real (`STRIPE_API_KEY` vazia ou `replace-me`, o padrão — o
`docker-compose.yml` nem repassa essa variável ao backend), o checkout usa o
`orders.service.MockPaymentGateway`, que não faz chamada de rede. Por padrão ele **aprova** toda
cobrança; para reproduzir um pagamento recusado localmente, defina `PAYMENT_MOCK_DECLINE` no
`.env` (propriedade `payment.mock.decline`):

| Valor | Comportamento |
|---|---|
| `none` (padrão) | aprova toda cobrança |
| `always` | recusa toda cobrança (o pedido sempre termina `FAILED`) |
| `first-attempt` | recusa a **primeira** cobrança de cada pedido e aprova as seguintes |

Um valor desconhecido impede o backend de subir. Com uma chave Stripe real configurada a
variável é ignorada (com um aviso no log). A memória do `first-attempt` ("este pedido já foi
recusado uma vez") fica em memória, por JVM: reiniciar o backend a zera.

Para ver o fluxo de pedido recusado → novo pagamento → pago:

1. `PAYMENT_MOCK_DECLINE=first-attempt` no `.env` e `docker compose up --build`.
2. Faça um checkout (`POST /api/orders/checkout`): a resposta vem `200` com `"status": "FAILED"`
   e o estoque não é baixado.
3. Refaça o pagamento como o comprador dono do pedido:
   ```bash
   curl -X POST http://localhost:8080/api/orders/{id}/payment \
     -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"paymentMethod": "CARD"}'
   ```
   Agora a cobrança é aprovada: o pedido fica `PAID` e o estoque é baixado (uma única vez).

Contrato de `POST /api/orders/{id}/payment` (body `{ "paymentMethod": "CARD" | "STORE" | "GIFT" }`,
que substitui a forma de pagamento gravada no pedido; o valor cobrado é o `totalAmount` original):

- `200` com o pedido (`OrderResponse`): `PAID` se aprovado; `FAILED` se recusado de novo (pode
  tentar outra vez).
- `400` sem body, sem `paymentMethod` ou com valor inválido.
- `403` se o pedido não é do usuário autenticado.
- `404` se o pedido não existe.
- `409` se o pedido não está `FAILED` (ex.: já `PAID`), se outro retry do mesmo pedido já está em
  andamento (duplo clique — só um deles cobra), ou se algum item não tem mais estoque suficiente
  (nada é cobrado).

### Testes de integração (Testcontainers)

`mvn test` também roda a suíte em `backend/src/test/java/com/mercatto/integration/`, que sobe a
aplicação inteira contra um PostgreSQL 16 real via Testcontainers (fluxo de checkout
pedido pago → evento → estoque decrementado, schemas por módulo, `AFTER_COMMIT`). É preciso ter
o **Docker rodando**; sem ele essas classes são puladas (skipped), não falham. Com Colima em vez
do Docker Desktop, exporte antes:
`DOCKER_HOST=unix://$HOME/.colima/default/docker.sock` e
`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`.

A avaliação da busca (`SearchEvalIT`: consultas de referência, métricas e latência) fica fora do
`mvn test` e roda sozinha com `mvn test -Dtest=SearchEvalIT`; metodologia e baseline em
[`docs/search-recommendation-baseline.md`](docs/search-recommendation-baseline.md).

## Seed de dados (ambiente de desenvolvimento)

Para que `Home.tsx` e `SellerDashboard.tsx` nunca renderizem vazios em um ambiente novo, o backend
semeia dados automaticamente ao subir:

- **Usuários** (`users.service.UserSeeder`): uma lista fixa de contas variadas — 3 sellers (o
  seller âncora `seller.demo@mercatto.dev` / `Seller123!`, dono dos produtos semeados, mais 2
  sellers extras) e 6 buyers, cada um com nome e e-mail próprios.
- **Produtos** (`catalog.service.AmazonProductSeeder`): 500 produtos de uma amostra curada do
  dataset Kaggle "Amazon Products 2023" (arquivo `backend/src/main/resources/seed/amazon-products-sample.csv`),
  distribuídos round-robin entre os sellers semeados.

Esse seed só roda quando `SPRING_PROFILES_ACTIVE=dev` (o padrão em `.env.example` e no
`docker-compose.yml`) — **nunca em produção**. Ele também é idempotente: em toda subida verifica
o que já existe (por e-mail, para usuários; pela contagem de produtos, para o catálogo) e só cria
o que estiver faltando, então rodar `docker compose up` várias vezes não duplica dados nem falha.

Para resetar e repopular do zero (apaga todos os dados do Postgres, incluindo o volume
`db_data`):

```bash
docker compose down -v   # remove o volume do Postgres, apaga TODOS os dados
docker compose up --build
```

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
