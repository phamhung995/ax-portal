package com.furelise.skillmanagement.mcp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Skill <-> MCP dependency join (owned by the MCP module). FK points INTO
 * skills_master; the skill schema is untouched. A skill pins an EXACT
 * mcp_version (BR-04). UNIQUE (skill_id, mcp_id) prevents the same MCP twice
 * on one skill (SRS §8.2).
 *
 * <p>Editable only while the skill is DRAFT/REJECTED; APPROVED skills change
 * MCPs via a new skill version.
 *
 * <p>source: SKILL_MD (declared in frontmatter of skill.md — Q-11) | MANUAL.
 * mcp_server_key: denormalized for frontmatter matching + display (Q-3).
 * mcp_version_number: pinned INTEGER version (Q-8).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "skill_mcp_dependency",
        uniqueConstraints = @UniqueConstraint(name = "uq_skill_mcp_dependency", columnNames = {"skill_id", "mcp_id"}))
public class SkillMcpDependency {

    public static final String SOURCE_SKILL_MD = "SKILL_MD";
    public static final String SOURCE_MANUAL = "MANUAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "mcp_id", nullable = false)
    private Long mcpId;

    @Column(name = "mcp_code", nullable = false, length = 100)
    private String mcpCode;

    @Column(name = "mcp_server_key", nullable = false, length = 100)
    private String mcpServerKey;

    @Column(name = "mcp_version_number", nullable = false)
    private Integer mcpVersionNumber;

    @Column(nullable = false)
    @Builder.Default
    private Boolean required = Boolean.TRUE;

    @Column(nullable = false, length = 20)
    private String source;
}
