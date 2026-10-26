---
name: qa-web
description: >
  Browser QA for the Facilities Desk web UI: dashboard summary, work-order
  list, search and filters, new-request form validation, assignment, status
  lifecycle, overdue display, and closed-order locking.
---

# Facilities Desk Web QA

## Testing target

This repository has no preview deployments. Test the pull-request branch
locally:

1. From the repository root, use `target/facilities-desk.jar` if present;
   otherwise build it with `mvn --batch-mode -q package -DskipTests`.
2. Start it in the background: `java -jar target/facilities-desk.jar --server.port=8080`.
3. Poll `http://127.0.0.1:8080/actuator/health` until it returns `UP`.
4. Use `http://127.0.0.1:8080` as the browser base URL.

If the app cannot start or port 8080 is unavailable, report web flows as
BLOCKED. Never substitute a remote environment.

## How to drive the browser

Invoke the `droid-control` skill before any browser interaction. Use its web
route plus its Capture and Verify stages. This skill describes what to test and
what success looks like; droid-control owns browser mechanics, recording, and
verification.

Read `.factory/skills/qa/config.yaml` before capture. Record video only when
`video_evidence` is true, and load Compose only when both `video_evidence` and
`droid_control.compose` are true.

## Authentication

None. The app has no login and needs no credentials or secrets.

## App notes

- The app starts with six sample work orders (`WO-1001` to `WO-1006`), created
  relative to the server's start time. Restart the app for a clean baseline
  when a flow depends on the starting counts.
- Starting state: `WO-1001` HVAC, Urgent, Assigned to Jordan Lee, overdue.
  `WO-1002` Electrical, High, Open, overdue. `WO-1003` Plumbing, High,
  In Progress, Priya Shah. `WO-1004` Security, Urgent, Open. `WO-1005`
  Cleaning, Medium, Assigned to Sam Rivera. `WO-1006` General, Low, Completed
  by Alex Kim.
- Starting summary cards: Active 5, Overdue 2, Urgent active 2, Completed 1.
- Elements carry stable `data-testid` attributes, for example
  `new-request-button`, `filter-query`, `filter-priority`, `filter-overdue`,
  `work-order-row-<ID>`, `assignee-input`, `assign-button`, `status-<STATUS>`,
  `detail-activity`, `form-errors`, `detail-error`.
- The new-request form opens in a native `<dialog>`.
- Clicking a row opens the detail panel on the right; the selected row is
  highlighted.
- Badges and buttons use title case ("In Progress", "On Hold"); activity log
  entries use sentence case ("Status changed from Assigned to In progress").
- Status buttons in the detail panel show only the moves the workflow allows.
  Assignment uses the assign form, never a status button.
- Search waits about 200 ms after typing before it refreshes the list.

## Persona

### Facilities coordinator

Can create, search, filter, assign, and move work orders through the workflow.
Must not be able to create a request with missing required fields, complete a
work order that was never started, or change a completed or cancelled work
order.

## Flow menu

Select only the flows relevant to the diff.

### W1. Dashboard loads with summary and list

1. Open the app.
2. Verify the header reads "Facilities Desk".
3. Verify the summary cards and the six sample rows from the starting state.
4. Verify active work orders are listed before the completed one, ordered by
   due time, and overdue rows show "Overdue by ..." in red.

Success: counts match the starting state and every row shows ID, title,
location, category, priority, status, assignee, and due text.

### W2. New request validation (negative)

1. Click "New request".
2. Clear the fields and submit.

Success: the dialog stays open, the error message names Title, Location, and
Requester, and no new row appears.

### W3. Create an urgent work order

1. Click "New request" and enter a fictional title, location, requester,
   category, and priority Urgent.
2. Submit.

Success: the dialog closes, the new `WO-` ID is selected in the detail panel,
due text reads "Due in 4h", the row appears in the list, and Active and Urgent
active each go up by one. The activity log shows "Request created by <name>".

### W4. Search and filters

1. Search `priya`: only `WO-1003` remains.
2. Clear search and filter priority Urgent: `WO-1001` and `WO-1004` remain.
3. Clear and check "Overdue only": `WO-1001` and `WO-1002` remain.
4. Search a nonsense term (negative).

Success: each step shows exactly the expected rows, and the nonsense search
shows "No work orders match these filters."

### W5. Assign a technician

1. Open `WO-1002` (Open, unassigned).
2. Submit the assign form with an empty name (negative).
3. Assign "Jordan Lee".

Success: the empty submit shows an assignee error and changes nothing; the
valid submit moves the status to Assigned, shows Jordan Lee in the row and the
panel, and adds "Assigned to Jordan Lee" to the activity log.

### W6. Status lifecycle

1. Open `WO-1005` (Assigned). Verify the buttons are In Progress and Cancelled
   only.
2. Click In Progress, then On Hold, then In Progress.
3. Enter the note "Work finished" and click Completed.

Success: each step updates the panel, the row badge, and the activity log
("Status changed from ... to ..."); the final entry ends with ": Work
finished"; Completed goes up by one and Active goes down by one.

### W7. Closed work orders are locked (negative)

1. Open `WO-1006` (Completed).

Success: the assign form and status buttons are hidden, and the due column
reads "Completed".

### W8. Response targets and overdue display

1. Compare due text for `WO-1004` (Urgent, about 3h left) and `WO-1005`
   (Medium, about 2d left).
2. Check that only active orders past their due time show "Overdue by ...".

Success: due text matches each priority's response target (Urgent 4h, High
24h, Medium 72h, Low 7d) from creation, and the Overdue card equals the number
of overdue rows.

## Evidence

Capture an accessibility snapshot after each meaningful step and the final
state, trimmed to the table, panel, or dialog that proves the result. Save
screenshots to `qa-results/$RUN_ID/`. For a visual before/after (for example a
status badge changing) and when ImageMagick is available, combine two
screenshots into an animated GIF. Follow the orchestrator's video rules when
video evidence is on.

## Error handling

- A failed list request shows a red message above the table; report the HTTP
  status and body from the API.
- A rejected assign or status change shows the server's message in the detail
  panel without changing the row.
- A rejected create keeps the dialog open with field errors.

## Known Failure Modes

1. **Port 8080 is already in use.** Stop the stale `facilities-desk.jar`
   process, then restart the branch build. Never test another deployment.
2. **Counts differ from the starting state.** Earlier flows in the same run
   created or changed work orders. Restart the app or adjust the expected
   numbers by the changes you made.
3. **Rows have not refreshed yet.** After typing in search, wait for the
   debounce before asserting the list.
4. **Due text drifts by an hour.** Seed data is relative to start time, so
   "Due in 3h" can read "Due in 2h" on a slow run. Assert the order of
   magnitude and the overdue state, not the exact minute.
