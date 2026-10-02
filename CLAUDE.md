# Mercatto

A marketplace project (Amazon-like) being built for a college course. Structured as a modular
monolith: Java 21 + Spring Boot backend (`/backend`, packages-by-module: `users`, `catalog`,
`orders`, `cart`, `sellers`), React + Vite + TypeScript + Tailwind frontend (`/frontend`), one PostgreSQL
database with one schema per module. See `README.md` for the module-communication rules
("Modularity Contract") — cross-module calls go through a module's public `service`
interface or `ApplicationEvent`s only, never direct repository/entity access or a shared
transaction.

## What exists

- `/backend`, `/frontend`, `docker-compose.yml`, `.env.example` — the application skeleton.
- `frontend/design-reference/` — design material: an HTML prototype (`Mercatto.dc.html`, a
  proprietary streaming-template runtime — do not port it, read it for structure/copy/values
  only) and the "Industry" design system/token set (`_ds/`). High-fidelity reference for all nine
  screens (see its `README.md`); not production code to copy directly — recreate the screens as
  React components using the frontend's own stack, mapping these tokens onto Tailwind.

## GitHub workflow

- Before starting any implementation, update the local `dev` (`git checkout dev && git pull`)
  and create a new branch from it for the work. `dev` is the integration branch and the
  repository's default branch — there is no `main`.
- Never push directly to `dev`. Every change goes in through a Pull Request from the work branch
  into `dev`.
- Before opening the PR, run the tests and checks for the affected code to make sure nothing
  broke: `mvn test` in `/backend` for backend changes, and `npm run lint` / `npm run build` in
  `/frontend` for frontend changes. The `/pr-check` skill automates this (tests + code review +
  Modularity Contract check). If the user asks to open the PR without having run `/pr-check` in
  the conversation, recommend running it first — but whether to run it or go straight to the PR
  is their call.
