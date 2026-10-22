package com.campus.booking.controller;

import com.campus.booking.attendance.AttendanceService;
import com.campus.booking.attendance.AttendanceService.CheckinCode;
import com.campus.booking.attendance.AttendanceService.Report;
import com.campus.booking.booking.Requester;
import com.campus.booking.event.EventService;
import com.campus.booking.model.Attendance;
import com.campus.booking.model.Feedback;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AttendanceController {

    public record CheckinRequest(@NotBlank @Size(max = 20) String code) {
    }

    public record FeedbackRequest(@Min(1) @Max(5) int rating, @Size(max = 1000) String comment) {
    }

    private final AttendanceService attendance;
    private final EventService events;

    public AttendanceController(AttendanceService attendance, EventService events) {
        this.attendance = attendance;
        this.events = events;
    }

    /** The rotating code for the organizer's check-in screen. Club organizers, advisor and admins only. */
    @GetMapping("/api/events/{id}/checkin-code")
    public CheckinCode code(@PathVariable String id, Authentication auth) {
        events.requireInsider(id, Requester.from(auth));
        return attendance.currentCode(id);
    }

    @PostMapping("/api/events/{id}/checkin")
    public Attendance checkIn(@PathVariable String id, @Valid @RequestBody CheckinRequest req, Authentication auth) {
        return attendance.checkIn(id, req.code(), Requester.from(auth));
    }

    @PostMapping("/api/events/{id}/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public Feedback feedback(@PathVariable String id, @Valid @RequestBody FeedbackRequest req, Authentication auth) {
        return attendance.giveFeedback(id, req.rating(), req.comment(), Requester.from(auth));
    }

    @GetMapping("/api/events/{id}/report")
    public Report report(@PathVariable String id, Authentication auth) {
        events.requireInsider(id, Requester.from(auth));
        return attendance.report(id);
    }
}
