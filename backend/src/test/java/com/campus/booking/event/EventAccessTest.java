package com.campus.booking.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campus.booking.config.SecurityConfig;
import com.campus.booking.controller.ClubController;
import com.campus.booking.controller.EventController;
import com.campus.booking.model.Event;
import com.campus.booking.model.Role;
import com.campus.booking.model.User;
import com.campus.booking.repository.ClubRepository;
import com.campus.booking.repository.UserRepository;
import com.campus.booking.security.JwtAuthFilter;
import com.campus.booking.security.JwtService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Role rules for events and clubs. The per-club checks are tested against a real database in EventFlowTest. */
@WebMvcTest({EventController.class, ClubController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=unit-test-secret-unit-test-secret-1234",
        "app.jwt.expiry-minutes=60",
        "app.cors.allowed-origin=http://localhost:5173"
})
class EventAccessTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean EventService service;
    @MockBean ClubRepository clubs;
    @MockBean UserRepository users;

    private static final String EVENT = """
            {"clubId":"c1","title":"Hack night","resourceId":"r1","start":"2030-01-10T10:00:00Z",
             "end":"2030-01-10T12:00:00Z","expectedAttendees":40}""";

    private String as(Role role) {
        return "Bearer " + jwt.generate(new User("T", role.name().toLowerCase() + "@campus.test", "h", role, "CS"));
    }

    @Test
    void everyoneLoggedInSeesPublishedEvents() throws Exception {
        when(service.published()).thenReturn(List.of());
        for (Role role : Role.values()) {
            mvc.perform(get("/api/events").header(HttpHeaders.AUTHORIZATION, as(role))).andExpect(status().isOk());
        }
        mvc.perform(get("/api/events")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyOrganizersCreateAndSubmit() throws Exception {
        when(service.create(any(), any())).thenReturn(new Event());
        for (Role role : List.of(Role.STUDENT, Role.FACULTY_ADVISOR, Role.RESOURCE_ADMIN)) {
            mvc.perform(post("/api/events").header(HttpHeaders.AUTHORIZATION, as(role))
                    .contentType(MediaType.APPLICATION_JSON).content(EVENT)).andExpect(status().isForbidden());
            mvc.perform(post("/api/events/e1/submit").header(HttpHeaders.AUTHORIZATION, as(role)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/events").header(HttpHeaders.AUTHORIZATION, as(Role.CLUB_ORGANIZER))
                .contentType(MediaType.APPLICATION_JSON).content(EVENT)).andExpect(status().isCreated());
    }

    @Test
    void advisorStepIsForAdvisorsOnly() throws Exception {
        when(service.advisorApprove(anyString(), any())).thenReturn(new Event());
        for (Role role : List.of(Role.STUDENT, Role.CLUB_ORGANIZER, Role.RESOURCE_ADMIN)) {
            mvc.perform(post("/api/events/e1/advisor-approve").header(HttpHeaders.AUTHORIZATION, as(role)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/events/e1/advisor-approve").header(HttpHeaders.AUTHORIZATION, as(Role.FACULTY_ADVISOR)))
                .andExpect(status().isOk());
    }

    @Test
    void finalApprovalIsForResourceAdminsOnly() throws Exception {
        when(service.managerApprove(anyString(), any())).thenReturn(new Event());
        for (Role role : List.of(Role.STUDENT, Role.CLUB_ORGANIZER, Role.FACULTY_ADVISOR)) {
            mvc.perform(post("/api/events/e1/manager-approve").header(HttpHeaders.AUTHORIZATION, as(role)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/events/e1/manager-approve").header(HttpHeaders.AUTHORIZATION, as(Role.RESOURCE_ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void rejectingNeedsAReason() throws Exception {
        mvc.perform(post("/api/events/e1/advisor-reject").header(HttpHeaders.AUTHORIZATION, as(Role.FACULTY_ADVISOR))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void onlySuperAdminsManageClubs() throws Exception {
        String club = "{\"name\":\"Robotics\",\"advisorEmail\":\"a@campus.test\",\"organizerEmails\":[]}";
        for (Role role : List.of(Role.STUDENT, Role.CLUB_ORGANIZER, Role.FACULTY_ADVISOR, Role.RESOURCE_ADMIN)) {
            mvc.perform(post("/api/clubs").header(HttpHeaders.AUTHORIZATION, as(role))
                    .contentType(MediaType.APPLICATION_JSON).content(club)).andExpect(status().isForbidden());
        }
    }
}
