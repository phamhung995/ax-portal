package com.furelise.skillmanagement.skill;

import com.furelise.skillmanagement.model.Role;
import com.furelise.skillmanagement.org.model.HrSystemAssignment;
import com.furelise.skillmanagement.org.model.OrganizationMaster;
import com.furelise.skillmanagement.org.model.SystemMaster;
import com.furelise.skillmanagement.org.repository.HrSystemAssignmentRepository;
import com.furelise.skillmanagement.org.repository.OrganizationRepository;
import com.furelise.skillmanagement.org.repository.SubSystemMasterRepository;
import com.furelise.skillmanagement.org.repository.SystemMasterRepository;
import com.furelise.skillmanagement.repository.RoleRepository;
import com.furelise.skillmanagement.skill.dto.SkillShareResult;
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.skill.model.SkillShare;
import com.furelise.skillmanagement.skill.repository.SkillRepository;
import com.furelise.skillmanagement.skill.repository.SkillShareRepository;
import com.furelise.skillmanagement.skill.service.SkillAccessHelper;
import com.furelise.skillmanagement.skill.service.SkillShareService;
import com.furelise.skillmanagement.skill.service.SkillWorkflowService;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H2 integration test for the skill share feature (PROJECT scope).
 *
 * <p>Setup mirrors SkillProjectScopeVisibilityTest: Alice (G1) creates a
 * PROJECT skill on project PSS-ALPHA. On top of that:
 * <ul>
 *   <li>USER share to Eve (G2, no project overlap) → Eve sees it.</li>
 *   <li>PROJECT share to PSS-GAMMA → Frank (PIC of SYS-D, project
 *       PSS-GAMMA) sees it, even though PSS-GAMMA is not Alice's project.</li>
 *   <li>Unshared Carol (PSS-BETA) still does NOT see it.</li>
 *   <li>Removing the USER share hides it from Eve again.</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:skillsharedb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS TEXT")
class SkillShareVisibilityTest {

    @Autowired private SkillRepository skillRepository;
    @Autowired private SkillShareRepository shareRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private HrSystemAssignmentRepository hrAssignmentRepository;
    @Autowired private SystemMasterRepository systemMasterRepository;
    @Autowired private SubSystemMasterRepository subSystemMasterRepository;
    @Autowired private RoleRepository roleRepository;

    private SkillAccessHelper accessHelper;
    private SkillWorkflowService workflowService;

    private SkillShareService shareService;

    private Role normalRole;
    private Long aliceId, carolId, eveId, frankId;
    private Long skillId;

    @BeforeEach
    void seed() {
        accessHelper = new SkillAccessHelper(
                organizationRepository, skillRepository,
                hrAssignmentRepository, systemMasterRepository,
                subSystemMasterRepository, userRepository);
        workflowService = org.mockito.Mockito.mock(SkillWorkflowService.class);
        shareService = new SkillShareService(
                skillRepository, shareRepository, userRepository, accessHelper, workflowService);

        normalRole = roleRepository.findAll().stream()
                .filter(r -> "NORMAL_USER".equals(r.getName()))
                .findFirst()
                .orElseGet(() -> roleRepository.save(
                        Role.builder().name("NORMAL_USER").description("t").build()));

        aliceId = saveUser("Alice", "ALICE");
        carolId = saveUser("Carol", "CAROL");
        eveId = saveUser("Eve", "EVE");
        frankId = saveUser("Frank", "FRANK");

        // Alice + Carol in G1; Eve in G2; Frank in G3 (own group, no overlap).
        saveOrg("ALICE", "Alice", "P1", "G1", "C1", "D1");
        saveOrg("CAROL", "Carol", "P1", "G1", "C1", "D1");
        saveOrg("EVE", "Eve", "P9", "G2", "C2", "D2");
        saveOrg("FRANK", "Frank", "P3", "G3", "C3", "D3");

        // Systems: Alice's project PSS-ALPHA (SYS-A, group G1); Carol's
        // PSS-BETA (SYS-C, group G1); Frank's PSS-GAMMA (SYS-D, group G3).
        systemMasterRepository.save(SystemMaster.builder()
                .systemKey("SYS-A").systemName("System A")
                .projectKey("PSS-ALPHA").gdcGroup("G1")
                .importedAt(Instant.now()).build());
        systemMasterRepository.save(SystemMaster.builder()
                .systemKey("SYS-C").systemName("System C")
                .projectKey("PSS-BETA").gdcGroup("G1")
                .importedAt(Instant.now()).build());
        systemMasterRepository.save(SystemMaster.builder()
                .systemKey("SYS-D").systemName("System D")
                .projectKey("PSS-GAMMA").gdcGroup("G3")
                .importedAt(Instant.now()).build());

        hrAssignmentRepository.save(hrRow("ALICE", "SYS-A"));
        hrAssignmentRepository.save(hrRow("CAROL", "SYS-C"));
        hrAssignmentRepository.save(hrRow("FRANK", "SYS-D"));

        skillId = skillRepository.save(Skill.builder()
                .name("Shared Project Skill")
                .approvalStatus(Skill.STATUS_APPROVED)
                .publicScope(Skill.SCOPE_PROJECT)
                .systemKey("SYS-A")
                .createdBy(aliceId)
                .build()).getId();
    }

