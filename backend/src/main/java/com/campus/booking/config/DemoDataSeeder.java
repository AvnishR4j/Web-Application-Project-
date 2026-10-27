package com.campus.booking.config;

import com.campus.booking.model.Club;
import com.campus.booking.model.Resource;
import com.campus.booking.model.ResourceType;
import com.campus.booking.model.Role;
import com.campus.booking.model.User;
import com.campus.booking.repository.ClubRepository;
import com.campus.booking.repository.ResourceRepository;
import com.campus.booking.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Creates one demo user per role so every role can be tried on a fresh database.
 * Runs only when SEED_DEMO_DATA=true. The password comes from DEMO_PASSWORD (default Password@123 for local
 * development). On every start, existing demo users are reset to that password, so changing DEMO_PASSWORD on a
 * deployed server retires the old one.
 */
@Component
@Order(1)
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository users;
    private final ResourceRepository resources;
    private final ClubRepository clubs;
    private final PasswordEncoder encoder;
    private final boolean enabled;
    private final String password;

    public DemoDataSeeder(UserRepository users, ResourceRepository resources, ClubRepository clubs,
                          PasswordEncoder encoder, @Value("${app.seed-demo-data}") boolean enabled,
                          @Value("${app.demo-password}") String password) {
        this.users = users;
        this.resources = resources;
        this.clubs = clubs;
        this.encoder = encoder;
        this.enabled = enabled;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        seed("Demo Student", "student@campus.test", Role.STUDENT, "Computer Science");
        seed("Demo Student Two", "student2@campus.test", Role.STUDENT, "Electronics");
        seed("Demo Student Three", "student3@campus.test", Role.STUDENT, "Mechanical");
        seed("Demo Organizer", "organizer@campus.test", Role.CLUB_ORGANIZER, "Computer Science");
        seed("Demo Faculty Advisor", "advisor@campus.test", Role.FACULTY_ADVISOR, "Computer Science");
        seed("Demo Resource Admin", "resources@campus.test", Role.RESOURCE_ADMIN, "Facilities");
        seed("Demo Super Admin", "admin@campus.test", Role.SUPER_ADMIN, "Administration");
        seedResource("Seminar Hall", ResourceType.ROOM, "Main Block, Floor 1", 120, 15, "08:00", "20:00", false);
        seedResource("Computer Lab 1", ResourceType.LAB, "Science Block, Floor 2", 40, 15, "09:00", "18:00", true);
        seedResource("Portable Projector", ResourceType.EQUIPMENT, "Store Room", 0, 45, null, null, false);
        if (!clubs.existsByName("Coding Club")) {
            Club club = new Club();
            club.setName("Coding Club");
            club.setDescription("Demo club: advisor@campus.test advises, organizer@campus.test organizes.");
            club.setAdvisorEmail("advisor@campus.test");
            club.setOrganizerEmails(List.of("organizer@campus.test"));
            clubs.save(club);
        }
        log.warn("Demo users are ready. Their password is DEMO_PASSWORD; share it only with people who should sign in.");
    }

    private void seed(String name, String email, Role role, String department) {
        users.findByEmail(email).ifPresentOrElse(existing -> {
            if (!encoder.matches(password, existing.getPasswordHash())) {
                existing.setPasswordHash(encoder.encode(password));
                users.save(existing);
            }
        }, () -> users.save(new User(name, email, encoder.encode(password), role, department)));
    }

    private void seedResource(String name, ResourceType type, String location, int capacity, int buffer,
                              String open, String close, boolean requiresTraining) {
        if (!resources.existsByName(name)) {
            Resource r = new Resource();
            r.setName(name);
            r.setType(type);
            r.setLocation(location);
            r.setCapacity(capacity);
            r.setBufferMinutes(buffer);
            r.setOpenTime(open);
            r.setCloseTime(close);
            r.setRequiresTraining(requiresTraining);
            resources.save(r);
        }
    }
}
