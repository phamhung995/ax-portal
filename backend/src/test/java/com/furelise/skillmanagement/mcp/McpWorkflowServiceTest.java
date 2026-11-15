package com.furelise.skillmanagement.mcp;

import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.mcp.dto.McpPendingMetadata;
import com.furelise.skillmanagement.mcp.model.McpMaster;
import com.furelise.skillmanagement.mcp.repository.McpMasterRepository;
import com.furelise.skillmanagement.mcp.repository.McpStatusHistoryRepository;
import com.furelise.skillmanagement.mcp.service.McpAccessHelper;
import com.furelise.skillmanagement.mcp.service.McpNotificationService;
import com.furelise.skillmanagement.mcp.service.McpVersionUpdateService;
import com.furelise.skillmanagement.mcp.service.impl.McpWorkflowServiceImpl;
import com.furelise.skillmanagement.model.Role;
import com.furelise.skillmanagement.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpWorkflowServiceTest {

    @Mock private McpMasterRepository mcpMasterRepository;
    @Mock private McpStatusHistoryRepository mcpStatusHistoryRepository;
    @Mock private McpAccessHelper mcpAccessHelper;
    @Mock private McpVersionUpdateService versionUpdateService;
    @Mock private McpNotificationService mcpNotificationService;

    @InjectMocks private McpWorkflowServiceImpl workflowService;

    private User owner;
    private User admin;
    private McpMaster mcp;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(1L).name("Owner").email("owner@example.com")
                .knoxId("owner").role(Role.builder().name("NORMAL_USER").build()).build();
        admin = User.builder().id(3L).name("Admin").email("admin@example.com")
                .knoxId("admin").role(Role.builder().name("ADMIN_USER").build()).build();
        mcp = McpMaster.builder()
                .id(100L).mcpCode("MCP-0001").mcpName("Test MCP").serverKey("test-mcp")
                .status(McpMaster.STATUS_DRAFT).createdBy(1L).deletedYn("N").build();
    }

    private void stubVisible() {
        when(mcpAccessHelper.getVisible(eq(100L), any(User.class))).thenReturn(mcp);
    }

    private McpPendingMetadata pendingMetadata() {
        return new McpPendingMetadata("Test MCP v2", null, List.of(), List.of(1L), List.of(1L));
    }

    @Test
    void draftToPendingByOwner_ok() {
        stubVisible();
        workflowService.transition(100L, owner, McpMaster.STATUS_PENDING_APPROVAL, null);
        assertEquals(McpMaster.STATUS_PENDING_APPROVAL, mcp.getStatus());
        verify(mcpStatusHistoryRepository).save(any());
        // A new-MCP submit notifies the admins.
        verify(mcpNotificationService).notifySubmitForApproval(eq(100L), eq("Test MCP"), eq("Owner"));
    }

    @Test
    void draftToPendingByAdmin_ok() {
        stubVisible();
        workflowService.transition(100L, admin, McpMaster.STATUS_PENDING_APPROVAL, null);
        assertEquals(McpMaster.STATUS_PENDING_APPROVAL, mcp.getStatus());
    }

    @Test
    void draftToApproved_rejected() {
        stubVisible();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowService.transition(100L, admin, McpMaster.STATUS_APPROVED, null));
        assertEquals(BusinessErrorCode.MCP_INVALID_TRANSITION, ex.getCode());
    }

    @Test
    void pendingToApproved_byNonAdmin_rejected() {
        mcp.setStatus(McpMaster.STATUS_PENDING_APPROVAL);
        stubVisible();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowService.transition(100L, owner, McpMaster.STATUS_APPROVED, null));
        assertEquals(BusinessErrorCode.MCP_INVALID_TRANSITION, ex.getCode());
    }

    @Test
    void pendingToApproved_byAdmin_ok_notifiesOwner() {
        mcp.setStatus(McpMaster.STATUS_PENDING_APPROVAL);
        stubVisible();
        workflowService.transition(100L, admin, McpMaster.STATUS_APPROVED, null);
        assertEquals(McpMaster.STATUS_APPROVED, mcp.getStatus());
        verify(mcpNotificationService).notifyOwnerDecision(eq(100L), eq("Test MCP"), eq(1L), eq(true), isNull());
    }

    @Test
    void pendingToRejected_requiresReason() {
        mcp.setStatus(McpMaster.STATUS_PENDING_APPROVAL);
        stubVisible();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowService.transition(100L, admin, McpMaster.STATUS_REJECTED, "  "));
        assertEquals(BusinessErrorCode.MCP_INVALID_TRANSITION, ex.getCode());
    }

    @Test
    void pendingToRejected_byAdmin_ok() {
        mcp.setStatus(McpMaster.STATUS_PENDING_APPROVAL);
        stubVisible();
        workflowService.transition(100L, admin, McpMaster.STATUS_REJECTED, "not good");
        assertEquals(McpMaster.STATUS_REJECTED, mcp.getStatus());
        assertEquals("not good", mcp.getRejectionReason());
        verify(mcpNotificationService).notifyOwnerDecision(eq(100L), eq("Test MCP"), eq(1L), eq(false), eq("not good"));
    }

    @Test
    void pendingToDraft_withdrawByOwner_ok() {
        mcp.setStatus(McpMaster.STATUS_PENDING_APPROVAL);
        stubVisible();
        workflowService.transition(100L, owner, McpMaster.STATUS_DRAFT, null);
        assertEquals(McpMaster.STATUS_DRAFT, mcp.getStatus());
        // No owner-decision notification when the actor is the owner.
        verify(mcpNotificationService, never()).notifyOwnerDecision(anyLong(), anyString(), anyLong(), anyBoolean(), any());
    }

    @Test
    void pendingToDraft_withdrawByNonOwner_rejected() {
        mcp.setStatus(McpMaster.STATUS_PENDING_APPROVAL);
        stubVisible();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowService.transition(100L, admin, McpMaster.STATUS_DRAFT, null));
        assertEquals(BusinessErrorCode.MCP_INVALID_TRANSITION, ex.getCode());
    }

    @Test
    void approvedToAnyDirect_rejected() {
        mcp.setStatus(McpMaster.STATUS_APPROVED);
        stubVisible();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowService.transition(100L, admin, McpMaster.STATUS_DRAFT, null));
        assertEquals(BusinessErrorCode.MCP_INVALID_TRANSITION, ex.getCode());
    }

    // ── Version update transitions ───────────────────────────────────────────

    @Test
    void approvedToPendingByOwner_versionUpdateSubmission_ok() {
        mcp.setStatus(McpMaster.STATUS_APPROVED);
        stubVisible();
        workflowService.transition(100L, owner, McpMaster.STATUS_PENDING_APPROVAL, null);
        assertEquals(McpMaster.STATUS_PENDING_APPROVAL, mcp.getStatus());
        // A version update is NOT a new-MCP submit — no admin notification.
        verify(mcpNotificationService, never()).notifySubmitForApproval(anyLong(), anyString(), anyString());
    }

    @Test
    void approvedToPendingByNonOwner_rejected() {
        mcp.setStatus(McpMaster.STATUS_APPROVED);
        stubVisible();
        User stranger = User.builder().id(9L).name("Stranger").email("s@example.com")
                .knoxId("stranger").role(Role.builder().name("NORMAL_USER").build()).build();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowService.transition(100L, stranger, McpMaster.STATUS_PENDING_APPROVAL, null));
        assertEquals(BusinessErrorCode.MCP_INVALID_TRANSITION, ex.getCode());
    }

    @Test
    void versionUpdateApproved_completesAndStaysApproved() {
        mcp.setStatus(McpMaster.STATUS_PENDING_APPROVAL);
        mcp.setPendingMetadata(pendingMetadata());
        stubVisible();

        workflowService.transition(100L, admin, McpMaster.STATUS_APPROVED, null);

        assertEquals(McpMaster.STATUS_APPROVED, mcp.getStatus());
        verify(versionUpdateService).completeVersionUpdate(mcp, admin);
        verify(versionUpdateService, never()).discardPendingVersion(any());
    }

    @Test
    void versionUpdateRejected_staysApprovedKeepsReason() {
        mcp.setStatus(McpMaster.STATUS_PENDING_APPROVAL);
        mcp.setPendingMetadata(pendingMetadata());
        stubVisible();

        workflowService.transition(100L, admin, McpMaster.STATUS_REJECTED, "not ready");

        // The old version stays live — the MCP does NOT go to REJECTED.
        assertEquals(McpMaster.STATUS_APPROVED, mcp.getStatus());
        assertEquals("not ready", mcp.getRejectionReason());
        verify(versionUpdateService).discardPendingVersion(mcp);
        verify(versionUpdateService, never()).completeVersionUpdate(any(), any());
        // The owner gets the "rejected" message with the reason.
        verify(mcpNotificationService).notifyOwnerDecision(eq(100L), eq("Test MCP"), eq(1L), eq(false), eq("not ready"));
    }

    @Test
    void versionUpdateCancelledByOwner_discardsAndStaysApproved() {
        mcp.setStatus(McpMaster.STATUS_PENDING_APPROVAL);
        mcp.setPendingMetadata(pendingMetadata());
        stubVisible();

        // Owner withdraws a pending version update → the MCP stays APPROVED.
        workflowService.transition(100L, owner, McpMaster.STATUS_DRAFT, null);

        assertEquals(McpMaster.STATUS_APPROVED, mcp.getStatus());
        verify(versionUpdateService).discardPendingVersion(mcp);
        verify(versionUpdateService, never()).completeVersionUpdate(any(), any());
    }

    @Test
    void isVersionUpdatePending_onlyWithPendingMetadata() {
        mcp.setStatus(McpMaster.STATUS_PENDING_APPROVAL);
        assertFalse(mcp.isVersionUpdatePending());
        mcp.setPendingMetadata(pendingMetadata());
        assertTrue(mcp.isVersionUpdatePending());
    }
}
