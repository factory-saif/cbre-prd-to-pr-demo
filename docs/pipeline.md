# PRD-to-PR pipeline

This repository demonstrates Factory driving a request from a Linear issue to a
pull request that has passed code review and end-to-end QA, with no human
steps until the final merge.

```mermaid
flowchart TD
    H[Human creates Linear issue<br/>and assigns it to Factory] --> P1
    P1["1 · PRD<br/>Linear delegation"] -->|"Structured PRD comment"| P2
    P2["2 · Planner<br/>scheduled, every 5 min"] -->|stories labeled Story| P3
    P3["3 · Dev<br/>chained after Planner"] -->|PR on story/KEY| GH
    subgraph GH [GitHub, on every PR open and push]
        CR["4 · Code Review<br/>install-code-review"]
        QA["5 · E2E QA<br/>install-qa, required check"]
    end
    GH --> B["6 · PR Babysitter<br/>scheduled every 10 min, also chained after Dev"]
    B -->|checks red or review threads open| F[Fix on same branch, push]
    F --> GH
    B -->|all green, no open threads| R[Label Release<br/>update Linear]
    B -->|3 failed attempts| N[Label route:human]
    R --> M[Human merges]
```

## Stages

| # | Stage | Runs as | Trigger | Reads | Writes |
| --- | --- | --- | --- | --- | --- |
| 1 | PRD | Linear delegation (issue assigned to Factory) | Assignment or `@Factory` mention | Issue title, description, comments | `## Structured PRD` comment and, when its tools allow, `PRD` label; or `## PRD Questions` when blocked |
| 2 | Planner | Scheduled automation | Every 5 minutes | Issues with a trusted `## Structured PRD` comment and no stories | Story sub-issues with `## Implementation Plan`, `Story` label, `PRD` label when missing |
| 3 | Dev | Chained automation | After each Planner run | Stories labeled `Story` without `Implementation` | Branch `story/<KEY>`, tests, pull request, `## Pull Request` comment, `Implementation` label |
| 4 | Code Review | GitHub Action | PR opened, reopened, and every push | Pull request diff | Inline review comments |
| 5 | E2E QA | GitHub Action, required check | PR opened, reopened, and every push | Pull request diff, running app | One sticky QA report comment, evidence artifacts |
| 6 | PR Babysitter | Scheduled automation, also chained after Dev | Every 10 minutes | Open `story/*` PRs, checks, review threads | Fix commits, thread replies, `## Fix Loop #n` comment, final labels |

Every stage loads one skill, `prd-to-pr-demo`, and runs only its own
section. The stage logic lives in this repository, versioned with the code.

## Why a PR Babysitter, not a return to Dev

- Automation chains cannot loop, so the fix cycle restarts from GitHub
  events instead: a fix push reruns Code Review and QA automatically.
- One pull request keeps its review threads, QA report, and commit history.
- Fixes see only the failing check and the open threads, so they stay small.
- The Dev stage stays simple: one story, one pull request.
- The built-in PR Babysitter only drives failing CI to green. Review comments
  do not fail a check, so this babysitter handles both.

## Babysitter rules on each run

| PR state | Action |
| --- | --- |
| Checks still running | Skip until the next run |
| QA or tests red, or review threads unresolved | Fix on the same branch, reply to and resolve each addressed thread, push, add `bug`, post `## Fix Loop #n` in Linear |
| All checks green and no unresolved threads | Add `Release`, remove `bug`, move the Linear story forward |
| Three fix attempts without going green | Add `route:human`, stop, explain in Linear |

## Linear labels

The pipeline reuses labels that already exist in the workspace, so each label
has the meaning below only on issues in the `Demo` project.

| Label | Set by | Meaning |
| --- | --- | --- |
| `PRD` | PRD, or Planner when PRD cannot set labels | Structured PRD posted |
| `Story` | Planner | Story has an implementation plan |
| `Implementation` | Dev | Pull request exists for the story |
| `bug` | PR Babysitter | A fix is in progress |
| `Release` | PR Babysitter | Review and QA are clean |
| `route:human` | PR Babysitter | Automatic fixing stopped |
| `Experiment` | Presenter (opt-in) | Dev ships one known defect first so the fix loop is visible |

## Guardrails

- Nothing merges automatically. A human merges the green pull request.
- Tests are never skipped, disabled, or deleted to make a check pass.
- Every stage checks Linear and GitHub state before acting, so reruns are safe.
- Fixes stay within the story's implementation plan.
- `Experiment` is visible on the Linear story and recorded in a Linear
  comment. It is never used outside a demo.

## Setup status

| Component | Status |
| --- | --- |
| Demo application and tests | Done |
| Code Review workflow (`install-code-review`) | Done |
| E2E QA workflow and skills (`install-qa`) | Done |
| Build, unit test, and container workflow (`ci.yml`) | Done |
| `prd-to-pr-demo` skill | Done |
| Linear project `Demo` and sample issue | Done |
| Linear labels (existing labels, mapped above) | Done |
| Linear delegation to Factory | Pending: verify in Factory Linear settings |
| Planner, Dev, and PR Babysitter automations | Done, on `saif-poc-sandbox` |
| Repository secrets `FACTORY_API_KEY`, `QA_EVIDENCE_TOKEN` | Done |
