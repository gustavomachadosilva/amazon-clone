---
name: resolve-card
description: Look up a card (issue) on this repo's GitHub Project board by number, show its full details, and ask the user how to proceed. Use when the user runs /resolve-card <NUMBER> or asks to look up/start a project card or issue by number.
argument-hint: <CARD NUMBER>
allowed-tools: [Bash(gh repo view*), Bash(gh project list*), Bash(gh project item-list*), Bash(gh issue view*), Bash(gh pr create*), Bash(git status*), Bash(git checkout*), Bash(git pull*), Bash(git push*), Bash(git branch*), AskUserQuestion, Agent, Skill, EnterWorktree, ExitWorktree]
---

# /resolve-card — Look up a card on GitHub Projects

Looks up a card by number in the GitHub Project associated with this repository, presents
its information to the user and asks how to proceed. Don't assume an action (implement,
move status, etc.) without the user confirming.

## Argument

Card number: `$ARGUMENTS`

If `$ARGUMENTS` is empty or not a number, ask the user for the card number before
continuing.

## Steps

1. **Find owner/repo:**
   ```bash
   gh repo view --json owner,name --jq '.owner.login + "/" + .name'
   ```

2. **Find the repository's Project.** List the owner's projects and pick the one that
   matches this repository (title equal to or containing the repo name,
   case-insensitive):
   ```bash
   gh project list --owner <owner> --format json
   ```
   Keep the `number` of the project found. If there's more than one plausible candidate,
   ask the user which one to use instead of guessing.

3. **Find the card's item by issue number** (the `content.number` field in the project
   JSON is the issue/card number):
   ```bash
   gh project item-list <PROJECT_NUMBER> --owner <owner> --format json --limit 200 \
     --jq '.items[] | select(.content.number == <NUMBER>)'
   ```

4. **If it isn't found in the project**, try the issue directly in the repository (it may
   exist but not be on the board yet):
   ```bash
   gh issue view <NUMBER> --repo <owner>/<repo> --json number,title,state,body,labels,assignees,url
   ```
   In that case, explicitly tell the user that the card isn't on the project board.

5. **If it isn't found in either**, clearly state that card/issue `<NUMBER>` doesn't
   exist and stop — don't make up data.

## Presentation

Once the card is found, show it in an organized way (without inventing fields that didn't
come from the API):

- Title and number (with the URL link)
- Status, Priority, Size, Milestone (when they exist in the project)
- Labels
- Assignees
- Issue body/description (usually contains context and acceptance criteria)
- Dependencies, if mentioned in the body (e.g. "Depends on: ...")

## After presenting

