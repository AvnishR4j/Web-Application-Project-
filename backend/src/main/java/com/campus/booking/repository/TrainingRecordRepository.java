package com.campus.booking.repository;

import com.campus.booking.model.TrainingRecord;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TrainingRecordRepository extends MongoRepository<TrainingRecord, String> {
    boolean existsByUserEmailAndResourceId(String userEmail, String resourceId);
    List<TrainingRecord> findByResourceId(String resourceId);
    void deleteByUserEmailAndResourceId(String userEmail, String resourceId);
}
