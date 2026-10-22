package com.campus.booking.registration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campus.booking.config.SecurityConfig;
import com.campus.booking.controller.RegistrationController;
import com.campus.booking.event.EventService;
import com.campus.booking.exception.ApiExceptions.ForbiddenException;
import com.campus.booking.model.Registration;
import com.campus.booking.model.Role;
import com.campus.booking.model.User;
import com.campus.booking.security.JwtAuthFilter;
import com.campus.booking.security.JwtService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RegistrationController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=unit-test-secret-unit-test-secret-1234",
        "app.jwt.expiry-minutes=60",
        "app.cors.allowed-origin=http://localhost:5173"
})
class RegistrationAccessTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean RegistrationService registrations;
    @MockBean EventService events;

    private String as(Role role) {
        return "Bearer " + jwt.generate(new User("T", role.name().toLowerCase() + "@campus.test", "h", role, "CS"));
    }

    @Test
    void anonymousCannotRegister() throws Exception {
        mvc.perform(post("/api/events/e1/register")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/registrations/mine")).andExpect(status().isUnauthorized());
    }

    @Test
    void studentCanRegisterCancelAndSeeTheirOwn() throws Exception {
        when(registrations.register(anyString(), any())).thenReturn(new Registration());
        when(registrations.cancel(anyString(), any())).thenReturn(new Registration());
        when(registrations.mine(any())).thenReturn(List.of());
        when(events.published()).thenReturn(List.of());
        when(registrations.summaries(any(), any())).thenReturn(List.of());
        mvc.perform(post("/api/events/e1/register").header(HttpHeaders.AUTHORIZATION, as(Role.STUDENT)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/events/e1/unregister").header(HttpHeaders.AUTHORIZATION, as(Role.STUDENT)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/registrations/mine").header(HttpHeaders.AUTHORIZATION, as(Role.STUDENT)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/events/seats").header(HttpHeaders.AUTHORIZATION, as(Role.STUDENT)))
                .andExpect(status().isOk());
    }

    @Test
    void outsidersCannotSeeAnEventsRegistrantList() throws Exception {
        doThrow(new ForbiddenException("no")).when(events).requireInsider(anyString(), any());
        mvc.perform(get("/api/events/e1/registrations").header(HttpHeaders.AUTHORIZATION, as(Role.STUDENT)))
                .andExpect(status().isForbidden());
    }
}
