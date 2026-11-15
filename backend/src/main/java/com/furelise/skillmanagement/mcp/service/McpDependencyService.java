package com.furelise.skillmanagement.mcp.service;

import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.mcp.model.McpFile;
import com.furelise.skillmanagement.mcp.model.McpMaster;
import com.furelise.skillmanagement.mcp.model.McpVersion;
import com.furelise.skillmanagement.mcp.model.McpVersionFile;
import com.furelise.skillmanagement.mcp.model.SkillMcpDependency;
import com.furelise.skillmanagement.mcp.repository.McpFileRepository;
import com.furelise.skillmanagement.mcp.repository.McpMasterRepository;
import com.furelise.skillmanagement.mcp.repository.McpVersionFileRepository;
import com.furelise.skillmanagement.mcp.repository.McpVersionRepository;
import com.furelise.skillmanagement.mcp.repository.SkillMcpDependencyRepository;
import com.furelise.skillmanagement.skill.dto.McpDependencyItem;
import com.furelise.skillmanagement.skill.dto.McpDependencyItem.McpDependencyFileItem;
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages skill_mcp_dependency rows (Skill ↔ MCP integration).
 *
 * <p>Rebuilt 2026-11 on the Skill model: the MCP master row IS the current
 * version (mcp_master.version), so a skill pins an MCP at a version NUMBER
 * that must be ≤ the MCP's current version. Validation:
 * <ul>
 *   <li>mcpId must exist and not be soft-deleted (400 MCP_NOT_FOUND);</li>
 *   <li>the pinned version must be ≤ the MCP's current version
 *       (400 MCP_VERSION_NOT_FOUND);</li>
 *   <li>the MCP must be APPROVED (or a version-update-pending MCP, which
 *       still serves its live approved version) — EXCEPT when the skill
 *       creator owns the MCP (owner can pin their own DRAFT/PENDING MCPs);</li>
 *   <li>No duplicate mcpId in the list (400).</li>
 * </ul>
 *
 * <p>Dependencies are optional (every pinned MCP is stored with
 * {@code required = false}); ALL deps are validated at skill submit/approve
 * (McpPublishValidationService).
 *
 * <p>Dependencies are editable only when the skill is DRAFT/REJECTED.
 * The caller (SkillController) enforces this.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpDependencyService {

    private final SkillMcpDependencyRepository skillMcpDependencyRepository;
    private final McpMasterRepository mcpMasterRepository;
    private final McpFileRepository mcpFileRepository;
    private final McpVersionRepository mcpVersionRepository;
    private final McpVersionFileRepository mcpVersionFileRepository;

    /**
     * Replaces the MCP dependencies of a skill (delete old, insert new).
     * Called after skill create/update.
     *
     * @param skillId        the skill id
     * @param skillCreatorId the creator (owner) of the skill — used for the
     *                       owner exception: owner can pin their own
     *                       DRAFT/PENDING MCPs
     * @param skillScope     the skill's public scope (kept for the signature —
     *                       MCPs are PUBLIC only, no scope coverage check)
     * @param dependencies   list of {mcpId, mcpVersion} (null/empty = clear all)
     * @param source         SKILL_MD or MANUAL (default MANUAL)
     */
    @Transactional
    public void saveDependencies(Long skillId, Long skillCreatorId, String skillScope,
                                 List<McpDependencyInput> dependencies, String source) {
        // Clear existing — bulk JPQL delete executes immediately, so the old
        // rows are gone before the IDENTITY inserts below (a derived delete
        // is deferred to flush and would violate uq_skill_mcp_dependency).
        skillMcpDependencyRepository.bulkDeleteBySkillId(skillId);

        if (dependencies == null || dependencies.isEmpty()) {
            return;
        }

        Set<Long> seen = new HashSet<>();
        for (McpDependencyInput dep : dependencies) {
            if (dep.mcpId() == null) {
                throw new BusinessException(BusinessErrorCode.MCP_NOT_FOUND, "mcpId is required.");
            }
            if (!seen.add(dep.mcpId())) {
                throw new BusinessException(BusinessErrorCode.MCP_NOT_FOUND,
                        "Duplicate MCP in dependency list: " + dep.mcpId());
            }
            // Validate MCP exists (not soft-deleted).
            McpMaster master = mcpMasterRepository.findById(dep.mcpId())
                    .filter(m -> !"Y".equals(m.getDeletedYn()))
                    .orElseThrow(() -> new BusinessException(BusinessErrorCode.MCP_NOT_FOUND,
                            "MCP not found: " + dep.mcpId()));
            // Validate the pinned version is ≤ the MCP's current version.
            if (dep.mcpVersion() == null || dep.mcpVersion() < 1
                    || dep.mcpVersion() > master.getVersion()) {
                throw new BusinessException(BusinessErrorCode.MCP_VERSION_NOT_FOUND,
                        "MCP version not found: " + dep.mcpId() + "@" + dep.mcpVersion());
            }
            // The MCP must be live (APPROVED, or a version-update-pending
            // MCP which still serves its live approved version) — EXCEPT
            // when the skill creator owns this MCP (owner can pin their own
            // DRAFT/PENDING MCPs).
            boolean live = McpMaster.STATUS_APPROVED.equals(master.getStatus())
                    || master.isVersionUpdatePending();
            if (!live && !ownsMcp(skillCreatorId, master)) {
                throw new BusinessException(BusinessErrorCode.MCP_VERSION_NOT_APPROVED,
                        "MCP not approved: " + dep.mcpId() + "@" + dep.mcpVersion());
            }

            skillMcpDependencyRepository.save(SkillMcpDependency.builder()
                    .skillId(skillId)
                    .mcpId(dep.mcpId())
                    .mcpCode(master.getMcpCode())
                    .mcpServerKey(master.getServerKey())
                    .mcpVersionNumber(dep.mcpVersion())
                    // Every dependency is optional (required = false).
                    .required(Boolean.FALSE)
                    .source(source != null ? source : SkillMcpDependency.SOURCE_MANUAL)
                    .build());
        }
    }

    /** True when the skill creator is also the MCP creator. */
    private boolean ownsMcp(Long skillCreatorId, McpMaster master) {
        return skillCreatorId != null && skillCreatorId.equals(master.getCreatedBy());
    }

    /**
     * Returns the MCP dependencies of a skill for the Skill Detail "MCPs (n)"
     * tab. For each dependency the response carries the MCP status, whether
     * the viewer can download it ({@code availableForViewer} = APPROVED +
     * visible to viewer, OR soft-deleted — a deleted MCP's files stay
     * downloadable so linked skills keep working) and, when available, the
     * live files of the MCP.
     *
     * <p>Internal access: when the skill's scope is PRIVATE or PROJECT, an
     * unapproved MCP is still available — the viewer already sees the skill
     * (internal audience), so the MCP approval gate (which protects PUBLIC
     * distribution) does not apply.
     *
     * @param skillScope the skill's public scope (drives the internal rule)
     */
    @Transactional(readOnly = true)
    public List<McpDependencyItem> listForSkill(Long skillId, String skillScope, User viewer) {
        List<SkillMcpDependency> deps = skillMcpDependencyRepository.findBySkillId(skillId);
        if (deps.isEmpty()) {
            return List.of();
        }
        boolean internal = Skill.SCOPE_PRIVATE.equals(skillScope)
                || Skill.SCOPE_PROJECT.equals(skillScope);
        List<McpDependencyItem> items = new ArrayList<>();
        for (SkillMcpDependency dep : deps) {
            McpMaster master = mcpMasterRepository.findById(dep.getMcpId()).orElse(null);
            boolean exists = master != null;
            boolean deleted = exists && "Y".equals(master.getDeletedYn());
            boolean visible = exists && !deleted && mcpMasterRepository.count(
                    McpMasterRepository.visibleTo(viewer)
                            .and((root, query, cb) -> cb.equal(root.get("id"), dep.getMcpId()))) > 0;
            boolean approved = exists
                    && (McpMaster.STATUS_APPROVED.equals(master.getStatus())
                        || master.isVersionUpdatePending());
            // A deleted MCP stays downloadable (linked skills keep working).
            // An unapproved MCP stays downloadable for internal skills
            // (PRIVATE/PROJECT) — the viewer is already inside the audience.
            boolean available = deleted || approved && visible
                    || (internal && exists && !deleted);

            List<McpDependencyFileItem> files = null;
            if (available) {
                files = mcpFileRepository.findByMcpIdAndPendingYn(dep.getMcpId(), "N").stream()
                        .map(f -> new McpDependencyFileItem(
                                f.getId(), null, f.getFileName(), f.getFileSize()))
                        .toList();
            }
            items.add(new McpDependencyItem(
                    dep.getMcpId(),
                    dep.getMcpCode(),
                    exists ? master.getMcpName() : null,
                    dep.getMcpServerKey(),
                    dep.getMcpVersionNumber(),
                    dep.getSource(),
                    exists ? master.getStatus() : null,
                    deleted,
                    available,
                    visible,
                    files,
                    skillScope));
        }
        return items;
    }

    /** Input for saving a dependency: mcpId + pinned version number. */
    public record McpDependencyInput(Long mcpId, Integer mcpVersion) {
    }

    /**
     * Files of a skill's pinned MCP version, resolved WITHOUT the MCP's own
     * visibility rule — the caller (SkillController) enforces that the
     * viewer can see the SKILL, which is the access boundary (internal
     * access for PRIVATE/PROJECT skills). The MCP must be a dependency of
     * the skill; the version must be ≤ the MCP's current version.
     *
     * <p>The current version has no snapshot row until superseded — its
     * files are the live {@code mcp_files} (pending_yn='N').
     */
    @Transactional(readOnly = true)
    public List<McpVersionFile> skillDepVersionFiles(Long skillId, Long mcpId, int version) {
        SkillMcpDependency dep = skillMcpDependencyRepository.findBySkillId(skillId).stream()
                .filter(d -> d.getMcpId().equals(mcpId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.MCP_NOT_FOUND,
                        "This MCP is not a dependency of the skill."));
        McpMaster master = mcpMasterRepository.findById(mcpId).orElse(null);
        if (master == null) {
            throw new BusinessException(BusinessErrorCode.MCP_NOT_FOUND, "MCP not found.");
        }
        if (version < 1 || version > master.getVersion()) {
            throw new BusinessException(BusinessErrorCode.MCP_VERSION_NOT_FOUND,
                    "MCP version does not exist.");
        }
        // Current version: the live mcp_files ARE this version.
        if (version == master.getVersion()) {
            List<McpVersionFile> files = mcpFileRepository.findByMcpIdAndPendingYn(mcpId, "N").stream()
                    .map(f -> McpVersionFile.builder()
                            .fileName(f.getFileName())
                            .filePath(f.getFilePath())
                            .fileSize(f.getFileSize())
                            .build())
                    .toList();
            if (files.isEmpty()) {
                throw new BusinessException(BusinessErrorCode.MCP_FILE_NOT_FOUND,
                        "This version has no files.");
            }
            return files;
        }
        // Superseded version: the snapshot.
        McpVersion snapshot = mcpVersionRepository.findByMcpIdAndVersion(mcpId, version)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.MCP_VERSION_NOT_FOUND,
                        "MCP version does not exist."));
        List<McpVersionFile> files = mcpVersionFileRepository.findByMcpVersionId(snapshot.getId());
        if (files.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.MCP_FILE_NOT_FOUND,
                    "This version has no files.");
        }
        return files;
    }
}
