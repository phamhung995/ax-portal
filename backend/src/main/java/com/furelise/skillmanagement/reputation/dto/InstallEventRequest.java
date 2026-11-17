package com.furelise.skillmanagement.reputation.dto;

/**
 * One install outcome reported by the companion app (GĐ6, BA FR-6).
 * targetType is SKILL or MCP. Either userId or knoxId identifies the
 * installing user (knoxId is resolved to a user when possible); both may
 * be null for an anonymous install. success is required; version is the
 * skill/MCP version that was installed (part of the dedupe key).
 */
public record InstallEventRequest(
        String targetType,
        Long targetId,
        Long userId,
        String knoxId,
        Boolean success,
        String version
) {
}
