package com.furelise.skillmanagement.mcp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Subscription: a user follows an MCP to receive new-version
 * notifications. Composite PK (mcp_id, user_id).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "mcp_subscriptions")
@IdClass(McpSubscription.McpSubscriptionId.class)
public class McpSubscription {

    @Id
    @Column(name = "mcp_id", nullable = false)
    private Long mcpId;

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class McpSubscriptionId implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long mcpId;
        private Long userId;
    }
}
