---
name: cbre-demo-prd-to-pr
description: >
  Run one stage of the Facilities Desk PRD-to-PR pipeline: prd (turn a Linear
  issue into a structured PRD), plan (split a PRD into story sub-issues with
  implementation plans), dev (implement a story and open a pull request), or
  babysit (drive open story pull requests to green by fixing failed checks and
  review threads). Use when a Factory automation or Linear delegation names one
  of these stages for the CBRE Demo project.
---

# CBRE Demo: PRD to PR

This skill holds every stage of the pipeline described in
`docs/pipeline.md`. Each caller names exactly one stage. Read this file, then
read and follow only that stage's file:

| Stage | File | Caller |
| --- | --- | --- |
| `prd` | `stages/prd.md` | Linear delegation (issue assigned to Factory) |
| `plan` | `stages/plan.md` | Planner automation, every 5 minutes |
| `dev` | `stages/dev.md` | Dev automation, chained after Planner |
| `babysit` | `stages/babysit.md` | PR Babysitter automation, every 10 minutes and after Dev |

If no stage is named, stop and say which stages exist. Never run more than
one stage in a session.

## Fixed context

| Item | Value |
| --- | --- |
| Repository | `factory-saif/cbre-prd-to-pr-demo`, default branch `main` |
| Local checkout | Repository root containing this skill |
| Build and test | `mvn --batch-mode verify` (Java 21, Maven) |
| Linear team | SE Demo, key `SED` |
| Linear project | `CBRE Demo` |
| Linear states | `Todo`, `In Progress`, `In Review`, `Done` |
| Story branch | `story/<KEY>`, for example `story/SED-142` |
| PR title | `[<KEY>] <story title>` |

Use the Linear connector tools for Linear and the `gh` CLI for GitHub. Never
mention `@droid` in a GitHub comment or reply: that starts the Droid Tag
workflow.

## Workspace

Runs can overlap (Planner, Dev, and PR Babysitter fire on their own
schedules). Each automation run works in a fresh clone made for that run and
deletes it at the end. Never do git work in a checkout that another session
might use.

## Linear signals

Only these labels and comment headings move work between stages. Each stage
checks them before acting, so a rerun never repeats work.

| Signal | Kind | Written by | Read by |
| --- | --- | --- | --- |
| `## Structured PRD` | Comment on the parent issue | prd | plan |
| `prd-ready` | Label on the parent issue | prd | plan |
| `## Stories` | Comment on the parent issue | plan | plan (idempotency) |
| `## Implementation Plan` | First heading of each story description | plan | dev, babysit |
| `ready-for-dev` | Label on a story | plan | dev |
| `## Pull Request` | Comment on a story | dev | dev (idempotency), babysit |
| `pr-open` | Label on a story | dev | dev (idempotency) |
| `## Seeded Defect` | Comment on a story | dev (demo only) | presenter |
| `## Fix Loop #<n>` | Comment on a story | babysit | babysit (attempt count, lock) |
| `fixing` | Label on a story | babysit | babysit |
| `ready-to-merge` | Label on a story | babysit | human |
| `needs-human` | Label on a story | babysit | human |
| `demo-seed-defect` | Label on a parent issue or story | presenter | plan, dev |

If a label is missing from the SED team, stop and report it. Do not create
labels and do not substitute other labels.

## Guardrails for every stage

- Nothing merges automatically. Never merge, close, or force-push a pull
  request. Never push to `main`.
- Never skip, disable, delete, or weaken a test or a check to make it pass.
- Stay inside the story's implementation plan. If the plan is wrong, say so
  in a Linear comment instead of widening the change.
- Only touch issues in the `CBRE Demo` project and pull requests whose head
  branch starts with `story/`.
- Never commit secrets, tokens, or environment values.
- When something is ambiguous or blocked, comment on the Linear issue with the
  exact question or error and stop work on that item. Do not guess.
- End every run with a one-line summary: items processed, items skipped and
  why, and links to anything created.
