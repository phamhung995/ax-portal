package com.furelise.skillmanagement.mcp.repository;

import com.furelise.skillmanagement.mcp.model.McpSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface McpSubscriptionRepository
        extends JpaRepository<McpSubscription, McpSubscription.McpSubscriptionId> {

    boolean existsByMcpIdAndUserId(Long mcpId, Long userId);

    List<McpSubscription> findByMcpId(Long mcpId);

    List<McpSubscription> findByUserId(Long userId);

    void deleteByMcpIdAndUserId(Long mcpId, Long userId);

    long countByUserId(Long userId);
}
