package com.campus.booking.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.campus.booking.booking.ConflictEngine.Code;
import com.campus.booking.booking.ConflictEngine.Proposal;
import com.campus.booking.model.BlockedKind;
import com.campus.booking.model.BlockedPeriod;
import com.campus.booking.model.Booking;
import com.campus.booking.model.BookingStatus;
import com.campus.booking.model.Resource;
import com.campus.booking.model.ResourceType;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConflictEngineTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    private static Instant at(String time) {
        return Instant.parse("2030-01-10T" + time + ":00Z");
    }

    private static Resource room(int capacity, int buffer) {
        Resource r = new Resource();
        r.setId("r1");
        r.setName("Seminar Hall");
        r.setType(ResourceType.ROOM);
        r.setCapacity(capacity);
        r.setBufferMinutes(buffer);
        r.setActive(true);
        return r;
    }

    private static Booking booking(String start, String end, BookingStatus status) {
        Booking b = new Booking();
        b.setResourceId("r1");
        b.setStart(at(start));
        b.setEnd(at(end));
        b.setStatus(status);
        return b;
    }

    private static Set<Code> run(Resource r, Proposal p, List<Booking> existing, List<BlockedPeriod> blocked) {
        Set<Code> codes = EnumSet.noneOf(Code.class);
        ConflictEngine.evaluate(r, p, existing, blocked, ZONE, NOW).forEach(v -> codes.add(v.code()));
        return codes;
    }

    private static Proposal slot(String start, String end, int attendees) {
        return new Proposal(at(start), at(end), attendees, true);
    }

    @Test
    void freeSlotHasNoViolations() {
        assertTrue(run(room(100, 15), slot("10:00", "12:00", 50), List.of(), List.of()).isEmpty());
    }

    @Test
    void sameTimeOverlaps() {
        var existing = List.of(booking("10:00", "12:00", BookingStatus.APPROVED));
        assertEquals(Set.of(Code.OVERLAP), run(room(100, 0), slot("10:00", "12:00", 10), existing, List.of()));
    }

    @Test
    void partialOverlapOnEitherSideIsDetected() {
        var existing = List.of(booking("10:00", "12:00", BookingStatus.PENDING));
        assertEquals(Set.of(Code.OVERLAP), run(room(100, 0), slot("09:00", "10:30", 10), existing, List.of()));
        assertEquals(Set.of(Code.OVERLAP), run(room(100, 0), slot("11:30", "13:00", 10), existing, List.of()));
        assertEquals(Set.of(Code.OVERLAP), run(room(100, 0), slot("10:30", "11:00", 10), existing, List.of()));
        assertEquals(Set.of(Code.OVERLAP), run(room(100, 0), slot("09:00", "13:00", 10), existing, List.of()));
    }

    @Test
    void backToBackIsAllowedWhenThereIsNoBuffer() {
        var existing = List.of(booking("10:00", "12:00", BookingStatus.APPROVED));
        assertTrue(run(room(100, 0), slot("12:00", "13:00", 10), existing, List.of()).isEmpty());
        assertTrue(run(room(100, 0), slot("09:00", "10:00", 10), existing, List.of()).isEmpty());
    }

    @Test
    void bufferIsRequiredOnBothSides() {
        var existing = List.of(booking("10:00", "12:00", BookingStatus.APPROVED));
        assertEquals(Set.of(Code.BUFFER), run(room(100, 15), slot("12:00", "13:00", 10), existing, List.of()));
        assertEquals(Set.of(Code.BUFFER), run(room(100, 15), slot("12:10", "13:00", 10), existing, List.of()));
        assertEquals(Set.of(Code.BUFFER), run(room(100, 15), slot("09:00", "09:50", 10), existing, List.of()));
    }

    @Test
    void gapExactlyEqualToBufferIsAllowed() {
        var existing = List.of(booking("10:00", "12:00", BookingStatus.APPROVED));
        assertTrue(run(room(100, 15), slot("12:15", "13:00", 10), existing, List.of()).isEmpty());
        assertTrue(run(room(100, 15), slot("09:00", "09:45", 10), existing, List.of()).isEmpty());
    }

    @Test
    void overlapIsReportedInsteadOfBufferNotBoth() {
        var existing = List.of(booking("10:00", "12:00", BookingStatus.APPROVED));
        assertEquals(Set.of(Code.OVERLAP), run(room(100, 45), slot("11:00", "13:00", 10), existing, List.of()));
    }

    @Test
    void cancelledAndRejectedBookingsDoNotHoldTheSlot() {
        var existing = List.of(booking("10:00", "12:00", BookingStatus.CANCELLED),
                booking("10:00", "12:00", BookingStatus.REJECTED));
        assertTrue(run(room(100, 15), slot("10:00", "12:00", 10), existing, List.of()).isEmpty());
    }

    @Test
    void capacityIsEnforcedForRoomsAndLabsButNotEquipment() {
        assertEquals(Set.of(Code.CAPACITY), run(room(40, 0), slot("10:00", "11:00", 41), List.of(), List.of()));
        assertTrue(run(room(40, 0), slot("10:00", "11:00", 40), List.of(), List.of()).isEmpty());

        Resource projector = room(0, 0);
        projector.setType(ResourceType.EQUIPMENT);
        assertTrue(run(projector, slot("10:00", "11:00", 80), List.of(), List.of()).isEmpty());
    }

    private static BlockedPeriod blocked(String start, String end) {
        BlockedPeriod b = new BlockedPeriod();
        b.setStart(at(start));
        b.setEnd(at(end));
        b.setKind(BlockedKind.EXAM);
        b.setReason("Midterms");
        return b;
    }

    @Test
    void blockedPeriodStopsBookingsThatTouchIt() {
        var blocked = List.of(blocked("09:00", "13:00"));
        assertEquals(Set.of(Code.BLOCKED), run(room(100, 0), slot("12:00", "14:00", 10), List.of(), blocked));
        assertTrue(run(room(100, 0), slot("13:00", "14:00", 10), List.of(), blocked).isEmpty());
    }

    @Test
    void trainingGateAppliesOnlyWhenRequired() {
        Resource lab = room(40, 0);
        lab.setType(ResourceType.LAB);
        lab.setRequiresTraining(true);
        var untrained = new Proposal(at("10:00"), at("11:00"), 10, false);
        assertEquals(Set.of(Code.ACCESS), run(lab, untrained, List.of(), List.of()));
        assertTrue(run(lab, slot("10:00", "11:00", 10), List.of(), List.of()).isEmpty());
        assertTrue(run(room(40, 0), untrained, List.of(), List.of()).isEmpty());
    }

    @Test
    void openingHoursAreEnforced() {
        Resource r = room(100, 0);
        r.setOpenTime("08:00");
        r.setCloseTime("20:00");
        assertTrue(run(r, slot("08:00", "20:00", 10), List.of(), List.of()).isEmpty());
        assertEquals(Set.of(Code.OUTSIDE_HOURS), run(r, slot("07:30", "09:00", 10), List.of(), List.of()));
        assertEquals(Set.of(Code.OUTSIDE_HOURS), run(r, slot("19:00", "20:30", 10), List.of(), List.of()));
        var overnight = new Proposal(at("19:00"), Instant.parse("2030-01-11T09:00:00Z"), 10, true);
        assertEquals(Set.of(Code.OUTSIDE_HOURS), run(r, overnight, List.of(), List.of()));
    }

    @Test
    void openingHoursUseTheCampusTimeZone() {
        Resource r = room(100, 0);
        r.setOpenTime("08:00");
        r.setCloseTime("20:00");
        // 03:00-04:00 UTC is 08:30-09:30 in Asia/Kolkata (UTC+5:30), which is inside opening hours.
        var p = new Proposal(at("03:00"), at("04:00"), 10, true);
        var codes = ConflictEngine.evaluate(r, p, List.of(), List.of(), ZoneId.of("Asia/Kolkata"), NOW);
        assertTrue(codes.isEmpty());
    }

    @Test
    void badTimesAreRejectedEarly() {
        assertEquals(Set.of(Code.INVALID_TIME), run(room(100, 0), slot("12:00", "10:00", 10), List.of(), List.of()));
        assertEquals(Set.of(Code.INVALID_TIME), run(room(100, 0), slot("10:00", "10:00", 10), List.of(), List.of()));
        var past = new Proposal(Instant.parse("2029-12-31T10:00:00Z"), Instant.parse("2029-12-31T11:00:00Z"), 1, true);
        assertEquals(Set.of(Code.INVALID_TIME), run(room(100, 0), past, List.of(), List.of()));
    }

    @Test
    void inactiveResourceCannotBeBooked() {
        Resource r = room(100, 0);
        r.setActive(false);
        assertEquals(Set.of(Code.RESOURCE_INACTIVE), run(r, slot("10:00", "11:00", 10), List.of(), List.of()));
    }

    @Test
    void severalProblemsAreReportedTogether() {
        Resource r = room(10, 15);
        r.setRequiresTraining(true);
        var existing = List.of(booking("10:00", "12:00", BookingStatus.APPROVED));
        var p = new Proposal(at("11:00"), at("13:00"), 50, false);
        assertEquals(Set.of(Code.CAPACITY, Code.OVERLAP, Code.ACCESS), run(r, p, existing, List.of()));
    }
}
