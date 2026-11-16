package com.furelise.skillmanagement.gamification.repository;

import com.furelise.skillmanagement.gamification.model.Perk;
import com.furelise.skillmanagement.gamification.model.PerkRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PerkRepository extends JpaRepository<Perk, Long> {

    List<Perk> findByActiveOrderByCostAsc(String active);
}

