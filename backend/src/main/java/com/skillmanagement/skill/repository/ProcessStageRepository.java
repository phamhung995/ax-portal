package com.skillmanagement.skill.repository;

import com.skillmanagement.skill.model.ProcessStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for process_stage.
 */
public interface ProcessStageRepository extends JpaRepository<ProcessStage, Long> {

    List<ProcessStage> findAllByOrderBySortOrderAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
