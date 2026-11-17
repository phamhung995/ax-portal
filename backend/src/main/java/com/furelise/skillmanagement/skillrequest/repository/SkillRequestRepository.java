package com.furelise.skillmanagement.skillrequest.repository;

import com.furelise.skillmanagement.skillrequest.model.SkillRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * Repository for skill_requests.
 * All requests are visible to every authenticated user (no scope concept),
 * so no visibility Specification is needed.
 */
public interface SkillRequestRepository
        extends JpaRepository<SkillRequest, Long>, JpaSpecificationExecutor<SkillRequest> {

    /** Requests fulfilled by any of the given skills (contributor fulfilled count, GĐ5). */
    long countByFulfilledSkillIdInAndFulfillmentStatus(List<Long> skillIds, String fulfillmentStatus);
}
