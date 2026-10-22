package com.campus.booking.report;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campus.booking.config.SecurityConfig;
import com.campus.booking.controller.ReportController;
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

@WebMvcTest(ReportController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=unit-test-secret-unit-test-secret-1234",
        "app.jwt.expiry-minutes=60",
        "app.cors.allowed-origin=http://localhost:5173"
})
class ReportAccessTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean ReportService reports;

    private static final String RANGE = "?from=2030-01-01T00:00:00Z&to=2030-02-01T00:00:00Z";

    private String as(Role role) {
        return "Bearer " + jwt.generate(new User("T", role.name().toLowerCase() + "@campus.test", "h", role, "CS"));
    }

    @Test
    void onlyAdminsSeeReports() throws Exception {
        when(reports.utilization(any(), any())).thenReturn(List.of());
        for (Role role : List.of(Role.STUDENT, Role.CLUB_ORGANIZER, Role.FACULTY_ADVISOR)) {
            mvc.perform(get("/api/reports/utilization" + RANGE).header(HttpHeaders.AUTHORIZATION, as(role)))
                    .andExpect(status().isForbidden());
        }
        for (Role role : List.of(Role.RESOURCE_ADMIN, Role.SUPER_ADMIN)) {
            mvc.perform(get("/api/reports/utilization" + RANGE).header(HttpHeaders.AUTHORIZATION, as(role)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void auditLogIsForSuperAdminsOnly() throws Exception {
        when(reports.auditLog(anyInt())).thenReturn(List.of());
        mvc.perform(get("/api/admin/audit").header(HttpHeaders.AUTHORIZATION, as(Role.RESOURCE_ADMIN)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit").header(HttpHeaders.AUTHORIZATION, as(Role.SUPER_ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void backwardsOrHugeRangesAreRejected() throws Exception {
        mvc.perform(get("/api/reports/utilization?from=2030-02-01T00:00:00Z&to=2030-01-01T00:00:00Z")
                .header(HttpHeaders.AUTHORIZATION, as(Role.RESOURCE_ADMIN))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/attendance?from=2030-01-01T00:00:00Z&to=2032-01-01T00:00:00Z")
                .header(HttpHeaders.AUTHORIZATION, as(Role.RESOURCE_ADMIN))).andExpect(status().isBadRequest());
    }
}
