package com.campus.booking.controller;

import com.campus.booking.exception.ApiExceptions.BadRequestException;
import com.campus.booking.report.ReportService;
import com.campus.booking.report.ReportService.AttendanceSummary;
import com.campus.booking.report.ReportService.AuditEntry;
import com.campus.booking.report.ReportService.ResourceUsage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportController {

    private static final Duration MAX_RANGE = Duration.ofDays(366);

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/api/reports/utilization")
    public List<ResourceUsage> utilization(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        checkRange(from, to);
        return reports.utilization(from, to);
    }

    @GetMapping("/api/reports/attendance")
    public AttendanceSummary attendance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        checkRange(from, to);
        return reports.attendance(from, to);
    }

    /** Every recorded change to every event, newest first. Super admins only (under /api/admin). */
    @GetMapping("/api/admin/audit")
    public List<AuditEntry> audit(@RequestParam(defaultValue = "200") int limit) {
        return reports.auditLog(limit);
    }

    private static void checkRange(Instant from, Instant to) {
        if (!to.isAfter(from)) {
            throw new BadRequestException("'to' must be after 'from'");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new BadRequestException("The range can be at most one year");
        }
    }
}
