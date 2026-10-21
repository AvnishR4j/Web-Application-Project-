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
| 1. Foundation | Repo, replica set, auth, four roles, role-protected pages, seed data | Code complete |
| 2. Resources and conflict engine | Resource catalog, five conflict checks, transactional booking | Not started |
| 3. Events and approvals | Event state machine, advisor then space manager approval, audit log | Not started |
| 4. Registration | Capacity, waitlist, auto-promotion | Not started |
| 5. Attendance and feedback | Rotating QR, check-in, feedback, notifications | Not started |
| 6. Reports and polish | Utilization report, dashboard, docs, demo script | Not started |

### Phase 1 verification

- Frontend: `npm run build` passes.
- Backend: written and reviewed but **not yet compiled or tested**. The environment it was written in
  could not reach Maven Central, so run `cd backend && mvn test` first on your machine and fix anything it reports.
  The tests cover token handling (`JwtServiceTest`) and that students, organizers and resource admins
  get 403 on admin endpoints (`RoleAccessTest`).
- Not yet exercised end to end against a real MongoDB (register, login, role change).

## Roles

Self-registration always creates a `STUDENT`. A `SUPER_ADMIN` creates organizer and admin accounts or changes roles
(Users page, or `POST /api/admin/users`, `PATCH /api/admin/users/{id}/role`). Role rules are enforced on the server;
the UI only hides pages.

## API (Phase 1)

| Method and path | Who | Purpose |
| --- | --- | --- |
| POST /api/auth/register | Anyone | Create a student account |
| POST /api/auth/login | Anyone | Get a JWT |
| GET /api/users/me | Logged in | Current user |
| GET /api/admin/users | Super admin | List users |
| POST /api/admin/users | Super admin | Create a user with any role |
| PATCH /api/admin/users/{id}/role | Super admin | Change a role (not your own) |

Errors use one shape: `{"status": 409, "message": "...", "fields": {}}`.
