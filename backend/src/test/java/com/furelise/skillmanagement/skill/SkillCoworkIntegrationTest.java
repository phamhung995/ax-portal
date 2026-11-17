package com.furelise.skillmanagement.skill;

import com.furelise.skillmanagement.audit.service.AuditService;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.mail.EmailService;
import com.furelise.skillmanagement.mcp.repository.McpMasterRepository;
import com.furelise.skillmanagement.mcp.repository.McpVersionFileRepository;
import com.furelise.skillmanagement.mcp.repository.McpVersionRepository;
import com.furelise.skillmanagement.mcp.repository.SkillMcpDependencyRepository;
import com.furelise.skillmanagement.mcp.service.McpAccessHelper;
import com.furelise.skillmanagement.mcp.service.McpDependencyService;
import com.furelise.skillmanagement.mcp.service.McpPublishValidationService;
import com.furelise.skillmanagement.model.Role;
import com.furelise.skillmanagement.notification.model.Notification;
import com.furelise.skillmanagement.notification.repository.NotificationRepository;
import com.furelise.skillmanagement.org.repository.OrganizationRepository;
import com.furelise.skillmanagement.repository.RoleRepository;
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.skill.model.ProcessStage;
import com.furelise.skillmanagement.skill.model.SkillCategory;
import com.furelise.skillmanagement.skill.model.SkillCategoryMapping;
import com.furelise.skillmanagement.skill.model.SkillCoworkUser;
import com.furelise.skillmanagement.skill.model.SkillFile;
import com.furelise.skillmanagement.skill.model.SkillStageMapping;
import com.furelise.skillmanagement.skill.model.SkillStatusHistory;
import com.furelise.skillmanagement.skill.repository.ProcessStageRepository;
import com.furelise.skillmanagement.skill.repository.SkillCategoryMappingRepository;
import com.furelise.skillmanagement.skill.repository.SkillCategoryRepository;
import com.furelise.skillmanagement.skill.repository.SkillCoworkUserRepository;
import com.furelise.skillmanagement.skill.repository.SkillFileRepository;
import com.furelise.skillmanagement.skill.repository.SkillRatingRepository;
import com.furelise.skillmanagement.skill.repository.SkillRepository;
import com.furelise.skillmanagement.skill.repository.SkillShareRepository;
import com.furelise.skillmanagement.skill.repository.SkillStageMappingRepository;
import com.furelise.skillmanagement.skill.repository.SkillStatusHistoryRepository;
import com.furelise.skillmanagement.skill.repository.SkillSubscriptionRepository;
import com.furelise.skillmanagement.skill.repository.SkillViewLogRepository;
import com.furelise.skillmanagement.skill.repository.SkillDownloadLogRepository;
import com.furelise.skillmanagement.skill.repository.SkillVersionFileRepository;
import com.furelise.skillmanagement.skill.repository.SkillVersionRepository;
import com.furelise.skillmanagement.skill.service.SkillFileStorageService;
import com.furelise.skillmanagement.skill.service.SkillVersionUpdateService;
import com.furelise.skillmanagement.skill.service.SkillWorkflowService;
import com.furelise.skillmanagement.skill.service.impl.SkillServiceImpl;
import com.furelise.skillmanagement.skill.service.impl.SkillWorkflowServiceImpl;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.repository.UserRepository;
import com.furelise.skillmanagement.user.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H2 integration test for the Cowork feature (V47 skill_cowork_users).
 * Seeds creator / cowork / stranger / admin + a DRAFT skill and asserts the
 * permission matrix: cowork can edit/submit but NOT delete; only
 * creator/admin manage the cowork list; new cowork users are notified.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
// Isolated H2 database: this context's create-drop must not drop the shared
// "testdb" tables that other @DataJpaTest classes still use.
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:coworkdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS TEXT")
class SkillCoworkIntegrationTest {

