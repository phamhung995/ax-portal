package com.furelise.skillmanagement.mcp.repository;

import com.furelise.skillmanagement.mcp.model.McpStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for mcp_stage (MCP's own taxonomy).
 */
public interface McpStageRepository extends JpaRepository<McpStage, Long> {

    List<McpStage> findAllByOrderBySortOrderAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
