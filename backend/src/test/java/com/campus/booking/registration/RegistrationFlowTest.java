package com.campus.booking.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.campus.booking.booking.Requester;
import com.campus.booking.exception.ApiExceptions.ConflictException;
import com.campus.booking.model.Event;
import com.campus.booking.model.EventStatus;
import com.campus.booking.model.Registration;
import com.campus.booking.model.RegistrationStatus;
import com.campus.booking.model.Role;
import com.campus.booking.repository.EventRepository;
import com.campus.booking.repository.RegistrationRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The Phase 4 acceptance test: cancelling one seat promotes the first waitlisted student, and parallel
 * registrations never overfill an event. Runs only when MONGODB_URI points at a replica set.
 * Events are inserted directly as APPROVED so these tests focus on registration alone.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MONGODB_URI", matches = ".+")
class RegistrationFlowTest {

    @Autowired RegistrationService service;
    @Autowired EventRepository events;
    @Autowired RegistrationRepository registrations;

    private final List<String> created = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String id : created) {
            registrations.deleteByEventId(id);
            events.deleteById(id);
        }
    }

    private Event approvedEvent(int seats) {
        Event e = new Event();
        e.setClubId("test-club-" + UUID.randomUUID());
        e.setTitle("Registration test");
        e.setResourceId("test-room");
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        e.setStart(start);
        e.setEnd(start.plus(2, ChronoUnit.HOURS));
        e.setExpectedAttendees(seats);
        e.setStatus(EventStatus.APPROVED);
        e = events.save(e);
        created.add(e.getId());
        return e;
    }

    private static Requester student(String name) {
        return new Requester(name + "@campus.test", Role.STUDENT);
    }

    private RegistrationStatus statusOf(Event e, String name) {
        return registrations.findByEventIdAndUserEmail(e.getId(), name + "@campus.test").orElseThrow().getStatus();
    }

    private long count(Event e, RegistrationStatus s) {
        return registrations.countByEventIdAndStatus(e.getId(), s);
    }

    private static List<Object> inParallel(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<Object> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    return task.call();
                } catch (Exception e) {
                    return e;
                }
            }));
        }
        ready.await();
        go.countDown();
        List<Object> results = new ArrayList<>();
        for (Future<Object> f : futures) {
            results.add(f.get());
        }
        pool.shutdown();
        return results;
    }

    @Test
    void thirtyParallelRegistrationsFillExactlyTenSeats() throws Exception {
        Event e = approvedEvent(10);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String name = "s" + i;
            tasks.add(() -> service.register(e.getId(), student(name)));
        }
        List<Object> results = inParallel(tasks);
        assertTrue(results.stream().allMatch(r -> r instanceof Registration), "Failures: " + results);
        assertEquals(10, count(e, RegistrationStatus.CONFIRMED));
        assertEquals(20, count(e, RegistrationStatus.WAITLISTED));
    }

    @Test
    void cancellingOneSeatPromotesTheFirstWaitlistedStudent() {
        Event e = approvedEvent(2);
        service.register(e.getId(), student("a"));
        service.register(e.getId(), student("b"));
        service.register(e.getId(), student("c"));
        service.register(e.getId(), student("d"));
        assertEquals(RegistrationStatus.WAITLISTED, statusOf(e, "c"));

        service.cancel(e.getId(), student("a"));

        assertEquals(RegistrationStatus.CANCELLED, statusOf(e, "a"));
        Registration c = registrations.findByEventIdAndUserEmail(e.getId(), "c@campus.test").orElseThrow();
        assertEquals(RegistrationStatus.CONFIRMED, c.getStatus());
        assertTrue(c.isPromoted());
        assertEquals(RegistrationStatus.WAITLISTED, statusOf(e, "d"), "only one seat was freed");
        assertEquals(2, count(e, RegistrationStatus.CONFIRMED));
    }

    @Test
    void cancellingFromTheWaitlistPromotesNobody() {
        Event e = approvedEvent(1);
        service.register(e.getId(), student("a"));
        service.register(e.getId(), student("b"));
        service.register(e.getId(), student("c"));
        service.cancel(e.getId(), student("b"));
        assertEquals(RegistrationStatus.CONFIRMED, statusOf(e, "a"));
        assertEquals(RegistrationStatus.WAITLISTED, statusOf(e, "c"));
        assertEquals(1, count(e, RegistrationStatus.CONFIRMED));
    }

    @Test
    void registeringTwiceKeepsOnePlace() {
        Event e = approvedEvent(5);
        Registration first = service.register(e.getId(), student("a"));
        Registration second = service.register(e.getId(), student("a"));
        assertEquals(first.getId(), second.getId());
        assertEquals(1, count(e, RegistrationStatus.CONFIRMED));
    }

    @Test
    void reRegisteringAfterCancellingJoinsTheBackOfTheQueue() {
        Event e = approvedEvent(1);
        service.register(e.getId(), student("a"));
        service.register(e.getId(), student("b"));
        service.cancel(e.getId(), student("a"));          // b is promoted
        Registration again = service.register(e.getId(), student("a"));
        assertEquals(RegistrationStatus.WAITLISTED, again.getStatus());
        assertEquals(RegistrationStatus.CONFIRMED, statusOf(e, "b"));
    }

    @Test
    void cancelAndRegisterRacingNeverOverfill() throws Exception {
        Event e = approvedEvent(1);
        service.register(e.getId(), student("a"));
        service.register(e.getId(), student("b"));        // waitlisted, first in queue
        List<Object> results = inParallel(List.of(
                () -> service.cancel(e.getId(), student("a")),
                () -> service.register(e.getId(), student("c"))));
        assertTrue(results.stream().allMatch(r -> r instanceof Registration), "Failures: " + results);
        assertEquals(1, count(e, RegistrationStatus.CONFIRMED));
        // b was queued first. c can only take the seat if c registered after a cancelled and b was already promoted,
        // so either way b holds the seat.
        assertEquals(RegistrationStatus.CONFIRMED, statusOf(e, "b"));
        assertEquals(RegistrationStatus.WAITLISTED, statusOf(e, "c"));
    }

    @Test
    void moreSeatsOnReapprovalPromoteFromTheWaitlist() {
        Event e = approvedEvent(1);
        service.register(e.getId(), student("a"));
        service.register(e.getId(), student("b"));
        service.register(e.getId(), student("c"));
        Event reloaded = events.findById(e.getId()).orElseThrow();
        reloaded.setExpectedAttendees(2);
        events.save(reloaded);
        service.fillFromWaitlist(e.getId());
        assertEquals(RegistrationStatus.CONFIRMED, statusOf(e, "b"));
        assertEquals(RegistrationStatus.WAITLISTED, statusOf(e, "c"));
    }

    @Test
    void onlyApprovedEventsTakeRegistrations() {
        Event e = approvedEvent(5);
        Event reloaded = events.findById(e.getId()).orElseThrow();
        reloaded.setStatus(EventStatus.SUBMITTED);
        events.save(reloaded);
        assertThrows(ConflictException.class, () -> service.register(e.getId(), student("a")));
        assertFalse(registrations.findByEventIdAndUserEmail(e.getId(), "a@campus.test").isPresent());
    }

    @Test
    void waitlistPositionsAreInOrder() {
        Event e = approvedEvent(1);
        for (String name : List.of("a", "b", "c", "d")) {
            service.register(e.getId(), student(name));
        }
        List<Integer> positions = new ArrayList<>();
        for (String name : List.of("b", "c", "d")) {
            var summary = service.summaries(List.of(events.findById(e.getId()).orElseThrow()), student(name)).get(0);
            positions.add(summary.myWaitlistPosition());
        }
        assertEquals(List.of(1, 2, 3), positions);
        Set<Integer> unique = new HashSet<>(positions);
        assertEquals(3, unique.size());
    }
}
