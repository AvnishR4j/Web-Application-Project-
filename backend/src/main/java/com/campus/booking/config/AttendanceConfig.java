package com.campus.booking.config;

import com.campus.booking.attendance.RotatingCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AttendanceConfig {

    /** Signs check-in codes. Defaults to the JWT secret; set CHECKIN_SECRET to keep them separate. */
    @Bean
    public RotatingCode rotatingCode(@Value("${app.checkin.secret}") String secret) {
        return new RotatingCode(secret);
    }
}
