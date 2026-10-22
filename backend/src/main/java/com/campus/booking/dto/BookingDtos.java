package com.campus.booking.dto;

import com.campus.booking.booking.BookingService;
import com.campus.booking.model.BlockedKind;
import com.campus.booking.model.ResourceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class BookingDtos {

    private BookingDtos() {
    }

    private static final String HHMM = "^([01]\\d|2[0-3]):[0-5]\\d$";

    public record ResourceRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull ResourceType type,
            @Size(max = 200) String location,
            @Min(0) @Max(100000) int capacity,
            @Min(0) @Max(240) int bufferMinutes,
            @Pattern(regexp = HHMM, message = "must be HH:mm") String openTime,
            @Pattern(regexp = HHMM, message = "must be HH:mm") String closeTime,
            boolean requiresTraining,
            Boolean active) {
    }

    public record BlockedPeriodRequest(
            String resourceId,
            @NotNull Instant start,
            @NotNull Instant end,
            @NotNull BlockedKind kind,
            @Size(max = 200) String reason) {
    }

    public record TrainingRequest(
            @NotBlank String userEmail,
            @NotBlank String resourceId) {
    }

    public record BookingRequest(
            @NotBlank String resourceId,
            @NotBlank @Size(max = 160) String title,
            @NotNull Instant start,
            @NotNull Instant end,
            @Min(1) @Max(100000) int attendees) {

        public BookingService.BookingInput toInput() {
            return new BookingService.BookingInput(resourceId, title, start, end, attendees);
        }
    }

    public record RejectRequest(@NotBlank @Size(max = 300) String reason) {
    }

    /** Only the times and status of other people's bookings, never their titles or names. */
    public record ScheduleSlot(Instant start, Instant end, String status) {
    }
}
