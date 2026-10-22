package com.campus.booking.report;

import com.campus.booking.model.AuditLog;
import com.campus.booking.model.Booking;
import com.campus.booking.model.BookingStatus;
import com.campus.booking.model.Club;
import com.campus.booking.model.Event;
import com.campus.booking.model.EventStatus;
import com.campus.booking.model.RegistrationStatus;
import com.campus.booking.model.Resource;
import com.campus.booking.repository.AttendanceRepository;
import com.campus.booking.repository.AuditLogRepository;
import com.campus.booking.repository.BookingRepository;
import com.campus.booking.repository.ClubRepository;
import com.campus.booking.repository.EventRepository;
import com.campus.booking.repository.RegistrationRepository;
import com.campus.booking.repository.ResourceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/** Phase 6 reports for resource admins and super admins. */
@Service
public class ReportService {

    public record ResourceUsage(String resourceId, String name, String type, boolean active, long bookings,
                                long eventBookings, long bookedMinutes, long openMinutes, Double utilization) {
    }

    public record EventAttendance(String eventId, String title, String clubName, Instant start, long registered,
                                  long attended, long noShows, Double attendanceRate) {
    }

    public record ClubAttendance(String clubName, long events, long registered, long attended, Double attendanceRate) {
    }

    public record AttendanceSummary(long events, long registered, long attended, long noShows, Double attendanceRate,
                                    List<EventAttendance> byEvent, List<ClubAttendance> byClub) {
    }

    public record AuditEntry(AuditLog entry, String eventTitle) {
    }

    private final ResourceRepository resources;
    private final BookingRepository bookings;
    private final EventRepository events;
    private final ClubRepository clubs;
    private final RegistrationRepository registrations;
    private final AttendanceRepository attendance;
    private final AuditLogRepository audit;
    private final Clock clock;
    private final ZoneId zone;

    public ReportService(ResourceRepository resources, BookingRepository bookings, EventRepository events,
                         ClubRepository clubs, RegistrationRepository registrations, AttendanceRepository attendance,
                         AuditLogRepository audit, Clock clock, @Value("${app.booking.zone}") String zone) {
        this.resources = resources;
        this.bookings = bookings;
        this.events = events;
        this.clubs = clubs;
        this.registrations = registrations;
        this.attendance = attendance;
        this.audit = audit;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    /** Approved booked time as a share of each resource's opening hours in [from, to). Busiest first. */
    public List<ResourceUsage> utilization(Instant from, Instant to) {
        Map<String, List<Booking>> byResource = bookings
                .findByStatusAndStartLessThanAndEndGreaterThan(BookingStatus.APPROVED, to, from).stream()
                .collect(Collectors.groupingBy(Booking::getResourceId));
        List<ResourceUsage> out = new ArrayList<>();
        for (Resource r : resources.findAll(Sort.by("name"))) {
            List<Booking> list = byResource.getOrDefault(r.getId(), List.of());
            long booked = list.stream()
                    .mapToLong(b -> UtilizationCalculator.overlapMinutes(b.getStart(), b.getEnd(), from, to)).sum();
            long open = UtilizationCalculator.openMinutes(r.getOpenTime(), r.getCloseTime(), from, to, zone);
            long eventBookings = list.stream().filter(b -> b.getEventId() != null).count();
            out.add(new ResourceUsage(r.getId(), r.getName(), r.getType().name(), r.isActive(), list.size(),
                    eventBookings, booked, open, UtilizationCalculator.percent(booked, open)));
        }
        out.sort(Comparator.comparing((ResourceUsage u) -> u.utilization() == null ? -1 : u.utilization()).reversed());
        return out;
    }

    /** Registered versus attended for approved or completed events that have ended inside [from, to). */
    public AttendanceSummary attendance(Instant from, Instant to) {
        Instant now = Instant.now(clock);
        Instant until = to.isBefore(now) ? to : now;
        Map<String, String> clubNames = clubs.findAll().stream().collect(Collectors.toMap(Club::getId, Club::getName));
        List<EventAttendance> rows = new ArrayList<>();
        for (EventStatus status : List.of(EventStatus.APPROVED, EventStatus.COMPLETED)) {
            for (Event e : events.findByStatusAndEndGreaterThanOrderByStartAsc(status, from)) {
                if (e.getEnd().isAfter(until)) {
                    continue;
                }
                long registered = registrations.countByEventIdAndStatus(e.getId(), RegistrationStatus.CONFIRMED);
                long attended = attendance.countByEventId(e.getId());
                rows.add(new EventAttendance(e.getId(), e.getTitle(), clubNames.getOrDefault(e.getClubId(), "-"),
                        e.getStart(), registered, attended, Math.max(0, registered - attended),
                        UtilizationCalculator.percent(attended, registered)));
            }
        }
        rows.sort(Comparator.comparing(EventAttendance::start).reversed());
        Map<String, List<EventAttendance>> byClubRows = rows.stream()
                .collect(Collectors.groupingBy(EventAttendance::clubName));
        List<ClubAttendance> byClub = byClubRows.entrySet().stream().map(en -> {
            long reg = en.getValue().stream().mapToLong(EventAttendance::registered).sum();
            long att = en.getValue().stream().mapToLong(EventAttendance::attended).sum();
            return new ClubAttendance(en.getKey(), en.getValue().size(), reg, att, UtilizationCalculator.percent(att, reg));
        }).sorted(Comparator.comparing(ClubAttendance::clubName)).toList();
        long registered = rows.stream().mapToLong(EventAttendance::registered).sum();
        long attended = rows.stream().mapToLong(EventAttendance::attended).sum();
        long noShows = rows.stream().mapToLong(EventAttendance::noShows).sum();
        return new AttendanceSummary(rows.size(), registered, attended, noShows,
                UtilizationCalculator.percent(attended, registered), rows, byClub);
    }

    /** Newest audit entries first, with the event's title. */
    public List<AuditEntry> auditLog(int limit) {
        List<AuditLog> entries = audit.findAll(PageRequest.of(0, Math.max(1, Math.min(limit, 500)),
                Sort.by(Sort.Direction.DESC, "at"))).getContent();
        Map<String, String> titles = new HashMap<>();
        events.findAllById(entries.stream().map(AuditLog::getEventId).distinct().toList())
                .forEach(e -> titles.put(e.getId(), e.getTitle()));
        return entries.stream().map(a -> new AuditEntry(a, titles.getOrDefault(a.getEventId(), "(deleted event)")))
                .toList();
    }
}
