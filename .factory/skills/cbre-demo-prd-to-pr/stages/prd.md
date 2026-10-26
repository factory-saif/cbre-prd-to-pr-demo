# Stage: prd

Turn one Linear issue into a structured PRD. This stage runs when a person
assigns a `CBRE Demo` issue to Factory or mentions `@Factory` on it. It never
changes the repository: no branches, commits, or pull requests.

Delegated sessions run on a shared computer that does not have this
repository. If the current directory is not a checkout of it, make a
read-only clone over public HTTPS and read the skill from there:

```bash
git clone --depth 1 https://github.com/factory-saif/cbre-prd-to-pr-demo.git /tmp/cbre-prd-to-pr-demo
```

## Steps

1. Read the issue: title, description, labels, project, and every comment.
2. Stop if the issue is not in the `CBRE Demo` project.
3. Stop if a comment already starts with `## Structured PRD`. Make sure the
   issue carries `PRD`, then reply that the PRD already exists.
4. Read `README.md` and `docs/pipeline.md` so the PRD uses the real domain
   (work orders, priorities, response targets, status workflow, API, web UI).
5. Write the PRD as one comment whose first line is exactly
   `## Structured PRD`, with these sections:
   - **Problem**: who is affected and what is hard today.
   - **Users**: facilities coordinator, technician, requester, as relevant.
   - **Outcome**: what changes for the user and why it matters.
   - **Scope**: the behavior to build, split into API and web UI where both
     apply.
   - **Acceptance criteria**: numbered, each one verifiable through the API or
     the browser. Include error cases (`400`, `404`, `409`) where they apply.
   - **Non-goals**: what this change will not do.
   - **Risks and open questions**: anything the issue leaves undecided.
6. Add the `PRD` label. Keep every existing label, including
   `Experiment`. If your Linear tools cannot change labels, skip this step:
   the Planner adds `PRD` to any issue with a `## Structured PRD` comment.
7. Move the issue to `Todo` if it is in `Backlog` or `Triage` and your
   tools allow it.

If you have no tool that posts a Linear comment, make your final reply the
PRD itself, starting with the line `## Structured PRD`. Linear posts the
final reply as a comment on the issue.

## Rules

- Derive acceptance criteria from the issue. Do not invent product decisions.
  When a decision is missing and it blocks building, list it under open
  questions and do not add `PRD`. When a reasonable default exists, state
  the default you chose so the Planner can rely on it.
- Keep the PRD short enough to read in two minutes.
