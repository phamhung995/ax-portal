package com.furelise.skillmanagement.user.repository;

import com.furelise.skillmanagement.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
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
     * Single IN-clause fetch for a set of ids. The inherited
     * {@code findAllById} issues one SELECT per id (N+1), which is too
     * slow for the AI News comment list (one query per commenter).
     */
    @Query("select u from User u where u.id in :ids")
    List<User> findByIdIn(@Param("ids") Collection<Long> ids);

    /** Users created within a window (chart "new users" metric). */
    List<User> findByCreatedAtBetween(Instant start, Instant end);

    /**
     * Users whose Knox ID (email local part) matches, case-insensitive.
     * Not unique: the live DB can hold two users sharing a local part
     * (e.g. admin@skillmgmt.local and admin@gmail.com), so this returns a list.
     */
    List<User> findByKnoxIdIgnoreCase(String knoxId);

    /** Users whose Knox ID matches one of the given values, case-insensitive (chart bucketing). */
    List<User> findByKnoxIdIgnoreCaseIn(Collection<String> knoxIds);

    boolean existsByKnoxIdIgnoreCase(String knoxId);

    boolean existsByKnoxIdIgnoreCaseAndIdNot(String knoxId, Long id);
}
