package com.campus.booking.repository;

import com.campus.booking.model.EmailToken;
import com.campus.booking.model.EmailToken.Purpose;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EmailTokenRepository extends MongoRepository<EmailToken, String> {

    Optional<EmailToken> findFirstByEmailAndPurposeOrderByCreatedAtDesc(String email, Purpose purpose);

    long countByEmailAndPurposeAndCreatedAtAfter(String email, Purpose purpose, Instant after);

    Optional<EmailToken> findByHashAndPurpose(String hash, Purpose purpose);

    void deleteByEmailAndPurpose(String email, Purpose purpose);
}
