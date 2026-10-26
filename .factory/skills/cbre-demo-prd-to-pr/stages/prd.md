# Stage: prd

Turn one Linear issue into a structured PRD. This stage runs when a person
assigns a `CBRE Demo` issue to Factory or mentions `@Factory` on it. It never
changes the repository: no branches, commits, or pull requests.

## Get the repository

Delegated sessions run on a shared computer that has no checkout of this
repository. If the current directory is not a checkout of it, clone a
read-only copy into a new private directory and read the skill from there:

```bash
WS=$(mktemp -d) && echo "$WS" && git clone --depth 1 https://github.com/factory-saif/cbre-prd-to-pr-demo.git "$WS/repo"
```

Shell variables do not carry over between commands, so note the absolute
path the command prints and use that literal path afterwards. Never reuse an
existing directory. Delete the directory before you stop, including when the
clone fails or you stop early.

## Steps

1. Read the issue: title, description, labels, project, and every comment.
2. Stop if the issue is not in the `CBRE Demo` project.
3. Stop if a trusted `## Structured PRD` comment exists (see the signal rule
   in `SKILL.md`), and reply that the PRD already exists. Add `PRD` first if
   it is missing and your Linear tools can change labels.
4. Read `README.md` and `docs/pipeline.md` so the PRD uses the real domain
   (work orders, priorities, response targets, status workflow, API, web UI).
5. If a missing product decision blocks building: when a trusted
   `## PRD Questions` comment already exists and no one has answered it since,
   reply that the questions are still open and stop. Otherwise post one
   comment whose first line is exactly `## PRD Questions`, list each question,
   and stop. Do not add `PRD`. After someone answers, delegating the issue
   again writes the PRD.
6. Otherwise, write the PRD as one comment whose first line is exactly
   `## Structured PRD`, with these sections:
   - **Problem**: who is affected and what is hard today.
   - **Users**: facilities coordinator, technician, requester, as relevant.
   - **Outcome**: what changes for the user and why it matters.
   - **Scope**: the behavior to build, split into API and web UI where both
     apply.
   - **Acceptance criteria**: numbered, each one verifiable through the API or
     the browser. Include error cases (`400`, `404`, `409`) where they apply.
   - **Non-goals**: what this change will not do.
   - **Risks and open questions**: non-blocking items only, each with the
     default you chose so the Planner can rely on it.
7. Add the `PRD` label and keep every existing label, including `Experiment`.
   If your Linear tools cannot change labels, skip this step: the Planner adds
   `PRD` when it picks up the PRD.
8. Move the issue to `Todo` if it is in `Backlog` or `Triage` and your tools
   allow it.

## Posting without comment tools

If you have no tool that posts a Linear comment, Linear posts your final reply
as the comment. Make that reply start with the line `## Structured PRD` (or
`## PRD Questions`) and contain only that content, then end it with the
one-line summary that `SKILL.md` requires as its last line.

## Rules

- Derive acceptance criteria from the issue. Do not invent product decisions.
- Keep the PRD short enough to read in two minutes.
