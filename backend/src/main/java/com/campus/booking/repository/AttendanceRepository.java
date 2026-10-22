package com.campus.booking.repository;

import com.campus.booking.model.Attendance;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AttendanceRepository extends MongoRepository<Attendance, String> {
    Optional<Attendance> findByEventIdAndUserEmail(String eventId, String userEmail);
    boolean existsByEventIdAndUserEmail(String eventId, String userEmail);
    long countByEventId(String eventId);
    List<Attendance> findByEventIdOrderByCheckedInAtAsc(String eventId);
    List<Attendance> findByUserEmail(String userEmail);

    void deleteByEventId(String eventId);
}
