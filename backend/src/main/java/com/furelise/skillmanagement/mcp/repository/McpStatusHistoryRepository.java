package com.furelise.skillmanagement.mcp.repository;

import com.furelise.skillmanagement.mcp.model.McpStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface McpStatusHistoryRepository extends JpaRepository<McpStatusHistory, Long> {

    List<McpStatusHistory> findByMcpIdOrderByChangedAtDesc(Long mcpId);
}
