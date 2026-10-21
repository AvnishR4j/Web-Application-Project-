package com.campus.booking.controller;

import com.campus.booking.dto.BookingDtos.TrainingRequest;
import com.campus.booking.exception.ApiExceptions.ConflictException;
import com.campus.booking.exception.ApiExceptions.NotFoundException;
import com.campus.booking.model.TrainingRecord;
import com.campus.booking.repository.ResourceRepository;
import com.campus.booking.repository.TrainingRecordRepository;
import com.campus.booking.repository.UserRepository;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Records that a user is trained or signed off for a lab or piece of equipment. */
@RestController
@RequestMapping("/api/training")
public class TrainingController {

    private final TrainingRecordRepository training;
    private final ResourceRepository resources;
    private final UserRepository users;

    public TrainingController(TrainingRecordRepository training, ResourceRepository resources, UserRepository users) {
        this.training = training;
        this.resources = resources;
        this.users = users;
    }

    @GetMapping
    public List<TrainingRecord> list(@RequestParam String resourceId) {
        return training.findByResourceId(resourceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrainingRecord grant(@Valid @RequestBody TrainingRequest req, Authentication auth) {
        String email = req.userEmail().trim().toLowerCase();
        if (!users.existsByEmail(email)) {
            throw new NotFoundException("No user with this email");
        }
        if (!resources.existsById(req.resourceId())) {
            throw new NotFoundException("Resource not found");
        }
        if (training.existsByUserEmailAndResourceId(email, req.resourceId())) {
            throw new ConflictException("This user already has access to this resource");
        }
        return training.save(new TrainingRecord(email, req.resourceId(), auth.getName()));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@RequestParam String userEmail, @RequestParam String resourceId) {
        training.deleteByUserEmailAndResourceId(userEmail.trim().toLowerCase(), resourceId);
    }
}
