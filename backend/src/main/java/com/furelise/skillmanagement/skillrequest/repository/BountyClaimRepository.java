package com.furelise.skillmanagement.skillrequest.repository;

import com.furelise.skillmanagement.skillrequest.model.BountyClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BountyClaimRepository extends JpaRepository<BountyClaim, Long> {

    List<BountyClaim> findByRequestIdOrderByCreatedAtAsc(Long requestId);

    Optional<BountyClaim> findByRequestIdAndSkillId(Long requestId, Long skillId);

    /** Pending claims whose decision deadline has passed (auto-award). */
    List<BountyClaim> findByStatusAndDecideByBefore(String status, Instant before);

    long countByClaimantIdAndStatus(Long claimantId, String status);

    long countByRequestIdAndStatus(Long requestId, String status);
}
