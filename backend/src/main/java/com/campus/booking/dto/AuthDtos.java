package com.campus.booking.dto;

import com.campus.booking.model.Role;
import com.campus.booking.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 200) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @Size(max = 100) String department) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    /** Used by a SUPER_ADMIN to create an account with any role. */
    public record AdminCreateUserRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 200) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @Size(max = 100) String department,
            @NotNull Role role) {
    }

    public record RoleChangeRequest(@NotNull Role role) {
    }

    public record UserResponse(String id, String name, String email, Role role, String department) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.getDepartment());
        }
    }

    public record AuthResponse(String token, UserResponse user) {
    }

    public record GoogleLoginRequest(@NotBlank @Size(max = 4096) String credential) {
    }

    /** What the sign-in pages need to know: whether to offer Google, and whether passwords can create accounts. */
    public record AuthConfig(String googleClientId, java.util.List<String> googleDomains, boolean passwordSignup,
                             boolean emailCode, boolean passwordReset) {
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 200) String email) {
    }

    public record CodeLogin(@NotBlank @Email @Size(max = 200) String email, @NotBlank @Size(max = 12) String code) {
    }

    public record PasswordReset(@NotBlank @Size(max = 200) String token, @NotBlank @Size(min = 8, max = 128) String password) {
    }

    public record EmailPreference(@NotBlank @Email String email, @NotBlank @Size(max = 200) String token, boolean subscribed) {
    }
}
