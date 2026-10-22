package com.campus.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// JWT authentication replaces the default in-memory user, so skip its generated-password log line.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class BookingApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookingApplication.class, args);
    }
}
