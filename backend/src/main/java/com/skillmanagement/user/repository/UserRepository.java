package com.skillmanagement.user.repository;

import com.skillmanagement.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for User entity.
 */
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    /** Email is the login ID and unique case-insensitively (SRS BR-01). */
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    List<User> findAllByOrderByCreatedAtDesc();

    /**
     * Users whose Knox ID (email local part) matches, case-insensitive.
     * Not unique: the live DB can hold two users sharing a local part
     * (e.g. admin@skillmgmt.local and admin@gmail.com), so this returns a list.
     */
    List<User> findByKnoxIdIgnoreCase(String knoxId);

    boolean existsByKnoxIdIgnoreCase(String knoxId);

    boolean existsByKnoxIdIgnoreCaseAndIdNot(String knoxId, Long id);
}
