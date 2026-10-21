package com.campus.booking.config;

import com.campus.booking.model.Role;
import com.campus.booking.model.User;
import com.campus.booking.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder encoder;
    private final boolean enabled;

    public DemoDataSeeder(UserRepository users, PasswordEncoder encoder,
                          @Value("${app.seed-demo-data}") boolean enabled) {
        this.users = users;
        this.encoder = encoder;
        this.enabled = enabled;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        seed("Demo Student", "student@campus.test", Role.STUDENT, "Computer Science");
        seed("Demo Organizer", "organizer@campus.test", Role.CLUB_ORGANIZER, "Computer Science");
        seed("Demo Resource Admin", "resources@campus.test", Role.RESOURCE_ADMIN, "Facilities");
        seed("Demo Super Admin", "admin@campus.test", Role.SUPER_ADMIN, "Administration");
        log.warn("Demo users seeded (password: {}). Do not use this setting in production.", DEMO_PASSWORD);
    }

    private void seed(String name, String email, Role role, String department) {
        if (!users.existsByEmail(email)) {
            users.save(new User(name, email, encoder.encode(DEMO_PASSWORD), role, department));
        }
    }
}
