package com.furelise.skillmanagement.aitools.repository;

import com.furelise.skillmanagement.aitools.model.AiTool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

/**
 * Repository for ai_tools. No visibility rules (no scope/status) —
 * every authenticated user sees every not-deleted tool.
 */
public interface AiToolRepository extends JpaRepository<AiTool, Long>, JpaSpecificationExecutor<AiTool> {

    boolean existsByNameIgnoreCaseAndDeletedYn(String name, String deletedYn);

    List<AiTool> findByIdIn(Collection<Long> ids);
}
