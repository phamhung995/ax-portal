package com.skillmanagement.skillrequest.repository;

import com.skillmanagement.skillrequest.model.SkillRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repository for skill_requests.
 * All requests are visible to every authenticated user (no scope concept),
 * so no visibility Specification is needed.
 */
public interface SkillRequestRepository
        extends JpaRepository<SkillRequest, Long>, JpaSpecificationExecutor<SkillRequest> {
}
