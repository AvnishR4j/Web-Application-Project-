package com.campus.booking.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.campus.booking.model.BookingStatus;
import com.campus.booking.model.Resource;
import com.campus.booking.model.ResourceType;
import com.campus.booking.model.Role;
import com.campus.booking.repository.BookingRepository;
import com.campus.booking.repository.ResourceRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The Phase 2 acceptance test: 20 parallel requests for one slot create exactly one booking.
 *
 * Needs a real MongoDB replica set (transactions do not work on a standalone server), so it only runs
 * when MONGODB_URI is set, for example to a MongoDB Atlas free cluster:
 *   MONGODB_URI="mongodb+srv://user:pass@cluster.mongodb.net/campus_booking_test" mvn test
 * Use a throwaway database name. The test creates and removes its own resource.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MONGODB_URI", matches = ".+")
class BookingConcurrencyTest {

    @Autowired BookingService service;
    @Autowired ResourceRepository resources;
    @Autowired BookingRepository bookings;

    private Resource resource;
    private final Requester admin = new Requester("concurrency-test@campus.test", Role.RESOURCE_ADMIN);

    @BeforeEach
    void createResource() {
        resource = new Resource();
        resource.setName("Concurrency Test Hall " + UUID.randomUUID());
        resource.setType(ResourceType.ROOM);
        resource.setCapacity(100);
        resource.setBufferMinutes(15);
        resource = resources.save(resource);
    }

    @AfterEach
    void cleanUp() {
        bookings.deleteByResourceId(resource.getId());
        resources.deleteById(resource.getId());
    }

    private BookingService.BookingInput slot(int startHour) {
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS).plus(startHour, ChronoUnit.HOURS);
        return new BookingService.BookingInput(resource.getId(), "Race", start, start.plus(2, ChronoUnit.HOURS), 10);
    }

    private <T> List<Object> runInParallel(int n, java.util.function.IntFunction<Callable<T>> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Callable<T> c = task.apply(i);
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    return c.call();
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
    void twentyParallelRequestsForOneSlotCreateExactlyOneBooking() throws Exception {
        var input = slot(9);
        List<Object> results = runInParallel(20,
                i -> () -> service.create(input, "key-" + UUID.randomUUID(), admin));

        long created = results.stream().filter(r -> r instanceof com.campus.booking.model.Booking).count();
        long refused = results.stream().filter(r -> r instanceof BookingConflictException).count();
        List<Object> unexpected = results.stream().filter(r -> r instanceof Exception
                && !(r instanceof BookingConflictException)).toList();

        assertTrue(unexpected.isEmpty(), "Unexpected failures: " + unexpected);
        assertEquals(1, created, "exactly one request should win the slot");
        assertEquals(19, refused, "the other 19 should be told the slot is taken");
        assertEquals(1, bookings.countByResourceId(resource.getId()));
    }

    @Test
    void parallelRequestsForDifferentFreeSlotsAllSucceed() throws Exception {
        // 8 slots, 3 hours apart, so buffers never interfere. Only real conflicts should fail.
        List<Object> results = runInParallel(8,
                i -> () -> service.create(slot(i * 3), "key-" + UUID.randomUUID(), admin));
        assertTrue(results.stream().noneMatch(r -> r instanceof Exception), "Failures: " + results);
        assertEquals(8, bookings.countByResourceId(resource.getId()));
    }

    @Test
    void repeatingTheSameKeyReturnsTheSameBooking() throws Exception {
        var input = slot(9);
        List<Object> results = runInParallel(5, i -> () -> service.create(input, "same-key", admin));
        Set<String> ids = new HashSet<>();
        for (Object r : results) {
            assertTrue(r instanceof com.campus.booking.model.Booking, "Unexpected: " + r);
            ids.add(((com.campus.booking.model.Booking) r).getId());
        }
        assertEquals(1, ids.size());
        assertEquals(1, bookings.countByResourceId(resource.getId()));
    }

    @Test
    void cancellingFreesTheSlot() {
        var input = slot(9);
        var first = service.create(input, "k1", admin);
        assertEquals(BookingStatus.PENDING, first.getStatus());
        service.cancel(first.getId(), admin);
        var second = service.create(input, "k2", admin);
        assertEquals(BookingStatus.PENDING, second.getStatus());
    }
}
