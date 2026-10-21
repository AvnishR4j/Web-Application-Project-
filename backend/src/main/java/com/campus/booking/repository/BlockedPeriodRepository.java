package com.campus.booking.repository;

import com.campus.booking.model.BlockedPeriod;
import java.time.Instant;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BlockedPeriodRepository extends MongoRepository<BlockedPeriod, String> {

    List<BlockedPeriod> findByResourceIdAndStartLessThanAndEndGreaterThan(String resourceId, Instant to, Instant from);

    /** Periods that apply to every resource (resourceId is null). */
    List<BlockedPeriod> findByResourceIdIsNullAndStartLessThanAndEndGreaterThan(Instant to, Instant from);

    List<BlockedPeriod> findByResourceId(String resourceId);
}
