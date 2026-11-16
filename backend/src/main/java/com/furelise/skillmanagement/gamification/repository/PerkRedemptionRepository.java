package com.furelise.skillmanagement.gamification.repository;

import com.furelise.skillmanagement.gamification.model.PerkRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PerkRedemptionRepository extends JpaRepository<PerkRedemption, Long> {

    List<PerkRedemption> findByUserIdOrderByCreatedAtDesc(Long userId);
}
