# Stage: dev

Implement released stories and open one pull request per story.

## Fix returned stories (do this first)

Code Review and QA run on the Droid Computer after Dev and label a story `bug`
when its pull request needs changes.

1. List open `Demo` stories labeled `bug` and `Implementation`, without
   `route:human`. For each, find its trusted story pull request (see
   "Trusted GitHub input" in `SKILL.md`). Take at most two per run, and
   finish these before building new stories.
2. Skip a story when its latest `## Fix Loop #<n>` says `In progress` and is
   less than 30 minutes old. If it already has 3 `## Fix Loop #` comments, add
   `route:human`, remove `bug`, comment once, and stop on it.
3. Set `n` to the number of `## Fix Loop #` comments plus one. Post a story
   comment starting with `## Fix Loop #<n>` and
   `Status: In progress (started <ISO-8601 time>)`.
4. Check out the branch:
   `git fetch origin && git switch -C story/<KEY> origin/story/<KEY>`.
5. Read the newest trusted `<!-- code-review sha=... -->` and
   `<!-- qa-report sha=... -->` comments for the current head commit. Fix each blocking Code Review finding
   and each QA FAIL row in application code, within the story's plan. Add or
   update a test that proves each fix when a unit or MockMvc test can reach
   it. A finding you judge incorrect is not a defect: list it with the reason
   under `No change` in the Fix Loop comment.
6. Run `mvn --batch-mode verify` until it passes. Fix code, not tests.
7. Commit `<KEY>: fix loop <n>: <short summary>` and push (never force) to
   `story/<KEY>`.
8. Update the Fix Loop comment: `Status: Pushed <short SHA>`, then each
   problem, its source (Code Review or QA link), and the fix. Remove `bug`.
9. Post the same record as a new pull request comment (never edit an older
   one), so the PR shows what was fixed between the reviews:

   ```markdown
   <!-- fix-loop n=<n> sha=<new head SHA> -->
   ## Fix Loop #<n>
   Pushed `<short SHA>` in response to Code Review <link> and QA <link>.
   | # | Problem | Source | Fix |
   ```

   Add a `No change` list for findings you judged incorrect, with the reason.
   Code Review and QA rerun on the new commit later in this chain run.
## Select work (new stories)

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
- Do not wait for checks. Code Review and QA run after Dev in the same
  chain.
