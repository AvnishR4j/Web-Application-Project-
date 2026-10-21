package com.campus.booking.booking;

import com.campus.booking.model.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/** Who is calling, taken from the verified JWT. */
public record Requester(String email, Role role) {

    public static Requester from(Authentication auth) {
        Role role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> Role.valueOf(a.substring("ROLE_".length())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Authenticated user has no role"));
        return new Requester(auth.getName(), role);
    }

    public boolean isResourceManager() {
        return role == Role.RESOURCE_ADMIN || role == Role.SUPER_ADMIN;
    }
}
