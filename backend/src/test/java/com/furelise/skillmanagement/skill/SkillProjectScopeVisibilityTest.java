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
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.skill.repository.SkillRepository;
import com.furelise.skillmanagement.skill.service.SkillAccessHelper;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H2 integration test for the PROJECT scope visibility rule.
 *
 * <p>PIC rule: a person is PIC of a system when any month column from
 * jan up to the current month is &gt; 0. The seed sets the CURRENT month
 * column, so the test is deterministic in any month.
 *
 * <p>Setup:
 * <ul>
 *   <li>Alice (P1/G1) is PIC of SYS-A (project PSS-ALPHA) → creates a
 *       PROJECT skill.</li>
 *   <li>Bob (P2/G1) is PIC of SYS-B (project PSS-ALPHA via client_of_sds)
 *       → shares a project with Alice → SEES the skill.</li>
 *   <li>Carol (P1/G1) is PIC of SYS-C (project PSS-BETA) → different
 *       project, same part → does NOT see the skill.</li>
 *   <li>Dave (G1, no part) is the GROUP manager → sees it via org.</li>
 *   <li>Eve (G2) is in another group → does NOT see it.</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:projectscopedb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS TEXT")
class SkillProjectScopeVisibilityTest {

    @Autowired private SkillRepository skillRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private HrSystemAssignmentRepository hrAssignmentRepository;
    @Autowired private SystemMasterRepository systemMasterRepository;
    @Autowired private SubSystemMasterRepository subSystemMasterRepository;
    @Autowired private RoleRepository roleRepository;

    private SkillAccessHelper accessHelper;

    private Role normalRole;
    private Long aliceId, bobId, carolId, daveId, eveId;
    private Long skillId;

    @BeforeEach
    void seed() {
        accessHelper = new SkillAccessHelper(
                organizationRepository, skillRepository,
                hrAssignmentRepository, systemMasterRepository,
                subSystemMasterRepository, userRepository);

        normalRole = roleRepository.findAll().stream()
                .filter(r -> "NORMAL_USER".equals(r.getName()))
                .findFirst()
                .orElseGet(() -> roleRepository.save(
                        Role.builder().name("NORMAL_USER").description("t").build()));

        aliceId = saveUser("Alice", "ALICE");
        bobId = saveUser("Bob", "BOB");
        carolId = saveUser("Carol", "CAROL");
        daveId = saveUser("Dave", "DAVE");
        eveId = saveUser("Eve", "EVE");

        // Org rows: Alice/Bob/Carol in G1 (parts P1/P2/P1, center C1);
        // Dave is the G1 group manager (row stops at group); Eve in G2.
        saveOrg("ALICE", "Alice", "P1", "G1", "C1", "D1", "E1");
        saveOrg("BOB", "Bob", "P2", "G1", "C1", "D1", "E2");
        saveOrg("CAROL", "Carol", "P1", "G1", "C1", "D1", "E3");
        saveOrg("DAVE", "Dave", null, "G1", null, null, "E4");
        saveOrg("EVE", "Eve", "P9", "G2", "C2", "D2", "E5");

        // Systems: SYS-A → project_key PSS-ALPHA; SYS-B → client_of_sds
        // PSS-ALPHA (client wins over project_key); SYS-C → PSS-BETA.
        systemMasterRepository.save(SystemMaster.builder()
                .systemKey("SYS-A").systemName("System A")
                .projectKey("PSS-ALPHA").importedAt(java.time.Instant.now()).build());
        systemMasterRepository.save(SystemMaster.builder()
                .systemKey("SYS-B").systemName("System B")
                .clientOfSds("PSS-ALPHA").projectKey("PSS-OTHER")
                .importedAt(java.time.Instant.now()).build());
        systemMasterRepository.save(SystemMaster.builder()
                .systemKey("SYS-C").systemName("System C")
                .projectKey("PSS-BETA").importedAt(java.time.Instant.now()).build());
        // SYS-D is in Alice's project (PSS-ALPHA) but nobody is PIC of it —
        // it must still appear in the form system options.
        systemMasterRepository.save(SystemMaster.builder()
                .systemKey("SYS-D").systemName("System D")
                .projectKey("PSS-ALPHA").importedAt(java.time.Instant.now()).build());
        // SYS-E is in Alice's project but End phase — always excluded.
        systemMasterRepository.save(SystemMaster.builder()
                .systemKey("SYS-E").systemName("System E")
                .projectKey("PSS-ALPHA").currentPhase("End")
                .importedAt(java.time.Instant.now()).build());

        // PIC assignments: the current month column is always inside the
        // jan..current-month range, so this is deterministic year-round.
        hrAssignmentRepository.save(hrRow("ALICE", "SYS-A"));
        hrAssignmentRepository.save(hrRow("BOB", "SYS-B"));
        hrAssignmentRepository.save(hrRow("CAROL", "SYS-C"));

        skillId = skillRepository.save(Skill.builder()
                .name("Project Skill")
                .approvalStatus(Skill.STATUS_APPROVED)
                .publicScope(Skill.SCOPE_PROJECT)
                .systemKey("SYS-A")
                .createdBy(aliceId)
                .build()).getId();
    }

