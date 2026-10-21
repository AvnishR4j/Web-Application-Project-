package com.campus.booking.controller;

import com.campus.booking.dto.AuthDtos.AuthResponse;
import com.campus.booking.dto.AuthDtos.LoginRequest;
import com.campus.booking.dto.AuthDtos.RegisterRequest;
import com.campus.booking.dto.AuthDtos.UserResponse;
import com.campus.booking.exception.ApiExceptions.ConflictException;
import com.campus.booking.exception.ApiExceptions.UnauthorizedException;
import com.campus.booking.model.Role;
import com.campus.booking.model.User;
import com.campus.booking.repository.UserRepository;
import com.campus.booking.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String BAD_LOGIN = "Invalid email or password";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    /** Self-registration always creates a STUDENT. Other roles are assigned by a SUPER_ADMIN. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = new User(req.name().trim(), email, encoder.encode(req.password()),
                Role.STUDENT, req.department());
        users.save(user);
        return new AuthResponse(jwt.generate(user), UserResponse.from(user));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        User user = users.findByEmail(req.email().trim().toLowerCase())
                .orElseThrow(() -> new UnauthorizedException(BAD_LOGIN));
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new UnauthorizedException(BAD_LOGIN);
        }
        return new AuthResponse(jwt.generate(user), UserResponse.from(user));
    }
}
