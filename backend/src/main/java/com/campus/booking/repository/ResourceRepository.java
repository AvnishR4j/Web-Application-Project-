package com.campus.booking.repository;

import com.campus.booking.model.Resource;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ResourceRepository extends MongoRepository<Resource, String> {
    List<Resource> findByActiveTrueOrderByNameAsc();
    boolean existsByName(String name);
}
