package com.campus.booking.controller;

import com.campus.booking.dto.AuthDtos.AdminCreateUserRequest;
import com.campus.booking.dto.AuthDtos.RoleChangeRequest;
import com.campus.booking.dto.AuthDtos.UserResponse;
import com.campus.booking.exception.ApiExceptions.BadRequestException;
import com.campus.booking.exception.ApiExceptions.ConflictException;
import com.campus.booking.exception.ApiExceptions.NotFoundException;
import com.campus.booking.model.User;
import com.campus.booking.repository.UserRepository;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** User and role management. The whole /api/admin/** path is restricted to SUPER_ADMIN in SecurityConfig. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AdminUserController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @GetMapping
    public List<UserResponse> list() {
        return users.findAll().stream().map(UserResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody AdminCreateUserRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = new User(req.name().trim(), email, encoder.encode(req.password()),
                req.role(), req.department());
        return UserResponse.from(users.save(user));
    }

    @PatchMapping("/{id}/role")
    public UserResponse changeRole(@PathVariable String id, @Valid @RequestBody RoleChangeRequest req,
                                   Authentication auth) {
        User user = users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getEmail().equals(auth.getName())) {
            throw new BadRequestException("You cannot change your own role");
        }
        user.setRole(req.role());
        return UserResponse.from(users.save(user));
    }
}
