package com.campus.booking.repository;

import com.campus.booking.model.AuditLog;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
    List<AuditLog> findByEventIdOrderByAtAsc(String eventId);
    void deleteByEventId(String eventId);
}
