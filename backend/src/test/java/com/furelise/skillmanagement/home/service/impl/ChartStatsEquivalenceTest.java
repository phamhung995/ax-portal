package com.furelise.skillmanagement.home.service.impl;

import com.furelise.skillmanagement.auth.model.LoginManagement;
import com.furelise.skillmanagement.auth.repository.LoginRepository;
import com.furelise.skillmanagement.feedback.repository.FeedbackRepository;
import com.furelise.skillmanagement.home.dto.ChartDimensions;
import com.furelise.skillmanagement.home.dto.ChartStats;
import com.furelise.skillmanagement.model.Role;
import com.furelise.skillmanagement.org.model.OrganizationMaster;
import com.furelise.skillmanagement.org.repository.OrganizationRepository;
import com.furelise.skillmanagement.repository.RoleRepository;
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.skill.model.SkillDownloadLog;
import com.furelise.skillmanagement.skill.model.SkillRating;
import com.furelise.skillmanagement.skill.model.SkillViewLog;
import com.furelise.skillmanagement.skill.repository.*;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the SQL-scoped chart implementation
 * ({@code doChartStats} / {@code chartDimensions}). The dataset below was
 * previously cross-checked against the original in-memory implementation
 * (full-table findAll) and produced identical numbers; the expected values
 * here are hand-computed from the same dataset, so any future change to the
 * SQL queries that alters the numbers will fail this test.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ChartStatsEquivalenceTest {

    @Autowired private SkillRepository skillRepository;
    @Autowired private SkillRatingRepository skillRatingRepository;
    @Autowired private SkillSubscriptionRepository skillSubscriptionRepository;
    @Autowired private SkillViewLogRepository skillViewLogRepository;
    @Autowired private SkillDownloadLogRepository skillDownloadLogRepository;
    @Autowired private SkillCategoryRepository skillCategoryRepository;
    @Autowired private SkillCategoryMappingRepository skillCategoryMappingRepository;
    @Autowired private ProcessStageRepository processStageRepository;
    @Autowired private SkillStageMappingRepository skillStageMappingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private com.furelise.skillmanagement.org.repository.HrSystemAssignmentRepository hrAssignmentRepository;
    @Autowired private com.furelise.skillmanagement.org.repository.SystemMasterRepository systemMasterRepository;
    @Autowired private com.furelise.skillmanagement.org.repository.SubSystemMasterRepository subSystemMasterRepository;
    @Autowired private FeedbackRepository feedbackRepository;
    @Autowired private LoginRepository loginRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private JdbcTemplate jdbc;

    private HomeServiceImpl service;
    private Role normalRole;

    /** Fixed window shared by all assertions. */
    private Instant end;
    private Instant start;

    private Long aliceId, bobId, carolId, daveId, eveId;

    @BeforeEach
    void seed() {
        service = new HomeServiceImpl(
                skillRepository, skillRatingRepository, skillSubscriptionRepository,
                skillViewLogRepository, skillDownloadLogRepository,
                skillCategoryRepository, skillCategoryMappingRepository,
                processStageRepository, skillStageMappingRepository,
                userRepository, organizationRepository, feedbackRepository, loginRepository,
                new com.furelise.skillmanagement.skill.service.SkillAccessHelper(
                        organizationRepository, skillRepository,
                        hrAssignmentRepository, systemMasterRepository,
                        subSystemMasterRepository, userRepository));

        end = Instant.now().plusSeconds(60); // +60s so users saved after `end`
        // is captured still fall inside the window
        start = end.minusSeconds(365L * 24 * 3600); // 1y window

        normalRole = roleRepository.save(Role.builder()
                .name("NORMAL_USER").description("test").build());

        // ── Users ──────────────────────────────────────────────────────────
        aliceId = saveUser("Alice", "alice@corp.com", "ALICE");
        bobId   = saveUser("Bob", "bob@corp.com", "BOB");
        carolId = saveUser("Carol", "carol@corp.com", "CAROL");
        daveId  = saveUser("Dave", "dave@corp.com", "DAVE");
        eveId   = saveUser("Eve", "eve@corp.com", "EVE");

        // Make alice an OLD user (created before the window) so the
        // "new users" metric differentiates between users.
        jdbc.update("UPDATE \"user\" SET created_at = ? WHERE id = ?",
                start.minusSeconds(86_400), aliceId);

        // ── Org rows (one person = one row, knox_id PK) ─
        saveOrg("ALICE", "Alice", "Y", "P1", "G1", "E1");
        saveOrg("BOB", "Bob", "Y", "P2", "G1", "E2");
        saveOrg("CAROL", "Carol", "Y", "P1", "G1", "E3");
        // Dave is not employed → must be excluded everywhere (G2 has no Y row).
        saveOrg("DAVE", "Dave", "N", "P4", "G2", "E4");
        // Eve's part is blank → group G3 shows with an empty part set,
        // and Eve forms no part-mode bucket.
        saveOrg("EVE", "Eve", "Y", "", "G3", "E5");
        // Grace has no user account → her org row must not form a bucket.
        saveOrg("GRACE", "Grace", "Y", "P9", "g1", "E6");

        // ── Skills ─────────────────────────────────────────────────────────
        Long s1 = saveSkill("S1", aliceId, Skill.STATUS_APPROVED, "N", end.minusSeconds(3600));
        saveSkill("S2", aliceId, Skill.STATUS_APPROVED, "N", start.minusSeconds(86_400)); // outside window
        saveSkill("S3", bobId, Skill.STATUS_DRAFT, "N", end.minusSeconds(3600));          // not approved
        saveSkill("S4", carolId, Skill.STATUS_APPROVED, "Y", end.minusSeconds(3600));     // deleted
        Long s5 = saveSkill("S5", carolId, Skill.STATUS_APPROVED, "N", end.minusSeconds(3600));
        saveSkill("S6", eveId, Skill.STATUS_APPROVED, "N", end.minusSeconds(3600));       // eve = G3 only

        // ── Ratings ────────────────────────────────────────────────────────
        saveRating(s1, aliceId, "5.0");
        saveRating(s1, bobId, "4.0");   // S1 avg = 4.5
        saveRating(s5, carolId, "3.0"); // S5 avg = 3.0

        // ── View logs (views count EVERYONE, including the creator) ───────
        saveView(s1, aliceId); // creator view — still counted
        saveView(s1, bobId);
        saveView(s1, daveId);  // non-bucket viewer — still counted
        saveView(s5, bobId);
        // View outside the window — must be ignored.
        saveViewAt(s1, bobId, start.minusSeconds(86_400));

        // ── Download logs (only download_all=true, creator excluded) ──────
        saveDownload(s1, aliceId, true);  // creator — excluded
        saveDownload(s1, bobId, true);    // counted
        saveDownload(s1, daveId, true);   // counted (non-bucket user)
        saveDownload(s1, bobId, false);   // not "Download All" — ignored
        saveDownload(s5, carolId, true);  // creator — excluded
        saveDownloadAt(s5, bobId, true, start.minusSeconds(86_400)); // outside window

        // ── Logins ─────────────────────────────────────────────────────────
        saveLogin(aliceId, "alice@corp.com", "SUCCESS", end.minusSeconds(3600));
        saveLogin(bobId, "bob@corp.com", "FAILURE", end.minusSeconds(3600)); // not active
        saveLogin(carolId, "carol@corp.com", "SUCCESS", start.minusSeconds(86_400)); // outside
        saveLogin(eveId, "eve@corp.com", "SUCCESS", end.minusSeconds(3600));
    }

    @Test
    void groupDimensionMatchesHandComputed() {
        ChartStats stats = service.doChartStats("1y", "group", null, start, end);

        assertThat(stats.period()).isEqualTo("1y");
        assertThat(stats.dimension()).isEqualTo("group");
        assertThat(stats.group()).isNull();

        // G1 = {alice, bob, carol}: approved-in-window S1 (alice, avg 4.5)
        //    + S5 (carol, avg 3.0) → avg of avgs = 3.8;
        //    views = 3 (S1: alice+bob+dave) + 1 (S5: bob) = 4;
        //    downloads = 2 (S1: bob+dave, creator alice excluded);
        //    active = 1 (only alice has an in-window SUCCESS login);
        //    new = 2 (bob, carol — alice is old).
        // G3 = {eve} (part blank still forms a group bucket):
        //    S6 approved but no rating → 1 skill, avg null, views 0,
        //    downloads 0, active 1, new 1.
        // g1 = {grace — no user account} → empty bucket, dropped from stats.
        // Sort order (ASCII): G1 < G3.
        assertThat(stats.entities()).hasSize(2);

        ChartStats.Entity g1 = stats.entities().get(0);
        assertThat(g1.name()).isEqualTo("G1");
        assertThat(g1.approvedSkills()).isEqualTo(2);
        assertThat(g1.avgRating()).isEqualTo(3.8);
        assertThat(g1.totalViews()).isEqualTo(4);
        assertThat(g1.totalDownloads()).isEqualTo(2);
        assertThat(g1.activeUsers()).isEqualTo(1);
        assertThat(g1.newUsers()).isEqualTo(2);

        ChartStats.Entity g3 = stats.entities().get(1);
        assertThat(g3.name()).isEqualTo("G3");
        assertThat(g3.approvedSkills()).isEqualTo(1);
        assertThat(g3.avgRating()).isNull();
        assertThat(g3.totalViews()).isEqualTo(0);
        assertThat(g3.totalDownloads()).isEqualTo(0);
        assertThat(g3.activeUsers()).isEqualTo(1);
        assertThat(g3.newUsers()).isEqualTo(1);
    }

    @Test
    void partDimensionMatchesHandComputed() {
        ChartStats stats = service.doChartStats("1y", "part", "G1", start, end);

        assertThat(stats.dimension()).isEqualTo("part");
        assertThat(stats.group()).isEqualTo("G1");

        // P1 = {alice, carol}: S1 (4.5) + S5 (3.0) → 3.8; views 4; downloads 2;
        //    active 1 (alice); new 1 (carol — alice is old).
        // P2 = {bob}: no approved-in-window skill (S3 draft) → all zero
        //    except new 1.
        assertThat(stats.entities()).hasSize(2);

        ChartStats.Entity p1 = stats.entities().get(0);
        assertThat(p1.name()).isEqualTo("P1");
        assertThat(p1.approvedSkills()).isEqualTo(2);
        assertThat(p1.avgRating()).isEqualTo(3.8);
        assertThat(p1.totalViews()).isEqualTo(4);
        assertThat(p1.totalDownloads()).isEqualTo(2);
        assertThat(p1.activeUsers()).isEqualTo(1);
        assertThat(p1.newUsers()).isEqualTo(1);

        ChartStats.Entity p2 = stats.entities().get(1);
        assertThat(p2.name()).isEqualTo("P2");
        assertThat(p2.approvedSkills()).isEqualTo(0);
        assertThat(p2.avgRating()).isNull();
        assertThat(p2.totalViews()).isEqualTo(0);
        assertThat(p2.totalDownloads()).isEqualTo(0);
        assertThat(p2.activeUsers()).isEqualTo(0);
        assertThat(p2.newUsers()).isEqualTo(1);
    }

    @Test
    void chartDimensionsMatchesHandComputed() {
        ChartDimensions dims = service.chartDimensions();

        // G1: P1, P2. G3: {} (eve's part blank). g1: P9 (grace's row).
        // G2 has no employed=Y row → not a dimension.
        assertThat(dims.groups()).containsExactlyInAnyOrder("G1", "G3", "g1");
        assertThat(dims.partsByGroup().get("G1")).containsExactly("P1", "P2");
        assertThat(dims.partsByGroup().get("G3")).isEmpty();
        assertThat(dims.partsByGroup().get("g1")).containsExactly("P9");
    }

    // ── seed helpers ────────────────────────────────────────────────────────

    private Long saveUser(String name, String email, String knoxId) {
        return userRepository.save(User.builder()
                .name(name).email(email).password("x").knoxId(knoxId)
                .role(normalRole)
                .build()).getId();
    }

    private void saveOrg(String knoxId, String userName, String employed,
                         String part, String groupName, String empNo) {
        organizationRepository.save(OrganizationMaster.builder()
                .knoxId(knoxId).userName(userName).employed(employed)
                .regularPartner("Regular").part(part).groupName(groupName)
                .employeeNumber(empNo).build());
    }

    private Long saveSkill(String name, Long createdBy, String status,
                           String deletedYn, Instant updatedAt) {
        return skillRepository.save(Skill.builder()
                .name(name).approvalStatus(status).deletedYn(deletedYn)
                .createdBy(createdBy)
                .createdAt(updatedAt.minusSeconds(86_400))
                .updatedAt(updatedAt)
                .build()).getId();
    }

    private void saveRating(Long skillId, Long userId, String rating) {
        skillRatingRepository.save(SkillRating.builder()
                .skillId(skillId).userId(userId)
                .rating(new BigDecimal(rating)).createdAt(end).build());
    }

    private void saveView(Long skillId, Long userId) {
        saveViewAt(skillId, userId, end.minusSeconds(1800));
    }

    private void saveViewAt(Long skillId, Long userId, Instant at) {
        skillViewLogRepository.save(SkillViewLog.builder()
                .skillId(skillId).userId(userId).viewedAt(at).build());
    }

    private void saveDownload(Long skillId, Long userId, boolean downloadAll) {
        saveDownloadAt(skillId, userId, downloadAll, end.minusSeconds(1800));
    }

    private void saveDownloadAt(Long skillId, Long userId, boolean downloadAll, Instant at) {
        skillDownloadLogRepository.save(SkillDownloadLog.builder()
                .skillId(skillId).userId(userId).downloadAll(downloadAll)
                .downloadedAt(at).build());
    }

    private void saveLogin(Long userId, String email, String result, Instant at) {
        loginRepository.save(LoginManagement.builder()
                .userId(userId).email(email).loginResult(result).loginAt(at).build());
    }
}
