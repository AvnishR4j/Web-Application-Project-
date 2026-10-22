package com.campus.booking.config;

import com.campus.booking.security.JwtAuthFilter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final String allowedOrigin;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          @Value("${app.cors.allowed-origin}") String allowedOrigin) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.allowedOrigin = allowedOrigin;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Spring forwards refused requests to /error. Without this, a logged-in user who is not allowed
                // (should be 403) got 401, because the error page itself required login.
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/auth/**", "/api/health").permitAll()
                .requestMatchers("/api/admin/**").hasRole("SUPER_ADMIN")
                // Catalog, blocked periods and training: managed by resource admins (and super admins).
                .requestMatchers(HttpMethod.POST, "/api/resources").hasAnyRole("RESOURCE_ADMIN", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/resources/*").hasAnyRole("RESOURCE_ADMIN", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/blocked-periods").hasAnyRole("RESOURCE_ADMIN", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/blocked-periods/*").hasAnyRole("RESOURCE_ADMIN", "SUPER_ADMIN")
                .requestMatchers("/api/training").hasAnyRole("RESOURCE_ADMIN", "SUPER_ADMIN")
                // Bookings: approving and listing everything is for admins, the rest for people who book.
                .requestMatchers(HttpMethod.PATCH, "/api/bookings/*/approve", "/api/bookings/*/reject")
                    .hasAnyRole("RESOURCE_ADMIN", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/bookings").hasAnyRole("RESOURCE_ADMIN", "SUPER_ADMIN")
                .requestMatchers("/api/reports/**").hasAnyRole("RESOURCE_ADMIN", "SUPER_ADMIN")
                // Clubs: anyone logged in can read, only super admins manage them.
                .requestMatchers(HttpMethod.POST, "/api/clubs").hasRole("SUPER_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/clubs/*").hasRole("SUPER_ADMIN")
                // Events: the per-club checks (is this your club? are you its advisor?) live in EventService.
                .requestMatchers(HttpMethod.POST, "/api/events/*/advisor-approve", "/api/events/*/advisor-reject")
                    .hasAnyRole("FACULTY_ADVISOR", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/events/advisor-queue").hasAnyRole("FACULTY_ADVISOR", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/events/*/manager-approve", "/api/events/*/manager-reject")
                    .hasAnyRole("RESOURCE_ADMIN", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/events/manager-queue").hasAnyRole("RESOURCE_ADMIN", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/events/mine").hasAnyRole("CLUB_ORGANIZER", "SUPER_ADMIN")
                // Check-in screen and report: students never; which club is checked in AttendanceController.
                .requestMatchers(HttpMethod.GET, "/api/events/*/checkin-code", "/api/events/*/report")
                    .hasAnyRole("CLUB_ORGANIZER", "FACULTY_ADVISOR", "RESOURCE_ADMIN", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/events", "/api/events/*/submit", "/api/events/*/cancel",
                        "/api/events/*/complete").hasAnyRole("CLUB_ORGANIZER", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/events/*").hasAnyRole("CLUB_ORGANIZER", "SUPER_ADMIN")
                .requestMatchers("/api/bookings/**").hasAnyRole("CLUB_ORGANIZER", "RESOURCE_ADMIN", "SUPER_ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
