package com.furelise.skillmanagement.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.notification.model.Notification;
import com.furelise.skillmanagement.mcp.service.McpDependencyService;
import com.furelise.skillmanagement.org.model.OrganizationMaster;
import com.furelise.skillmanagement.model.Role;
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.skill.model.SkillFile;
import com.furelise.skillmanagement.skill.model.SkillSubscription;
import com.furelise.skillmanagement.skill.model.SkillViewLog;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.model.UserSettings;
import com.furelise.skillmanagement.notification.repository.NotificationRepository;
import com.furelise.skillmanagement.org.repository.OrganizationRepository;
import com.furelise.skillmanagement.skill.repository.SkillFileRepository;
import com.furelise.skillmanagement.skill.repository.SkillRatingRepository;
import com.furelise.skillmanagement.skill.repository.SkillRepository;
import com.furelise.skillmanagement.skill.repository.SkillStatusHistoryRepository;
import com.furelise.skillmanagement.skill.repository.SkillSubscriptionRepository;
import com.furelise.skillmanagement.skill.repository.SkillViewLogRepository;
import com.furelise.skillmanagement.user.repository.UserRepository;
import com.furelise.skillmanagement.user.repository.UserSettingsRepository;
import com.furelise.skillmanagement.common.RateLimiter;
import com.furelise.skillmanagement.mail.EmailService;
import com.furelise.skillmanagement.skill.service.SkillAccessHelper;
import com.furelise.skillmanagement.skill.service.SkillFileStorageService;
import com.furelise.skillmanagement.skill.service.impl.SkillServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillServiceTest {

    @Mock private SkillRepository skillRepository;
    @Mock private SkillRatingRepository skillRatingRepository;
    @Mock private SkillStatusHistoryRepository skillStatusHistoryRepository;
    @Mock private SkillSubscriptionRepository skillSubscriptionRepository;
    @Mock private SkillViewLogRepository skillViewLogRepository;
    @Mock private com.furelise.skillmanagement.skill.repository.SkillCategoryRepository skillCategoryRepository;
    @Mock private com.furelise.skillmanagement.skill.repository.ProcessStageRepository processStageRepository;
    @Mock private com.furelise.skillmanagement.skill.repository.SkillCategoryMappingRepository skillCategoryMappingRepository;
    @Mock private com.furelise.skillmanagement.skill.repository.SkillStageMappingRepository skillStageMappingRepository;
    @Mock private com.furelise.skillmanagement.skill.repository.SkillCoworkUserRepository skillCoworkUserRepository;
    @Mock private com.furelise.skillmanagement.skill.repository.SkillShareRepository skillShareRepository;
    @Mock private SkillAccessHelper skillAccessHelper;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private SkillFileStorageService skillFileStorageService;
    @Mock private SkillFileRepository skillFileRepository;
    @Mock private RateLimiter rateLimiter;
    @Mock private NotificationRepository notificationRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private EmailService emailService;
    @Mock private com.furelise.skillmanagement.notification.service.EventNotificationService eventNotificationService;
    @Mock private com.furelise.skillmanagement.audit.service.AuditService auditService;
    @Mock private com.furelise.skillmanagement.skill.service.SkillWorkflowService skillWorkflowService;
    @Mock private com.furelise.skillmanagement.skill.service.SkillVersionUpdateService skillVersionUpdateService;
    @Mock private com.furelise.skillmanagement.mcp.service.McpDependencyService mcpDependencyService;
    @Mock private com.furelise.skillmanagement.skill.repository.SkillVersionRepository skillVersionRepository;
    @Mock private com.furelise.skillmanagement.skill.repository.SkillVersionFileRepository skillVersionFileRepository;
    @org.mockito.Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private SkillServiceImpl skillService;

    private User creator;
    private User other;
    private User admin;
    private Skill skill;

    @BeforeEach
    void setUp() {
        creator = User.builder().id(1L).name("Creator").email("creator@example.com")
                .knoxId("creator").role(Role.builder().name("NORMAL_USER").build()).build();
        other = User.builder().id(2L).name("Other").email("other@example.com")
                .knoxId("other").role(Role.builder().name("NORMAL_USER").build()).build();
        admin = User.builder().id(3L).name("Admin").email("admin@example.com")
                .knoxId("admin").role(Role.builder().name("ADMIN_USER").build()).build();
        skill = Skill.builder()
                .id(100L).name("Test Skill").description("desc")
                .approvalStatus(Skill.STATUS_DRAFT).publicScope(Skill.SCOPE_PRIVATE)
                .viewCount(0).version(1).deletedYn("N")
                .createdBy(1L).createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        // @Value field is not injected by Mockito — set the 15-minute view window.
        org.springframework.test.util.ReflectionTestUtils
                .setField(skillService, "viewWindowMinutes", 15);
    }

    private void stubVisible() {
        when(skillAccessHelper.getVisible(eq(100L), any(User.class))).thenReturn(skill);
    }

    /** Stubs the category/stage master lookups used by create/update. */
    private void stubTaxonomy() {
        stubCategory();
        stubStage();
    }

    /** A minimal non-empty file for create tests (file is now required). */
    private MultipartFile testFile() {
        return new MockMultipartFile("file", "skill.txt", "text/plain", "hello".getBytes());
    }

    private void stubCategory() {
        when(skillCategoryRepository.findAllById(List.of(1L))).thenReturn(List.of(
                com.furelise.skillmanagement.skill.model.SkillCategory.builder()
                        .id(1L).name("Quality").build()));
    }

    private void stubStage() {
        when(processStageRepository.findAllById(List.of(1L))).thenReturn(List.of(
                com.furelise.skillmanagement.skill.model.ProcessStage.builder()
                        .id(1L).name("Development").sortOrder(3).build()));
    }

    // ── Create ──────────────────────────────────────────────────────────────

    @Test
    void create_blankName_rejected() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.create("  ", null, "PRIVATE", null, null, null, null, null, null, null, creator));
        assertEquals(BusinessErrorCode.SKILL_NAME_REQUIRED, ex.getCode());
    }

    @Test
    void create_noCategory_rejected() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(skillRepository.existsByNameIgnoreCaseAndDeletedYnAndCreatedBy("New", "N", 1L)).thenReturn(false);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.create("New", null, "PRIVATE", null, null, null, null, null, null, null, creator));
        assertEquals(BusinessErrorCode.SKILL_CATEGORY_REQUIRED, ex.getCode());
    }

    @Test
    void create_noStage_rejected() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(skillRepository.existsByNameIgnoreCaseAndDeletedYnAndCreatedBy("New", "N", 1L)).thenReturn(false);
        stubCategory();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.create("New", null, "PRIVATE", null, null, null, List.of(1L), null, null, null, creator));
        assertEquals(BusinessErrorCode.SKILL_STAGE_REQUIRED, ex.getCode());
    }

    @Test
    void create_noFile_rejected() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(skillRepository.existsByNameIgnoreCaseAndDeletedYnAndCreatedBy("New", "N", 1L)).thenReturn(false);
        stubTaxonomy();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.create("New", null, "PRIVATE", null, null, null, List.of(1L), List.of(1L), null, null, creator));
        assertEquals(BusinessErrorCode.SKILL_FILE_REQUIRED, ex.getCode());
    }

    @Test
    void create_duplicateName_rejected() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(skillRepository.existsByNameIgnoreCaseAndDeletedYnAndCreatedBy("Dup", "N", 1L)).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.create("Dup", null, "PRIVATE", null, null, null, null, null, null, null, creator));
        assertEquals(BusinessErrorCode.SKILL_NAME_DUPLICATE, ex.getCode());
    }

    @Test
    void create_sameNameAsOtherUsersSkill_allowed() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(skillRepository.existsByNameIgnoreCaseAndDeletedYnAndCreatedBy("Dup", "N", 1L)).thenReturn(false);
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> {
            Skill s = inv.getArgument(0);
            s.setId(56L);
            return s;
        });
        when(skillFileStorageService.store(eq(56L), any())).thenReturn("skills/56/skill.txt");
        stubTaxonomy();

        Long id = skillService.create("Dup", null, "PRIVATE", null, null, null, List.of(1L), List.of(1L), null,
                new MultipartFile[]{testFile()}, creator);

        assertEquals(56L, id);
        verify(skillCategoryMappingRepository).save(any());
        verify(skillStageMappingRepository).save(any());
    }

    @Test
    void create_invalidScope_rejected() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.create("New", null, "EVERYONE", null, null, null, null, null, null, null, creator));
        assertEquals(BusinessErrorCode.SKILL_INVALID_SCOPE, ex.getCode());
    }

    @Test
    void create_tooManyHashtags_rejected() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        String json = "[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\",\"h\",\"i\",\"j\",\"k\"]";
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.create("New", null, "PRIVATE", null, null, json, null, null, null, null, creator));
        assertEquals(BusinessErrorCode.SKILL_HASHTAGS_TOO_MANY, ex.getCode());
    }

    @Test
    void create_invalidHashtag_rejected() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.create("New", null, "PRIVATE", null, null, "[\"bad tag!\"]", null, null, null, null, creator));
        assertEquals(BusinessErrorCode.SKILL_INVALID_HASHTAG, ex.getCode());
    }

    @Test
    void create_success_savesDraftWithHistory() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(skillRepository.existsByNameIgnoreCaseAndDeletedYnAndCreatedBy("New Skill", "N", 1L)).thenReturn(false);
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> {
            Skill s = inv.getArgument(0);
            s.setId(55L);
            return s;
        });
        when(skillFileStorageService.store(eq(55L), any())).thenReturn("skills/55/skill.txt");
        stubTaxonomy();

        Long id = skillService.create("New Skill", "d", "PUBLIC", null, null, "[\"tag1\"]", List.of(1L), List.of(1L), null,
                new MultipartFile[]{testFile()}, creator);

        assertEquals(55L, id);
        ArgumentCaptor<Skill> captor = ArgumentCaptor.forClass(Skill.class);
        verify(skillRepository).save(captor.capture());
        assertEquals(Skill.STATUS_DRAFT, captor.getValue().getApprovalStatus());
        verify(skillStatusHistoryRepository).save(any());
    }

    // ── Auto-approve on creation (PRIVATE/PROJECT) ────────────────────────────

    @Test
    void create_privateScope_autoApproved() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(skillRepository.existsByNameIgnoreCaseAndDeletedYnAndCreatedBy("New Skill", "N", 1L)).thenReturn(false);
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> {
            Skill s = inv.getArgument(0);
            s.setId(55L);
            return s;
        });
        when(skillFileStorageService.store(eq(55L), any())).thenReturn("skills/55/skill.txt");
        stubTaxonomy();

        skillService.create("New Skill", "d", "PRIVATE", null, null, null, List.of(1L), List.of(1L), null,
                new MultipartFile[]{testFile()}, creator);

        ArgumentCaptor<Skill> captor = ArgumentCaptor.forClass(Skill.class);
        verify(skillRepository).save(captor.capture());
        assertEquals(Skill.STATUS_APPROVED, captor.getValue().getApprovalStatus());
    }

    @Test
    void create_projectScope_autoApproved() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(skillRepository.existsByNameIgnoreCaseAndDeletedYnAndCreatedBy("New Skill", "N", 1L)).thenReturn(false);
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> {
            Skill s = inv.getArgument(0);
            s.setId(55L);
            return s;
        });
        when(skillFileStorageService.store(eq(55L), any())).thenReturn("skills/55/skill.txt");
        stubTaxonomy();

        skillService.create("New Skill", "d", "PROJECT", "SYS-A", null, null, List.of(1L), List.of(1L), null,
                new MultipartFile[]{testFile()}, creator);

        ArgumentCaptor<Skill> captor = ArgumentCaptor.forClass(Skill.class);
        verify(skillRepository).save(captor.capture());
        assertEquals(Skill.STATUS_APPROVED, captor.getValue().getApprovalStatus());
    }

    @Test
    void create_partScope_staysDraft() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(skillRepository.existsByNameIgnoreCaseAndDeletedYnAndCreatedBy("New Skill", "N", 1L)).thenReturn(false);
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> {
            Skill s = inv.getArgument(0);
            s.setId(55L);
            return s;
        });
        when(skillFileStorageService.store(eq(55L), any())).thenReturn("skills/55/skill.txt");
        stubTaxonomy();

        skillService.create("New Skill", "d", "PART", null, null, null, List.of(1L), List.of(1L), null,
                new MultipartFile[]{testFile()}, creator);

        ArgumentCaptor<Skill> captor = ArgumentCaptor.forClass(Skill.class);
        verify(skillRepository).save(captor.capture());
        assertEquals(Skill.STATUS_DRAFT, captor.getValue().getApprovalStatus());
    }

    @Test
    void create_rateLimited_rejected() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(false);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.create("New", null, "PRIVATE", null, null, null, null, null, null, null, creator));
        assertEquals(BusinessErrorCode.SKILL_RATE_LIMITED, ex.getCode());
    }

    // ── Update ──────────────────────────────────────────────────────────────

    @Test
    void update_notCreatorOrAdmin_rejected() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        stubVisible();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.update(100L, "Test Skill", null, "PRIVATE", null, null, null,
                        null, null, null, null, null, null, skill.getUpdatedAt(), other));
        assertEquals(BusinessErrorCode.SKILL_NOT_EDITABLE, ex.getCode());
    }

    @Test
    void update_pendingApproval_rejected() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        stubVisible();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.update(100L, "Test Skill", null, "PRIVATE", null, null, null,
                        null, null, null, null, null, null, skill.getUpdatedAt(), creator));
        assertEquals(BusinessErrorCode.SKILL_NOT_EDITABLE, ex.getCode());
    }

    @Test
    void update_staleUpdatedAt_optimisticLock() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        stubVisible();
        Instant stale = skill.getUpdatedAt().minusSeconds(60);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.update(100L, "Test Skill", null, "PRIVATE", null, null, null,
                        null, null, null, null, null, null, stale, creator));
        assertEquals(BusinessErrorCode.SKILL_OPTIMISTIC_LOCK, ex.getCode());
    }

    @Test
    void update_publishedNonAdminNameChange_fieldReadOnly() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        stubVisible();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.update(100L, "Renamed", null, "PRIVATE", null, null, null,
                        null, null, null, null, null, null, skill.getUpdatedAt(), creator));
        assertEquals(BusinessErrorCode.SKILL_FIELD_READ_ONLY, ex.getCode());
    }

    @Test
    void update_publishedAdminNameChange_allowed() {
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        stubVisible();
        when(skillRepository.existsByNameIgnoreCaseAndIdNotAndDeletedYnAndCreatedBy("Renamed", 100L, "N", 1L)).thenReturn(false);
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
        stubTaxonomy();

        int version = skillService.update(100L, "Renamed", null, "PRIVATE", null, null, null,
                List.of(1L), List.of(1L), null, null, null, null, skill.getUpdatedAt(), admin);

        // Regular edit does not bump the version (only version updates do).
        assertEquals(1, version);
        assertEquals("Renamed", skill.getName());
    }

    // ── Scope change (widening requires approval) ─────────────────────────────

    @Test
    void update_approvedScopeWidening_stagesScopeAndTransitions() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        stubVisible();
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
        stubTaxonomy();

        skillService.update(100L, "Test Skill", null, "PUBLIC", null, null, null,
                List.of(1L), List.of(1L), null, null, null, null, skill.getUpdatedAt(), creator);

        // The new scope is NOT applied — it is staged for admin review.
        assertEquals(Skill.SCOPE_PRIVATE, skill.getPublicScope());
        assertEquals(Skill.PENDING_REASON_SCOPE, skill.getPendingReason());
        assertEquals(Skill.SCOPE_PUBLIC, skill.getPendingScope());
        verify(skillWorkflowService).transition(100L, creator, Skill.STATUS_PENDING_APPROVAL, null);
    }

    @Test
    void update_approvedScopeNarrowing_appliesImmediately() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        skill.setPublicScope(Skill.SCOPE_PUBLIC);
        stubVisible();
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
        stubTaxonomy();

        skillService.update(100L, "Test Skill", null, "PRIVATE", null, null, null,
                List.of(1L), List.of(1L), null, null, null, null, skill.getUpdatedAt(), creator);

        // Narrowing applies directly — no pending state.
        assertEquals(Skill.SCOPE_PRIVATE, skill.getPublicScope());
        assertNull(skill.getPendingReason());
        assertNull(skill.getPendingScope());
        verify(skillWorkflowService, never()).transition(anyLong(), any(), anyString(), any());
    }

    @Test
    void update_approvedScopeSameTier_appliesImmediately() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        stubVisible();
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
        stubTaxonomy();

        // PRIVATE → PROJECT stays in the auto-approve tier → applies directly.
        skillService.update(100L, "Test Skill", null, "PROJECT", null, null, null,
                List.of(1L), List.of(1L), null, null, null, null, skill.getUpdatedAt(), creator);

        assertEquals(Skill.SCOPE_PROJECT, skill.getPublicScope());
        assertNull(skill.getPendingReason());
        verify(skillWorkflowService, never()).transition(anyLong(), any(), anyString(), any());
    }

    @Test
    void update_reviewedScopeWidening_appliesImmediately() {
        // V91: a reviewed skill (admin approved it once) applies a scope
        // widening directly — no pending state, no transition.
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        skill.setAdminReviewedYn("Y");
        stubVisible();
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
        stubTaxonomy();

        skillService.update(100L, "Test Skill", null, "PUBLIC", null, null, null,
                List.of(1L), List.of(1L), null, null, null, null, skill.getUpdatedAt(), creator);

        assertEquals(Skill.SCOPE_PUBLIC, skill.getPublicScope());
        assertNull(skill.getPendingReason());
        assertNull(skill.getPendingScope());
        verify(skillWorkflowService, never()).transition(anyLong(), any(), anyString(), any());
    }

    @Test
    void update_duplicateName_rejected() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        stubVisible();
        when(skillRepository.existsByNameIgnoreCaseAndIdNotAndDeletedYnAndCreatedBy("Other Name", 100L, "N", 1L)).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.update(100L, "Other Name", null, "PRIVATE", null, null, null,
                        null, null, null, null, null, null, skill.getUpdatedAt(), creator));
        assertEquals(BusinessErrorCode.SKILL_NAME_DUPLICATE, ex.getCode());
    }

    @Test
    void update_sameNameAsOtherUsersSkill_allowed() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        stubVisible();
        when(skillRepository.existsByNameIgnoreCaseAndIdNotAndDeletedYnAndCreatedBy("Their Skill", 100L, "N", 1L)).thenReturn(false);
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
        stubTaxonomy();

        skillService.update(100L, "Their Skill", null, "PRIVATE", null, null, null,
                List.of(1L), List.of(1L), null, null, null, null, skill.getUpdatedAt(), creator);

        assertEquals("Their Skill", skill.getName());
    }

    @Test
    void update_published_notifiesSubscribers() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        stubVisible();
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
        SkillSubscription sub = SkillSubscription.builder().skillId(100L).userId(2L).build();
        when(skillSubscriptionRepository.findBySkillId(100L)).thenReturn(List.of(sub));
        when(userRepository.findByIdIn(anyCollection())).thenReturn(List.of(other));
        when(userSettingsRepository.findAllById(anyCollection())).thenReturn(List.of());
        stubTaxonomy();

        skillService.update(100L, "Test Skill", "new desc", "PRIVATE", null, null, null,
                List.of(1L), List.of(1L), null, null, null, null, skill.getUpdatedAt(), creator);

        // The fan-out goes through EventNotificationService (batch, settings
        // map empty = both channels on by default).
        verify(eventNotificationService).notifyBatch(eq(creator), anyMap(), anyMap(), any());
    }

    @Test
    void update_newFiles_appendedToExisting() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        stubVisible();
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
        when(skillFileStorageService.store(eq(100L), any())).thenReturn("skills/100/new.txt");
        MockMultipartFile file = new MockMultipartFile("file", "new.txt",
                "text/plain", "hello".getBytes());
        stubTaxonomy();

        int version = skillService.update(100L, "Test Skill", null, "PRIVATE", null, null, null,
                List.of(1L), List.of(1L), null, new MultipartFile[]{file}, null, null, skill.getUpdatedAt(), creator);

        // Regular edit does not bump the version (only version updates do).
        assertEquals(1, version);
        // Old files are kept (append semantics); the new file is stored + metadata saved.
        verify(skillFileStorageService).store(eq(100L), any());
        verify(skillFileRepository).save(any(SkillFile.class));
        verify(skillFileStorageService, never()).delete(anyString());
    }

    @Test
    void update_withMcpDependencies_savesDependencies() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        stubVisible();
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
        stubTaxonomy();
        List<McpDependencyService.McpDependencyInput> deps =
                List.of(new McpDependencyService.McpDependencyInput(10L, 2));

        skillService.update(100L, "Test Skill", null, "PRIVATE", null, null, null,
                List.of(1L), List.of(1L), null, null, null, deps, skill.getUpdatedAt(), creator);

        // Q-18 exception uses the skill's CREATOR (1L), not the editor.
        verify(mcpDependencyService).saveDependencies(100L, 1L, "PRIVATE", deps, "MANUAL");
    }

    @Test
    void update_approvedSkill_mcpDependenciesIgnored() {
        // 2026-11-06: like files, the dependency set of an APPROVED skill is
        // managed through the version-update flow — a regular edit keeps the
        // existing dependencies (param ignored).
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        stubVisible();
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
        stubTaxonomy();
        List<McpDependencyService.McpDependencyInput> deps =
                List.of(new McpDependencyService.McpDependencyInput(10L, 2));

        skillService.update(100L, "Test Skill", null, "PRIVATE", null, null, null,
                List.of(1L), List.of(1L), null, null, null, deps, skill.getUpdatedAt(), creator);

        verify(mcpDependencyService, never()).saveDependencies(anyLong(), anyLong(), anyString(), any(), anyString());
    }

    // ── Delete (all-or-nothing) ─────────────────────────────────────────────

    @Test
    void deleteBatch_partialPermission_deletesNothing() {
        Skill mine = Skill.builder().id(1L).name("Mine").createdBy(1L).deletedYn("N").build();
        Skill theirs = Skill.builder().id(2L).name("Theirs").createdBy(9L).deletedYn("N").build();
        when(skillRepository.findByIdIn(List.of(1L, 2L))).thenReturn(List.of(mine, theirs));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.deleteBatch(List.of(1L, 2L), creator));
        assertEquals(BusinessErrorCode.SKILL_DELETE_FORBIDDEN, ex.getCode());
        verify(skillRepository, never()).save(any(Skill.class));
    }

    @Test
    void deleteBatch_allAllowed_softDeletes() {
        Skill mine = Skill.builder().id(1L).name("Mine").createdBy(1L).deletedYn("N").build();
        when(skillRepository.findByIdIn(List.of(1L))).thenReturn(List.of(mine));
        when(userRepository.findAllById(any())).thenReturn(List.of());
        SkillFile sf = SkillFile.builder().id(10L).skillId(1L)
                .fileName("f.txt").filePath("skills/1/f.txt").fileSize(5L).build();
        when(skillFileRepository.findBySkillId(1L)).thenReturn(List.of(sf));

        skillService.deleteBatch(List.of(1L), creator);

        assertEquals("Y", mine.getDeletedYn());
        verify(skillRepository).save(mine);
        verify(skillFileStorageService).delete("skills/1/f.txt");
        verify(skillFileRepository).deleteBySkillId(1L);
        verify(skillSubscriptionRepository).deleteBySkillId(1L);
    }

    // ── View count (idempotent within the 15-minute view window) ─────────────

    @Test
    void incrementView_recentView_noIncrease() {
        stubVisible();
        SkillViewLog recent = SkillViewLog.builder().skillId(100L).userId(1L)
                .viewedAt(Instant.now().minus(10, java.time.temporal.ChronoUnit.MINUTES)).build();
        when(skillViewLogRepository.findTopBySkillIdAndUserIdOrderByViewedAtDesc(100L, 1L))
                .thenReturn(Optional.of(recent));

        int count = skillService.incrementView(100L, creator);

        assertEquals(0, count);
        verify(skillViewLogRepository, never()).save(any());
    }

    @Test
    void incrementView_oldView_increasesAndLogs() {
        stubVisible();
        SkillViewLog old = SkillViewLog.builder().skillId(100L).userId(1L)
                .viewedAt(Instant.now().minus(16, java.time.temporal.ChronoUnit.MINUTES)).build();
        when(skillViewLogRepository.findTopBySkillIdAndUserIdOrderByViewedAtDesc(100L, 1L))
                .thenReturn(Optional.of(old));
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));

        int count = skillService.incrementView(100L, creator);

        assertEquals(1, count);
        verify(skillViewLogRepository).save(any(SkillViewLog.class));
    }

    // ── Subscription ────────────────────────────────────────────────────────

    @Test
    void subscribe_notPublished_rejected() {
        stubVisible();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.subscribe(100L, other));
        assertEquals(BusinessErrorCode.SKILL_NOT_APPROVED, ex.getCode());
    }

    @Test
    void subscribe_ownSkill_rejected() {
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        stubVisible();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.subscribe(100L, creator));
        assertEquals(BusinessErrorCode.SKILL_SUBSCRIBE_FORBIDDEN, ex.getCode());
    }

    @Test
    void subscribe_published_success() {
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        stubVisible();
        when(skillSubscriptionRepository.existsBySkillIdAndUserId(100L, 2L)).thenReturn(false);

        skillService.subscribe(100L, other);

        verify(skillSubscriptionRepository).save(any(SkillSubscription.class));
    }

    @Test
    void unsubscribe_deletesRow() {
        stubVisible();
        skillService.unsubscribe(100L, other);
        verify(skillSubscriptionRepository).deleteBySkillIdAndUserId(100L, 2L);
    }

    // ── Delete file ─────────────────────────────────────────────────────────

    @Test
    void deleteFile_notFound_404() {
        stubVisible();
        when(skillFileRepository.findById(7L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.deleteFile(100L, 7L, creator));
        assertEquals(BusinessErrorCode.SKILL_FILE_NOT_FOUND, ex.getCode());
    }

    @Test
    void deleteFile_success_removesFileAndRow() {
        stubVisible();
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
        SkillFile file = SkillFile.builder().id(7L).skillId(100L)
                .fileName("f.txt").filePath("skills/100/f.txt").fileSize(5L).build();
        when(skillFileRepository.findById(7L)).thenReturn(Optional.of(file));

        skillService.deleteFile(100L, 7L, creator);

        verify(skillFileStorageService).delete("skills/100/f.txt");
        verify(skillFileRepository).delete(file);
    }

    @Test
    void deleteFile_notCreatorOrAdmin_rejected() {
        stubVisible();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.deleteFile(100L, 7L, other));
        assertEquals(BusinessErrorCode.SKILL_NOT_EDITABLE, ex.getCode());
    }

    // ── Update (APPROVED skill: files ignored) ───────────────────────────────

    @Test
    void update_approvedSkill_filesIgnored() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        stubVisible();
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile file = new MockMultipartFile("file", "new.txt",
                "text/plain", "hello".getBytes());
        stubTaxonomy();

        skillService.update(100L, "Test Skill", null, "PRIVATE", null, null, null,
                List.of(1L), List.of(1L), null, new MultipartFile[]{file}, null, null, skill.getUpdatedAt(), creator);

        // Files of an approved skill are only managed via Update Version.
        verify(skillFileStorageService, never()).store(anyLong(), any());
        verify(skillFileRepository, never()).save(any(SkillFile.class));
    }

    // ── Delete file (APPROVED skill: locked) ─────────────────────────────────

    @Test
    void deleteFile_approvedSkill_fileLocked() {
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        stubVisible();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.deleteFile(100L, 7L, creator));
        assertEquals(BusinessErrorCode.SKILL_FILE_LOCKED, ex.getCode());
    }

    // ── Version update ───────────────────────────────────────────────────────

    @Test
    void updateVersion_draftSkill_rejected() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        stubVisible();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.updateVersion(100L, "Test Skill", null, "PRIVATE", null, null, null,
                        List.of(1L), List.of(1L), null, null, null, creator));
        assertEquals(BusinessErrorCode.SKILL_VERSION_UPDATE_NOT_ALLOWED, ex.getCode());
    }

    @Test
    void updateVersion_notCreatorOrAdmin_rejected() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        stubVisible();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.updateVersion(100L, "Test Skill", null, "PRIVATE", null, null, null,
                        List.of(1L), List.of(1L), null, null, null, other));
        assertEquals(BusinessErrorCode.SKILL_NOT_EDITABLE, ex.getCode());
    }

    @Test
    void updateVersion_pendingAlreadyExists_rejected() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        skill.setPendingMetadata(new com.furelise.skillmanagement.skill.dto.SkillPendingMetadata(
                "Test Skill", null, "PRIVATE", List.of(), List.of(1L), List.of(1L), null, null));
        stubVisible();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.updateVersion(100L, "Test Skill", null, "PRIVATE", null, null, null,
                        List.of(1L), List.of(1L), null, null, null, creator));
        assertEquals(BusinessErrorCode.SKILL_VERSION_UPDATE_NOT_ALLOWED, ex.getCode());
    }

    @Test
    void updateVersion_noNewFile_rejected() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        stubVisible();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.updateVersion(100L, "Test Skill", null, "PRIVATE", null, null, null,
                        List.of(1L), List.of(1L), null, null, null, creator));
        assertEquals(BusinessErrorCode.SKILL_VERSION_FILE_REQUIRED, ex.getCode());
    }

    @Test
    void updateVersion_approved_stagesAndTransitions() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        stubVisible();
        when(skillRepository.existsByNameIgnoreCaseAndIdNotAndDeletedYnAndCreatedBy("New Name", 100L, "N", 1L)).thenReturn(false);
        when(skillFileStorageService.store(eq(100L), any())).thenReturn("skills/100/new.txt");
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile file = new MockMultipartFile("file", "new.txt",
                "text/plain", "hello".getBytes());
        stubTaxonomy();

        skillService.updateVersion(100L, "New Name", "new desc", "PUBLIC", null, null, "[\"tag1\"]",
                List.of(1L), List.of(1L), new MultipartFile[]{file}, null, null, creator);

        // Staged metadata + pending file + transition to PENDING_APPROVAL.
        assertNotNull(skill.getPendingMetadata());
        assertEquals("New Name", skill.getPendingMetadata().name());
        assertEquals("PUBLIC", skill.getPendingMetadata().publicScope());
        ArgumentCaptor<SkillFile> fileCaptor = ArgumentCaptor.forClass(SkillFile.class);
        verify(skillFileRepository).save(fileCaptor.capture());
        assertEquals("Y", fileCaptor.getValue().getPendingYn());
        verify(skillWorkflowService).transition(100L, creator, Skill.STATUS_PENDING_APPROVAL, null);
    }

    @Test
    void updateVersion_privateScope_autoCompletesWithoutPending() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        stubVisible();
        when(skillRepository.existsByNameIgnoreCaseAndIdNotAndDeletedYnAndCreatedBy("New Name", 100L, "N", 1L)).thenReturn(false);
        when(skillFileStorageService.store(eq(100L), any())).thenReturn("skills/100/new.txt");
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile file = new MockMultipartFile("file", "new.txt",
                "text/plain", "hello".getBytes());
        stubTaxonomy();

        skillService.updateVersion(100L, "New Name", "new desc", "PRIVATE", null, null, null,
                List.of(1L), List.of(1L), new MultipartFile[]{file}, null, null, creator);

        // PRIVATE scope: the version update completes immediately — no
        // workflow transition. (completeVersionUpdate is mocked here, so the
        // staged fields are cleared by the real service, not the mock.)
        verify(skillVersionUpdateService).completeVersionUpdate(skill, creator);
        verify(skillWorkflowService, never()).transition(anyLong(), any(), anyString(), any());
    }

    @Test
    void updateVersion_projectScope_autoCompletesWithoutPending() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        skill.setPublicScope(Skill.SCOPE_PROJECT);
        stubVisible();
        when(skillRepository.existsByNameIgnoreCaseAndIdNotAndDeletedYnAndCreatedBy("New Name", 100L, "N", 1L)).thenReturn(false);
        when(skillFileStorageService.store(eq(100L), any())).thenReturn("skills/100/new.txt");
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile file = new MockMultipartFile("file", "new.txt",
                "text/plain", "hello".getBytes());
        stubTaxonomy();

        skillService.updateVersion(100L, "New Name", "new desc", "PROJECT", null, null, null,
                List.of(1L), List.of(1L), new MultipartFile[]{file}, null, null, creator);

        verify(skillVersionUpdateService).completeVersionUpdate(skill, creator);
        verify(skillWorkflowService, never()).transition(anyLong(), any(), anyString(), any());
    }

    @Test
    void updateVersion_projectScope_withMcpDependencies_savesBeforeAutoComplete() {
        // Regression: the auto-complete path used to return before
        // saveDependencies, silently dropping the new MCP links.
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        skill.setPublicScope(Skill.SCOPE_PROJECT);
        stubVisible();
        when(skillRepository.existsByNameIgnoreCaseAndIdNotAndDeletedYnAndCreatedBy("New Name", 100L, "N", 1L)).thenReturn(false);
        when(skillFileStorageService.store(eq(100L), any())).thenReturn("skills/100/new.txt");
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile file = new MockMultipartFile("file", "new.txt",
                "text/plain", "hello".getBytes());
        stubTaxonomy();
        List<McpDependencyService.McpDependencyInput> deps =
                List.of(new McpDependencyService.McpDependencyInput(10L, 2));

        skillService.updateVersion(100L, "New Name", "new desc", "PROJECT", null, null, null,
                List.of(1L), List.of(1L), new MultipartFile[]{file}, null, deps, creator);

        // Dependencies must be saved even when the update auto-completes.
        InOrder inOrder = inOrder(mcpDependencyService, skillVersionUpdateService);
        inOrder.verify(mcpDependencyService).saveDependencies(100L, 1L, "PROJECT", deps, "MANUAL");
        inOrder.verify(skillVersionUpdateService).completeVersionUpdate(skill, creator);
        verify(skillWorkflowService, never()).transition(anyLong(), any(), anyString(), any());
    }

    @Test
    void updateVersion_reviewedSkill_privateScope_stillRequiresApproval() {
        // V91 anti-cheat: a skill that was already reviewed by an admin may
        // have been narrowed from a wide scope back to PRIVATE — its version
        // updates must still go through admin review (no auto-complete).
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        skill.setPublicScope(Skill.SCOPE_PRIVATE);
        skill.setAdminReviewedYn("Y");
        stubVisible();
        when(skillRepository.existsByNameIgnoreCaseAndIdNotAndDeletedYnAndCreatedBy("New Name", 100L, "N", 1L)).thenReturn(false);
        when(skillFileStorageService.store(eq(100L), any())).thenReturn("skills/100/new.txt");
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile file = new MockMultipartFile("file", "new.txt",
                "text/plain", "hello".getBytes());
        stubTaxonomy();

        skillService.updateVersion(100L, "New Name", "new desc", "PRIVATE", null, null, null,
                List.of(1L), List.of(1L), new MultipartFile[]{file}, null, null, creator);

        // Reviewed skill: no auto-complete — the update goes to PENDING_APPROVAL.
        verify(skillVersionUpdateService, never()).completeVersionUpdate(any(), any());
        verify(skillWorkflowService).transition(100L, creator, Skill.STATUS_PENDING_APPROVAL, null);
    }

    @Test
