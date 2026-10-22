package com.skillmanagement.ainews.repository;

import com.skillmanagement.ainews.model.AiNewsSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiNewsSourceRepository extends JpaRepository<AiNewsSource, Long> {

    List<AiNewsSource> findByStatus(String status);
}