    @Autowired private SkillRepository skillRepository;
    @Autowired private SkillFileRepository skillFileRepository;
    @Autowired private SkillRatingRepository skillRatingRepository;
    @Autowired private SkillStatusHistoryRepository skillStatusHistoryRepository;
    @Autowired private SkillShareRepository skillShareRepository;
    @Autowired private SkillSubscriptionRepository skillSubscriptionRepository;
    @Autowired private SkillViewLogRepository skillViewLogRepository;
    @Autowired private SkillDownloadLogRepository skillDownloadLogRepository;
    @Autowired private SkillCategoryRepository skillCategoryRepository;
    @Autowired private ProcessStageRepository processStageRepository;
    @Autowired private SkillCategoryMappingRepository skillCategoryMappingRepository;
    @Autowired private SkillStageMappingRepository skillStageMappingRepository;
    @Autowired private SkillCoworkUserRepository skillCoworkUserRepository;
    @Autowired private SkillVersionRepository skillVersionRepository;
    @Autowired private SkillVersionFileRepository skillVersionFileRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private com.furelise.skillmanagement.org.repository.HrSystemAssignmentRepository hrAssignmentRepository;
    @Autowired private com.furelise.skillmanagement.org.repository.SystemMasterRepository systemMasterRepository;
    @Autowired private com.furelise.skillmanagement.org.repository.SubSystemMasterRepository subSystemMasterRepository;
    @Autowired private UserSettingsRepository userSettingsRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private SkillMcpDependencyRepository skillMcpDependencyRepository;
    @Autowired private McpMasterRepository mcpMasterRepository;
    @Autowired private McpVersionRepository mcpVersionRepository;
    @Autowired private McpVersionFileRepository mcpVersionFileRepository;
    @Autowired private com.furelise.skillmanagement.mcp.repository.McpFileRepository mcpFileRepository;

    private SkillWorkflowService skillWorkflowService;

    private SkillServiceImpl service;

    private Role normalRole;
    private Role adminRole;
    private Long creatorId, coworkId, strangerId, adminId;
    private Long categoryId;
    private Long stageId;
    private Long skillId;

