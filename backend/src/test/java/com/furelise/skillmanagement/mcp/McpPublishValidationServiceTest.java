package com.furelise.skillmanagement.mcp;

import com.furelise.skillmanagement.mcp.dto.McpPendingMetadata;
import com.furelise.skillmanagement.mcp.model.McpMaster;
import com.furelise.skillmanagement.mcp.model.SkillMcpDependency;
import com.furelise.skillmanagement.mcp.repository.McpMasterRepository;
import com.furelise.skillmanagement.mcp.repository.SkillMcpDependencyRepository;
import com.furelise.skillmanagement.mcp.service.McpPublishValidationService;
import com.furelise.skillmanagement.mcp.service.McpPublishValidationService.Blocker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpPublishValidationServiceTest {

    @Mock private SkillMcpDependencyRepository skillMcpDependencyRepository;
    @Mock private McpMasterRepository mcpMasterRepository;

    @InjectMocks private McpPublishValidationService service;

    private McpMaster approvedMcp;

    @BeforeEach
    void setUp() {
        approvedMcp = McpMaster.builder()
                .id(10L).mcpCode("MCP-0010").mcpName("Approved MCP").serverKey("approved-mcp")
                .status(McpMaster.STATUS_APPROVED).version(2).createdBy(1L).deletedYn("N").build();
    }

    private SkillMcpDependency dep(Long mcpId, String code, Integer version) {
        return SkillMcpDependency.builder()
                .skillId(100L).mcpId(mcpId).mcpCode(code)
                .mcpServerKey("key").mcpVersionNumber(version)
                .required(Boolean.FALSE).source(SkillMcpDependency.SOURCE_MANUAL).build();
    }

    @Test
    void noDependencies_noBlockers() {
        when(skillMcpDependencyRepository.findBySkillId(100L)).thenReturn(List.of());
        assertTrue(service.validate(100L).isEmpty());
    }

    @Test
    void approvedMcp_currentVersion_noBlockers() {
        when(skillMcpDependencyRepository.findBySkillId(100L)).thenReturn(List.of(dep(10L, "MCP-0010", 2)));
        when(mcpMasterRepository.findById(10L)).thenReturn(Optional.of(approvedMcp));
        assertTrue(service.validate(100L).isEmpty());
    }

    @Test
    void versionUpdatePendingMcp_stillLive_noBlockers() {
        McpMaster pending = McpMaster.builder()
                .id(11L).mcpCode("MCP-0011").mcpName("Pending MCP").serverKey("pending-mcp")
                .status(McpMaster.STATUS_PENDING_APPROVAL).version(1).createdBy(1L).deletedYn("N")
                .pendingMetadata(new McpPendingMetadata("Pending v2", null, List.of(), List.of(1L), List.of(1L)))
                .build();
        when(skillMcpDependencyRepository.findBySkillId(100L)).thenReturn(List.of(dep(11L, "MCP-0011", 1)));
        when(mcpMasterRepository.findById(11L)).thenReturn(Optional.of(pending));
        assertTrue(service.validate(100L).isEmpty());
    }

    @Test
    void deletedMcp_blockerMcpDeleted() {
        McpMaster deleted = McpMaster.builder()
                .id(12L).mcpCode("MCP-0012").mcpName("Deleted MCP").serverKey("deleted-mcp")
                .status(McpMaster.STATUS_APPROVED).version(1).createdBy(1L).deletedYn("Y").build();
        when(skillMcpDependencyRepository.findBySkillId(100L)).thenReturn(List.of(dep(12L, "MCP-0012", 1)));
        when(mcpMasterRepository.findById(12L)).thenReturn(Optional.of(deleted));

        List<Blocker> blockers = service.validate(100L);

        assertEquals(1, blockers.size());
        assertEquals("MCP_DELETED", blockers.get(0).getReason());
        assertEquals(12L, blockers.get(0).getMcpId());
    }

    @Test
    void missingMcp_blockerMcpDeleted() {
        when(skillMcpDependencyRepository.findBySkillId(100L)).thenReturn(List.of(dep(99L, "MCP-0099", 1)));
        when(mcpMasterRepository.findById(99L)).thenReturn(Optional.empty());

        List<Blocker> blockers = service.validate(100L);

        assertEquals(1, blockers.size());
        assertEquals("MCP_DELETED", blockers.get(0).getReason());
    }

    @Test
    void draftMcp_blockerNotApproved() {
        McpMaster draft = McpMaster.builder()
                .id(13L).mcpCode("MCP-0013").mcpName("Draft MCP").serverKey("draft-mcp")
                .status(McpMaster.STATUS_DRAFT).version(1).createdBy(1L).deletedYn("N").build();
        when(skillMcpDependencyRepository.findBySkillId(100L)).thenReturn(List.of(dep(13L, "MCP-0013", 1)));
        when(mcpMasterRepository.findById(13L)).thenReturn(Optional.of(draft));

        List<Blocker> blockers = service.validate(100L);

        assertEquals(1, blockers.size());
        assertEquals("NOT_APPROVED", blockers.get(0).getReason());
    }

    @Test
    void pinnedVersionAboveCurrent_blockerNotApproved() {
        // The MCP is approved at v2 but the skill pins v3 (impossible state,
        // but the validation must still block it).
        when(skillMcpDependencyRepository.findBySkillId(100L)).thenReturn(List.of(dep(10L, "MCP-0010", 3)));
        when(mcpMasterRepository.findById(10L)).thenReturn(Optional.of(approvedMcp));

        List<Blocker> blockers = service.validate(100L);

        assertEquals(1, blockers.size());
        assertEquals("NOT_APPROVED", blockers.get(0).getReason());
    }
}
