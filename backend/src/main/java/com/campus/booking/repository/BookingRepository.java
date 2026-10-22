package com.campus.booking.repository;

import com.campus.booking.model.Booking;
import com.campus.booking.model.BookingStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BookingRepository extends MongoRepository<Booking, String> {

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    List<Booking> findByRequesterEmailOrderByStartDesc(String requesterEmail);

    List<Booking> findByStatusOrderByStartAsc(BookingStatus status);

    /** Bookings of one resource with the given statuses that start before {@code to} and end after {@code from}. */
    List<Booking> findByResourceIdAndStatusInAndStartLessThanAndEndGreaterThan(
            String resourceId, Collection<BookingStatus> statuses, Instant to, Instant from);

    long countByResourceId(String resourceId);

    void deleteByResourceId(String resourceId);
}
