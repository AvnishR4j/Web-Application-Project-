package com.campus.booking.repository;

import com.campus.booking.model.Feedback;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FeedbackRepository extends MongoRepository<Feedback, String> {
    boolean existsByEventIdAndUserEmail(String eventId, String userEmail);
    List<Feedback> findByEventIdOrderByCreatedAtDesc(String eventId);
    List<Feedback> findByUserEmail(String userEmail);

    void deleteByEventId(String eventId);
}