    @Test
    void sharedUserSeesSkill() {
        // Eve is in another group with no project overlap — only visible
        // through an explicit USER share.
        shareRepository.save(share(SkillShare.TYPE_USER, eveId, null));
        assertThat(visibleIds(eveId)).contains(skillId);
    }

    @Test
    void sharedProjectMemberSeesSkill() {
        // Frank's project PSS-GAMMA is shared → he sees the skill even
        // though PSS-GAMMA is not Alice's project.
        shareRepository.save(share(SkillShare.TYPE_PROJECT, null, "PSS-GAMMA"));
        assertThat(visibleIds(frankId)).contains(skillId);
    }

    @Test
    void unsharedUserStillCannotSee() {
        // Carol shares G1 with Alice but not the project, and has no share.
        shareRepository.save(share(SkillShare.TYPE_USER, eveId, null));
        assertThat(visibleIds(carolId)).doesNotContain(skillId);
    }

    @Test
    void removingShareHidesSkillAgain() {
        shareRepository.save(share(SkillShare.TYPE_USER, eveId, null));
        assertThat(visibleIds(eveId)).contains(skillId);
        shareRepository.deleteAll(shareRepository.findBySkillIdOrderByCreatedAtAsc(skillId));
        assertThat(visibleIds(eveId)).doesNotContain(skillId);
    }

    @Test
    void shareableProjectsAreAllProjectsExceptCreators() {
        // Alice is PIC of SYS-A → her project is PSS-ALPHA. The shareable
        // projects are ALL projects of non-End-phase systems except her
        // own: PSS-BETA (SYS-C) and PSS-GAMMA (SYS-D).
        Skill skill = skillRepository.findById(skillId).orElseThrow();
        List<String> projects = accessHelper.shareableProjectKeys(skill);
        assertThat(projects).containsExactlyInAnyOrder("pss-beta", "pss-gamma");
    }

    @Test
    void endPhaseSystemProjectsAreNotShareable() {
        // A system in End phase contributes no shareable project.
        systemMasterRepository.save(SystemMaster.builder()
                .systemKey("SYS-E").systemName("System E")
                .projectKey("PSS-DELTA").gdcGroup("G9")
                .currentPhase("End")
                .importedAt(Instant.now()).build());
        Skill skill = skillRepository.findById(skillId).orElseThrow();
        List<String> projects = accessHelper.shareableProjectKeys(skill);
        assertThat(projects).doesNotContain("pss-delta");
    }

    @Test
    void batchRemoveDeletesOnlyThisSkillsShares() {
        // Two shares on this skill + one foreign share id → only the two
        // belonging to the skill are deleted; Eve loses visibility.
        SkillShare mine1 = shareRepository.save(share(SkillShare.TYPE_USER, eveId, null));
        SkillShare mine2 = shareRepository.save(share(SkillShare.TYPE_PROJECT, null, "PSS-GAMMA"));
        User alice = userRepository.findById(aliceId).orElseThrow();
        int removed = shareService.removeShares(skillId,
                List.of(mine1.getId(), mine2.getId(), 999999L), alice);
        assertThat(removed).isEqualTo(2);
        assertThat(shareRepository.findBySkillIdOrderByCreatedAtAsc(skillId)).isEmpty();
        assertThat(visibleIds(eveId)).doesNotContain(skillId);
    }

    @Test
    void batchShareSkipsAlreadyShared() {
        // Share Eve first (internal — same project as Alice), then batch-share
        // [Eve, Carol] → only Carol is added (external → PENDING, so the
        // skill goes pending and Carol does NOT see it until approved).
        shareRepository.save(share(SkillShare.TYPE_USER, eveId, null));
        User alice = userRepository.findById(aliceId).orElseThrow();
        SkillShareResult added = shareService.shareUsers(skillId, List.of(eveId, carolId), alice);
        assertThat(added.items()).hasSize(1);
        assertThat(added.items().get(0).userId()).isEqualTo(carolId);
        assertThat(added.pendingCount()).isEqualTo(1);
        assertThat(visibleIds(eveId)).contains(skillId);
        // Carol's share is PENDING → no visibility, and the skill is pending.
        assertThat(visibleIds(carolId)).doesNotContain(skillId);
        Skill skill = skillRepository.findById(skillId).orElseThrow();
        assertThat(skill.getPendingReason()).isEqualTo(Skill.PENDING_REASON_SHARE);
    }