void updateVersion_withMcpDependencies_savesBeforeTransition() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        stubVisible();
        when(skillRepository.existsByNameIgnoreCaseAndIdNotAndDeletedYnAndCreatedBy("New Name", 100L, "N", 1L)).thenReturn(false);
        when(skillFileStorageService.store(eq(100L), any())).thenReturn("skills/100/new.txt");
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile file = new MockMultipartFile("file", "new.txt",
                "text/plain", "hello".getBytes());
        stubTaxonomy();
        List<McpDependencyService.McpDependencyInput> deps =
                List.of(new McpDependencyService.McpDependencyInput(10L, 2));

        skillService.updateVersion(100L, "New Name", "new desc", "PUBLIC", null, null, "[\"tag1\"]",
                List.of(1L), List.of(1L), new MultipartFile[]{file}, null, deps, creator);

        // Dependencies must be saved BEFORE the workflow transition so the
        // publish validation inside transition validates the NEW set.
        InOrder inOrder = inOrder(mcpDependencyService, skillWorkflowService);
        inOrder.verify(mcpDependencyService).saveDependencies(100L, 1L, "PUBLIC", deps, "MANUAL");
        inOrder.verify(skillWorkflowService).transition(100L, creator, Skill.STATUS_PENDING_APPROVAL, null);
    }

    @Test
    void cancelVersionUpdate_pending_discardsAndApproves() {
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        skill.setPendingMetadata(new com.furelise.skillmanagement.skill.dto.SkillPendingMetadata(
                "Test Skill", null, "PRIVATE", List.of(), List.of(1L), List.of(1L), null, null));
        stubVisible();
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
        // The real service clears the staged fields; simulate that on the mock.
        doAnswer(inv -> {
            skill.setPendingMetadata(null);
            skill.setPendingRemoveFileIds(null);
            return null;
        }).when(skillVersionUpdateService).discardPendingVersion(skill);

        skillService.cancelVersionUpdate(100L, creator);

        verify(skillVersionUpdateService).discardPendingVersion(skill);
        assertEquals(Skill.STATUS_APPROVED, skill.getApprovalStatus());
        assertNull(skill.getPendingMetadata());
        verify(skillStatusHistoryRepository).save(any());
    }

    @Test
    void cancelVersionUpdate_noPending_rejected() {
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        stubVisible();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.cancelVersionUpdate(100L, creator));
        assertEquals(BusinessErrorCode.SKILL_VERSION_UPDATE_NOT_ALLOWED, ex.getCode());
    }

    // ── Version history ──────────────────────────────────────────────────────

    @Test
    void listVersions_returnsSnapshotsWithFiles() {
        stubVisible();
        com.furelise.skillmanagement.skill.model.SkillVersion sv =
                com.furelise.skillmanagement.skill.model.SkillVersion.builder()
                        .id(10L).skillId(100L).version(1).name("Old Name")
                        .description("old desc").publicScope("PRIVATE")
                        .hashtags(List.of("old")).createdBy(1L)
                        .createdAt(Instant.now()).build();
        com.furelise.skillmanagement.skill.model.SkillVersionFile svf =
                com.furelise.skillmanagement.skill.model.SkillVersionFile.builder()
                        .id(20L).skillVersionId(10L).fileName("old.txt")
                        .filePath("skills/100/old.txt").fileSize(5L).build();
        when(skillVersionRepository.findBySkillIdOrderByVersionDesc(100L)).thenReturn(List.of(sv));
        when(skillVersionFileRepository.findBySkillVersionId(10L)).thenReturn(List.of(svf));
        when(userRepository.findAllById(Set.of(1L))).thenReturn(List.of(creator));

        List<com.furelise.skillmanagement.skill.dto.SkillVersionItem> items =
                skillService.listVersions(100L, creator);

        assertEquals(1, items.size());
        assertEquals(1, items.get(0).version());
        assertEquals("Old Name", items.get(0).name());
        assertEquals("Creator", items.get(0).creatorName());
        assertEquals(1, items.get(0).files().size());
        assertEquals("old.txt", items.get(0).files().get(0).fileName());
    }

    @Test
    void downloadVersionFile_returnsFile() {
        stubVisible();
        com.furelise.skillmanagement.skill.model.SkillVersion sv =
                com.furelise.skillmanagement.skill.model.SkillVersion.builder()
                        .id(10L).skillId(100L).version(1).name("Old Name")
                        .publicScope("PRIVATE").createdBy(1L).createdAt(Instant.now()).build();
        com.furelise.skillmanagement.skill.model.SkillVersionFile svf =
                com.furelise.skillmanagement.skill.model.SkillVersionFile.builder()
                        .id(20L).skillVersionId(10L).fileName("old.txt")
                        .filePath("skills/100/old.txt").fileSize(5L).build();
        when(skillVersionRepository.findBySkillIdOrderByVersionDesc(100L)).thenReturn(List.of(sv));
        when(skillVersionFileRepository.findById(20L)).thenReturn(Optional.of(svf));
        when(skillFileStorageService.load("skills/100/old.txt")).thenReturn(java.io.InputStream.nullInputStream());

        com.furelise.skillmanagement.skill.dto.SkillVersionFileDownload dl =
                skillService.downloadVersionFile(100L, 1, 20L, creator);

        assertEquals("old.txt", dl.fileName());
    }

    @Test
    void downloadVersionFile_versionNotFound_404() {
        stubVisible();
        when(skillVersionRepository.findBySkillIdOrderByVersionDesc(100L)).thenReturn(List.of());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.downloadVersionFile(100L, 1, 20L, creator));
        assertEquals(BusinessErrorCode.SKILL_VERSION_NOT_FOUND, ex.getCode());
    }

    @Test
    void downloadVersionFile_fileNotFound_404() {
        stubVisible();
        com.furelise.skillmanagement.skill.model.SkillVersion sv =
                com.furelise.skillmanagement.skill.model.SkillVersion.builder()
                        .id(10L).skillId(100L).version(1).name("Old Name")
                        .publicScope("PRIVATE").createdBy(1L).createdAt(Instant.now()).build();
        when(skillVersionRepository.findBySkillIdOrderByVersionDesc(100L)).thenReturn(List.of(sv));
        when(skillVersionFileRepository.findById(20L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> skillService.downloadVersionFile(100L, 1, 20L, creator));
        assertEquals(BusinessErrorCode.SKILL_VERSION_FILE_NOT_FOUND, ex.getCode());
    }
}