Ask the user how they want to proceed — don't presume the next action. Offer plausible
alternatives (e.g. start implementing now, create a branch, just wanted to see the
information, move the card's status) but leave the decision explicitly with the user
before taking any action in the code or on the board.

If the user confirms they want to implement the card now, follow the orchestrated flow
below.

## Orchestrated execution (planning → implementation)

When the user confirms they want to implement the card, don't implement it yourself
directly in this skill. Run it in two sequential stages, each delegated to a different
agent via `Agent`. Each agent starts with no memory of this conversation, so every prompt
must be self-contained: include the card's number/title/URL, full body and acceptance
criteria, labels and dependencies, the relevant part of the "Modularity Contract"
(`README.md`) and the Git workflow from `CLAUDE.md`.

The card is always resolved in its own worktree for the task — never directly in the
session's main working directory. This isolates the card's changes from any other work in
progress and avoids conflicts with the `dev` branch.

### Stage 0 — Prepare the worktree

Run `git status` before anything else — if there are uncommitted changes from another
task in the main directory, warn the user and don't discard anything.

The worktree must be created on top of an **already updated** local `dev`, so sync `dev`
first:

```bash
git status
git checkout dev
git pull
```

Only then create the task's worktree with the `EnterWorktree` tool (don't use
`git worktree add` manually — the tool already creates the worktree in
`.claude/worktrees/` and switches the session's working directory there):

```
EnterWorktree(name: "card-<NUMBER>-<short-title-slug>")
```

Once it's created, check the generated branch name (`git branch --show-current`). The
branch naming pattern already used in the repo is `feature/<number>-<slug>` (e.g.
`feature/37-login-endpoint`) — lowercase, words separated by hyphens. If the branch
created by `EnterWorktree` doesn't follow this pattern, rename it before proceeding:

```bash
git branch -m feature/<NUMBER>-<short-title-slug>
```

The following stages (planning and implementation) run with the working directory
already inside that worktree.

### Stage 1 — Planning (`Plan` agent)

Call `Agent` with `subagent_type: "Plan"` and `run_in_background: false` (the next stage
depends on the result). In the prompt, give the agent:

- All the card's data (title, body, acceptance criteria, labels, dependencies, URL).
- The backend/frontend module(s) likely affected.
- The "Modularity Contract" rules (`README.md`) that apply.
- Ask for a concrete, step-by-step plan: files to create/change, order of changes, and
  testing strategy (ArchUnit/unit tests on the backend, lint/build on the frontend) —
  only the plan, without writing code.

When the plan comes back, present the user with an objective summary (main steps, files
involved) and ask whether to proceed to implementation with this plan or whether
something needs adjusting first. Don't skip this confirmation — it's the only check
before code is written.

### Stage 2 — Implementation (an agent different from planning)

After the user approves the plan, call `Agent` again with a `subagent_type` different
from the one used in Stage 1 (e.g. `general-purpose`), passing the approved plan and the
card's data in the prompt — again self-contained, this agent hasn't seen the
conversation or the plan either. Instruct it to:

- Implement following the plan and the Modularity Contract.
- Run `mvn test` (backend) and/or `npm run lint && npm run build` (frontend), depending
  on what changed — the same gate described in `CLAUDE.md`.
- Report what was done, what passed/failed in the tests, and any deviation from the
  original plan with the reason.

Run this stage in the foreground (`run_in_background: false`) when the user is waiting
for the result in this conversation. For large cards, you may offer to run it in the
background and notify the user when it finishes — but confirm that preference with them
first, don't decide on your own.

### After implementation

Summarize what was done and ask the user how they want to proceed (`AskUserQuestion`),
offering **open the PR directly** as the default/recommended option — this is now this
skill's normal behavior, there's no need to confirm it ahead of time every time. Mention
`/pr-check` (tests + code review + Modularity Contract checklist) as a suggestion for
anyone who wants that extra check first, but make it clear it isn't mandatory.

- **If the user chooses to open the PR directly**: push the branch created in Stage 0
  and run `gh pr create`:
  ```bash
  git push -u origin feature/<NUMBER>-<slug>
  gh pr create --base dev --title "<card title>" \
    --body "Closes #<NUMBER>

  <short summary of what was implemented, based on the Stage 1 plan>"
  ```
  Report the created PR's URL at the end.

- **If the user chooses to run `/pr-check` first**: call `Skill(skill: "pr-check")` and,
  with the result in hand, ask again how to proceed (open the PR, fix something first,
  etc.).

### After opening the PR — what to do with the worktree

The PR has already been opened from the Stage 0 worktree. Don't decide the worktree's
fate on your own — present the options to the user (`AskUserQuestion`) and only then act:

- **Keep the worktree**: useful if the user will keep working on this card (e.g. changes
  requested in the PR review). Use `ExitWorktree(action: "keep")` if leaving it for their
  session, or simply do nothing and keep working in it.
- **Remove the worktree now**: since the work is committed and pushed to the remote PR,
  it's safe to free the disk space. Use `ExitWorktree(action: "remove")`. If there are
  any uncommitted changes or commits outside the PR branch, the tool refuses the removal
  unless `discard_changes: true` is passed — in that case, confirm with the user before
  forcing it, so no work is discarded by accident.
