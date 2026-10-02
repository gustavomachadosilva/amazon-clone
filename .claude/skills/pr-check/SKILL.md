---
name: pr-check
description: Runs the backend and/or frontend tests and reviews the code changed on the current branch before opening a PR. Use when the user runs /pr-check, asks to "run the tests before the PR", "check if it's ready for a PR", or review the changes before opening a pull request.
argument-hint: "[optional base branch, default main]"
allowed-tools: [Bash(git*), Bash(mvn*), Bash(npm*), Bash(gh*), Read, Grep, Glob, Skill, AskUserQuestion]
---

# /pr-check — Test and review before opening a PR

Quality gate to run **before** opening a PR in this repo. It does three things, in this
order, and only recommends opening the PR if all three pass: (1) runs the relevant tests,
(2) reviews the diff for bugs/simplifications, (3) checks the changes against this
project's specific modularity rules (`README.md` → "Modularity Contract"), which a generic
review doesn't know about.

Don't open, push or create the PR on your own — this skill only reports the result and
asks the user how they want to proceed.

## 0. Diff scope

Base branch: `$ARGUMENTS` if provided, otherwise `main` (this repo's main branch, per
`CLAUDE.md`).

```bash
git status
git fetch origin main --quiet 2>/dev/null || true
git merge-base --is-ancestor <base> HEAD 2>/dev/null || true
git diff <base>...HEAD --stat
git diff <base>...HEAD --name-only
```

If there are uncommitted changes (dirty `git status`), include them in the review scope
(also run `git diff` without a range) but warn the user that they haven't been committed
yet.

Classify the changed files:
- **backend**: anything in `backend/`
- **frontend**: anything in `frontend/` (outside `design-reference/`)
- **infra/other**: `docker-compose.yml`, `.env.example`, `README.md`, etc.

If nothing changed relative to the base, say so and stop — there's nothing to test or
review.

## 1. Run the tests

Only run a side's suite if that side changed (or if the user explicitly asked to run
everything).

**Backend** (Maven — includes the ArchUnit tests that enforce the "Modularity Contract"
described in the README):
```bash
cd backend && mvn -q test
```

> **Note:** on a Mac with Homebrew, `mvn` may resolve to a JDK newer than the project's
> Java 21, making `ArchitectureBoundaryTest` fail with `Unsupported class file major
> version 70` — that's not a real architecture rule violation, it's ArchUnit failing to
> read bytecode from a JVM newer than it supports. If that happens, point `JAVA_HOME` at
> the local JDK 21 before running `mvn test` (see the README, "Running locally" section).

**Frontend** — there's currently no test framework configured (`frontend/package.json`
only has `lint`, `build`, `dev`, `preview`). Run what exists as the quality gate:
```bash
cd frontend && npm run lint
cd frontend && npm run build
```
If the frontend changes added non-trivial logic (hooks, data transformation functions,
etc.) and no automated tests cover it, flag that as a gap in the final summary — don't
install a new test runner on your own without the user asking.

If any command fails, **don't treat it as a hard stop**: show the relevant failure (not
the whole log) and go straight to the final summary marking the test as FAILED. It's still
worth continuing to steps 2 and 3 if it's quick, to give a complete picture at once — but
make it clear that the PR should not be opened with broken tests.

## 2. Code review

Delegate the general review (correctness bugs, simplification, reuse, efficiency) to the
existing `code-review` skill, over the same diff/branch:

```
Skill(skill: "code-review")
```

Leave its effort level at its default (it reuses the last one used, or the skill's
default) unless the user asks for a specific level.

## 3. Modularity Contract checklist

Besides the generic review, manually check the changed Java files (`git diff
<base>...HEAD -- 'backend/**/*.java'`) against the 6 rules in the README
("Modularity Contract" section). For each changed/new file in
`backend/src/main/java/com/mercatto/<module>/...`:

1. **No cross-module JPA relationships**: no `@ManyToOne`/`@OneToMany`/`@JoinColumn`
   pointing to another module's entity. Cross-module references must be a plain id
   (`Long`).
2. **No transaction spanning modules**: a `@Transactional` method must not call a
   *mutating* method of another module's `service` inside the same transaction — that
   should be an `ApplicationEvent` (`@TransactionalEventListener(phase =
   AFTER_COMMIT)`).
3. **Only `service`/`event` are public API**: no class in one module may import
   `<othermodule>.repository.*` or another module's entity directly. Implementations
   (`*ServiceImpl`) should be package-private when possible.
4. **External integrations are ports**: no business logic may import a third-party SDK
   (Stripe, payment gateway, etc.) directly — there must be an interface
   (`service.PaymentGateway`-style) with a mock/stub.
5. **One table, one schema**: new entities use `@Table(schema = "<module>")` matching the
   owning module, never `public` or another module's schema.
6. **Boundary = package**: no class was moved into another module's package just to
   "make access easier".

Use `grep`/`Read` on the diff's files to check this — no need to read the whole module,
only what changed and the imports it references. If something violates a rule, cite
file:line.

## 4. Final summary

Present a short, direct summary, in this order:

- **Tests**: backend (passed/failed/not run — reason), frontend lint+build (same),
  relevant coverage gaps.
- **Code review**: main findings from `code-review` (or "no findings").
- **Modularity Contract**: violations found (file:line + rule) or "no violations".
- **Verdict**: ready to open the PR, or a list of what needs fixing first.

Don't open the PR, `git push` or `gh pr create` automatically — ask the user how they
want to proceed (fix now, open anyway, etc.), unless they already explicitly asked to
open the PR when invoking this skill.
