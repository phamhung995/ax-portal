package com.furelise.skillmanagement.reputation.repository;

import com.furelise.skillmanagement.reputation.model.ReputationScore;
import com.furelise.skillmanagement.reputation.model.ReputationScoreId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReputationScoreRepository extends JpaRepository<ReputationScore, ReputationScoreId> {

    List<ReputationScore> findByIdTargetTypeAndIdTargetIdIn(String targetType, Collection<Long> targetIds);

    List<ReputationScore> findByIdTargetTypeOrderByScoreDesc(String targetType);
}
