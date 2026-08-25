# Mercatto

A marketplace project (Amazon-like) being built for a college course. Structured as a modular
monolith: Java 21 + Spring Boot backend (`/backend`, packages-by-module: `users`, `catalog`,
`orders`, `sellers`), React + Vite + TypeScript + Tailwind frontend (`/frontend`), one PostgreSQL
database with one schema per module. See `README.md` for the module-communication rules
("Contrato de Modularidade") — cross-module calls go through a module's public `service`
interface or `ApplicationEvent`s only, never direct repository/entity access or a shared
transaction.

## What exists

- `/backend`, `/frontend`, `docker-compose.yml`, `.env.example` — the application skeleton.
- `frontend/design-reference/` — design material: an HTML prototype (`Mercatto.dc.html`, a
  proprietary streaming-template runtime — do not port it, read it for structure/copy/values
  only) and the "Industry" design system/token set (`_ds/`). High-fidelity reference for all nine
  screens (see its `README.md`); not production code to copy directly — recreate the screens as
  React components using the frontend's own stack, mapping these tokens onto Tailwind.
