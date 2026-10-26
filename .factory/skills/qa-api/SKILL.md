---
name: qa-api
description: >
  Functional HTTP QA for the Facilities Desk REST API: listing and filters,
  creation and validation, response-target due dates, assignment, the status
  workflow, error codes, and summary counts.
---

# Facilities Desk API QA

## Testing target

Test the pull-request branch locally:

1. From the repository root, use `target/facilities-desk.jar` if present;
   otherwise build it with `mvn --batch-mode -q package -DskipTests`.
2. Start it in the background: `java -jar target/facilities-desk.jar --server.port=8080`.
3. Poll `http://127.0.0.1:8080/actuator/health` until it returns `UP`.
4. Send requests to `http://127.0.0.1:8080` with curl.

If startup fails, report API flows as BLOCKED. Never fall back to a remote
environment.

## Authentication

None. No credentials, secrets, or external services are involved. JSON bodies
need `Content-Type: application/json`.

## API summary

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/work-orders` | Filters: `status`, `priority`, `category`, `overdue`, `q` |
| POST | `/api/work-orders` | `title`, `location`, `category`, `priority`, `requester`, optional `description`; 201 with `Location` |
| GET | `/api/work-orders/{id}` | 404 problem detail when unknown |
| POST | `/api/work-orders/{id}/assign` | `assignee`; OPEN becomes ASSIGNED |
| POST | `/api/work-orders/{id}/status` | `status`, optional `note`; 409 when the workflow forbids it |
| GET | `/api/work-orders/summary` | `total`, `active`, `overdue`, `urgentActive`, `byStatus` |

Errors are RFC 9457 problem details with `title` and `detail`; validation
errors add an `errors` map keyed by field.

Workflow: OPEN -> ASSIGNED (assign action only) -> IN_PROGRESS <-> ON_HOLD;
IN_PROGRESS -> COMPLETED; any active status -> CANCELLED. COMPLETED and
CANCELLED are closed.

Response targets: URGENT 4h, HIGH 24h, MEDIUM 72h, LOW 7d.

## Persona

Facilities coordinator. Positive requests create, filter, assign, and move work
orders. Negative requests must be rejected: missing fields, unknown enum
values, unknown IDs, forbidden status moves, and changes to closed orders.

## Flow menu

Select only the flows relevant to the diff.

### A1. List and summary

`GET /api/work-orders/summary` on a fresh start.

Success: HTTP 200 with `total` 6, `active` 5, `overdue` 2, `urgentActive` 2,
and `byStatus` OPEN 2, ASSIGNED 2, IN_PROGRESS 1, COMPLETED 1, ON_HOLD 0,
CANCELLED 0. `GET
/api/work-orders` returns six items with active ones first.

### A2. Create and due date

POST a valid HIGH work order.

Success: HTTP 201, `Location` ends with the new ID, `status` OPEN, `dueAt`
equals `createdAt` plus 24 hours, `overdue` false, `nextStatuses` is
`["ASSIGNED","CANCELLED"]`, and `activity` has one "Request created by ..."
entry.

### A3. Validation errors (negative)

POST `{"title":" ","category":"HVAC"}`.

Success: HTTP 400, `title` "Validation failed", and `errors` contains `title`,
`location`, `priority`, and `requester` but not `category`.

### A4. Unknown values (negative)

POST a work order with priority `CRITICAL`, then `GET
/api/work-orders?priority=SOON`.

Success: both return HTTP 400 "Malformed request"; the query error names the
`priority` parameter.

### A5. Unknown ID (negative)

`GET /api/work-orders/WO-9999`.

Success: HTTP 404 with detail "No work order exists with ID WO-9999".

### A6. Assign and lifecycle

On a new work order: assign "Priya Shah", move to IN_PROGRESS, then COMPLETED
with a note.

Success: each call returns HTTP 200; status goes ASSIGNED, IN_PROGRESS,
COMPLETED; `completedAt` is set; `nextStatuses` ends empty; the last activity
entry includes the note.

### A7. Forbidden moves (negative)

1. On an OPEN work order, POST status COMPLETED.
2. POST status ASSIGNED to any active order.
3. On a COMPLETED order, POST assign.

Success: all return HTTP 409 "Invalid status change" with a message naming the
move, and the work order is unchanged on a follow-up GET.

### A8. Filters and search

Query `status=ASSIGNED`, `priority=URGENT`, `category=PLUMBING`,
`overdue=true`, and `q=priya` on a fresh start.

Success: ASSIGNED returns `WO-1001` and `WO-1005`; URGENT returns `WO-1001` and
`WO-1004`; PLUMBING returns `WO-1003`; overdue returns `WO-1001` and
`WO-1002`; `q=priya` returns `WO-1003`.

## Evidence

For each flow, record the method and path, the HTTP status, and only the
response fields that prove the result, as fenced text in the report's
evidence block.

## Error handling

- Business-rule violations are 409, validation and unknown values are 400,
  unknown IDs are 404.
- A 500 is always a FAIL; include the response body.

## Known Failure Modes

1. **Port 8080 is already in use.** Stop the stale `facilities-desk.jar`
   process and restart the branch build.
2. **Counts differ from the starting state.** Earlier flows created or changed
   work orders. Restart the app or account for your own changes.
3. **Missing JSON content type.** POSTs without `Content-Type:
   application/json` return HTTP 415; set the header.
