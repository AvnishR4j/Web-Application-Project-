# Campus Event and Resource Booking System

Clubs create events, students register, and organizers book rooms, labs or equipment.
Admins approve requests, and the system blocks booking conflicts.

**Stack:** React, Java Spring Boot, Spring Security (JWT), Spring Data MongoDB, MongoDB, REST APIs.

The full workflow design, with the real-world source behind every rule, is in
[docs/PLAN.md](docs/PLAN.md). Nothing gets built that is not in that plan.

## Repository layout

```
backend/    Spring Boot 3 REST API (Java 21, Maven)
frontend/   React app (Vite)
docs/       Plan, workflows, business rules
docker-compose.yml   MongoDB as a single-node replica set
.env.example         Settings to copy to .env
```

## Quick start

Requirements: Java 21, Maven 3.9+, Node 20+, and MongoDB as a replica set (transactions need one).

1. **Database**, either:
   - Docker: `docker compose up -d` (a single-node replica set on localhost), or
   - MongoDB Atlas free tier (a replica set already): create a cluster and a database user, allow your IP
     in Network Access, and copy the connection string with a database name, for example
     `mongodb+srv://USER:PASSWORD@cluster0.xxxxx.mongodb.net/campus_booking?retryWrites=true&w=majority`.
2. **Backend** (http://localhost:8080):
   ```
   cd backend
   SEED_DEMO_DATA=true mvn spring-boot:run
   ```
   With Atlas, put `MONGODB_URI="<connection string>"` in front of that command.
3. **Frontend** (http://localhost:5173):
   ```
   cd frontend
   npm install
   npm run dev
   ```

Run the backend tests with `cd backend && mvn test`. The concurrency and workflow tests run only when
`MONGODB_URI` points at a replica set; use a separate database name such as `campus_booking_test`.

**[docs/DEMO.md](docs/DEMO.md)** walks through every workflow in about 15 minutes.

### Settings

| Variable | Default | Meaning |
| --- | --- | --- |
| `MONGODB_URI` | local replica set | Database connection string |
| `JWT_SECRET` | dev value | 32+ characters; set a real one outside development |
| `SEED_DEMO_DATA` | false | Create demo users, rooms and a club (development only) |
| `BOOKING_ZONE` | Asia/Kolkata | Time zone for room opening hours |
| `EVENT_MIN_LEAD_DAYS` | 7 | Notice required for event requests |
| `CHECKIN_OPENS_MINUTES_BEFORE` | 30 | When event check-in opens |
| `CHECKIN_SECRET` | `JWT_SECRET` | Signs the rotating check-in codes |
| `NOSHOW_LIMIT` / `NOSHOW_WINDOW_DAYS` | 3 / 60 | Missed events that pause new registrations (0 turns it off) |

### Demo accounts (only when `SEED_DEMO_DATA=true`)

| Role | Email |
| --- | --- |
| Student | student@campus.test (also student2@, student3@) |
| Club organizer | organizer@campus.test |
| Faculty advisor | advisor@campus.test |
| Resource admin | resources@campus.test |
| Super admin | admin@campus.test |

All of them use `DEMO_PASSWORD`. When you run locally without setting it, that is `Password@123`. The live site
uses a different password, set on Render and shared only within the team. Changing `DEMO_PASSWORD` and restarting
resets every demo account to the new password. If your local backend uses the same database as the live site, set
the same `DEMO_PASSWORD` locally too, or it will reset the accounts back to `Password@123`.

Set a real `JWT_SECRET` (32+ characters) outside development. See `.env.example`.

## Build status

| Phase | Scope | Status |
| --- | --- | --- |
| 1. Foundation | Repo, replica set, auth, four roles, role-protected pages, seed data | Done: `mvn test` passes (10 of 10) |
| 2. Resources and conflict engine | Resource catalog, five conflict checks, transactional booking | Done: 40 of 40 tests pass, including 20 parallel requests creating exactly one booking on MongoDB Atlas |
| 3. Events and approvals | Event state machine, advisor then space manager approval, audit log | Done: 64 of 64 tests pass on MongoDB Atlas; an event goes Draft to Approved and reserves its room |
| 4. Registration | Capacity, waitlist, auto-promotion | Done: 76 of 76 tests pass on MongoDB Atlas; 30 parallel registrations fill exactly 10 seats |
| 5. Attendance and feedback | Rotating QR, check-in, feedback, report | Done: 101 of 101 tests pass on MongoDB Atlas; a full event runs end to end |
| 6. Reports and polish | Utilization and attendance reports, audit log page, no-show rule, demo script | Done: Phase 6 tests pass on MongoDB Atlas; reports and audit log checked on the live app |

### Verification status

- Phase 1: `mvn test` passes on a developer machine (Java 21, Maven, Spring Boot 3.3.5): token handling and role access.
  Not yet exercised end to end against a real MongoDB (register, login, role change).
- Phase 2: `mvn test` passes 40 of 40 against a MongoDB Atlas replica set: the 16 conflict-engine tests, role access for
  resources and bookings, and `BookingConcurrencyTest` (20 parallel requests for one slot create exactly one booking; a
  repeated Idempotency-Key returns the same booking; cancelling frees the slot). `BookingConcurrencyTest` is skipped unless
  `MONGODB_URI` is set; point it at a throwaway database, because it creates and deletes its own data.
  Atlas note: the cluster's IP Access List must allow the machine running the tests.
- Phase 3: `mvn test` passes 64 of 64 against MongoDB Atlas, including `EventStateMachineTest` (10), `EventAccessTest` (6)
  and `EventFlowTest` (8): Draft to Approved reserves the room; 7-day notice enforced; two events cannot hold one room;
  rejecting, cancelling or editing frees the room; only the club's own advisor can approve; the manager cannot skip the advisor.
- Phase 4: `mvn test` passes 76 of 76 against MongoDB Atlas, including `RegistrationFlowTest` (9): 30 parallel
  registrations fill exactly 10 seats and waitlist 20; cancelling a seat promotes the first waitlisted student; a cancel
  racing a new registration never overfills; waitlist positions stay in order.
- Phase 5: `mvn test` passes 101 of 101 against MongoDB Atlas, including `RotatingCodeTest` (13), `AttendanceFlowTest` (6:
  a full event runs end to end with check-in, feedback and report; old or wrong codes rejected; only confirmed registrants;
  check-in only around the event; feedback only from attendees, once) and `AttendanceAccessTest` (6).
- Phase 6: `UtilizationCalculatorTest` (6), `ReportAccessTest` (3) and `NoShowAndReportFlowTest` (3, on MongoDB Atlas) pass.
  Checked on the live app: room-use figures match the bookings made earlier, and students, organizers and resource
  admins get 403 on admin-only reports and the audit log.
- Frontend: `npm run build` passes.

## Roles

Five roles: student, club organizer, faculty advisor, resource admin, super admin.
Self-registration always creates a `STUDENT`. A `SUPER_ADMIN` creates organizer and admin accounts or changes roles
(Users page, or `POST /api/admin/users`, `PATCH /api/admin/users/{id}/role`). Role rules are enforced on the server;
the UI only hides pages.

## API

| Method and path | Who | Purpose |
| --- | --- | --- |
| POST /api/auth/register | Anyone | Create a student account |
| POST /api/auth/login | Anyone | Get a JWT |
| GET /api/users/me | Logged in | Current user |
| GET /api/admin/users | Super admin | List users |
| POST /api/admin/users | Super admin | Create a user with any role |
| PATCH /api/admin/users/{id}/role | Super admin | Change a role (not your own) |

| GET /api/resources | Logged in | List bookable resources (admins can add `?includeInactive=true`) |
| POST /api/resources, PUT /api/resources/{id} | Resource admin, super admin | Add or edit a room, lab or equipment |
| GET/POST /api/blocked-periods, DELETE /api/blocked-periods/{id} | Read: logged in. Write: resource admin, super admin | Holidays, exams, maintenance, classes. Omit `resourceId` to block everything |
| GET/POST/DELETE /api/training | Resource admin, super admin | Record or revoke training for a lab or equipment |
| POST /api/bookings/check | Organizer, resource admin, super admin | Dry run: lists every rule the booking would break |
| POST /api/bookings | Same | Request a booking. Needs an `Idempotency-Key` header; repeating a key returns the same booking |
| GET /api/bookings/mine | Same | Your requests |
| GET /api/bookings/schedule?resourceId&from&to | Same | Times already taken (no names or titles) |
| GET /api/bookings?status= | Resource admin, super admin | All requests |
| PATCH /api/bookings/{id}/approve, /reject | Resource admin, super admin | Decide a pending request (reject needs a reason) |
| PATCH /api/bookings/{id}/cancel | Owner, or resource admin | Cancel before the start time; under 72 hours is flagged as a late cancellation |

| GET /api/clubs, GET /api/clubs/mine | Logged in | Clubs (all, or the ones you organize or advise) |
| POST /api/clubs, PUT /api/clubs/{id} | Super admin | Create or edit a club: one faculty advisor, any number of organizers |
| GET /api/events | Logged in | Approved upcoming events (all a student sees) |
| GET /api/events/mine | Organizer | Events of your clubs, any status |
| POST /api/events, PUT /api/events/{id} | Organizer of that club | Draft or edit an event. Editing after submission sends it back to draft and frees the room |
| POST /api/events/{id}/submit | Organizer of that club | At least 7 days ahead (`EVENT_MIN_LEAD_DAYS`); reserves the venue through the conflict engine |
| POST /api/events/{id}/advisor-approve, /advisor-reject | That club's faculty advisor | First approval step (reject needs a reason) |
| POST /api/events/{id}/manager-approve, /manager-reject | Resource admin, super admin | Final step; approving confirms the room booking and publishes the event |
| POST /api/events/{id}/cancel, /complete | Organizer of that club, super admin | Cancel frees the room; complete only after the event ends |
| GET /api/events/seats | Logged in | Seats left, waitlist length, and your own place for every published event |
| POST /api/events/{id}/register, /unregister | Logged in | Register (or join the waitlist when full); cancel. Cancelling a seat promotes the first waitlisted student |
| GET /api/registrations/mine | Logged in | Your registrations with waitlist position |
| GET /api/events/{id}/registrations | Club organizers, advisor, admins | Registered list and waitlist in order |
| GET /api/events/{id}/checkin-code | Club organizers, advisor, admins | Current 6-character code (changes every 30 s), live check-in count |
| POST /api/events/{id}/checkin | Confirmed registrant | Check in with the code (current or previous one). Opens 30 min before start, closes at the end |
| POST /api/events/{id}/feedback | Students who checked in | Rating 1 to 5 and optional comment, once, up to 14 days after the event |
| GET /api/events/{id}/report | Club organizers, advisor, admins | Registered, attended, no-shows, attendance rate, ratings, anonymous comments |
| GET /api/registrations/standing | Logged in | Your recent no-shows and whether registration is paused |
| GET /api/reports/utilization?from&to | Resource admin, super admin | Approved booked time against opening hours, per resource |
| GET /api/reports/attendance?from&to | Resource admin, super admin | Registered, attended and no-shows for ended events, by event and club |
| GET /api/admin/audit?limit= | Super admin | Every change to every event, newest first |
| GET /api/events/{id}/history | Club organizers, advisor, admins | Audit log: who did what, when, and why |

**Check-in on a phone during development:** the QR code links to the address the organizer's browser is using, so
`localhost` only works on the same computer. To scan with a phone on the same Wi-Fi, start the frontend with
`npm run dev -- --host` and open the check-in screen through the address it prints (for example `http://192.168.1.20:5173`).
Students can always type the 6-character code instead.

A booking that breaks rules returns HTTP 409 with one entry per rule in `fields`
(`OVERLAP`, `BUFFER`, `CAPACITY`, `BLOCKED`, `ACCESS`, `OUTSIDE_HOURS`, `RESOURCE_INACTIVE`, `INVALID_TIME`).
Opening hours use the time zone in `BOOKING_ZONE` (default Asia/Kolkata). A pending request already holds its slot.

Errors use one shape: `{"status": 409, "message": "...", "fields": {}}`.

## Deploying

The website runs on **Vercel**, the backend on **Render** (Vercel cannot run a long-running Java server), the data on **MongoDB Atlas**.

1. **Backend (Render).** New > Blueprint > this repository. `render.yaml` builds `backend/Dockerfile`; enter your Atlas
   `MONGODB_URI` when asked (`JWT_SECRET` is generated). Atlas Network Access must allow Render (`0.0.0.0/0`).
   Check `https://<service>.onrender.com/api/health` returns `{"status":"ok"}`.
2. **Website (Vercel).** Import this repository with root directory `frontend`. `frontend/vercel.json` forwards `/api/*`
   to the Render service; change its URL there if your service has a different name.
3. On Render's free tier the backend sleeps after 15 minutes without traffic, and the first request then takes about a
   minute. Open the health URL a minute before a demo.

## Not built (parked in the plan)

Paid tickets, single sign-on and student-system integration, a mobile app, recurring bookings, event
recommendations, and email notifications (the email provider is still an open decision). Check-in for room
bookings (the plan's 45-minute grace window) is also not built: no-shows are tracked for event registrations.
