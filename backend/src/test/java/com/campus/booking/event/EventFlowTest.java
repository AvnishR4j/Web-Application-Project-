package com.campus.booking.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.campus.booking.booking.BookingConflictException;
import com.campus.booking.booking.BookingService;
import com.campus.booking.booking.Requester;
import com.campus.booking.dto.EventDtos.EventRequest;
import com.campus.booking.exception.ApiExceptions.BadRequestException;
import com.campus.booking.exception.ApiExceptions.ConflictException;
import com.campus.booking.exception.ApiExceptions.ForbiddenException;
import com.campus.booking.model.BookingStatus;
import com.campus.booking.model.Club;
import com.campus.booking.model.Event;
import com.campus.booking.model.EventStatus;
import com.campus.booking.model.Resource;
import com.campus.booking.model.ResourceType;
import com.campus.booking.model.Role;
import com.campus.booking.repository.AuditLogRepository;
import com.campus.booking.repository.BookingRepository;
import com.campus.booking.repository.ClubRepository;
import com.campus.booking.repository.EventRepository;
import com.campus.booking.repository.ResourceRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The Phase 3 acceptance test: an event goes Draft to Approved and reserves its room.
 * Runs only when MONGODB_URI points at a replica set (for example MongoDB Atlas). Creates and removes its own data.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MONGODB_URI", matches = ".+")
class EventFlowTest {

    @Autowired EventService service;
    @Autowired BookingService bookingService;
    @Autowired ResourceRepository resources;
    @Autowired ClubRepository clubs;
    @Autowired EventRepository events;
    @Autowired BookingRepository bookings;
    @Autowired AuditLogRepository audit;

    private Resource room;
    private Club club;
    private Requester organizer;
    private Requester advisor;
    private final Requester manager = new Requester("manager-" + UUID.randomUUID() + "@campus.test", Role.RESOURCE_ADMIN);

    @BeforeEach
    void setUp() {
        String tag = UUID.randomUUID().toString();
        organizer = new Requester("org-" + tag + "@campus.test", Role.CLUB_ORGANIZER);
        advisor = new Requester("adv-" + tag + "@campus.test", Role.FACULTY_ADVISOR);

        room = new Resource();
        room.setName("Event Flow Hall " + tag);
        room.setType(ResourceType.ROOM);
        room.setCapacity(100);
        room.setBufferMinutes(15);
        room = resources.save(room);

        club = new Club();
        club.setName("Event Flow Club " + tag);
        club.setAdvisorEmail(advisor.email());
        club.setOrganizerEmails(List.of(organizer.email()));
        club = clubs.save(club);
    }

    @AfterEach
    void cleanUp() {
        events.findByClubIdInOrderByStartDesc(List.of(club.getId())).forEach(e -> audit.deleteByEventId(e.getId()));
        events.deleteByClubId(club.getId());
        bookings.deleteByResourceId(room.getId());
        resources.deleteById(room.getId());
        clubs.deleteById(club.getId());
    }

    private EventRequest request(int daysAhead, int startHour) {
        Instant start = Instant.now().plus(daysAhead, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS)
                .plus(startHour, ChronoUnit.HOURS);
        return new EventRequest(club.getId(), "Flow test", null, room.getId(), start, start.plus(2, ChronoUnit.HOURS), 40);
    }

    @Test
    void draftToApprovedReservesTheRoom() {
        Event e = service.create(request(10, 9), organizer);
        assertEquals(EventStatus.DRAFT, e.getStatus());
        assertNull(e.getBookingId());

        e = service.submit(e.getId(), organizer);
        assertEquals(EventStatus.SUBMITTED, e.getStatus());
        var hold = bookings.findById(e.getBookingId()).orElseThrow();
        assertEquals(BookingStatus.PENDING, hold.getStatus());
        assertEquals(e.getId(), hold.getEventId());

        e = service.advisorApprove(e.getId(), advisor);
        assertEquals(EventStatus.ADVISOR_APPROVED, e.getStatus());

        e = service.managerApprove(e.getId(), manager);
        assertEquals(EventStatus.APPROVED, e.getStatus());
        assertEquals(BookingStatus.APPROVED, bookings.findById(e.getBookingId()).orElseThrow().getStatus());

        String id = e.getId();
        assertTrue(service.published().stream().anyMatch(p -> p.getId().equals(id)), "students should now see it");
        var history = audit.findByEventIdOrderByAtAsc(id).stream().map(a -> a.getAction()).toList();
        assertEquals(List.of("CREATED", "SUBMITTED", "ADVISOR_APPROVED", "APPROVED"), history);
    }

