---
name: qa
description: >
  Run functional QA for Facilities Desk. Analyzes the git diff to find the
  affected app areas, starts the checked-out branch locally, exercises the web
  UI and HTTP API as a facilities coordinator would, and writes a concise
  evidence-backed report. Uses droid-control for browser interaction. Use when
  testing pull requests, releases, or smoke testing the app.
---

# QA Orchestrator

**Scope:** This skill performs manual and functional QA only. Verify that the
application works by using it as a real facilities coordinator would, through
the browser and HTTP calls. Do not run or report CI checks, linters, type
checks, unit tests, `mvn test`, or other static analysis. Separate workflows
own those checks.

## 1. Load the runtime configuration

Read `.factory/skills/qa/config.yaml` on every run. It is the single source of
truth for environments, personas, app routing, evidence, cleanup, and failure
learning.

For every affected interactive app, invoke the `droid-control` skill before
interacting with it. Follow its routing tables:

- Load the target driver it selects for the app (the web route for `qa-web`).
- Use the Capture and Verify stages on every run.
- Load Compose only when both `video_evidence` and `droid_control.compose` are
  true in config.
- When Compose does not run, never invoke it. Keep raw captures and text
  snapshots as evidence.

Treat `droid_control.compose` and `video_evidence` as runtime switches. Read
them on every run; never cache or restate their values in this file or in an
app skill.

## 2. Determine the target

Use `default_target` unless the user names another environment, and respect
its restrictions.

This repository has no preview deployments. For pull-request code, test the
checked-out branch:

1. Use `target/facilities-desk.jar` if it exists; otherwise run the app's
   `build_command` from config.
2. Start the app with its `start_command` in the background.
3. Poll `http://127.0.0.1:8080/actuator/health` until it returns `UP`
   (up to 60 seconds).
4. Test `http://127.0.0.1:8080`.

Never fall back to a remote deployment. If the app cannot start, report every
affected flow as BLOCKED with the startup error.

## 3. Analyze the diff

Run `git diff --name-only <base>...HEAD` against the base you were given, then
read the full diff of the matched files. Map changed files to apps with
`apps.*.path_patterns` in config.

- Load and run only the skills for affected apps.
- Files that match no app pattern (for example `.factory/**`, `.github/**`,
  `docs/**`, `README.md`, `Dockerfile`, `src/test/**`) are not app changes.
- If no app is affected, report **INCONCLUSIVE**: "No app code changed -- QA
  not applicable for this diff." Do not run any flows.
- For each affected app, also write ad-hoc checks for changed behavior that no
  existing flow covers.

## 4. Pre-flight checks (affected apps only)

- `qa-web`: confirm `droid-control@factory-plugins` is active, the app is up,
  and `/` returns the Facilities Desk page.
- `qa-api`: confirm the app is up and `GET /api/work-orders/summary` returns
  HTTP 200.
- Compose prerequisites from the droid-control skill: only when config turns
  Compose on. Let the Compose stage resolve its own plugin root and build or
  install what it needs on first use.

If a pre-flight fails for one app, report that app's flows as **BLOCKED** with
the exact error and fix, then continue with the other affected app.

## 5. Execute diff-relevant flows

Read each affected app skill:

- `.factory/skills/qa-web/SKILL.md`
- `.factory/skills/qa-api/SKILL.md`

Each skill's flow list is a menu, not a checklist:

1. Pick the flows that exercise the changed behavior, plus adjacent flows that
   prove the change integrates (for example, a new field appears in the list,
   the detail panel, and the API response).
2. Skip unrelated flows.
3. Write a new ad-hoc flow when no existing flow covers the change.
4. Never run automated test suites. Interact with the app.

## 6. Evidence capture

Text snapshots are the primary evidence. They render inline in the PR comment.

