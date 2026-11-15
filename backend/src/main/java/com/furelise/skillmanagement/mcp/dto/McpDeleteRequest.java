package com.furelise.skillmanagement.mcp.dto;

import java.util.List;

/** DELETE /api/v1/mcps body: the selected MCP ids. */
public record McpDeleteRequest(List<Long> ids) {
}
