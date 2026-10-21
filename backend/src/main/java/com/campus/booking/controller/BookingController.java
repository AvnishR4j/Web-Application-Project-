package com.campus.booking.controller;

import com.campus.booking.booking.BookingService;
import com.campus.booking.booking.BookingService.CheckResult;
import com.campus.booking.booking.Requester;
import com.campus.booking.dto.BookingDtos.BookingRequest;
import com.campus.booking.dto.BookingDtos.RejectRequest;
import com.campus.booking.dto.BookingDtos.ScheduleSlot;
import com.campus.booking.exception.ApiExceptions.BadRequestException;
import com.campus.booking.model.Booking;
import com.campus.booking.model.BookingStatus;
import com.campus.booking.repository.BookingRepository;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService service;
    private final BookingRepository bookings;

    public BookingController(BookingService service, BookingRepository bookings) {
        this.service = service;
        this.bookings = bookings;
    }

    /** Dry run: tells the organizer what would block this booking, without saving anything. */
    @PostMapping("/check")
    public CheckResult check(@Valid @RequestBody BookingRequest req, Authentication auth) {
        return service.check(req.toInput(), Requester.from(auth));
    }

    /** Send a unique Idempotency-Key per submit (for example a UUID); a retry with the same key is safe. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Booking create(@RequestHeader("Idempotency-Key") String key,
                          @Valid @RequestBody BookingRequest req, Authentication auth) {
        if (key.isBlank() || key.length() > 100) {
            throw new BadRequestException("Idempotency-Key must be 1 to 100 characters");
        }
        return service.create(req.toInput(), key.trim(), Requester.from(auth));
    }

    @GetMapping("/mine")
    public List<Booking> mine(Authentication auth) {
        return bookings.findByRequesterEmailOrderByStartDesc(auth.getName());
    }

    /** Resource admins: every booking, optionally filtered by status. */
    @GetMapping
    public List<Booking> all(@RequestParam(required = false) BookingStatus status) {
        return status == null ? bookings.findAll(Sort.by("start")) : bookings.findByStatusOrderByStartAsc(status);
    }

    /** Which times are taken on a resource. Shows times and status only. */
    @GetMapping("/schedule")
    public List<ScheduleSlot> schedule(@RequestParam String resourceId,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.schedule(resourceId, from, to).stream()
                .map(b -> new ScheduleSlot(b.getStart(), b.getEnd(), b.getStatus().name()))
                .toList();
    }

    @PatchMapping("/{id}/approve")
    public Booking approve(@PathVariable String id, Authentication auth) {
        return service.approve(id, auth.getName());
    }

    @PatchMapping("/{id}/reject")
    public Booking reject(@PathVariable String id, @Valid @RequestBody RejectRequest req, Authentication auth) {
        return service.reject(id, auth.getName(), req.reason());
    }

    @PatchMapping("/{id}/cancel")
    public Booking cancel(@PathVariable String id, Authentication auth) {
        return service.cancel(id, Requester.from(auth));
    }
}