Web (through droid-control's web route):

- Capture the accessibility tree after each meaningful state change.
- Save screenshots to `./qa-results/$RUN_ID/`.
- When `imagemagick` is true and a before/after comparison explains a change,
  build an animated GIF from the two screenshots in the same folder.
- Each snapshot must show a different state; trim it to the relevant part.

API: record method, path, HTTP status, and only the response fields that prove
the result. Never include secrets.

Video evidence (only when `video_evidence` is true):

1. Follow droid-control Capture for recording start, interaction, stop, and
   raw-output verification. Record exactly one video per flow.
2. If Compose is off in config, keep the raw browser WebM.
3. If Compose is on in config, hand the raw recording to droid-control Compose
   and render `./qa-results/$RUN_ID/<flow-slug>.mp4` with a `single` layout and
   the literal `"preset": "factory"`. No other preset is allowed. Trim dead
   time, add a short title card, keep it at 60 seconds or less, and run the
   Compose finalization checks.
4. Verify the evidence file exists and is non-empty. If capture or Compose
   fails, retry that stage once, then fall back to text evidence and keep any
   raw capture in artifacts.
5. Upload each verified video to GitHub's user-attachments endpoint:

   ```bash
   REPO_ID="${REPO_ID:-$(gh api "repos/$GITHUB_REPOSITORY" --jq .id)}"
   curl -sS --fail-with-body --request POST \
     --header "Authorization: Bearer $QA_EVIDENCE_TOKEN" \
     --header "Content-Type: application/octet-stream" \
     --header "Accept: application/json" \
     --header "X-GitHub-Api-Version: 2022-11-28" \
     "https://uploads.github.com/user-attachments/assets?name=<evidence-file>&content_type=<url-encoded-content-type>&repository_id=$REPO_ID" \
     --data-binary "@./qa-results/$RUN_ID/<evidence-file>" \
   | jq -e '.url'
   ```

   - Use `video/webm` for raw WebM and `video/mp4` for Compose output.
   - The endpoint is undocumented and accepts only a classic PAT with `repo`
     scope (`QA_EVIDENCE_TOKEN`). A 404 means an installation token was used; a
     403 "Resource not accessible by personal access token" means a
     fine-grained PAT; a 403 about SAML means the PAT needs SSO authorization.
   - If `QA_EVIDENCE_TOKEN` is empty or the upload fails, fall back to text
     evidence and name the file as a downloadable artifact.
6. Embed rules: put each returned video `url` alone on its own line under the
   flow's label, never wrapped in `![]()`. Screenshots uploaded the same way use
   `![name](url)`. Never embed a link that did not come back from the upload.
   Without an upload, do not embed images; name the artifact file instead.

## 7. Test quality gate

1. Change-specific first: at least half the checks directly exercise the
   changed behavior.
2. Integration checks that prove the change fits existing features are valid.
3. No unrelated flows.
4. No automated test suites.
5. At least one negative or boundary check related to the change.
6. Interact with the app as a user would.
7. If you cannot say what the PR changes, mark it INCONCLUSIVE, never PASS.

## 8. Handle failures

Never silently skip a flow. If a flow cannot complete, report it as BLOCKED
with what was tried and how the user can fix it, then continue with the next
flow. Never abort the whole run for one failure.

## 9. Write the report

Write `qa-results/report.md` from `.factory/skills/qa/REPORT-TEMPLATE.md`.

- Start with `## QA Report`.
- Use only the template's result values.
- Add "Action Required" only for actionable failures, naming the file or
  behavior to fix.
- Put snapshots, video lines, and artifact names in the evidence block.
- Stop the local app when done.

## 10. Suggest skill updates (failure learning)

Read `failure_learning` from config.

If a FAIL or BLOCKED result reveals a new testing-environment insight (not a
bad selector, a skill typo, or an expected change from the PR) that is not
already under an app skill's Known Failure Modes, add:

```markdown
## Suggested Skill Updates (N issues found)

| # | Severity | File | Issue | Fix Prompt |
| --- | --- | --- | --- | --- |
| 1 | <emoji> <level> | `<file>` | <short description> | <details><summary>Copy</summary><br>`<self-contained droid prompt>`</details> |
```

Severity: `🔴 Breaking` (fails every run), `🟡 Degraded` (intermittent or
slow), `🔵 Info` (useful new knowledge).

- `suggest_in_report`: include the table in the report only. Do not write
  `qa-results/skill-updates.json`.
- `auto_commit` or `open_pr`: also write `qa-results/skill-updates.json` as an
  array of `{file, section, action, content}` edits for the workflow to apply.

Omit the section when there is nothing genuinely new.