    // ── External share → PENDING (admin approval required) ────────────────────

    @Test
    void shareInternalUser_effectiveImmediately() {
        // Carol is in the same project as Alice (PSS-ALPHA) → internal share.
        hrAssignmentRepository.save(hrRow("CAROL", "SYS-A"));
        User alice = userRepository.findById(aliceId).orElseThrow();
        SkillShareResult result = shareService.shareUsers(skillId, List.of(carolId), alice);
        assertThat(result.pendingCount()).isZero();
        assertThat(result.items().get(0).approvalStatus()).isEqualTo(SkillShare.STATUS_APPROVED);
        // Skill stays APPROVED — no pending state.
        Skill skill = skillRepository.findById(skillId).orElseThrow();
        assertThat(skill.getApprovalStatus()).isEqualTo(Skill.STATUS_APPROVED);
        assertThat(skill.getPendingReason()).isNull();
        assertThat(visibleIds(carolId)).contains(skillId);
    }

    @Test
    void shareExternalUser_skillBecomesSharePending() {
        // Eve has no project overlap with Alice → external share.
        User alice = userRepository.findById(aliceId).orElseThrow();
        SkillShareResult result = shareService.shareUsers(skillId, List.of(eveId), alice);
        assertThat(result.pendingCount()).isEqualTo(1);
        assertThat(result.items().get(0).approvalStatus()).isEqualTo(SkillShare.STATUS_PENDING);
        Skill skill = skillRepository.findById(skillId).orElseThrow();
        assertThat(skill.getPendingReason()).isEqualTo(Skill.PENDING_REASON_SHARE);
        // The workflow transition is requested (mock — the real transition
        // sets the status; here we assert the request was made).
        org.mockito.Mockito.verify(workflowService)
                .transition(skillId, alice, Skill.STATUS_PENDING_APPROVAL, null);
    }

    @Test
    void sharePending_projectMembersStillSee() {
        // Simulate the pending state: skill PENDING_APPROVAL + pendingReason
        // (the workflow transition sets the status; the share is PENDING).
        Skill skill = skillRepository.findById(skillId).orElseThrow();
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        skill.setPendingReason(Skill.PENDING_REASON_SHARE);
        skillRepository.save(skill);
        shareRepository.save(shareWithStatus(SkillShare.TYPE_USER, eveId, null, SkillShare.STATUS_PENDING));
        // Carol shares Alice's project (PSS-ALPHA) → still sees the skill.
        hrAssignmentRepository.save(hrRow("CAROL", "SYS-A"));
        assertThat(visibleIds(carolId)).contains(skillId);
    }

    @Test
    void sharePending_externalUserCannotSeeUntilApproved() {
        Skill skill = skillRepository.findById(skillId).orElseThrow();
        skill.setApprovalStatus(Skill.STATUS_PENDING_APPROVAL);
        skill.setPendingReason(Skill.PENDING_REASON_SHARE);
        skillRepository.save(skill);
        shareRepository.save(shareWithStatus(SkillShare.TYPE_USER, eveId, null, SkillShare.STATUS_PENDING));
        // Eve is the shared (external) user — PENDING share grants no
        // visibility until the admin approves.
        assertThat(visibleIds(eveId)).doesNotContain(skillId);
    }

    @Test
    void sharePending_approvedShareGrantsVisibility() {
        // After admin approval: skill back to APPROVED, share APPROVED.
        shareRepository.save(shareWithStatus(SkillShare.TYPE_USER, eveId, null, SkillShare.STATUS_APPROVED));
        assertThat(visibleIds(eveId)).contains(skillId);
    }

    @Test
    void sharePending_rejectedShareGrantsNoVisibility() {
        // After admin rejection: skill back to APPROVED, share REJECTED.
        Skill skill = skillRepository.findById(skillId).orElseThrow();
        skill.setApprovalStatus(Skill.STATUS_APPROVED);
        skillRepository.save(skill);
        shareRepository.save(shareWithStatus(SkillShare.TYPE_USER, eveId, null, SkillShare.STATUS_REJECTED));
        assertThat(visibleIds(eveId)).doesNotContain(skillId);
    }

