package com.campus.booking.repository;

import com.campus.booking.model.Club;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ClubRepository extends MongoRepository<Club, String> {
    boolean existsByName(String name);

    /** Clubs in which this email is one of the organizers (array element match). */
    @Query("{ 'organizerEmails': ?0 }")
    List<Club> findByOrganizer(String email);

    List<Club> findByAdvisorEmail(String email);
}
