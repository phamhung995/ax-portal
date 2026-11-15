package com.furelise.skillmanagement.mcp.service;

import com.furelise.skillmanagement.mcp.model.McpMaster;
import com.furelise.skillmanagement.mcp.model.SkillMcpDependency;
import com.furelise.skillmanagement.mcp.repository.McpMasterRepository;
import com.furelise.skillmanagement.mcp.repository.SkillMcpDependencyRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Publish validation for MCP dependencies (Skill ↔ MCP integration).
 *
 * <p>Validates when:
 * <ul>
 *   <li>Owner submits skill for approval (DRAFT/REJECTED → PENDING_APPROVAL);</li>
 *   <li>Admin approves skill (re-validate because the MCP may have changed
 *       between submit and approve).</li>
 * </ul>
 *
 * <p>Checks EVERY dependency (dependencies are optional at save, but ALL
 * pinned MCPs must be ready at submit):
 * <ol>
 *   <li>MCP exists and not soft-deleted (MCP_DELETED);</li>
 *   <li>The MCP is live — APPROVED or a version-update-pending MCP which
 *       still serves its approved version (NOT_APPROVED otherwise);</li>
 *   <li>The pinned version is ≤ the MCP's current version (NOT_APPROVED).</li>
 * </ol>
 *
 * <p>Returns {@code blockers[]} with {mcpId, mcpCode, mcpVersion, reason}.
 * Reasons: NOT_APPROVED, MCP_DELETED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpPublishValidationService {

    private final SkillMcpDependencyRepository skillMcpDependencyRepository;
    private final McpMasterRepository mcpMasterRepository;

    /**
     * Validates MCP dependencies for a skill being published.
     *
     * @param skillId the skill being submitted/approved
     * @return list of blockers (empty = validation passed)
     */
    @Transactional(readOnly = true)
    public List<Blocker> validate(Long skillId) {
        List<SkillMcpDependency> deps = skillMcpDependencyRepository.findBySkillId(skillId);
        List<Blocker> blockers = new ArrayList<>();
        for (SkillMcpDependency dep : deps) {
            McpMaster master = mcpMasterRepository.findById(dep.getMcpId()).orElse(null);
            if (master == null || "Y".equals(master.getDeletedYn())) {
                blockers.add(Blocker.builder()
                        .mcpId(dep.getMcpId()).mcpCode(dep.getMcpCode())
                        .mcpVersion(dep.getMcpVersionNumber()).reason("MCP_DELETED")
                        .build());
                continue;
            }
            boolean live = McpMaster.STATUS_APPROVED.equals(master.getStatus())
                    || master.isVersionUpdatePending();
            boolean versionOk = dep.getMcpVersionNumber() != null
                    && dep.getMcpVersionNumber() <= master.getVersion();
            if (!live || !versionOk) {
                blockers.add(Blocker.builder()
                        .mcpId(dep.getMcpId()).mcpCode(dep.getMcpCode())
                        .mcpVersion(dep.getMcpVersionNumber()).reason("NOT_APPROVED")
                        .build());
            }
        }
        return blockers;
    }

    /** One blocker item: {mcpId, mcpCode, mcpVersion, reason}. */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Blocker {
        private Long mcpId;
        private String mcpCode;
        private Integer mcpVersion;
        private String reason;
    }
}
