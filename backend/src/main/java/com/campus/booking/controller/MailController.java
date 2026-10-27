package com.campus.booking.controller;

import com.campus.booking.dto.AuthDtos.EmailPreference;
import com.campus.booking.exception.ApiExceptions.ForbiddenException;
import com.campus.booking.mail.UnsubscribeTokens;
import com.campus.booking.repository.UserRepository;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The one-click link at the bottom of "new event" emails. Works without signing in; the signed token proves the address. */
@RestController
@RequestMapping("/api/mail")
public class MailController {

    private final UserRepository users;
    private final UnsubscribeTokens tokens;

    public MailController(UserRepository users, UnsubscribeTokens tokens) {
        this.users = users;
        this.tokens = tokens;
    }

    @PostMapping("/preferences")
    public Map<String, Boolean> setPreference(@Valid @RequestBody EmailPreference req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (!tokens.valid(email, req.token())) {
            throw new ForbiddenException("This link is not valid. Use the link from your latest email.");
        }
        users.findByEmail(email).ifPresent((u) -> {
            u.setEventEmails(req.subscribed());
            users.save(u);
        });
        return Map.of("subscribed", req.subscribed());
    }
}
