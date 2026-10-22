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

Requirements: Java 21, Maven 3.9+, Node 20+, Docker.

1. **Database.** Transactions (needed for conflict-free booking in Phase 2) only work on a replica set,
   so use the compose file, which sets one up:
   ```
   docker compose up -d
   ```
2. **Backend** (http://localhost:8080):
   ```
   cd backend
   SEED_DEMO_DATA=true mvn spring-boot:run
   ```
3. **Frontend** (http://localhost:5173):
   ```
   cd frontend
   npm install
   npm run dev
   ```

Run the backend tests with `cd backend && mvn test`.

### Demo accounts (only when `SEED_DEMO_DATA=true`, development only)

| Role | Email | Password |
| --- | --- | --- |
| Student | student@campus.test | Password@123 |
| Club organizer | organizer@campus.test | Password@123 |
| Resource admin | resources@campus.test | Password@123 |
| Super admin | admin@campus.test | Password@123 |

Set a real `JWT_SECRET` (32+ characters) outside development. See `.env.example`.

## Build status

| Phase | Scope | Status |
| --- | --- | --- |
| 1. Foundation | Repo, replica set, auth, four roles, role-protected pages, seed data | Done: `mvn test` passes (10 of 10) |
| 2. Resources and conflict engine | Resource catalog, five conflict checks, transactional booking | Done: 40 of 40 tests pass, including 20 parallel requests creating exactly one booking on MongoDB Atlas |
| 3. Events and approvals | Event state machine, advisor then space manager approval, audit log | Not started |
| 4. Registration | Capacity, waitlist, auto-promotion | Not started |
| 5. Attendance and feedback | Rotating QR, check-in, feedback, notifications | Not started |
| 6. Reports and polish | Utilization report, dashboard, docs, demo script | Not started |

### Verification status

- Phase 1: `mvn test` passes on a developer machine (Java 21, Maven, Spring Boot 3.3.5): token handling and role access.
  Not yet exercised end to end against a real MongoDB (register, login, role change).
- Phase 2: `mvn test` passes 40 of 40 against a MongoDB Atlas replica set: the 16 conflict-engine tests, role access for
  resources and bookings, and `BookingConcurrencyTest` (20 parallel requests for one slot create exactly one booking; a
  repeated Idempotency-Key returns the same booking; cancelling frees the slot). `BookingConcurrencyTest` is skipped unless
  `MONGODB_URI` is set; point it at a throwaway database, because it creates and deletes its own data.
  Atlas note: the cluster's IP Access List must allow the machine running the tests.
- Frontend: `npm run build` passes.

## Roles

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

A booking that breaks rules returns HTTP 409 with one entry per rule in `fields`
(`OVERLAP`, `BUFFER`, `CAPACITY`, `BLOCKED`, `ACCESS`, `OUTSIDE_HOURS`, `RESOURCE_INACTIVE`, `INVALID_TIME`).
Opening hours use the time zone in `BOOKING_ZONE` (default Asia/Kolkata). A pending request already holds its slot.

Errors use one shape: `{"status": 409, "message": "...", "fields": {}}`.
