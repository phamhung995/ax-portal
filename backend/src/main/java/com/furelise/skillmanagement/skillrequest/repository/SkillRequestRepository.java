package com.furelise.skillmanagement.skillrequest.repository;

import com.furelise.skillmanagement.skillrequest.model.SkillRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;

/**
 * Repository for skill_requests.
 * All requests are visible to every authenticated user (no scope concept),
 * so no visibility Specification is needed.
 */
public interface SkillRequestRepository
        extends JpaRepository<SkillRequest, Long>, JpaSpecificationExecutor<SkillRequest> {

    /** Active bounties whose expiry has passed (nightly refund job). */
    List<SkillRequest> findByBountyStatusAndBountyExpiresAtBefore(String bountyStatus, Instant before);
}
