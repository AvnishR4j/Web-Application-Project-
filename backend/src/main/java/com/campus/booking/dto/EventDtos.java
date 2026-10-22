package com.campus.booking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class EventDtos {

    private EventDtos() {
    }

    public record EventRequest(
            @NotBlank String clubId,
            @NotBlank @Size(max = 160) String title,
            @Size(max = 2000) String description,
            @NotBlank String resourceId,
            @NotNull Instant start,
            @NotNull Instant end,
            @Min(1) @Max(100000) int expectedAttendees) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record OptionalReasonRequest(@Size(max = 500) String reason) {
    }

    public record ClubRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @NotBlank String advisorEmail,
            List<String> organizerEmails) {
    }
}
