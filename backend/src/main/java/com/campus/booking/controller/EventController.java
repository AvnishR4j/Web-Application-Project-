package com.campus.booking.controller;

import com.campus.booking.booking.Requester;
import com.campus.booking.dto.EventDtos.EventRequest;
import com.campus.booking.dto.EventDtos.OptionalReasonRequest;
import com.campus.booking.dto.EventDtos.ReasonRequest;
import com.campus.booking.event.EventService;
import com.campus.booking.model.AuditLog;
import com.campus.booking.model.Event;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    /** Approved upcoming events. This is all a student ever sees. */
    @GetMapping
    public List<Event> published() {
        return service.published();
    }

    @GetMapping("/rules")
    public Map<String, Object> rules() {
        return Map.of("minLeadDays", service.minLeadDays());
    }

    @GetMapping("/mine")
    public List<Event> mine(Authentication auth) {
        return service.forOrganizer(Requester.from(auth));
    }

    @GetMapping("/advisor-queue")
    public List<Event> advisorQueue(Authentication auth) {
        return service.advisorQueue(Requester.from(auth));
    }

    @GetMapping("/manager-queue")
    public List<Event> managerQueue() {
        return service.managerQueue();
    }

    @GetMapping("/{id}")
    public Event get(@PathVariable String id, Authentication auth) {
        return service.view(id, Requester.from(auth));
    }

    @GetMapping("/{id}/history")
    public List<AuditLog> history(@PathVariable String id, Authentication auth) {
        return service.history(id, Requester.from(auth));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Event create(@Valid @RequestBody EventRequest req, Authentication auth) {
        return service.create(req, Requester.from(auth));
    }

    @PutMapping("/{id}")
    public Event update(@PathVariable String id, @Valid @RequestBody EventRequest req, Authentication auth) {
        return service.update(id, req, Requester.from(auth));
    }

    @PostMapping("/{id}/submit")
    public Event submit(@PathVariable String id, Authentication auth) {
        return service.submit(id, Requester.from(auth));
    }

    @PostMapping("/{id}/cancel")
    public Event cancel(@PathVariable String id, @Valid @RequestBody(required = false) OptionalReasonRequest req,
                        Authentication auth) {
        return service.cancel(id, req == null ? null : req.reason(), Requester.from(auth));
    }

    @PostMapping("/{id}/complete")
    public Event complete(@PathVariable String id, Authentication auth) {
        return service.complete(id, Requester.from(auth));
    }

    @PostMapping("/{id}/advisor-approve")
    public Event advisorApprove(@PathVariable String id, Authentication auth) {
        return service.advisorApprove(id, Requester.from(auth));
    }

    @PostMapping("/{id}/advisor-reject")
    public Event advisorReject(@PathVariable String id, @Valid @RequestBody ReasonRequest req, Authentication auth) {
        return service.advisorReject(id, req.reason(), Requester.from(auth));
    }

    @PostMapping("/{id}/manager-approve")
    public Event managerApprove(@PathVariable String id, Authentication auth) {
        return service.managerApprove(id, Requester.from(auth));
    }

    @PostMapping("/{id}/manager-reject")
    public Event managerReject(@PathVariable String id, @Valid @RequestBody ReasonRequest req, Authentication auth) {
        return service.managerReject(id, req.reason(), Requester.from(auth));
    }
}
