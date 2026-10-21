package com.campus.booking.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campus.booking.config.SecurityConfig;
import com.campus.booking.controller.AdminUserController;
import com.campus.booking.model.Role;
import com.campus.booking.model.User;
import com.campus.booking.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Checks that role rules are enforced on the server, not just hidden in the UI. */
@WebMvcTest(AdminUserController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=unit-test-secret-unit-test-secret-1234",
        "app.jwt.expiry-minutes=60",
        "app.cors.allowed-origin=http://localhost:5173"
})
class RoleAccessTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean UserRepository users;

    private String tokenFor(Role role) {
        return "Bearer " + jwt.generate(new User("T", role.name().toLowerCase() + "@campus.test", "h", role, "CS"));
    }

    @Test
    void noTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotListUsers() throws Exception {
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, tokenFor(Role.STUDENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerAndResourceAdminCannotListUsers() throws Exception {
        for (Role role : List.of(Role.CLUB_ORGANIZER, Role.RESOURCE_ADMIN)) {
            mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, tokenFor(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void superAdminCanListUsers() throws Exception {
        Mockito.when(users.findAll()).thenReturn(List.of());
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, tokenFor(Role.SUPER_ADMIN)))
                .andExpect(status().isOk());
    }
}
