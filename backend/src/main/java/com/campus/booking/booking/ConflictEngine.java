package com.campus.booking.booking;

import com.campus.booking.model.BlockedPeriod;
import com.campus.booking.model.Booking;
import com.campus.booking.model.Resource;
import com.campus.booking.model.ResourceType;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The five conflict checks from the plan, as one pure function with no database access so it can be
 * tested exhaustively:
 * <ol>
 *   <li>time overlap on the same resource (start &lt; newEnd and end &gt; newStart)</li>
 *   <li>setup and teardown buffer</li>
 *   <li>capacity and resource type</li>
 *   <li>blocked periods (holidays, exams, maintenance, classes)</li>
 *   <li>access gate (training or sign-off)</li>
 * </ol>
 * Plus basic sanity: valid times, active resource, inside opening hours.
 */
public final class ConflictEngine {

    public enum Code { INVALID_TIME, RESOURCE_INACTIVE, OUTSIDE_HOURS, CAPACITY, OVERLAP, BUFFER, BLOCKED, ACCESS }

    public record Violation(Code code, String message) {
    }

    public record Proposal(Instant start, Instant end, int attendees, boolean hasAccess) {
    }

    private ConflictEngine() {
    }

    /**
     * @param existing bookings of this resource near the proposed time; cancelled and rejected ones are ignored
     * @param blocked  blocked periods that apply to this resource (its own and the global ones)
     */
    public static List<Violation> evaluate(Resource resource, Proposal p, List<Booking> existing,
                                           List<BlockedPeriod> blocked, ZoneId zone, Instant now) {
        List<Violation> out = new ArrayList<>();

        if (p.start() == null || p.end() == null || !p.end().isAfter(p.start())) {
            out.add(new Violation(Code.INVALID_TIME, "The end time must be after the start time."));
            return out;
        }
        if (!p.start().isAfter(now)) {
            out.add(new Violation(Code.INVALID_TIME, "The booking must start in the future."));
            return out;
        }

        if (!resource.isActive()) {
            out.add(new Violation(Code.RESOURCE_INACTIVE, resource.getName() + " is not available for booking."));
        }

        checkOpeningHours(resource, p, zone, out);

        // 3. Capacity applies to places people sit in, not to equipment.
        if (resource.getType() != ResourceType.EQUIPMENT && p.attendees() > resource.getCapacity()) {
            out.add(new Violation(Code.CAPACITY, "Expected attendees (" + p.attendees()
                    + ") exceed the capacity of " + resource.getName() + " (" + resource.getCapacity() + ")."));
        }

        // 1 and 2. Overlap, then buffer. One violation of each kind is enough.
        Duration buffer = Duration.ofMinutes(Math.max(0, resource.getBufferMinutes()));
        boolean overlap = false;
        boolean bufferHit = false;
        for (Booking b : existing) {
            if (b.getStatus() == null || !b.getStatus().holdsSlot()) {
                continue;
            }
            if (b.getStart().isBefore(p.end()) && b.getEnd().isAfter(p.start())) {
                overlap = true;
            } else if (b.getStart().isBefore(p.end().plus(buffer)) && b.getEnd().plus(buffer).isAfter(p.start())) {
                bufferHit = true;
            }
        }
        if (overlap) {
            out.add(new Violation(Code.OVERLAP, resource.getName() + " is already booked during part of this time."));
        } else if (bufferHit) {
            out.add(new Violation(Code.BUFFER, resource.getName() + " needs a "
                    + resource.getBufferMinutes() + "-minute gap between bookings for setup and cleanup."));
        }

        // 4. Blocked periods.
        for (BlockedPeriod b : blocked) {
            if (b.getStart().isBefore(p.end()) && b.getEnd().isAfter(p.start())) {
                out.add(new Violation(Code.BLOCKED, "Not available: " + b.getKind().name().toLowerCase()
                        + (b.getReason() == null || b.getReason().isBlank() ? "" : " (" + b.getReason() + ")") + "."));
                break;
            }
        }

        // 5. Access gate.
        if (resource.isRequiresTraining() && !p.hasAccess()) {
            out.add(new Violation(Code.ACCESS, "Training or sign-off is required before booking "
                    + resource.getName() + ". Ask a resource admin to record it."));
        }

        return out;
    }

    public static Set<Code> codes(List<Violation> violations) {
        Set<Code> set = EnumSet.noneOf(Code.class);
        violations.forEach(v -> set.add(v.code()));
        return set;
    }

    private static void checkOpeningHours(Resource r, Proposal p, ZoneId zone, List<Violation> out) {
        if (r.getOpenTime() == null || r.getCloseTime() == null) {
            return;
        }
        ZonedDateTime s = p.start().atZone(zone);
        ZonedDateTime e = p.end().atZone(zone);
        LocalTime open = LocalTime.parse(r.getOpenTime());
        LocalTime close = LocalTime.parse(r.getCloseTime());
        boolean sameDay = s.toLocalDate().equals(e.toLocalDate());
        boolean inside = sameDay && !s.toLocalTime().isBefore(open) && !e.toLocalTime().isAfter(close);
        if (!inside) {
            out.add(new Violation(Code.OUTSIDE_HOURS, r.getName() + " can be booked " + r.getOpenTime() + " to "
                    + r.getCloseTime() + " on a single day (" + zone + ")."));
        }
    }
}