    @BeforeEach
    void seed() {
        // No disk / mail / audit in tests — stub the non-JPA collaborators.
        SkillFileStorageService fileStorage = new SkillFileStorageService() {
            @Override public String store(Long skillId, org.springframework.web.multipart.MultipartFile file) {
                return "s/" + file.getOriginalFilename();
            }
            @Override public void delete(String relativePath) { /* no-op */ }
            @Override public java.io.InputStream load(String relativePath) {
                return new java.io.ByteArrayInputStream(new byte[0]);
            }
        };
        EmailService emailService = new EmailService() {
            @Override public void sendOtpEmail(String to, String otpType, String otp, String subject) { /* no-op */ }
            @Override public void sendNotificationEmail(String to, String subject, String body) { /* no-op */ }
        };
        AuditService auditService = new AuditService() {
            @Override public void log(User actor, String action, String entityType, String entityId, String details) { /* no-op */ }
            @Override public void log(String action, String entityType, String entityId, String details) { /* no-op */ }
            @Override public com.furelise.skillmanagement.audit.dto.AuditLogPage page(int page, int pageSize, String actorName, String entityType, String action) {
                return null;
            }
        };
        var accessHelper = new com.furelise.skillmanagement.skill.service.SkillAccessHelper(
                organizationRepository, skillRepository,
                hrAssignmentRepository, systemMasterRepository,
                subSystemMasterRepository, userRepository);
        var eventNotificationService =
                new com.furelise.skillmanagement.notification.service.impl.EventNotificationServiceImpl(
                        notificationRepository,
                        new com.furelise.skillmanagement.notification.service.NotificationEmailSender(
                                emailService),
                        userRepository, userSettingsRepository);
        SkillVersionUpdateService versionUpdateService = new SkillVersionUpdateService(
                skillRepository, skillFileRepository, skillVersionRepository,
                skillVersionFileRepository, skillCategoryMappingRepository,
                skillStageMappingRepository, skillSubscriptionRepository,
                fileStorage, userRepository, userSettingsRepository,
                eventNotificationService);
        skillWorkflowService = new SkillWorkflowServiceImpl(
                skillRepository, skillStatusHistoryRepository, skillShareRepository,
                userRepository,
                userSettingsRepository, eventNotificationService,
                accessHelper, versionUpdateService, skillCoworkUserRepository,
                new McpPublishValidationService(
                        skillMcpDependencyRepository, mcpMasterRepository),
                null); // McpNotificationService — not exercised in this test
        McpDependencyService mcpDependencyService = new McpDependencyService(
                skillMcpDependencyRepository, mcpMasterRepository, mcpFileRepository,
                mcpVersionRepository, mcpVersionFileRepository);
        // Reputation stub: this test never exercises scoring; search paths
        // only need getReputations() to return empty (no cached scores).
        var reputationService = new com.furelise.skillmanagement.reputation.service.ReputationService() {
            @Override
            public com.furelise.skillmanagement.reputation.model.ReputationScore refreshSkill(Long skillId) {
                return null;
            }

            @Override
            public com.furelise.skillmanagement.reputation.model.ReputationScore refreshMcp(Long mcpId) {
                return null;
            }

            @Override
            public com.furelise.skillmanagement.reputation.model.ReputationScore refreshAiTool(Long toolId) {
                return null;
            }

            @Override
            public com.furelise.skillmanagement.reputation.model.ReputationScore refreshAiNews(Long newsId) {
                return null;
            }

            @Override
            public com.furelise.skillmanagement.reputation.model.ReputationScore refreshSkillRequest(Long requestId) {
                return null;
            }

            @Override
            public void refreshContributor(Long userId) {
            }

            @Override
            public com.furelise.skillmanagement.reputation.dto.ContributorResponse getContributor(Long userId) {
                return null;
            }

            @Override
            public com.furelise.skillmanagement.reputation.dto.ReputationResponse getReputation(
                    String targetType, Long targetId) {
                return null;
            }

            @Override
            public java.util.Map<Long, com.furelise.skillmanagement.reputation.model.ReputationScore> getReputations(
                    String targetType, java.util.List<Long> targetIds) {
                return java.util.Map.of();
            }

            @Override
            public com.furelise.skillmanagement.reputation.dto.ReputationExplainResponse explain(
                    String targetType, Long targetId) {
                return null;
            }
        };
        service = new SkillServiceImpl(
                skillRepository, skillFileRepository, skillRatingRepository,
                skillStatusHistoryRepository, skillSubscriptionRepository,
                skillViewLogRepository, skillDownloadLogRepository,
                skillCategoryRepository, processStageRepository,
                skillCategoryMappingRepository, skillStageMappingRepository,
                skillCoworkUserRepository,
                skillShareRepository,
                accessHelper,
                mcpDependencyService,
                skillWorkflowService, versionUpdateService,
                skillVersionRepository, skillVersionFileRepository,
                userRepository, organizationRepository,
                systemMasterRepository, subSystemMasterRepository,
                fileStorage, (key, max, window) -> true,
                reputationService,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                userSettingsRepository, eventNotificationService,
                auditService);

        normalRole = findOrCreateRole("NORMAL_USER");
        adminRole = findOrCreateRole("ADMIN_USER");

        creatorId = saveUser("Creator", "creator@corp.com", "CREATOR", normalRole);
        coworkId = saveUser("Cowork", "cowork@corp.com", "COWORK", normalRole);
        strangerId = saveUser("Stranger", "stranger@corp.com", "STRANGER", normalRole);
        adminId = saveUser("Admin", "admin@corp.com", "ADMIN", adminRole);

        categoryId = skillCategoryRepository.save(SkillCategory.builder().name("Cat1").build()).getId();
        stageId = processStageRepository.save(ProcessStage.builder().name("Stage1").build()).getId();

        skillId = skillRepository.save(Skill.builder()
                .name("Cowork Skill")
                .approvalStatus(Skill.STATUS_DRAFT)
                .publicScope(Skill.SCOPE_PRIVATE)
                .createdBy(creatorId)
                .build()).getId();
        // A live file is required for update() (file count must stay > 0).
        skillFileRepository.save(SkillFile.builder()
                .skillId(skillId).fileName("a.txt").filePath("s/a.txt")
                .fileSize(1L).pendingYn("N").build());
        skillCategoryMappingRepository.save(SkillCategoryMapping.builder()
                .skillId(skillId).categoryId(categoryId).build());
        skillStageMappingRepository.save(SkillStageMapping.builder()
                .skillId(skillId).stageId(stageId).build());
        skillStatusHistoryRepository.save(SkillStatusHistory.builder()
                .skillId(skillId).toStatus(Skill.STATUS_DRAFT).changedBy(creatorId).build());
    }

