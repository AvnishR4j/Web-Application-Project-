package com.campus.booking.booking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campus.booking.config.SecurityConfig;
import com.campus.booking.controller.BookingController;
import com.campus.booking.controller.ResourceController;
import com.campus.booking.model.Booking;
import com.campus.booking.model.Role;
import com.campus.booking.model.User;
import com.campus.booking.repository.BookingRepository;
import com.campus.booking.repository.ResourceRepository;
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

/** Role rules for resources and bookings, enforced on the server. Services are mocked. */
@WebMvcTest({BookingController.class, ResourceController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=unit-test-secret-unit-test-secret-1234",
        "app.jwt.expiry-minutes=60",
        "app.cors.allowed-origin=http://localhost:5173"
})
class BookingAccessTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean BookingService service;
    @MockBean BookingRepository bookings;
    @MockBean ResourceRepository resources;

    private static final String CHECK_BODY = """
            {"resourceId":"r1","title":"Hackathon","start":"2030-01-10T10:00:00Z",
             "end":"2030-01-10T12:00:00Z","attendees":30}""";

    private static final String RESOURCE_BODY = """
            {"name":"Hall","type":"ROOM","capacity":100,"bufferMinutes":15,
             "openTime":"08:00","closeTime":"20:00","requiresTraining":false}""";

    private String as(Role role) {
        return "Bearer " + jwt.generate(new User("T", role.name().toLowerCase() + "@campus.test", "h", role, "CS"));
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/api/resources")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/bookings/check").contentType(MediaType.APPLICATION_JSON).content(CHECK_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anyLoggedInUserCanBrowseResources() throws Exception {
        when(resources.findByActiveTrueOrderByNameAsc()).thenReturn(List.of());
        mvc.perform(get("/api/resources").header(HttpHeaders.AUTHORIZATION, as(Role.STUDENT)))
                .andExpect(status().isOk());
    }

    @Test
    void onlyResourceManagersCanCreateResources() throws Exception {
        when(resources.save(any())).thenAnswer(i -> i.getArgument(0));
        for (Role role : List.of(Role.STUDENT, Role.CLUB_ORGANIZER)) {
            mvc.perform(post("/api/resources").header(HttpHeaders.AUTHORIZATION, as(role))
                            .contentType(MediaType.APPLICATION_JSON).content(RESOURCE_BODY))
                    .andExpect(status().isForbidden());
        }
        for (Role role : List.of(Role.RESOURCE_ADMIN, Role.SUPER_ADMIN)) {
            mvc.perform(post("/api/resources").header(HttpHeaders.AUTHORIZATION, as(role))
                            .contentType(MediaType.APPLICATION_JSON).content(RESOURCE_BODY))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void invalidResourceReturnsFieldErrors() throws Exception {
        String bad = """
                {"name":"","type":"ROOM","capacity":10,"bufferMinutes":0,"openTime":"8am","closeTime":"20:00"}""";
        mvc.perform(post("/api/resources").header(HttpHeaders.AUTHORIZATION, as(Role.RESOURCE_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists())
                .andExpect(jsonPath("$.fields.openTime").exists());
    }

    @Test
    void studentsCannotUseBookingEndpoints() throws Exception {
        mvc.perform(post("/api/bookings/check").header(HttpHeaders.AUTHORIZATION, as(Role.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON).content(CHECK_BODY))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/bookings/mine").header(HttpHeaders.AUTHORIZATION, as(Role.STUDENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerCanCheckAndCreate() throws Exception {
        when(service.check(any(), any())).thenReturn(new BookingService.CheckResult(true, List.of()));
        when(service.create(any(), anyString(), any())).thenReturn(new Booking());
        mvc.perform(post("/api/bookings/check").header(HttpHeaders.AUTHORIZATION, as(Role.CLUB_ORGANIZER))
                        .contentType(MediaType.APPLICATION_JSON).content(CHECK_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
        mvc.perform(post("/api/bookings").header(HttpHeaders.AUTHORIZATION, as(Role.CLUB_ORGANIZER))
                        .header("Idempotency-Key", "abc-123")
                        .contentType(MediaType.APPLICATION_JSON).content(CHECK_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void creatingWithoutIdempotencyKeyIsRejected() throws Exception {
        mvc.perform(post("/api/bookings").header(HttpHeaders.AUTHORIZATION, as(Role.CLUB_ORGANIZER))
                        .contentType(MediaType.APPLICATION_JSON).content(CHECK_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conflictsComeBackAs409WithOneEntryPerRule() throws Exception {
        when(service.create(any(), anyString(), any())).thenThrow(new BookingConflictException(List.of(
                new ConflictEngine.Violation(ConflictEngine.Code.OVERLAP, "already booked"),
                new ConflictEngine.Violation(ConflictEngine.Code.CAPACITY, "too many people"))));
        mvc.perform(post("/api/bookings").header(HttpHeaders.AUTHORIZATION, as(Role.CLUB_ORGANIZER))
                        .header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(CHECK_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fields.OVERLAP").value("already booked"))
                .andExpect(jsonPath("$.fields.CAPACITY").value("too many people"));
    }

    @Test
    void onlyResourceManagersCanApproveReject() throws Exception {
        when(service.approve(anyString(), anyString())).thenReturn(new Booking());
        mvc.perform(patch("/api/bookings/b1/approve").header(HttpHeaders.AUTHORIZATION, as(Role.CLUB_ORGANIZER)))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/bookings/b1/approve").header(HttpHeaders.AUTHORIZATION, as(Role.STUDENT)))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/bookings/b1/approve").header(HttpHeaders.AUTHORIZATION, as(Role.RESOURCE_ADMIN)))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/bookings/b1/reject").header(HttpHeaders.AUTHORIZATION, as(Role.CLUB_ORGANIZER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"no\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerCannotListEveryBooking() throws Exception {
        when(bookings.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of());
        mvc.perform(get("/api/bookings").header(HttpHeaders.AUTHORIZATION, as(Role.CLUB_ORGANIZER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/bookings").header(HttpHeaders.AUTHORIZATION, as(Role.RESOURCE_ADMIN)))
                .andExpect(status().isOk());
    }
}
