package com.furelise.skillmanagement.skill;

import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.model.Role;
import com.furelise.skillmanagement.notification.service.EventNotificationService;
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.skill.repository.SkillRepository;
import com.furelise.skillmanagement.skill.repository.SkillStatusHistoryRepository;
import com.furelise.skillmanagement.user.repository.UserRepository;
import com.furelise.skillmanagement.user.repository.UserSettingsRepository;
import com.furelise.skillmanagement.skill.service.SkillAccessHelper;
import com.furelise.skillmanagement.skill.service.impl.SkillWorkflowServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillWorkflowServiceTest {

    @Mock private SkillRepository skillRepository;
    @Mock private SkillStatusHistoryRepository skillStatusHistoryRepository;
    @Mock private com.furelise.skillmanagement.skill.repository.SkillShareRepository skillShareRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private EventNotificationService eventNotificationService;
    @Mock private SkillAccessHelper skillAccessHelper;
    @Mock private com.furelise.skillmanagement.skill.service.SkillVersionUpdateService versionUpdateService;
    @Mock private com.furelise.skillmanagement.skill.repository.SkillCoworkUserRepository skillCoworkUserRepository;
    @Mock private com.furelise.skillmanagement.mcp.service.McpPublishValidationService mcpPublishValidationService;
    @Mock private com.furelise.skillmanagement.mcp.service.McpNotificationService mcpNotificationService;

    @InjectMocks private SkillWorkflowServiceImpl workflowService;

    private User creator;
    private User admin;
    private Skill skill;

    @BeforeEach
    void setUp() {
        creator = User.builder().id(1L).name("Creator").email("creator@example.com")
                .knoxId("creator").role(Role.builder().name("NORMAL_USER").build()).build();
        admin = User.builder().id(3L).name("Admin").email("admin@example.com")
                .knoxId("admin").role(Role.builder().name("ADMIN_USER").build()).build();
        skill = Skill.builder()
                .id(100L).name("Test Skill")
                .approvalStatus(Skill.STATUS_DRAFT).publicScope(Skill.SCOPE_PRIVATE)
                .createdBy(1L).deletedYn("N").build();
    }

    private void stubVisible() {
        when(skillAccessHelper.getVisible(eq(100L), any(User.class))).thenReturn(skill);
    }

    @Test
    void draftToPendingByCreator_ok() {
        stubVisible();
        workflowService.transition(100L, creator, Skill.STATUS_PENDING_APPROVAL, null);
        assertEquals(Skill.STATUS_PENDING_APPROVAL, skill.getApprovalStatus());
        verify(skillStatusHistoryRepository).save(any());
    }

    @Test
    void draftToPublished_rejected() {
        stubVisible();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowService.transition(100L, admin, Skill.STATUS_APPROVED, null));
        assertEquals(BusinessErrorCode.SKILL_INVALID_TRANSITION, ex.getCode());
    }

    @Test
    void pendingToPublished_byNonAdmin_rejected() {
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        stubVisible();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowService.transition(100L, creator, Skill.STATUS_APPROVED, null));
        assertEquals(BusinessErrorCode.SKILL_INVALID_TRANSITION, ex.getCode());
    }

    @Test
    void pendingToRejected_requiresReason() {
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        stubVisible();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowService.transition(100L, admin, Skill.STATUS_REJECTED, "  "));
        assertEquals(BusinessErrorCode.SKILL_INVALID_TRANSITION, ex.getCode());
    }

    @Test
    void pendingToRejected_byAdmin_ok() {
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        stubVisible();
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.empty());

        workflowService.transition(100L, admin, Skill.STATUS_REJECTED, "not good");

        assertEquals(Skill.STATUS_REJECTED, skill.getApprovalStatus());
        assertEquals("not good", skill.getRejectionReason());
        verify(eventNotificationService).notify(eq(admin), eq(creator), isNull(), any());
    }

    @Test
    void pendingToDraft_withdrawByCreator_ok() {
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        stubVisible();
        workflowService.transition(100L, creator, Skill.STATUS_DRAFT, null);
        assertEquals(Skill.STATUS_DRAFT, skill.getApprovalStatus());
        // No notification when the actor is the creator.
        verify(eventNotificationService, never()).notify(any(), any(), any(), any());
    }

    @Test
    void pendingToDraft_withdrawByNonCreator_rejected() {
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        stubVisible();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowService.transition(100L, admin, Skill.STATUS_DRAFT, null));
        assertEquals(BusinessErrorCode.SKILL_INVALID_TRANSITION, ex.getCode());
    }

    @Test
    void publishedToAny_rejected() {
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        stubVisible();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowService.transition(100L, admin, Skill.STATUS_DRAFT, null));
        assertEquals(BusinessErrorCode.SKILL_INVALID_TRANSITION, ex.getCode());
    }

    // ── Version update transitions ───────────────────────────────────────────

    @Test
    void approvedToPendingByCreator_versionUpdateSubmission_ok() {
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        stubVisible();
        workflowService.transition(100L, creator, Skill.STATUS_PENDING_APPROVAL, null);
        assertEquals(Skill.STATUS_PENDING_APPROVAL, skill.getApprovalStatus());
    }

    @Test
    void approvedToPendingByNonCreator_rejected() {
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        stubVisible();
        User stranger = User.builder().id(9L).name("Stranger").email("s@example.com")
                .knoxId("stranger").role(Role.builder().name("NORMAL_USER").build()).build();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowService.transition(100L, stranger, Skill.STATUS_PENDING_APPROVAL, null));
        assertEquals(BusinessErrorCode.SKILL_INVALID_TRANSITION, ex.getCode());
    }

    @Test
    void versionUpdateApproved_completesAndStaysApproved() {
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        skill.setPendingMetadata(new com.furelise.skillmanagement.skill.dto.SkillPendingMetadata(
                "Test Skill", null, "PRIVATE", List.of(), List.of(1L), List.of(1L), null, null));
        stubVisible();

        workflowService.transition(100L, admin, Skill.STATUS_APPROVED, null);

        assertEquals(Skill.STATUS_APPROVED, skill.getApprovalStatus());
        verify(versionUpdateService).completeVersionUpdate(skill, admin);
        verify(versionUpdateService, never()).discardPendingVersion(any());
    }

    @Test
    void versionUpdateRejected_staysApprovedKeepsReason() {
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        skill.setPendingMetadata(new com.furelise.skillmanagement.skill.dto.SkillPendingMetadata(
                "Test Skill", null, "PRIVATE", List.of(), List.of(1L), List.of(1L), null, null));
        stubVisible();
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.empty());

        workflowService.transition(100L, admin, Skill.STATUS_REJECTED, "not ready");

        // The old version stays live — the skill does NOT go to REJECTED.
        assertEquals(Skill.STATUS_APPROVED, skill.getApprovalStatus());
        assertEquals("not ready", skill.getRejectionReason());
        verify(versionUpdateService).discardPendingVersion(skill);
        verify(versionUpdateService, never()).completeVersionUpdate(any(), any());
    }

    @Test
    void versionUpdateCancelledByCreator_discardsAndStaysApproved() {
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        skill.setPendingMetadata(new com.furelise.skillmanagement.skill.dto.SkillPendingMetadata(
                "Test Skill", null, "PRIVATE", List.of(), List.of(1L), List.of(1L), null, null));
        stubVisible();

        // Creator withdraws a pending version update → the skill stays APPROVED.
        workflowService.transition(100L, creator, Skill.STATUS_DRAFT, null);

        assertEquals(Skill.STATUS_APPROVED, skill.getApprovalStatus());
        verify(versionUpdateService).discardPendingVersion(skill);
        verify(versionUpdateService, never()).completeVersionUpdate(any(), any());
    }

    // ── Share pending (pending_reason='SHARE') ────────────────────────────────

    private com.furelise.skillmanagement.skill.model.SkillShare pendingShare() {
        return com.furelise.skillmanagement.skill.model.SkillShare.builder()
                .id(10L).skillId(100L).shareType("USER").userId(2L)
                .approvalStatus(com.furelise.skillmanagement.skill.model.SkillShare.STATUS_PENDING)
                .createdBy(1L).build();
    }

    @Test
    void sharePending_adminApprove_sharesApprovedAndReasonCleared() {
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        skill.setPendingReason(Skill.PENDING_REASON_SHARE);
        stubVisible();
        when(skillShareRepository.findBySkillIdAndApprovalStatus(100L, "PENDING"))
                .thenReturn(List.of(pendingShare()));

        workflowService.transition(100L, admin, Skill.STATUS_APPROVED, null);

        assertEquals(Skill.STATUS_APPROVED, skill.getApprovalStatus());
        assertNull(skill.getPendingReason());
        assertEquals("APPROVED", skillShareRepository.findBySkillIdAndApprovalStatus(100L, "PENDING")
                .get(0).getApprovalStatus());
        verify(skillShareRepository).save(any());
        // MCP re-validation must NOT run for a share pending approval.
        verify(mcpPublishValidationService, never()).validate(anyLong());
        // V91: the first admin approval marks the skill as reviewed.
        assertEquals("Y", skill.getAdminReviewedYn());
    }

    @Test
    void sharePending_adminReject_doesNotMarkReviewed() {
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        skill.setPendingReason(Skill.PENDING_REASON_SHARE);
        stubVisible();
        when(skillShareRepository.findBySkillIdAndApprovalStatus(100L, "PENDING"))
                .thenReturn(List.of(pendingShare()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.empty());

        workflowService.transition(100L, admin, Skill.STATUS_REJECTED, "out of scope");

        // Reject is not a review — the flag stays 'N'.
        assertEquals("N", skill.getAdminReviewedYn());
    }

    @Test
    void sharePending_creatorWithdraw_doesNotMarkReviewed() {
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        skill.setPendingReason(Skill.PENDING_REASON_SHARE);
        stubVisible();
        when(skillShareRepository.findBySkillIdAndApprovalStatus(100L, "PENDING"))
                .thenReturn(List.of(pendingShare()));

        workflowService.transition(100L, creator, Skill.STATUS_DRAFT, null);

        // A creator withdraw is not an admin review.
        assertEquals("N", skill.getAdminReviewedYn());
    }

    @Test
    void legacySubmit_adminApprove_marksReviewed() {
        // A plain new-skill submit (no pending metadata, no pending reason)
        // approved by the admin also counts as the one-time review.
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        stubVisible();
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.empty());

        workflowService.transition(100L, admin, Skill.STATUS_APPROVED, null);

        assertEquals(Skill.STATUS_APPROVED, skill.getApprovalStatus());
        assertEquals("Y", skill.getAdminReviewedYn());
    }

    @Test
    void sharePending_adminReject_skillBackToApprovedSharesRejected() {
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        skill.setPendingReason(Skill.PENDING_REASON_SHARE);
        stubVisible();
        when(skillShareRepository.findBySkillIdAndApprovalStatus(100L, "PENDING"))
                .thenReturn(List.of(pendingShare()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.empty());

        workflowService.transition(100L, admin, Skill.STATUS_REJECTED, "out of scope");

        // The skill goes back to APPROVED (not REJECTED); the reason is kept
        // for the creator; PENDING shares become REJECTED (kept for display).
        assertEquals(Skill.STATUS_APPROVED, skill.getApprovalStatus());
        assertEquals("out of scope", skill.getRejectionReason());
        assertNull(skill.getPendingReason());
        assertEquals("REJECTED", skillShareRepository.findBySkillIdAndApprovalStatus(100L, "PENDING")
                .get(0).getApprovalStatus());
        verify(eventNotificationService).notify(eq(admin), eq(creator), isNull(), any());
    }

    @Test
    void sharePending_creatorWithdraw_sharesDeletedAndBackToApproved() {
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        skill.setPendingReason(Skill.PENDING_REASON_SHARE);
        stubVisible();
        when(skillShareRepository.findBySkillIdAndApprovalStatus(100L, "PENDING"))
                .thenReturn(List.of(pendingShare()));

        workflowService.transition(100L, creator, Skill.STATUS_DRAFT, null);

        assertEquals(Skill.STATUS_APPROVED, skill.getApprovalStatus());
        assertNull(skill.getPendingReason());
        verify(skillShareRepository).deleteAll(any());
    }

    // ── Scope pending (pending_reason='SCOPE') ────────────────────────────────

    @Test
    void scopePending_adminApprove_newScopeApplied() {
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        skill.setPendingReason(Skill.PENDING_REASON_SCOPE);
        skill.setPendingScope(Skill.SCOPE_PUBLIC);
        stubVisible();
        when(mcpPublishValidationService.validate(100L)).thenReturn(List.of());

        workflowService.transition(100L, admin, Skill.STATUS_APPROVED, null);

        assertEquals(Skill.STATUS_APPROVED, skill.getApprovalStatus());
        assertEquals(Skill.SCOPE_PUBLIC, skill.getPublicScope());
        assertNull(skill.getPendingReason());
        assertNull(skill.getPendingScope());
    }

    @Test
    void scopePending_adminApprove_blockedByUnapprovedMcp() {
        // Widening from an auto-approved scope is the first real
        // publication — MCPs must be live before the admin can approve.
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        skill.setPendingReason(Skill.PENDING_REASON_SCOPE);
        skill.setPendingScope(Skill.SCOPE_PART);
        stubVisible();
        when(mcpPublishValidationService.validate(100L)).thenReturn(List.of(
                new com.furelise.skillmanagement.mcp.service.McpPublishValidationService.Blocker(
                        10L, "MCP-0010", 1, "NOT_APPROVED")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowService.transition(100L, admin, Skill.STATUS_APPROVED, null));

        assertEquals(BusinessErrorCode.MCP_DEPENDENCY_NOT_READY, ex.getCode());
        // The skill stays PENDING_APPROVAL with the old scope.
        assertEquals(Skill.STATUS_PENDING_APPROVAL, skill.getApprovalStatus());
        assertEquals(Skill.SCOPE_PRIVATE, skill.getPublicScope());
        assertEquals(Skill.PENDING_REASON_SCOPE, skill.getPendingReason());
    }

    @Test
    void scopePending_adminReject_oldScopeKept() {
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        skill.setPendingReason(Skill.PENDING_REASON_SCOPE);
        skill.setPendingScope(Skill.SCOPE_PUBLIC);
        stubVisible();
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.empty());

        workflowService.transition(100L, admin, Skill.STATUS_REJECTED, "too wide");

        assertEquals(Skill.STATUS_APPROVED, skill.getApprovalStatus());
        // The old scope still applies; the pending scope is dropped.
        assertEquals(Skill.SCOPE_PRIVATE, skill.getPublicScope());
        assertNull(skill.getPendingReason());
        assertNull(skill.getPendingScope());
        assertEquals("too wide", skill.getRejectionReason());
    }

    @Test
    void scopePending_creatorWithdraw_oldScopeKept() {
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        skill.setPendingReason(Skill.PENDING_REASON_SCOPE);
        skill.setPendingScope(Skill.SCOPE_PUBLIC);
        stubVisible();

        workflowService.transition(100L, creator, Skill.STATUS_DRAFT, null);

        assertEquals(Skill.STATUS_APPROVED, skill.getApprovalStatus());
        assertEquals(Skill.SCOPE_PRIVATE, skill.getPublicScope());
        assertNull(skill.getPendingReason());
        assertNull(skill.getPendingScope());
    }
}
