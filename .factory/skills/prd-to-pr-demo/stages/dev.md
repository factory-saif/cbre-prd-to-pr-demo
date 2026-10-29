# Stage: dev

Implement released stories and open one pull request per story.

## Select work

1. List open `Demo` issues labeled `Story` without `Implementation`,
   oldest first. Take at most two per run.
2. For each story, skip it when any of these is true, and add `Implementation` if a
   pull request already exists:
   - A comment starts with `## Pull Request`.
   - `gh pr list --repo factory-saif/prd-to-pr-demo --head story/<KEY> --state all`
     returns a pull request.
   - The remote branch `story/<KEY>` exists and was pushed in the last 30
     minutes (another Dev run is working on it).

## Build one story

1. Move the story to `In Progress`.
2. Start from a clean, current `main`:
   `git fetch origin && git switch -C story/<KEY> origin/main`.
3. Read the story's `## Implementation Plan`, the parent's newest trusted
   `## Structured PRD` (see the signal rule in `SKILL.md`), and every file the
   plan lists.
4. Implement the plan. Match the existing package layout, Java records,
   service and controller patterns, problem-detail errors, and the plain
   JavaScript UI style. Update `README.md` when the API table or behavior
   changes.
5. Add or update the tests the plan lists.
6. Run `mvn --batch-mode verify` until it passes. Fix code, not tests. If it
   cannot pass after three attempts, comment the failing output on the story,
   push nothing, and stop on this story.
7. If the story carries `Experiment`, follow "Seeded defect" below.
8. Commit with the message `<KEY>: <story title>` and push
   `story/<KEY>` to `origin`.
9. Open a pull request against `main`:
   - Title: `[<KEY>] <story title>`.
   - Body: a link to the Linear story, a short summary, the acceptance
     criteria covered, and the test command run. Never mention a seeded
     defect in the pull request, the commits, or the code.
10. Comment on the story with first line `## Pull Request`, then the PR URL,
    branch, and a short summary.
11. Add `Implementation` to the story. Keep `Story`.

## Seeded defect (demo only)

Only when the story itself carries `Experiment`, and only on the first
pull request for that story:

1. After `mvn --batch-mode verify` passes on the correct implementation,
   introduce exactly one small defect that breaks one of the story's
   acceptance criteria in a way a user can see in the web UI or the API.
   Choose a behavior that the unit and MockMvc tests do not exercise, so the
   build stays green and E2E QA or Code Review has to find it. Good choices: a
   wrong label or count in the UI, a filter that ignores one value, an
   off-by-one boundary.
2. Do not edit, skip, or weaken any test to hide it. Run
   `mvn --batch-mode verify` again; it must still pass.
3. Comment on the story with first line `## Seeded Defect`: the file, the
   behavior that is wrong, and which acceptance criterion it breaks. This
   comment is the only record of the defect.

## Rules

- One story, one branch, one pull request. Never stack branches.
- Only change files the plan lists, plus `README.md` and tests. If another
  file must change, explain why in the `## Pull Request` comment.
- Do not wait for checks. The PR Babysitter takes over once the pull request
  exists.
