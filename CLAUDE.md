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

## Fluxo de trabalho no GitHub

- Antes de começar qualquer implementação, atualize a `main` local (`git checkout main && git
  pull`) e crie uma branch nova a partir dela para o trabalho.
- Nunca faça push direto para a `main`. Toda mudança entra por Pull Request da branch de trabalho
  para a `main`.
- Antes de abrir a PR, rode os testes e verificações do código afetado para garantir que nada
  quebrou: `mvn test` em `/backend` para mudanças de backend, e `npm run lint` / `npm run build`
  em `/frontend` para mudanças de frontend. O skill `/pr-check` automatiza isso (testes +
  revisão de código + checagem do Contrato de Modularidade). Se o usuário pedir para abrir a PR
  sem ter rodado `/pr-check` na conversa, recomende rodar antes — mas a decisão de rodar ou ir
  direto para a PR é dele.
