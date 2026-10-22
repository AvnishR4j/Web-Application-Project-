package com.campus.booking.controller;

import com.campus.booking.dto.BookingDtos.BlockedPeriodRequest;
import com.campus.booking.exception.ApiExceptions.BadRequestException;
import com.campus.booking.exception.ApiExceptions.NotFoundException;
import com.campus.booking.model.BlockedPeriod;
import com.campus.booking.repository.BlockedPeriodRepository;
import com.campus.booking.repository.ResourceRepository;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/blocked-periods")
public class BlockedPeriodController {

    private final BlockedPeriodRepository blocked;
    private final ResourceRepository resources;

    public BlockedPeriodController(BlockedPeriodRepository blocked, ResourceRepository resources) {
        this.blocked = blocked;
        this.resources = resources;
    }

    @GetMapping
    public List<BlockedPeriod> list(@RequestParam(required = false) String resourceId) {
        return resourceId == null ? blocked.findAll(Sort.by("start")) : blocked.findByResourceId(resourceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BlockedPeriod create(@Valid @RequestBody BlockedPeriodRequest req, Authentication auth) {
        if (!req.end().isAfter(req.start())) {
            throw new BadRequestException("The end must be after the start");
        }
        if (req.resourceId() != null && !resources.existsById(req.resourceId())) {
            throw new NotFoundException("Resource not found");
        }
        BlockedPeriod b = new BlockedPeriod();
        b.setResourceId(req.resourceId());
        b.setStart(req.start());
        b.setEnd(req.end());
        b.setKind(req.kind());
        b.setReason(req.reason());
        b.setCreatedBy(auth.getName());
        return blocked.save(b);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        if (!blocked.existsById(id)) {
            throw new NotFoundException("Blocked period not found");
        }
        blocked.deleteById(id);
    }
}