    @Test
    void shareProject_alwaysPending() {
        // Every shareable project is outside the creator's projects →
        // external → PENDING.
        User alice = userRepository.findById(aliceId).orElseThrow();
        SkillShareResult result = shareService.shareProjects(skillId, List.of("PSS-GAMMA"), alice);
        assertThat(result.pendingCount()).isEqualTo(1);
        assertThat(result.items().get(0).approvalStatus()).isEqualTo(SkillShare.STATUS_PENDING);
        org.mockito.Mockito.verify(workflowService)
                .transition(skillId, alice, Skill.STATUS_PENDING_APPROVAL, null);
    }

    // ── Reviewed skill: external shares take effect immediately (V91) ─────────

    @Test
    void reviewedSkill_shareExternalUser_effectiveImmediately() {
        // The skill was reviewed once by an admin → external shares are
        // APPROVED immediately, no pending state.
        Skill skill = skillRepository.findById(skillId).orElseThrow();
        skill.setAdminReviewedYn("Y");
        skillRepository.save(skill);
        User alice = userRepository.findById(aliceId).orElseThrow();
        SkillShareResult result = shareService.shareUsers(skillId, List.of(eveId), alice);
        assertThat(result.pendingCount()).isZero();
        assertThat(result.items().get(0).approvalStatus()).isEqualTo(SkillShare.STATUS_APPROVED);
        Skill after = skillRepository.findById(skillId).orElseThrow();
        assertThat(after.getApprovalStatus()).isEqualTo(Skill.STATUS_APPROVED);
        assertThat(after.getPendingReason()).isNull();
        // Eve sees the skill immediately.
        assertThat(visibleIds(eveId)).contains(skillId);
        org.mockito.Mockito.verifyNoInteractions(workflowService);
    }

    @Test
    void reviewedSkill_shareProject_effectiveImmediately() {
        Skill skill = skillRepository.findById(skillId).orElseThrow();
        skill.setAdminReviewedYn("Y");
        skillRepository.save(skill);
        User alice = userRepository.findById(aliceId).orElseThrow();
        SkillShareResult result = shareService.shareProjects(skillId, List.of("PSS-GAMMA"), alice);
        assertThat(result.pendingCount()).isZero();
        assertThat(result.items().get(0).approvalStatus()).isEqualTo(SkillShare.STATUS_APPROVED);
        assertThat(visibleIds(frankId)).contains(skillId);
        org.mockito.Mockito.verifyNoInteractions(workflowService);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private List<Long> visibleIds(Long viewerId) {
        User viewer = userRepository.findById(viewerId).orElseThrow();
        return skillRepository.findAll(SkillRepository.visibleTo(viewer, accessHelper.visibility(viewer)))
                .stream().map(Skill::getId).toList();
    }

    private SkillShare share(String type, Long userId, String projectKey) {
        return shareWithStatus(type, userId, projectKey, SkillShare.STATUS_APPROVED);
    }

    private SkillShare shareWithStatus(String type, Long userId, String projectKey, String approvalStatus) {
        return SkillShare.builder()
                .skillId(skillId)
                .shareType(type)
                .userId(userId)
                .projectKey(projectKey)
                .approvalStatus(approvalStatus)
                .createdBy(aliceId)
                .build();
    }

    private Long saveUser(String name, String knoxId) {
        return userRepository.save(User.builder()
                .name(name).email(knoxId.toLowerCase() + "@corp.com").password("x")
                .knoxId(knoxId).role(normalRole).build()).getId();
    }

    private void saveOrg(String knoxId, String userName, String part,
                         String groupName, String center, String division) {
        organizationRepository.save(OrganizationMaster.builder()
                .knoxId(knoxId).userName(userName).employed("Y")
                .regularPartner("Regular").part(part).groupName(groupName)
                .center(center).division(division)
                .employeeNumber("E-" + knoxId).build());
    }

    /** A PIC row with the CURRENT month column set to 1.0 (others null). */
    private HrSystemAssignment hrRow(String knoxId, String systemKey) {
        HrSystemAssignment row = HrSystemAssignment.builder()
                .knoxId(knoxId).userName(knoxId).systemKey(systemKey)
                .importedAt(Instant.now())
                .build();
        switch (java.time.YearMonth.now().getMonthValue()) {
            case 1 -> row.setJan(new BigDecimal("1"));
            case 2 -> row.setFeb(new BigDecimal("1"));
            case 3 -> row.setMar(new BigDecimal("1"));
            case 4 -> row.setApr(new BigDecimal("1"));
            case 5 -> row.setMay(new BigDecimal("1"));
            case 6 -> row.setJun(new BigDecimal("1"));
            case 7 -> row.setJul(new BigDecimal("1"));
            case 8 -> row.setAug(new BigDecimal("1"));
            case 9 -> row.setSep(new BigDecimal("1"));
            case 10 -> row.setOct(new BigDecimal("1"));
            default -> row.setNov(new BigDecimal("1"));
        }
        return row;
    }
}