    private User user(Long id) {
        return userRepository.findById(id).orElseThrow();
    }

    private void addCoworkRow(Long userId) {
        skillCoworkUserRepository.save(SkillCoworkUser.builder()
                .skillId(skillId).userId(userId).build());
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "b.txt", "text/plain", "x".getBytes());
    }

    // ── Create ──────────────────────────────────────────────────────────────

    @Test
    void createWithCoworkUsers_storesRowsAndNotifies() {
        Long newSkillId = service.create("New Skill", null, Skill.SCOPE_PRIVATE,
                null, null,
                "[]", List.of(categoryId), List.of(stageId), List.of(coworkId),
                new MockMultipartFile[]{file()}, user(creatorId));

        assertThat(skillCoworkUserRepository.existsBySkillIdAndUserId(newSkillId, coworkId)).isTrue();
        assertThat(notificationRepository.findAll()).hasSize(1);
        Notification n = notificationRepository.findAll().get(0);
        assertThat(n.getUserId()).isEqualTo(coworkId);
        assertThat(n.getTitle()).contains("cowork");
    }

    @Test
    void createWithSelfAsCowork_rejected() {
        assertThatThrownBy(() -> service.create("Self Cowork", null, Skill.SCOPE_PRIVATE,
                null, null,
                "[]", List.of(categoryId), List.of(stageId), List.of(creatorId),
                new MockMultipartFile[]{file()}, user(creatorId)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(BusinessErrorCode.SKILL_INVALID_COWORK);
    }

    @Test
    void createWithNonexistentCowork_rejected() {
        assertThatThrownBy(() -> service.create("Bad Cowork", null, Skill.SCOPE_PRIVATE,
                null, null,
                "[]", List.of(categoryId), List.of(stageId), List.of(999_999L),
                new MockMultipartFile[]{file()}, user(creatorId)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(BusinessErrorCode.SKILL_INVALID_COWORK);
    }

    // ── Update (edit) ───────────────────────────────────────────────────────

    @Test
    void updateByCowork_ok() {
        addCoworkRow(coworkId);
        service.update(skillId, "Cowork Skill v2", null, Skill.SCOPE_PRIVATE,
                null, null,
                "[]", List.of(categoryId), List.of(stageId), List.of(),
                null, null, null, null, user(coworkId));
        assertThat(skillRepository.findById(skillId).orElseThrow().getName()).isEqualTo("Cowork Skill v2");
    }

    @Test
    void updateByStranger_rejected() {
        addCoworkRow(coworkId);
        // The skill is PRIVATE to the creator → the stranger gets 404 first.
        assertThatThrownBy(() -> service.update(skillId, "Hacked", null, Skill.SCOPE_PRIVATE,
                null, null,
                "[]", List.of(categoryId), List.of(stageId), List.of(),
                null, null, null, null, user(strangerId)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(BusinessErrorCode.SKILL_NOT_FOUND);
    }

    @Test
    void updateByCowork_cannotChangeCoworkList() {
        addCoworkRow(coworkId);
        // Cowork tries to add the stranger to the cowork list → silently kept.
        service.update(skillId, "Cowork Skill", null, Skill.SCOPE_PRIVATE,
                null, null,
                "[]", List.of(categoryId), List.of(stageId), List.of(strangerId),
                null, null, null, null, user(coworkId));
        assertThat(skillCoworkUserRepository.existsBySkillIdAndUserId(skillId, coworkId)).isTrue();
        assertThat(skillCoworkUserRepository.existsBySkillIdAndUserId(skillId, strangerId)).isFalse();
    }

    @Test
    void updateByCreator_addingNewCowork_notifiesOnlyNewUser() {
        addCoworkRow(coworkId);
        service.update(skillId, "Cowork Skill", null, Skill.SCOPE_PRIVATE,
                null, null,
                "[]", List.of(categoryId), List.of(stageId), List.of(coworkId, strangerId),
                null, null, null, null, user(creatorId));

        assertThat(skillCoworkUserRepository.existsBySkillIdAndUserId(skillId, coworkId)).isTrue();
        assertThat(skillCoworkUserRepository.existsBySkillIdAndUserId(skillId, strangerId)).isTrue();
        // Only the newly added user (stranger) is notified — not the existing cowork.
        List<Notification> all = notificationRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getUserId()).isEqualTo(strangerId);
    }

    // ── Delete (creator/admin only) ─────────────────────────────────────────

    @Test
    void deleteByCowork_denied() {
        addCoworkRow(coworkId);
        assertThatThrownBy(() -> service.deleteBatch(List.of(skillId), user(coworkId)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(BusinessErrorCode.SKILL_DELETE_FORBIDDEN);
        assertThat(skillRepository.findById(skillId).orElseThrow().getDeletedYn()).isEqualTo("N");
    }

    @Test
    void deleteByCreator_ok() {
        addCoworkRow(coworkId);
        service.deleteBatch(List.of(skillId), user(creatorId));
        assertThat(skillRepository.findById(skillId).orElseThrow().getDeletedYn()).isEqualTo("Y");
    }

    // ── Workflow transitions ────────────────────────────────────────────────

    @Test
    void coworkCanSubmitDraftToPending() {
        addCoworkRow(coworkId);
        skillWorkflowService.transition(skillId, user(coworkId), Skill.STATUS_PENDING_APPROVAL, null);
        assertThat(skillRepository.findById(skillId).orElseThrow().getApprovalStatus())
                .isEqualTo(Skill.STATUS_PENDING_APPROVAL);
    }

    @Test
    void coworkCannotWithdrawPending() {
        addCoworkRow(coworkId);
        skillRepository.findById(skillId).ifPresent(s -> {
            s.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
            skillRepository.save(s);
        });
        assertThatThrownBy(() ->
                skillWorkflowService.transition(skillId, user(coworkId), Skill.STATUS_DRAFT, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(BusinessErrorCode.SKILL_INVALID_TRANSITION);
    }

    @Test
    void strangerCannotSubmit() {
        // The skill is PRIVATE to the creator → the stranger gets 404 first.
        assertThatThrownBy(() ->
                skillWorkflowService.transition(skillId, user(strangerId), Skill.STATUS_PENDING_APPROVAL, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(BusinessErrorCode.SKILL_NOT_FOUND);
    }

    // ── Detail response ─────────────────────────────────────────────────────

    @Test
    void detailExposesCoworkUsersAndFlags() {
        addCoworkRow(coworkId);
        // The skill is PRIVATE → the stranger cannot see it (404).
        assertThatThrownBy(() -> service.getDetail(skillId, user(strangerId)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(BusinessErrorCode.SKILL_NOT_FOUND);

        com.furelise.skillmanagement.skill.dto.SkillDetailResponse coworkDetail =
                service.getDetail(skillId, user(coworkId));
        assertThat(coworkDetail.coworkUsers()).hasSize(1);
        assertThat(coworkDetail.coworkUsers().get(0).id()).isEqualTo(coworkId);
        assertThat(coworkDetail.coworkUsers().get(0).name()).isEqualTo("Cowork");
        assertThat(coworkDetail.isCreator()).isFalse();
        assertThat(coworkDetail.canEdit()).isTrue();
    }

    // ── seed helpers ────────────────────────────────────────────────────────

    private Role findOrCreateRole(String name) {
        // The H2 test DB is shared across Spring contexts (DB_CLOSE_DELAY=-1),
        // so roles from a previous context may already exist.
        return roleRepository.findAll().stream()
                .filter(r -> name.equals(r.getName()))
                .findFirst()
                .orElseGet(() -> roleRepository.save(
                        Role.builder().name(name).description("t").build()));
    }

    private Long saveUser(String name, String email, String knoxId, Role role) {
        return userRepository.save(User.builder()
                .name(name).email(email).password("x").knoxId(knoxId)
                .role(role).build()).getId();
    }
}
