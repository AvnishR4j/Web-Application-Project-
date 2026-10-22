package com.campus.booking.controller;

import com.campus.booking.booking.Requester;
import com.campus.booking.dto.EventDtos.ClubRequest;
import com.campus.booking.exception.ApiExceptions.BadRequestException;
import com.campus.booking.exception.ApiExceptions.ConflictException;
import com.campus.booking.exception.ApiExceptions.NotFoundException;
import com.campus.booking.model.Club;
import com.campus.booking.model.Role;
import com.campus.booking.model.User;
import com.campus.booking.repository.ClubRepository;
import com.campus.booking.repository.UserRepository;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
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
@RequestMapping("/api/clubs")
public class ClubController {

    private final ClubRepository clubs;
    private final UserRepository users;

    public ClubController(ClubRepository clubs, UserRepository users) {
        this.clubs = clubs;
        this.users = users;
    }

    @GetMapping
    public List<Club> list() {
        return clubs.findAll(Sort.by("name"));
    }

    /** Clubs the caller organizes or advises. */
    @GetMapping("/mine")
    public List<Club> mine(Authentication auth) {
        Requester who = Requester.from(auth);
        Set<Club> mine = new LinkedHashSet<>(clubs.findByOrganizer(who.email()));
        mine.addAll(clubs.findByAdvisorEmail(who.email()));
        return new ArrayList<>(mine);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Club create(@Valid @RequestBody ClubRequest req) {
        if (clubs.existsByName(req.name().trim())) {
            throw new ConflictException("A club with this name already exists");
        }
        Club club = new Club();
        apply(club, req);
        return clubs.save(club);
    }

    @PutMapping("/{id}")
    public Club update(@PathVariable String id, @Valid @RequestBody ClubRequest req) {
        Club club = clubs.findById(id).orElseThrow(() -> new NotFoundException("Club not found"));
        apply(club, req);
        return clubs.save(club);
    }

    private void apply(Club club, ClubRequest req) {
        String advisor = normalize(req.advisorEmail());
        requireRole(advisor, Role.FACULTY_ADVISOR, "advisor");
        List<String> organizers = new ArrayList<>();
        if (req.organizerEmails() != null) {
            for (String raw : req.organizerEmails()) {
                String email = normalize(raw);
                if (email.isEmpty() || organizers.contains(email)) {
                    continue;
                }
                requireRole(email, Role.CLUB_ORGANIZER, "organizer");
                organizers.add(email);
            }
        }
        club.setName(req.name().trim());
        club.setDescription(req.description());
        club.setAdvisorEmail(advisor);
        club.setOrganizerEmails(organizers);
    }

    private void requireRole(String email, Role role, String what) {
        User user = users.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("No user with email " + email + " (" + what + ")"));
        if (user.getRole() != role) {
            throw new BadRequestException(email + " must have the " + role + " role to be the club's " + what
                    + ". Change it on the Users page first.");
        }
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
