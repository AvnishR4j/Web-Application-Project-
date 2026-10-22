# Demo script

A walk through every workflow in about 15 minutes, using the demo accounts. Every password is `Password@123`.

## Before you start

1. A MongoDB replica set: either `docker compose up -d`, or a free MongoDB Atlas cluster (see the README).
2. Start the backend with demo data and **no notice period**, so the demo event can start within minutes:
   ```
   cd backend
   MONGODB_URI="<your connection string>" SEED_DEMO_DATA=true EVENT_MIN_LEAD_DAYS=0 mvn spring-boot:run
   ```
   Leave `MONGODB_URI` out if you use Docker.
3. Start the frontend: `cd frontend && npm install && npm run dev`, then open http://localhost:5173.

The demo data gives you five roles, three rooms (Seminar Hall, Computer Lab 1, Portable Projector) and a
"Coding Club" with `organizer@campus.test` as organizer and `advisor@campus.test` as faculty advisor.

Use a private browser window per person if you want several people logged in at once; otherwise log out
between steps.

## 1. Conflict checks on a room booking (organizer)

Log in as **organizer@campus.test**, open **Book**.

1. Pick **Computer Lab 1**, any time tomorrow 10:00 to 11:00, **50** attendees, click **Check availability**.
   It lists two problems: 50 exceeds the lab's 40 seats, and the lab needs training sign-off.
2. Switch to **Seminar Hall**, 50 attendees. **Check availability** says it is free. **Submit request**.
3. Try the same hall and time again. It is refused: the hall is already booked (a pending request holds its slot).

## 2. Approve the booking (resource admin)

Log in as **resources@campus.test**, open **Booking approvals**, click **Approve**.
The organizer's **My bookings** now shows it as approved.

## 3. An event through two approvals (organizer, advisor, resource admin)

1. **organizer@campus.test**, **My events**: club Coding Club, venue Seminar Hall, **3** seats, title
   "Demo talk", start **15 minutes from now**, end an hour later. **Save as draft**, then **Submit**.
   (If the hall is busy at that time, the conflict engine says so; pick a later slot or another room.)
2. **advisor@campus.test**, **Advisor review**: **Approve**. (Try **Reject** on another event: a reason is required.)
3. **resources@campus.test**, **Event approvals**: **Approve**. The event is now published and holds the room.
4. Back as the organizer, click **History** on the event: every step, who did it, and when.

## 4. Registration and the waitlist (three students)

1. To see the waitlist, give the event **2** seats in step 3 (or edit it to 2; it goes back for approval).
   **student@**, **student2@** and **student3@campus.test** each open **Events** and click **Register**.
   The first two are registered; the third lands on the **waitlist** as #1.
2. As one of the registered students, click **Cancel registration**. The first student on the waitlist moves up
   automatically; their **My registrations** says "Moved up from the waitlist".

## 5. Check-in, feedback and the event report

1. **organizer@campus.test**, **My events**, click **Check-in screen** on the event. A QR code and a 6-character
   code appear; both change every 30 seconds, and the count of checked-in students updates live.
2. As a registered student, open **My registrations** and click **Check in**, then type the code on the screen.
   (Scanning the QR with a phone works when the frontend runs with `npm run dev -- --host`.)
3. Try an old code a minute later: it is refused, so a photo shared in a group chat is useless.
4. As the student, give feedback on **My registrations** (rating and comment). A second attempt is refused,
   and a student who did not check in cannot give feedback.
5. As the organizer, open **Report** on the event: registered, attended, attendance rate, ratings and
   anonymous comments.

## 6. Reports and the audit log (admins)

1. **resources@campus.test**, **Reports**: how much each room was booked against its opening hours, and
   attendance and no-shows by club and by event for events that have ended.
2. **admin@campus.test**, **Audit log**: every change to every event, filterable by event, person or action.

## 7. The no-show rule

A student who misses 3 events they registered for within 60 days (`NOSHOW_LIMIT`, `NOSHOW_WINDOW_DAYS`) cannot
register for new events until older misses pass the 60-day mark. **My registrations** warns them before that.
This is covered by `NoShowAndReportFlowTest`; demonstrating it live needs events that have already ended.

## Proving the hard parts

`mvn test` with `MONGODB_URI` set runs the concurrency tests against a real replica set:
20 parallel requests for one room slot create exactly one booking, and 30 parallel registrations for
10 seats confirm exactly 10 and waitlist 20.
