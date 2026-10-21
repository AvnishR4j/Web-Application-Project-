package com.campus.booking.controller;

import com.campus.booking.dto.BookingDtos.ResourceRequest;
import com.campus.booking.exception.ApiExceptions.BadRequestException;
import com.campus.booking.exception.ApiExceptions.ConflictException;
import com.campus.booking.exception.ApiExceptions.NotFoundException;
import com.campus.booking.model.Resource;
import com.campus.booking.repository.ResourceRepository;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final ResourceRepository resources;

    public ResourceController(ResourceRepository resources) {
        this.resources = resources;
    }

    @GetMapping
    public List<Resource> list(@RequestParam(defaultValue = "false") boolean includeInactive, Authentication auth) {
        boolean manager = auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_RESOURCE_ADMIN") || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        if (includeInactive && manager) {
            return resources.findAll();
        }
        return resources.findByActiveTrueOrderByNameAsc();
    }

    @GetMapping("/{id}")
    public Resource get(@PathVariable String id) {
        return resources.findById(id).orElseThrow(() -> new NotFoundException("Resource not found"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Resource create(@Valid @RequestBody ResourceRequest req) {
        if (resources.existsByName(req.name().trim())) {
            throw new ConflictException("A resource with this name already exists");
        }
        Resource r = new Resource();
        apply(r, req);
        return resources.save(r);
    }

    @PutMapping("/{id}")
    public Resource update(@PathVariable String id, @Valid @RequestBody ResourceRequest req) {
        Resource r = resources.findById(id).orElseThrow(() -> new NotFoundException("Resource not found"));
        apply(r, req);
        return resources.save(r);
    }

    private void apply(Resource r, ResourceRequest req) {
        if ((req.openTime() == null) != (req.closeTime() == null)) {
            throw new BadRequestException("Give both openTime and closeTime, or neither");
        }
        if (req.openTime() != null && req.openTime().compareTo(req.closeTime()) >= 0) {
            throw new BadRequestException("closeTime must be after openTime");
        }
        r.setName(req.name().trim());
        r.setType(req.type());
        r.setLocation(req.location());
        r.setCapacity(req.capacity());
        r.setBufferMinutes(req.bufferMinutes());
        r.setOpenTime(req.openTime());
        r.setCloseTime(req.closeTime());
        r.setRequiresTraining(req.requiresTraining());
        r.setActive(req.active() == null || req.active());
    }
}
