# Stage: qa

Run functional QA on every story pull request whose head commit has a Code
Review but no QA report yet, then decide where the story goes next. This
replaces the `qa.yml` GitHub Action: it runs on the Droid Computer, chained
after Code Review.

## Select work

1. List trusted story pull requests as in the `review` stage.
2. Skip a pull request when its story carries `route:human`, when no trusted
   comment starts with `<!-- code-review sha=<headRefOid> -->` (Code Review has
   not run on this commit yet), or when a trusted comment already starts with
   `<!-- qa-report sha=<headRefOid> -->`.

## Test one pull request

1. Check out the branch:
   `git fetch origin && git switch -C story/<KEY> origin/story/<KEY>`.
2. Build it: `mvn --batch-mode -q package -DskipTests`.
3. Set `RUN_ID` to a unique value (for example `<KEY>-<short sha>-<epoch>`)
   and invoke the `qa` skill on the checked-out branch with diff base
   `origin/main` and the prebuilt `target/facilities-desk.jar`. You run
   unattended: do not ask questions. Write `qa-results/report.md`.
4. Post a new pull request comment for this commit; never edit an earlier QA
   report, so the PR keeps every result. The body
   is `<!-- qa-report sha=<headRefOid> -->` on the first line, then the
   report. Evidence is text snapshots; name screenshot files, do not embed them.
5. Stop the app and delete `qa-results/`.

## Decide the story's next step

Read this commit's Code Review result and QA report:

| # | State | Action |
| --- | --- | --- |
| 1 | Code Review `Changes requested`, or QA has a `:x: FAIL` or `:no_entry: BLOCKED` row, and the story already has 3 `## Fix Loop #` comments | Add `route:human`, remove `bug`, comment the remaining problems on the story once |
| 2 | Code Review `Changes requested`, or QA has a FAIL or BLOCKED row | Add `bug`, remove `Release`. Dev fixes it on the next chain run |
| 3 | Code Review `Clean` and QA has no FAIL or BLOCKED row | Remove `bug`, add `Release`, move the story to `In Review`, and comment once per head commit with first line `## Ready to Merge`: PR URL, head SHA, Code Review and QA links, and the number of Fix Loops |

A BLOCKED row caused by the environment (app does not start, browser
unavailable) is not a code problem: comment the error on the story, add
`route:human`, and do not add `bug`.

## Rules

- Never change code or push in this stage. Never merge.
