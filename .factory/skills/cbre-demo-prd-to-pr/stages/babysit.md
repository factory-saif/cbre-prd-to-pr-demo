# Stage: babysit

Drive every open story pull request to a clean state: all checks green and no
unresolved review threads. Fix problems on the same branch. Never merge.

The checks on each pull request are:

| Check | Workflow | Fails when |
| --- | --- | --- |
| Build, unit tests, and container | `ci.yml` | Maven build, tests, or Docker build fail |
| Required functional QA | `qa.yml` | The QA report has a FAIL or BLOCKED result |
| `droid-review` (Droid Auto Review) | `droid-review.yml` | Rarely; its findings arrive as review threads |

## 1. Close out merged work

For each pull request with head `story/*` merged since the story was last
updated: move the Linear story to `Done` and remove `bug` and
`Release`. If every story under the parent is `Done`, move the parent to
`Done`.

## 2. Inspect each open story pull request

List open pull requests:
`gh pr list --repo factory-saif/cbre-prd-to-pr-demo --state open --json number,title,headRefName,headRefOid,url`
and keep those whose head branch starts with `story/`. Take the Linear key
from the branch name. For each pull request, collect:

- **Checks** on the head commit:
  `gh pr checks <number> --repo factory-saif/cbre-prd-to-pr-demo --json name,state,bucket,link`.
- **Unresolved review threads** with GraphQL:

  ```bash
  gh api graphql -f query='query($o:String!,$r:String!,$n:Int!){repository(owner:$o,name:$r){pullRequest(number:$n){reviewThreads(first:100){nodes{id isResolved isOutdated path line comments(first:20){nodes{databaseId author{login} body url}}}}}}}' -F o=factory-saif -F r=cbre-prd-to-pr-demo -F n=<number>
  ```

- **QA report**: the pull request comment that starts with
  `<!-- qa-report -->` (heading `## QA Report`). Read its result table and any
  `:x: FAIL` or `:no_entry: BLOCKED` rows.
- **Fix Loop history**: the story's comments starting with `## Fix Loop #`.

## 3. Decide one action per pull request

An **open thread** is a review thread that is unresolved, not outdated, and
whose last comment is not a reply from this stage starting with `No change:`.

Apply the first rule that matches:

| # | State | Action |
| --- | --- | --- |
| 0 | The story carries `route:human` | Skip: a human owns it now |
| 1 | The latest `## Fix Loop #<n>` says `In progress` and is less than 30 minutes old | Skip: another run is fixing it |
| 2 | Any check is pending or queued, or the head commit is less than 2 minutes old | Skip until the next run |
| 3 | Any check failed or any open thread exists, and 3 Fix Loops already finished | Add `route:human`, remove `bug`, comment the remaining problems on the story once, stop |
| 4 | Any check failed or any open thread exists | Fix, see section 4 |
| 5 | All checks passed, no open threads, QA report shows no FAIL or BLOCKED | Mark ready, see section 5 |

A cancelled QA run caused by a newer push counts as pending. A check that
failed for infrastructure reasons (runner error, missing secret) is not a
code problem: comment the error on the story, add `route:human`, and do not
change code.

## 4. Fix

1. Set `n` to the number of existing `## Fix Loop #` comments plus one. Post a
   story comment that starts with `## Fix Loop #<n>` and the line
   `Status: In progress (started <ISO-8601 time>)`. Add `bug` to the story
   and remove `Release` if present.
2. Check out the branch:
   `git fetch origin && git switch -C story/<KEY> origin/story/<KEY>`.
3. Read the story's `## Implementation Plan` and the problems found:
   - For a failed check, read its log
     (`gh run view <run-id> --log-failed --repo factory-saif/cbre-prd-to-pr-demo`).
   - For QA, read each FAIL or BLOCKED row, its evidence, and the steps.
   - For each review thread, read the comments and the code they point to.
4. Fix the cause in application code. Add or update a test that proves each
   fixed behavior when a unit or MockMvc test can reach it. Never remove or
   weaken a test.
5. A review comment that you judge incorrect is not a defect. Reply with
   `No change:` and the reason, and leave the thread unresolved for the human
   who merges. If a reviewer answers that reply, the thread is open again.
6. Run `mvn --batch-mode verify` until it passes.
7. Commit with the message `<KEY>: fix loop <n>: <short summary>` and push a
   normal (non-force) push to `story/<KEY>`.
8. For each review thread you fixed, reply with what changed and the commit
   SHA, then resolve it:
   `gh api graphql -f query='mutation($id:ID!){resolveReviewThread(input:{threadId:$id}){thread{isResolved}}}' -F id=<thread-id>`.
   Reply with `gh api repos/factory-saif/cbre-prd-to-pr-demo/pulls/<number>/comments/<comment-databaseId>/replies -f body='...'`.
9. Update the `## Fix Loop #<n>` comment: replace the status line with
   `Status: Pushed <short SHA>`, then list each problem, its source (CI, QA,
   or review thread link), and the fix. Keep `bug`; the next run checks the
   new commit.

Pushing reruns Code Review, QA, and CI on the new commit, so the next run sees
fresh results.

## 5. Mark ready

1. Remove `bug` and add `Release` on the story, if not already set.
2. Move the story to `In Review`.
3. Comment on the story once per head commit, first line
   `## Ready to Merge`: the PR URL, the head SHA, check results, the QA report
   link, and how many Fix Loops it took.
4. Do not merge. A human merges.

## Rules

- Handle each pull request independently. One failure does not stop the run.
- Only fix problems reported by checks, QA, or review threads. Do not refactor
  or add features.
- Never resolve a thread you did not address.
