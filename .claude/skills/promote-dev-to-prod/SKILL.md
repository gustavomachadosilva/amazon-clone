---
name: promote-dev-to-prod
description: Updates the local dev branch, runs the quality gate (pr-check: tests + review + Modularity Contract) over the work being promoted and opens the PR from dev to prod. Use when the user runs /promote-dev-to-prod, asks to "promote dev to prod", "push to prod", "release to production" or "open the dev → prod promotion PR".
allowed-tools: [Bash(git*), Bash(gh*), Read, Skill, AskUserQuestion]
---

# /promote-dev-to-prod — Promote dev to prod

This skill exists to guarantee one thing: **no code reaches `prod` without having been
tested and reviewed**. `dev` is the development branch (the repository's default branch);
`prod` is protected and only accepts merges via Pull Request (see `CLAUDE.md` → "GitHub
workflow"). This skill automates the path: update local `dev` → run `/pr-check` over what
will be promoted → open the `dev` → `prod` PR.

If the quality gate fails at any point, **don't open the PR**. Stop, report what failed
and ask the user how to proceed.

## 0. Local repository state

```bash
git status
```

If there are uncommitted changes, **don't discard anything**: warn the user and ask
whether they want to commit, stash (`git stash -u`) or abort before switching branches.
Only proceed with a clean working tree (or with the user's go-ahead).

## 1. Update local dev

```bash
git fetch origin --quiet
git checkout dev
git pull origin dev
```

If the `checkout` or the `pull` fails (`dev` branch doesn't exist locally, divergence,
conflict), stop and report the error — don't try to fix it with `reset --hard` or `clean`
on your own.

## 2. Confirm there's something to promote

```bash
git fetch origin prod --quiet 2>/dev/null || true
git log origin/prod..dev --oneline
```

If there are no commits in `dev` that aren't in `prod` yet, tell the user and stop —
there's nothing to promote.

Also check whether there's already an open PR from `dev` to `prod`, to avoid duplicating
it:

```bash
gh pr list --base prod --head dev --state open
```

If one already exists, show the user the link and ask whether they want to reuse it (skip
to step 5) or proceed anyway.

## 3. Run the quality gate (pr-check)

Delegate to the existing `pr-check` skill, comparing `dev` against `prod` (that's the
diff that actually goes to production):

```
Skill(skill: "pr-check", args: "prod")
```

This covers, in this order: tests (`mvn test` on the backend, `lint`+`build` on the
frontend), code review (bugs/simplification/reuse/efficiency) and the Modularity Contract
checklist.

**Important:** `pr-check` doesn't switch branches — it runs the tests over the current
working tree, which is already `dev` (checked out in step 1). The `"prod"` argument is
only the base branch used to compute the diff (`prod...dev`), so that the code review and
the modularity checklist look at exactly what will be promoted, instead of `pr-check`'s
default (which would compare against `main`). The tests themselves always validate the
actual `dev` code, never `prod`'s.

## 4. Evaluate the result

- **Tests failed** → stop. Don't open the PR. Report the failure and ask whether the user
  wants to fix it now or cancel the promotion.
- **Code review found a blocking bug/finding**, or a **Modularity Contract violation** →
  stop. Report the findings with file:line and ask how to proceed. Don't open the PR
  "anyway" without the user's explicit confirmation — stating that they know the risks
  and want to proceed regardless.
- **Everything passed** (or the user explicitly confirmed they want to proceed despite
  non-blocking caveats) → go to step 5.

## 5. Open the dev to prod PR

```bash
gh pr create --base prod --head dev --title "Promote dev to prod" --body "$(cat <<'EOF'
## Summary
<list the relevant commits/changes from `git log origin/prod..dev --oneline`>

## Quality gate (pr-check)
- Tests: <passed/failed>
- Code review: <summary or "no findings">
- Modularity Contract: <no violations / list>

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Fill the body with the actual result from steps 3/4, not a generic placeholder.

## 6. Close the loop

Return to the user the link to the created (or reused) PR and a one-line summary of the
quality gate's verdict. Don't merge the PR — the final promotion to `prod` is the user's
decision (and the GitHub review process's).
