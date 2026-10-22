package com.campus.booking.repository;

import com.campus.booking.model.Event;
import com.campus.booking.model.EventStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EventRepository extends MongoRepository<Event, String> {
    List<Event> findByStatusAndEndGreaterThanOrderByStartAsc(EventStatus status, Instant now);
    List<Event> findByClubIdInOrderByStartDesc(Collection<String> clubIds);
    List<Event> findByClubIdInAndStatusOrderByStartAsc(Collection<String> clubIds, EventStatus status);
    List<Event> findByStatusOrderByStartAsc(EventStatus status);
    void deleteByClubId(String clubId);
}
