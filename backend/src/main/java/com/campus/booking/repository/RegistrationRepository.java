package com.campus.booking.repository;

import com.campus.booking.model.Registration;
import com.campus.booking.model.RegistrationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RegistrationRepository extends MongoRepository<Registration, String> {

    Optional<Registration> findByEventIdAndUserEmail(String eventId, String userEmail);

    long countByEventIdAndStatus(String eventId, RegistrationStatus status);

    /** Head of the waitlist: earliest waitlistedAt, ties broken by insertion order. */
    Optional<Registration> findFirstByEventIdAndStatusOrderByWaitlistedAtAscIdAsc(String eventId, RegistrationStatus status);

    List<Registration> findByEventIdAndStatusOrderByWaitlistedAtAscIdAsc(String eventId, RegistrationStatus status);

    List<Registration> findByEventIdAndStatusOrderByConfirmedAtAsc(String eventId, RegistrationStatus status);

    List<Registration> findByUserEmailOrderByCreatedAtDesc(String userEmail);

    void deleteByEventId(String eventId);
}
