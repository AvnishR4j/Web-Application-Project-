package com.campus.booking.repository;

import com.campus.booking.model.User;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    java.util.List<User> findByRole(com.campus.booking.model.Role role);

    java.util.List<User> findByEmailIn(java.util.Collection<String> emails);
}
