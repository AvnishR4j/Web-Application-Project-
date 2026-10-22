package com.campus.booking.attendance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campus.booking.config.SecurityConfig;
import com.campus.booking.controller.AttendanceController;
import com.campus.booking.event.EventService;
import com.campus.booking.exception.ApiExceptions.ForbiddenException;
import com.campus.booking.model.Attendance;
import com.campus.booking.model.Feedback;
import com.campus.booking.model.Role;
import com.campus.booking.model.User;
import com.campus.booking.security.JwtAuthFilter;
import com.campus.booking.security.JwtService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AttendanceController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=unit-test-secret-unit-test-secret-1234",
        "app.jwt.expiry-minutes=60",
        "app.cors.allowed-origin=http://localhost:5173"
})
class AttendanceAccessTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean AttendanceService attendance;
    @MockBean EventService events;

    private String as(Role role) {
        return "Bearer " + jwt.generate(new User("T", role.name().toLowerCase() + "@campus.test", "h", role, "CS"));
    }

    @Test
    void studentsNeverSeeTheCodeOrTheReport() throws Exception {
        mvc.perform(get("/api/events/e1/checkin-code").header(HttpHeaders.AUTHORIZATION, as(Role.STUDENT)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/events/e1/report").header(HttpHeaders.AUTHORIZATION, as(Role.STUDENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerOfAnotherClubIsRefused() throws Exception {
        doThrow(new ForbiddenException("no")).when(events).requireInsider(anyString(), any());
        mvc.perform(get("/api/events/e1/checkin-code").header(HttpHeaders.AUTHORIZATION, as(Role.CLUB_ORGANIZER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerOfTheClubGetsTheCode() throws Exception {
        when(attendance.currentCode(anyString()))
                .thenReturn(new AttendanceService.CheckinCode("ABC234", Instant.now(), 30, 0, 0));
        mvc.perform(get("/api/events/e1/checkin-code").header(HttpHeaders.AUTHORIZATION, as(Role.CLUB_ORGANIZER)))
                .andExpect(status().isOk());
    }

    @Test
    void studentCanCheckInAndGiveFeedback() throws Exception {
        when(attendance.checkIn(anyString(), anyString(), any())).thenReturn(new Attendance());
        when(attendance.giveFeedback(anyString(), anyInt(), any(), any())).thenReturn(new Feedback());
        mvc.perform(post("/api/events/e1/checkin").header(HttpHeaders.AUTHORIZATION, as(Role.STUDENT))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"ABC234\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/events/e1/feedback").header(HttpHeaders.AUTHORIZATION, as(Role.STUDENT))
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"comment\":\"Nice\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void ratingOutOfRangeIsRejected() throws Exception {
        mvc.perform(post("/api/events/e1/feedback").header(HttpHeaders.AUTHORIZATION, as(Role.STUDENT))
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":9}")).andExpect(status().isBadRequest());
    }

    @Test
    void anonymousCannotCheckIn() throws Exception {
        mvc.perform(post("/api/events/e1/checkin").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }
}