    @Test
    void submittingTooLateIsRefused() {
        Event e = service.create(request(2, 9), organizer);
        assertThrows(BadRequestException.class, () -> service.submit(e.getId(), organizer));
        assertEquals(EventStatus.DRAFT, events.findById(e.getId()).orElseThrow().getStatus());
    }

    @Test
    void twoEventsCannotHoldTheSameRoom() {
        Event first = service.submit(service.create(request(10, 9), organizer).getId(), organizer);
        Event second = service.create(request(10, 10), organizer);
        assertThrows(BookingConflictException.class, () -> service.submit(second.getId(), organizer));
        assertEquals(EventStatus.SUBMITTED, events.findById(first.getId()).orElseThrow().getStatus());
        assertEquals(EventStatus.DRAFT, events.findById(second.getId()).orElseThrow().getStatus());
    }

    @Test
    void rejectionFreesTheRoomForSomeoneElse() {
        Event first = service.submit(service.create(request(10, 9), organizer).getId(), organizer);
        first = service.advisorReject(first.getId(), "Clashes with exams", advisor);
        assertEquals(EventStatus.REJECTED, first.getStatus());
        assertEquals("Clashes with exams", first.getRejectionReason());
        assertEquals(BookingStatus.REJECTED, bookings.findById(first.getBookingId()).orElseThrow().getStatus());

        Event second = service.submit(service.create(request(10, 9), organizer).getId(), organizer);
        assertEquals(EventStatus.SUBMITTED, second.getStatus());
    }

    @Test
    void editingAnApprovedEventNeedsApprovalAgain() {
        Event e = service.submit(service.create(request(10, 9), organizer).getId(), organizer);
        e = service.advisorApprove(e.getId(), advisor);
        e = service.managerApprove(e.getId(), manager);
        String oldBooking = e.getBookingId();

        e = service.update(e.getId(), request(11, 9), organizer);
        assertEquals(EventStatus.DRAFT, e.getStatus());
        assertNull(e.getBookingId());
        assertEquals(BookingStatus.CANCELLED, bookings.findById(oldBooking).orElseThrow().getStatus());
        String id = e.getId();
        assertTrue(service.published().stream().noneMatch(p -> p.getId().equals(id)), "hidden until approved again");

        e = service.submit(id, organizer);
        assertEquals(EventStatus.SUBMITTED, e.getStatus());
        assertNotNull(e.getBookingId());
    }

    @Test
    void onlyThisClubsAdvisorCanApproveAndManagerCannotSkipAhead() {
        Event e = service.submit(service.create(request(10, 9), organizer).getId(), organizer);
        String id = e.getId();
        Requester otherAdvisor = new Requester("someone-else@campus.test", Role.FACULTY_ADVISOR);
        assertThrows(ForbiddenException.class, () -> service.advisorApprove(id, otherAdvisor));
        assertThrows(ConflictException.class, () -> service.managerApprove(id, manager));
        Requester outsider = new Requester("other-org@campus.test", Role.CLUB_ORGANIZER);
        assertThrows(ForbiddenException.class, () -> service.cancel(id, null, outsider));
    }

    @Test
    void eventRoomBookingCannotBeChangedDirectly() {
        Event e = service.submit(service.create(request(10, 9), organizer).getId(), organizer);
        String bookingId = e.getBookingId();
        assertThrows(ConflictException.class, () -> bookingService.cancel(bookingId, organizer));
        assertThrows(ConflictException.class, () -> bookingService.approve(bookingId, manager.email()));
    }

    @Test
    void cancellingReleasesTheRoom() {
        Event e = service.submit(service.create(request(10, 9), organizer).getId(), organizer);
        e = service.cancel(e.getId(), "Speaker unavailable", organizer);
        assertEquals(EventStatus.CANCELLED, e.getStatus());
        assertEquals(BookingStatus.CANCELLED, bookings.findById(e.getBookingId()).orElseThrow().getStatus());
    }
}
