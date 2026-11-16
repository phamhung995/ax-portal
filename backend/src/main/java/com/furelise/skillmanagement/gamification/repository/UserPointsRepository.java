package com.furelise.skillmanagement.gamification.repository;

import com.furelise.skillmanagement.gamification.model.UserPoints;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPointsRepository extends JpaRepository<UserPoints, Long> {
}

