# Workflow and build plan

This is the in-repo summary of the team's plan document. Every workflow step comes from a real
university, vendor or open-source system; features without evidence are parked.

## Roles

| Role | Does |
| --- | --- |
| Student | Browse approved events, register, cancel, check in, give feedback |
| Club organizer | Submit event and resource requests, revise them, see registrants |
| Resource admin | Approve or reject bookings for rooms, labs, equipment |
| Super admin | Manage users, roles, rules, reports |

## Workflow A: event request to approval

1. Draft: organizer fills the event form (title, time, venue, equipment, capacity, expected attendees).
2. Submit: the conflict check runs at submission.
3. Advisor review: approve or reject with a reason.
4. Space manager review: approve or reject the venue and equipment.
5. Approved: event is published and registration opens. Hidden from students before this.
6. Editing an approved request triggers approval again.
7. Completed or Cancelled.

States: Draft, Submitted, Advisor approved, Approved, Rejected, Cancelled, Completed.
Every change is written to an audit log (who, when, why).

## Workflow B: booking and conflict check (five checks)

1. Time overlap on the same resource: `start < newEnd AND end > newStart`.
2. Setup and teardown buffer (configurable per resource).
3. Capacity and room type.
4. Blocked periods (holidays, exams, maintenance).
5. Access gate: training or sign-off for labs and equipment.

Academic use outranks events. The overlap check and the insert run in one MongoDB transaction with an
idempotency key, so concurrent or repeated requests cannot double-book.

## Workflow C: registration

Confirmed while below capacity, otherwise Waitlisted (waitlisted students do not count toward capacity).
When a confirmed student cancels, the oldest waitlisted student is promoted after a second capacity check.

## Workflow D: attendance and feedback

Rotating QR code (30 seconds) for check-in, grace window for resource check-in, a feedback survey for attended
students, and registered-versus-attended reports.

## Default business rules (all configurable)

| Rule | Default |
| --- | --- |
| Minimum lead time for an event request | 7 days |
| Approval turnaround shown to organizers | up to 3 days |
| Setup and teardown buffer | 15 min fixed rooms, 45 min variable rooms |
| Late cancellation | under 72 hours triggers a warning |
| No-shows | repeated no-shows lead to loss of booking privileges |
| Resource check-in grace window | 45 min |
| Waitlist promotion | auto, oldest first |

Replace these with your college's real rules when known.

## Data model (MongoDB)

users, clubs, memberships, events, resources, bookings, registrations, attendance, feedback, auditLogs,
plus blockedPeriods and trainingRecords. Key indexes: unique email; unique (eventId, userId) on registrations,
attendance and feedback; compound (resourceId, start, end) and unique idempotencyKey on bookings.

## Phases

| Phase | Done when |
| --- | --- |
| 1. Foundation | Each role logs in and sees only its own pages |
| 2. Resources and conflict engine | 20 parallel requests for one slot create exactly one booking |
| 3. Events and approvals | An event goes Draft to Approved and reserves its room |
| 4. Registration | Cancelling one seat promotes the first waitlisted student |
| 5. Attendance and feedback | A full event runs end to end with a report |
| 6. Reports and polish | An evaluator can run the demo script without help |

## Parked (not built until the core works)

Paid tickets, single sign-on and student-system integration, mobile app for setup crews, recurring bookings,
event recommendations. Dropped: organizer reliability score (no source found).
