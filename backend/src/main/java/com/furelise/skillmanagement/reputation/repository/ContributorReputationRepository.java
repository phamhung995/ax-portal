package com.furelise.skillmanagement.reputation.repository;

import com.furelise.skillmanagement.reputation.model.ContributorReputation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContributorReputationRepository extends JpaRepository<ContributorReputation, Long> {
}
