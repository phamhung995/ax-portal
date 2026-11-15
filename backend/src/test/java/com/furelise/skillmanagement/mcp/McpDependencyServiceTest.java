package com.furelise.skillmanagement.mcp;

import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.mcp.model.McpMaster;
import com.furelise.skillmanagement.mcp.repository.McpFileRepository;
import com.furelise.skillmanagement.mcp.repository.McpMasterRepository;
import com.furelise.skillmanagement.mcp.repository.SkillMcpDependencyRepository;
import com.furelise.skillmanagement.mcp.service.McpDependencyService;
import com.furelise.skillmanagement.mcp.service.McpDependencyService.McpDependencyInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpDependencyServiceTest {

    @Mock private SkillMcpDependencyRepository skillMcpDependencyRepository;
    @Mock private McpMasterRepository mcpMasterRepository;
    @Mock private McpFileRepository mcpFileRepository;
    @Mock private com.furelise.skillmanagement.mcp.repository.McpVersionRepository mcpVersionRepository;
    @Mock private com.furelise.skillmanagement.mcp.repository.McpVersionFileRepository mcpVersionFileRepository;

    @InjectMocks private McpDependencyService service;

    private McpMaster approvedMcp;

    @BeforeEach
    void setUp() {
        approvedMcp = McpMaster.builder()
                .id(10L).mcpCode("MCP-0010").mcpName("Approved MCP").serverKey("approved-mcp")
                .status(McpMaster.STATUS_APPROVED).version(2).createdBy(1L).deletedYn("N").build();
    }

    @Test
    void saveDependencies_replacesExisting() {
        when(mcpMasterRepository.findById(10L)).thenReturn(Optional.of(approvedMcp));

        service.saveDependencies(100L, 9L, "PUBLIC",
                List.of(new McpDependencyInput(10L, 2)), null);

        verify(skillMcpDependencyRepository).bulkDeleteBySkillId(100L);
        ArgumentCaptor<com.furelise.skillmanagement.mcp.model.SkillMcpDependency> captor =
                ArgumentCaptor.forClass(com.furelise.skillmanagement.mcp.model.SkillMcpDependency.class);
        verify(skillMcpDependencyRepository).save(captor.capture());
        var saved = captor.getValue();
        assertEquals(100L, saved.getSkillId());
        assertEquals(10L, saved.getMcpId());
        assertEquals("MCP-0010", saved.getMcpCode());
        assertEquals("approved-mcp", saved.getMcpServerKey());
        assertEquals(2, saved.getMcpVersionNumber());
        // Every dependency is optional.
        assertEquals(Boolean.FALSE, saved.getRequired());
        assertEquals("MANUAL", saved.getSource());
    }

    @Test
    void saveDependencies_empty_clearsAll() {
        service.saveDependencies(100L, 9L, "PUBLIC", List.of(), null);
        verify(skillMcpDependencyRepository).bulkDeleteBySkillId(100L);
        verify(skillMcpDependencyRepository, never()).save(any());
    }

    @Test
    void saveDependencies_duplicateMcp_rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.saveDependencies(100L, 9L, "PUBLIC",
                        List.of(new McpDependencyInput(10L, 1), new McpDependencyInput(10L, 2)), null));
        assertEquals(BusinessErrorCode.MCP_NOT_FOUND, ex.getCode());
    }

    @Test
    void saveDependencies_missingMcp_rejected() {
        when(mcpMasterRepository.findById(99L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.saveDependencies(100L, 9L, "PUBLIC",
                        List.of(new McpDependencyInput(99L, 1)), null));
        assertEquals(BusinessErrorCode.MCP_NOT_FOUND, ex.getCode());
    }

    @Test
    void saveDependencies_deletedMcp_rejected() {
        McpMaster deleted = McpMaster.builder()
                .id(12L).mcpCode("MCP-0012").mcpName("Deleted").serverKey("deleted-mcp")
                .status(McpMaster.STATUS_APPROVED).version(1).createdBy(1L).deletedYn("Y").build();
        when(mcpMasterRepository.findById(12L)).thenReturn(Optional.of(deleted));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.saveDependencies(100L, 9L, "PUBLIC",
                        List.of(new McpDependencyInput(12L, 1)), null));
        assertEquals(BusinessErrorCode.MCP_NOT_FOUND, ex.getCode());
    }

    @Test
    void saveDependencies_versionAboveCurrent_rejected() {
        when(mcpMasterRepository.findById(10L)).thenReturn(Optional.of(approvedMcp));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.saveDependencies(100L, 9L, "PUBLIC",
                        List.of(new McpDependencyInput(10L, 3)), null));
        assertEquals(BusinessErrorCode.MCP_VERSION_NOT_FOUND, ex.getCode());
    }

    @Test
    void saveDependencies_draftMcp_byStranger_rejected() {
        McpMaster draft = McpMaster.builder()
                .id(13L).mcpCode("MCP-0013").mcpName("Draft MCP").serverKey("draft-mcp")
                .status(McpMaster.STATUS_DRAFT).version(1).createdBy(5L).deletedYn("N").build();
        when(mcpMasterRepository.findById(13L)).thenReturn(Optional.of(draft));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.saveDependencies(100L, 9L, "PUBLIC",
                        List.of(new McpDependencyInput(13L, 1)), null));
        assertEquals(BusinessErrorCode.MCP_VERSION_NOT_APPROVED, ex.getCode());
    }

    @Test
    void saveDependencies_ownDraftMcp_ownerException_ok() {
        // The skill creator owns the DRAFT MCP — allowed (Q-18 exception).
        McpMaster draft = McpMaster.builder()
                .id(13L).mcpCode("MCP-0013").mcpName("Draft MCP").serverKey("draft-mcp")
                .status(McpMaster.STATUS_DRAFT).version(1).createdBy(9L).deletedYn("N").build();
        when(mcpMasterRepository.findById(13L)).thenReturn(Optional.of(draft));

        service.saveDependencies(100L, 9L, "PUBLIC",
                List.of(new McpDependencyInput(13L, 1)), null);

        verify(skillMcpDependencyRepository).save(any());
    }

    // ── listForSkill: internal access (PRIVATE/PROJECT) ─────────────────────

    private com.furelise.skillmanagement.mcp.model.SkillMcpDependency dep() {
        return com.furelise.skillmanagement.mcp.model.SkillMcpDependency.builder()
                .skillId(100L).mcpId(10L).mcpCode("MCP-0010")
                .mcpServerKey("approved-mcp").mcpVersionNumber(2)
                .required(Boolean.FALSE).source("MANUAL").build();
    }

    @Test
    void listForSkill_draftMcp_publicSkill_notAvailable() {
        McpMaster draft = McpMaster.builder()
                .id(10L).mcpCode("MCP-0010").mcpName("Draft MCP").serverKey("draft-mcp")
                .status(McpMaster.STATUS_DRAFT).version(1).createdBy(5L).deletedYn("N").build();
        when(skillMcpDependencyRepository.findBySkillId(100L)).thenReturn(List.of(dep()));
        when(mcpMasterRepository.findById(10L)).thenReturn(Optional.of(draft));
        when(mcpMasterRepository.count(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<com.furelise.skillmanagement.mcp.model.McpMaster>>any())).thenReturn(0L);

        var items = service.listForSkill(100L, "PART",
                com.furelise.skillmanagement.user.model.User.builder().id(1L).build());

        assertEquals(1, items.size());
        assertFalse(items.get(0).availableForViewer());
        assertNull(items.get(0).files());
    }

    @Test
    void listForSkill_draftMcp_privateSkill_available() {
        // Internal access: an unapproved MCP is downloadable for a
        // PRIVATE/PROJECT skill — the viewer already sees the skill.
        McpMaster draft = McpMaster.builder()
                .id(10L).mcpCode("MCP-0010").mcpName("Draft MCP").serverKey("draft-mcp")
                .status(McpMaster.STATUS_DRAFT).version(1).createdBy(5L).deletedYn("N").build();
        when(skillMcpDependencyRepository.findBySkillId(100L)).thenReturn(List.of(dep()));
        when(mcpMasterRepository.findById(10L)).thenReturn(Optional.of(draft));
        when(mcpMasterRepository.count(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<com.furelise.skillmanagement.mcp.model.McpMaster>>any())).thenReturn(0L);
        when(mcpFileRepository.findByMcpIdAndPendingYn(10L, "N")).thenReturn(List.of(
                com.furelise.skillmanagement.mcp.model.McpFile.builder()
                        .id(50L).fileName("a.txt").filePath("m/10/a.txt").fileSize(1L).build()));

        var items = service.listForSkill(100L, "PRIVATE",
                com.furelise.skillmanagement.user.model.User.builder().id(1L).build());

        assertEquals(1, items.size());
        assertTrue(items.get(0).availableForViewer());
        assertFalse(items.get(0).mcpVisibleToViewer());
        assertEquals(1, items.get(0).files().size());
        assertEquals("PRIVATE", items.get(0).skillScope());
    }

    @Test
    void listForSkill_draftMcp_projectSkill_available() {
        McpMaster draft = McpMaster.builder()
                .id(10L).mcpCode("MCP-0010").mcpName("Draft MCP").serverKey("draft-mcp")
                .status(McpMaster.STATUS_DRAFT).version(1).createdBy(5L).deletedYn("N").build();
        when(skillMcpDependencyRepository.findBySkillId(100L)).thenReturn(List.of(dep()));
        when(mcpMasterRepository.findById(10L)).thenReturn(Optional.of(draft));
        when(mcpMasterRepository.count(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<com.furelise.skillmanagement.mcp.model.McpMaster>>any())).thenReturn(0L);
        when(mcpFileRepository.findByMcpIdAndPendingYn(10L, "N")).thenReturn(List.of());

        var items = service.listForSkill(100L, "PROJECT",
                com.furelise.skillmanagement.user.model.User.builder().id(1L).build());

        assertTrue(items.get(0).availableForViewer());
        assertEquals("PROJECT", items.get(0).skillScope());
    }

    @Test
    void listForSkill_approvedMcp_publicSkill_availableAndVisible() {
        when(skillMcpDependencyRepository.findBySkillId(100L)).thenReturn(List.of(dep()));
        when(mcpMasterRepository.findById(10L)).thenReturn(Optional.of(approvedMcp));
        when(mcpMasterRepository.count(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<com.furelise.skillmanagement.mcp.model.McpMaster>>any())).thenReturn(1L);
        when(mcpFileRepository.findByMcpIdAndPendingYn(10L, "N")).thenReturn(List.of(
                com.furelise.skillmanagement.mcp.model.McpFile.builder()
                        .id(50L).fileName("a.txt").filePath("m/10/a.txt").fileSize(1L).build()));

        var items = service.listForSkill(100L, "PUBLIC",
                com.furelise.skillmanagement.user.model.User.builder().id(1L).build());

        assertTrue(items.get(0).availableForViewer());
        assertTrue(items.get(0).mcpVisibleToViewer());
    }

    // ── skillDepVersionFiles ─────────────────────────────────────────────────

    @Test
    void skillDepVersionFiles_notADependency_rejected() {
        when(skillMcpDependencyRepository.findBySkillId(100L)).thenReturn(List.of());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.skillDepVersionFiles(100L, 10L, 1));
        assertEquals(BusinessErrorCode.MCP_NOT_FOUND, ex.getCode());
    }

    @Test
    void skillDepVersionFiles_currentVersion_servesLiveFiles() {
        when(skillMcpDependencyRepository.findBySkillId(100L)).thenReturn(List.of(dep()));
        when(mcpMasterRepository.findById(10L)).thenReturn(Optional.of(approvedMcp));
        when(mcpFileRepository.findByMcpIdAndPendingYn(10L, "N")).thenReturn(List.of(
                com.furelise.skillmanagement.mcp.model.McpFile.builder()
                        .id(50L).fileName("a.txt").filePath("m/10/a.txt").fileSize(1L).build()));

        var files = service.skillDepVersionFiles(100L, 10L, 2);

        assertEquals(1, files.size());
        assertEquals("a.txt", files.get(0).getFileName());
    }

    @Test
    void skillDepVersionFiles_versionAboveCurrent_rejected() {
        when(skillMcpDependencyRepository.findBySkillId(100L)).thenReturn(List.of(dep()));
        when(mcpMasterRepository.findById(10L)).thenReturn(Optional.of(approvedMcp));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.skillDepVersionFiles(100L, 10L, 3));
        assertEquals(BusinessErrorCode.MCP_VERSION_NOT_FOUND, ex.getCode());
    }
}
