package com.skillmanagement.skill;

import com.skillmanagement.exception.BusinessErrorCode;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.model.Role;
import com.skillmanagement.skill.dto.SkillPendingMetadata;
import com.skillmanagement.skill.model.Skill;
import com.skillmanagement.user.model.User;
import com.skillmanagement.notification.repository.NotificationRepository;
import com.skillmanagement.skill.repository.SkillRepository;
import com.skillmanagement.skill.repository.SkillStatusHistoryRepository;
import com.skillmanagement.skill.service.SkillVersionUpdateService;
import com.skillmanagement.user.repository.UserRepository;
import com.skillmanagement.user.repository.UserSettingsRepository;
import com.skillmanagement.mail.EmailService;
import com.skillmanagement.skill.service.SkillAccessHelper;
import com.skillmanagement.skill.service.impl.SkillWorkflowServiceImpl;
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
    @Mock private UserRepository userRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private EmailService emailService;
    @Mock private SkillAccessHelper skillAccessHelper;
    @Mock private SkillVersionUpdateService versionUpdateService;

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
        verify(notificationRepository).save(any());
        verify(emailService).sendNotificationEmail(eq("creator@example.com"), anyString(), anyString());
    }

    @Test
    void pendingToDraft_withdrawByCreator_ok() {
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        stubVisible();
        workflowService.transition(100L, creator, Skill.STATUS_DRAFT, null);
        assertEquals(Skill.STATUS_DRAFT, skill.getApprovalStatus());
        // No notification when the actor is the creator.
        verify(notificationRepository, never()).save(any());
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
        skill.setPendingMetadata(new SkillPendingMetadata(
                "Test Skill", null, "PRIVATE", List.of(), List.of(1L), List.of(1L)));
        stubVisible();

        workflowService.transition(100L, admin, Skill.STATUS_APPROVED, null);

        assertEquals(Skill.STATUS_APPROVED, skill.getApprovalStatus());
        verify(versionUpdateService).completeVersionUpdate(skill);
        verify(versionUpdateService, never()).discardPendingVersion(any());
    }

    @Test
    void versionUpdateRejected_staysApprovedKeepsReason() {
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        skill.setPendingMetadata(new SkillPendingMetadata(
                "Test Skill", null, "PRIVATE", List.of(), List.of(1L), List.of(1L)));
        stubVisible();
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.empty());

        workflowService.transition(100L, admin, Skill.STATUS_REJECTED, "not ready");

        // The old version stays live — the skill does NOT go to REJECTED.
        assertEquals(Skill.STATUS_APPROVED, skill.getApprovalStatus());
        assertEquals("not ready", skill.getRejectionReason());
        verify(versionUpdateService).discardPendingVersion(skill);
        verify(versionUpdateService, never()).completeVersionUpdate(any());
    }

    @Test
    void versionUpdateCancelledByCreator_discardsAndStaysApproved() {
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        skill.setPendingMetadata(new SkillPendingMetadata(
                "Test Skill", null, "PRIVATE", List.of(), List.of(1L), List.of(1L)));
        stubVisible();

        // Creator withdraws a pending version update → the skill stays APPROVED.
        workflowService.transition(100L, creator, Skill.STATUS_DRAFT, null);

        assertEquals(Skill.STATUS_APPROVED, skill.getApprovalStatus());
        verify(versionUpdateService).discardPendingVersion(skill);
        verify(versionUpdateService, never()).completeVersionUpdate(any());
    }
}
