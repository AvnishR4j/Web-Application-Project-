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
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Creates one demo user per role so every role can be tried on a fresh database.
 * Runs only when SEED_DEMO_DATA=true. Never enable this in a deployed environment.
 */
@Component
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    static final String DEMO_PASSWORD = "Password@123";

    private final UserRepository users;
    private final ResourceRepository resources;
    private final ClubRepository clubs;
    private final PasswordEncoder encoder;
    private final boolean enabled;

    public DemoDataSeeder(UserRepository users, ResourceRepository resources, ClubRepository clubs,
                          PasswordEncoder encoder, @Value("${app.seed-demo-data}") boolean enabled) {
        this.users = users;
        this.resources = resources;
        this.clubs = clubs;
        this.encoder = encoder;
        this.enabled = enabled;
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
        log.warn("Demo users seeded (password: {}). Do not use this setting in production.", DEMO_PASSWORD);
    }

    private void seed(String name, String email, Role role, String department) {
        if (!users.existsByEmail(email)) {
            users.save(new User(name, email, encoder.encode(DEMO_PASSWORD), role, department));
        }
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
