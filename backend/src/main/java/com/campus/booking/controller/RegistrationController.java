package com.campus.booking.controller;

import com.campus.booking.booking.Requester;
import com.campus.booking.event.EventService;
import com.campus.booking.model.Registration;
import com.campus.booking.registration.RegistrationService;
import com.campus.booking.registration.RegistrationService.EventRegistrations;
import com.campus.booking.registration.RegistrationService.MyRegistration;
import com.campus.booking.registration.RegistrationService.SeatSummary;
import com.campus.booking.registration.RegistrationService.Standing;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RegistrationController {

    private final RegistrationService registrations;
    private final EventService events;

    public RegistrationController(RegistrationService registrations, EventService events) {
        this.registrations = registrations;
        this.events = events;
    }

    /** Seats and the caller's own status for every published event. */
    @GetMapping("/api/events/seats")
    public List<SeatSummary> seats(Authentication auth) {
        return registrations.summaries(events.published(), Requester.from(auth));
    }

    @PostMapping("/api/events/{id}/register")
    public Registration register(@PathVariable String id, Authentication auth) {
        return registrations.register(id, Requester.from(auth));
    }

    @PostMapping("/api/events/{id}/unregister")
    public Registration unregister(@PathVariable String id, Authentication auth) {
        return registrations.cancel(id, Requester.from(auth));
    }

    @GetMapping("/api/registrations/mine")
    public List<MyRegistration> mine(Authentication auth) {
        return registrations.mine(Requester.from(auth));
    }

    /** How many recent no-shows the caller has, and whether registration is paused. */
    @GetMapping("/api/registrations/standing")
    public Standing standing(Authentication auth) {
        return registrations.standing(Requester.from(auth).email());
    }

    /** Confirmed list and waitlist in order. Only the club's organizers, its advisor and admins. */
    @GetMapping("/api/events/{id}/registrations")
    public EventRegistrations forEvent(@PathVariable String id, Authentication auth) {
        events.requireInsider(id, Requester.from(auth));
        return registrations.forEvent(id);
    }
}