    @Test
    void projectPeerSeesSkill() {
        // Bob shares project PSS-ALPHA with Alice (via SYS-B's client_of_sds).
        assertThat(visibleIds(bobId)).contains(skillId);
    }

    @Test
    void samePartDifferentProject_cannotSee() {
        // Carol is in the same part as Alice but her project is PSS-BETA.
        assertThat(visibleIds(carolId)).doesNotContain(skillId);
    }

    @Test
    void groupManagerSeesProjectSkill() {
        // Dave manages G1 → sees PROJECT skills of his group's members.
        assertThat(visibleIds(daveId)).contains(skillId);
    }

    @Test
    void otherGroup_cannotSee() {
        assertThat(visibleIds(eveId)).doesNotContain(skillId);
    }

    @Test
    void creatorSeesOwnProjectSkill() {
        assertThat(visibleIds(aliceId)).contains(skillId);
    }

    @Test
    void formSystems_includeAllSystemsOfCreatorProjects() {
        // Alice is PIC of SYS-A (project PSS-ALPHA). The form options must
        // include every non-End-phase system of PSS-ALPHA — SYS-B and SYS-D
        // included even though Alice is not PIC of them — and exclude the
        // End-phase SYS-E and the other project's SYS-C.
        var alice = userRepository.findById(aliceId).orElseThrow();
        List<String> keys = accessHelper.myFormSystems(alice).stream()
                .map(m -> m.get("systemKey")).toList();
        assertThat(keys).containsExactlyInAnyOrder("SYS-A", "SYS-B", "SYS-D");
    }

    @Test
    void formSystemsOf_skillScopedToCreatorNotEditor() {
        // The skill's creator is Alice → her project's systems, not Bob's.
        Skill skill = skillRepository.findById(skillId).orElseThrow();
        List<String> keys = accessHelper.formSystemsOf(skill).stream()
                .map(m -> m.get("systemKey")).toList();
        assertThat(keys).containsExactlyInAnyOrder("SYS-A", "SYS-B", "SYS-D");
    }

    @Test
    void formSystems_noPicRows_empty() {
        // Eve has no PIC rows → no projects → no systems.
        var eve = userRepository.findById(eveId).orElseThrow();
        assertThat(accessHelper.myFormSystems(eve)).isEmpty();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private List<Long> visibleIds(Long viewerId) {
        User viewer = userRepository.findById(viewerId).orElseThrow();
        return skillRepository.findAll(SkillRepository.visibleTo(viewer, accessHelper.visibility(viewer)))
                .stream().map(Skill::getId).toList();
    }

    private Long saveUser(String name, String knoxId) {
        return userRepository.save(User.builder()
                .name(name).email(knoxId.toLowerCase() + "@corp.com").password("x")
                .knoxId(knoxId).role(normalRole).build()).getId();
    }

    private void saveOrg(String knoxId, String userName, String part,
                         String groupName, String center, String division, String empNo) {
        organizationRepository.save(OrganizationMaster.builder()
                .knoxId(knoxId).userName(userName).employed("Y")
                .regularPartner("Regular").part(part).groupName(groupName)
                .center(center).division(division)
                .employeeNumber(empNo).build());
    }

    /** A PIC row with the CURRENT month column set to 1.0 (others null). */
    private HrSystemAssignment hrRow(String knoxId, String systemKey) {
        HrSystemAssignment row = HrSystemAssignment.builder()
                .knoxId(knoxId).userName(knoxId).systemKey(systemKey)
                .importedAt(java.time.Instant.now())
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
