package com.furelise.skillmanagement.mcp.repository;

import com.furelise.skillmanagement.mcp.model.McpViewLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface McpViewLogRepository extends JpaRepository<McpViewLog, Long> {

    /** Latest view entry for a user on an MCP (idempotent view-window check). */
    Optional<McpViewLog> findTopByMcpIdAndUserIdOrderByViewedAtDesc(Long mcpId, Long userId);

    /**
     * View-log rows within a window for a set of MCPs (chart "total views"
     * metric). Only the rows are needed — the caller counts them.
     */
    List<McpViewLog> findByMcpIdInAndViewedAtBetween(
            Collection<Long> mcpIds, Instant start, Instant end);
}
