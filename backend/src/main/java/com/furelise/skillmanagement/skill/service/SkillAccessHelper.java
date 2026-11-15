package com.furelise.skillmanagement.skill.service;

import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.org.model.OrganizationMaster;
import com.furelise.skillmanagement.org.model.SubSystemMaster;
import com.furelise.skillmanagement.org.model.SystemMaster;
import com.furelise.skillmanagement.org.repository.OrganizationRepository;
import com.furelise.skillmanagement.org.repository.SubSystemMasterRepository;
import com.furelise.skillmanagement.org.repository.SystemMasterRepository;
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.skill.repository.SkillRepository;
import com.furelise.skillmanagement.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Shared visibility helper: loads a skill and enforces the visibility rule
 * (status + scope) for the viewer. Not-visible skills return 404 SKILL_NOT_FOUND
 * so their existence is not revealed (Detail SRS TC-38).
 *
 * <p>Scope rule (hierarchy Division &gt; Center &gt; Group &gt; Part):
 * a row that stops at a level (has it, lacks the next lower level) marks the
 * person as the MANAGER of everything under that level.
 * <ul>
 *   <li>PART  -> same-part members + managers of the part's Group / Center / Division</li>
 *   <li>GROUP -> same-group members + managers of the group's Center / Division</li>
 * </ul>
 * The manager check is done on the CREATOR side: a skill's part/group is
 * visible to the viewer when the creator's part/group is in the viewer's
 * visible set (own + everything under the levels the viewer manages).
 */
@Component
@RequiredArgsConstructor
public class SkillAccessHelper {

    private final OrganizationRepository organizationRepository;
    private final SkillRepository skillRepository;
    private final com.furelise.skillmanagement.org.repository.HrSystemAssignmentRepository hrAssignmentRepository;
    private final SystemMasterRepository systemMasterRepository;
    private final SubSystemMasterRepository subSystemMasterRepository;
    private final com.furelise.skillmanagement.user.repository.UserRepository userRepository;

    /**
     * Returns the skill if it exists and is visible to the viewer,
     * otherwise throws 404 SKILL_NOT_FOUND.
     */
    @Transactional(readOnly = true)
    public Skill getVisible(Long skillId, User viewer) {
        Skill skill = skillRepository.findById(skillId).orElse(null);
        if (skill == null || "Y".equals(skill.getDeletedYn())) {
            throw new BusinessException(BusinessErrorCode.SKILL_NOT_FOUND, "Skill not found.");
        }
        OrgVisibility v = visibility(viewer);
        boolean visible = skillRepository.count(
                SkillRepository.visibleTo(viewer, v)
                        .and((root, query, cb) -> cb.equal(root.get("id"), skillId))) > 0;
        if (!visible) {
            throw new BusinessException(BusinessErrorCode.SKILL_NOT_FOUND, "Skill not found.");
        }
        return skill;
    }

    /**
     * The viewer's org facts for the scope rule. All values are lower-cased
     * and trimmed; blank levels are null.
     */
    @Transactional(readOnly = true)
    public OrgVisibility visibility(User viewer) {
        OrganizationMaster row = organizationRepository.findByKnoxIdIgnoreCase(viewer.getKnoxId())
                .stream()
                .filter(o -> "Y".equals(o.getEmployed()))
                .findFirst()
                .orElse(null);
        if (row == null) {
            return OrgVisibility.empty();
        }
        String part = norm(row.getPart());
        String group = norm(row.getGroupName());
        String center = norm(row.getCenter());
        String division = norm(row.getDivision());

        // Manager flag: the row stops at the highest level it has.
        boolean manager = (part == null && group != null)
                || (group == null && center != null)
                || (center == null && division != null);

        // PROJECT scope facts: the viewer's projects (project of a system =
        // client_of_sds when set, otherwise project_key) and the people who
        // share at least one project with the viewer — the PROJECT scope is
        // visible to them.
        Set<String> projects = projectsOf(viewer);
        Set<String> projectPeers = new LinkedHashSet<>();
        if (!projects.isEmpty()) {
            Map<String, Set<String>> picByKnox = picSystemsByKnox();
            // system key -> project, for every system anyone is PIC of.
            Set<String> allPicKeys = new LinkedHashSet<>();
            picByKnox.values().forEach(allPicKeys::addAll);
            Map<String, String> sysProject = new java.util.HashMap<>();
            for (var s : systemMasterRepository.findBySystemKeyIn(allPicKeys)) {
                String project = projectOf(s);
                if (project != null) {
                    sysProject.put(s.getSystemKey(), project);
                }
            }
            for (Map.Entry<String, Set<String>> e : picByKnox.entrySet()) {
                for (String k : e.getValue()) {
                    String project = sysProject.get(k);
                    if (project != null && projects.contains(project)) {
                        projectPeers.add(e.getKey());
                        break;
                    }
                }
            }
        }

        // Hierarchy maps from the employed='Y' rows (group -> center/division).
        Map<String, String> groupCenter = pairMap(
                organizationRepository.findGroupCenterWhereEmployedY(), 1);
        Map<String, String> groupDivision = pairMap(
                organizationRepository.findGroupDivisionWhereEmployedY(), 1);

        Set<String> visibleParts = new LinkedHashSet<>();
        Set<String> visibleGroups = new LinkedHashSet<>();
        if (part != null) {
            visibleParts.add(part);
        }
        if (group != null) {
            visibleGroups.add(group);
        }

        // Manager expansion: the row stops at the highest level it has.
        if (manager) {
            if (part == null && group != null) {
                // Manager of the group -> sees every part under it.
                for (Object[] p : organizationRepository.findGroupPartWhereEmployedY()) {
                    if (group.equals(norm((String) p[0]))) {
                        String pt = norm((String) p[1]);
                        if (pt != null) {
                            visibleParts.add(pt);
                        }
                    }
                }
            } else if (group == null && center != null) {
                // Manager of the center -> sees every group + part under it.
                visibleGroups.addAll(groupsWhere(groupCenter, groupDivision,
                        c -> center.equals(c), d -> true));
                visibleParts.addAll(partsUnder(visibleGroups, groupCenter, groupDivision));
            } else if (center == null && division != null) {
                // Manager of the division -> sees everything under it.
                visibleGroups.addAll(groupsWhere(groupCenter, groupDivision,
                        c -> true, d -> division.equals(d)));
                visibleParts.addAll(partsUnder(visibleGroups, groupCenter, groupDivision));
            }
        }
        return new OrgVisibility(part, group, center, division, visibleParts, visibleGroups,
                manager, projects, projectPeerCreatorIds(projectPeers));
    }

    /**
     * User ids of the people who share at least one project with the
     * viewer (PROJECT scope peers), resolved from their knox_ids.
     */
    private Set<Long> projectPeerCreatorIds(Set<String> peerKnoxIds) {
        if (peerKnoxIds.isEmpty()) {
            return Set.of();
        }
        return userRepository.findByKnoxIdIgnoreCaseIn(peerKnoxIds).stream()
                .map(User::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * The systems of the given user's projects, as {systemKey, systemName}
     * pairs — options for the PROJECT scope System combobox in the skill
     * form. The user's projects are the projects of every non-End-phase
     * system they are PIC of (jan..current month &gt; 0); the list then
     * includes ALL non-End-phase systems belonging to those projects (not
     * only the systems the user is PIC of).
     */
    @Transactional(readOnly = true)
    public List<Map<String, String>> myFormSystems(User user) {
        return projectSystemsOf(creatorProjects(user.getId()));
    }

    /**
     * The systems of the given skill's CREATOR's projects, as
     * {systemKey, systemName} pairs — options for the PROJECT scope System
     * combobox when editing/updating a skill. The list is scoped to the
     * creator (not the editor) so an admin or cowork user can keep the
     * creator's system assignment.
     */
    @Transactional(readOnly = true)
    public List<Map<String, String>> formSystemsOf(Skill skill) {
        return projectSystemsOf(creatorProjects(skill.getCreatedBy()));
    }

    /**
     * All non-End-phase systems whose project (client_of_sds when set,
     * otherwise project_key) is in the given set, as {systemKey,
     * systemName} pairs sorted by name.
     */
    private List<Map<String, String>> projectSystemsOf(Set<String> projects) {
        if (projects.isEmpty()) {
            return List.of();
        }
        return systemMasterRepository.findAll().stream()
                .filter(s -> !isEndPhase(s))
                .filter(s -> projects.contains(projectOf(s)))
                .sorted(java.util.Comparator.comparing(SystemMaster::getSystemName,
                        java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(s -> Map.of("systemKey", s.getSystemKey(),
                        "systemName", s.getSystemName() == null ? "" : s.getSystemName()))
                .toList();
    }

    /**
     * Sub systems of one system ({subSystemCode, subSystemName} pairs) —
     * options for the PROJECT scope Sub System combobox.
     */
    @Transactional(readOnly = true)
    public List<Map<String, String>> subSystemsOf(String systemKey) {
        return subSystemMasterRepository.findBySystemKeyIn(List.of(systemKey)).stream()
                .filter(s -> norm(s.getSubSystemCode()) != null && norm(s.getSubSystemName()) != null)
                .sorted(java.util.Comparator.comparing(SubSystemMaster::getSubSystemName,
                        java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(s -> Map.of(
                        "subSystemCode", s.getSubSystemCode() == null ? "" : s.getSubSystemCode(),
                        "subSystemName", s.getSubSystemName() == null ? "" : s.getSubSystemName()))
                .toList();
    }

    /**
     * The viewer's projects (project of a system = client_of_sds when set,
     * otherwise project_key) for every system they are PIC of.
     */
    public Set<String> projectsOf(User viewer) {
        Set<String> projects = new LinkedHashSet<>();
        Map<String, Set<String>> picByKnox = picSystemsByKnox();
        Set<String> viewerPicKeys = picByKnox.getOrDefault(
                norm(viewer.getKnoxId()), Set.of());
        if (viewerPicKeys.isEmpty()) {
            return projects;
        }
        for (var s : systemMasterRepository.findBySystemKeyIn(viewerPicKeys)) {
            String project = norm(s.getClientOfSds());
            if (project == null) {
                project = norm(s.getProjectKey());
            }
            if (project != null) {
                projects.add(project);
            }
        }
        return projects;
    }

    /**
     * The systems of the viewer's projects, as {systemKey, systemName}
     * pairs — options for the search filter System combobox.
     *
     * <p>Admin sees ALL systems. A manager (org row stops at a level) sees
     * every system whose GDC Group / GDC Part is in the viewer's visible
     * org set (own + managed). Everyone else sees only the systems of
     * their own projects (project of a system = client_of_sds when set,
     * otherwise project_key). End-phase systems are always excluded.
     */
    @Transactional(readOnly = true)
    public List<Map<String, String>> myProjectSystems(User user) {
        boolean admin = user.getRole() != null
                && "ADMIN_USER".equals(user.getRole().getName());
        List<SystemMaster> candidates;
        if (admin) {
            candidates = systemMasterRepository.findAll();
        } else {
            OrgVisibility v = visibility(user);
            if (v.manager()) {
                candidates = systemMasterRepository.findAll().stream()
                        .filter(s -> (norm(s.getGdcGroup()) != null
                                && v.visibleGroups().contains(norm(s.getGdcGroup())))
                                || (norm(s.getGdcPart()) != null
                                && v.visibleParts().contains(norm(s.getGdcPart()))))
                        .toList();
            } else {
                Set<String> projects = projectsOf(user);
                if (projects.isEmpty()) {
                    return List.of();
                }
                candidates = systemMasterRepository.findAll().stream()
                        .filter(s -> projects.contains(projectOf(s)))
                        .toList();
            }
        }
        return candidates.stream()
                .filter(s -> !isEndPhase(s))
                .sorted(java.util.Comparator.comparing(SystemMaster::getSystemName,
                        java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(s -> Map.of("systemKey", s.getSystemKey(),
                        "systemName", s.getSystemName() == null ? "" : s.getSystemName()))
                .toList();
    }

    /**
     * The sub systems of the viewer's projects ({systemKey,
     * subSystemCode, subSystemName} triples) — options for the search
     * filter Sub System combobox.
     */
    @Transactional(readOnly = true)
    public List<Map<String, String>> myProjectSubSystems(User user) {
        Set<String> systemKeys = myProjectSystems(user).stream()
                .map(m -> m.get("systemKey"))
                .collect(Collectors.toSet());
        if (systemKeys.isEmpty()) {
            return List.of();
        }
        return subSystemMasterRepository.findBySystemKeyIn(systemKeys).stream()
                .filter(s -> norm(s.getSubSystemCode()) != null && norm(s.getSubSystemName()) != null)
                .sorted(java.util.Comparator.comparing(SubSystemMaster::getSubSystemName,
                        java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(s -> Map.of(
                        "systemKey", s.getSystemKey() == null ? "" : s.getSystemKey(),
                        "subSystemCode", s.getSubSystemCode() == null ? "" : s.getSubSystemCode(),
                        "subSystemName", s.getSubSystemName() == null ? "" : s.getSubSystemName()))
                .toList();
    }

    /** client_of_sds when set, otherwise project_key (null when neither). */
    private static String projectOf(SystemMaster s) {
        String project = norm(s.getClientOfSds());
        return project != null ? project : norm(s.getProjectKey());
    }

    /**
     * The project keys a PROJECT-scope skill may be shared with: the
     * projects (client_of_sds when set, otherwise project_key) of every
     * non-End-phase system, EXCLUDING the projects the creator already
     * belongs to (sharing with your own project is a no-op — those users
     * already see the skill). Values are lower-cased/trimmed,
     * deduplicated, sorted.
     */
    @Transactional(readOnly = true)
    public List<String> shareableProjectKeys(Skill skill) {
        Long creatorId = skill.getCreatedBy();
        Set<String> creatorProjects = creatorProjects(creatorId);
        return systemMasterRepository.findAll().stream()
                .filter(s -> !isEndPhase(s))
                .map(SkillAccessHelper::projectOf)
                .filter(p -> p != null && !creatorProjects.contains(p))
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * The projects the given user belongs to: the project of every
     * non-End-phase system they are PIC of (jan..current month &gt; 0).
     */
    public Set<String> creatorProjects(Long userId) {
        String knoxId = userRepository.findById(userId)
                .map(User::getKnoxId)
                .orElse(null);
        if (knoxId == null) {
            return Set.of();
        }
        Map<String, Set<String>> picByKnox = picSystemsByKnox();
        Set<String> picKeys = picByKnox.getOrDefault(norm(knoxId), Set.of());
        if (picKeys.isEmpty()) {
            return Set.of();
        }
        return systemMasterRepository.findBySystemKeyIn(picKeys).stream()
                .filter(s -> !isEndPhase(s))
                .map(SkillAccessHelper::projectOf)
                .filter(p -> p != null)
                .collect(Collectors.toSet());
    }

    /** True when the system's current phase is "End" (case-insensitive). */
    private static boolean isEndPhase(SystemMaster s) {
        return s.getCurrentPhase() != null
                && "end".equalsIgnoreCase(s.getCurrentPhase().trim());
    }

    private Set<String> groupsWhere(Map<String, String> groupCenter, Map<String, String> groupDivision,
                                    java.util.function.Predicate<String> centerOk,
                                    java.util.function.Predicate<String> divisionOk) {
        return groupCenter.keySet().stream()
                .filter(g -> centerOk.test(groupCenter.get(g)))
                .filter(g -> divisionOk.test(groupDivision.getOrDefault(g, "")))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> partsUnder(Set<String> groups, Map<String, String> groupCenter,
                                   Map<String, String> groupDivision) {
        Set<String> out = new LinkedHashSet<>();
        for (Object[] p : organizationRepository.findGroupPartWhereEmployedY()) {
            String g = norm((String) p[0]);
            if (g != null && groups.contains(g)) {
                String pt = norm((String) p[1]);
                if (pt != null) {
                    out.add(pt);
                }
            }
        }
        return out;
    }

    private Map<String, String> pairMap(java.util.List<Object[]> pairs, int valueIdx) {
        return pairs.stream()
                .filter(p -> norm((String) p[0]) != null && norm((String) p[valueIdx]) != null)
                .collect(Collectors.toMap(
                        p -> norm((String) p[0]),
                        p -> norm((String) p[valueIdx]),
                        (a, b) -> a));
    }

    /**
     * For every person, the set of systems they are PIC of so far this
     * year: any month column from jan up to the current month &gt; 0
     * (e.g. in October → jan..oct). The set of columns depends on the
     * current month, so the check is done in Java over the 12 month
     * columns. Keys are lower-cased.
     */
    private Map<String, Set<String>> picSystemsByKnox() {
        int currentMonth = YearMonth.now().getMonthValue();
        Map<String, Set<String>> byKnox = new java.util.HashMap<>();
        for (Object[] row : hrAssignmentRepository.findAllKnoxSystemMonths()) {
            // row: knox_id, system_key, jan, feb, ..., dec
            String knox = norm((String) row[0]);
            String sysKey = (String) row[1];
            if (knox == null || sysKey == null) {
                continue;
            }
            for (int m = 1; m <= currentMonth; m++) {
                Object v = row[2 + (m - 1)]; // month columns: jan=2 ... dec=13
                if (v instanceof java.math.BigDecimal bd && bd.signum() > 0) {
                    byKnox.computeIfAbsent(knox, k -> new LinkedHashSet<>()).add(sysKey);
                    break;
                }
            }
        }
        return byKnox;
    }

    private static String norm(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim().toLowerCase(Locale.ROOT);
        return t.isEmpty() ? null : t;
    }

    /**
     * Viewer org facts for the scope rule (all values lower-cased/trimmed).
     *
     * @param part          the viewer's own part, or null
     * @param group         the viewer's own group, or null
     * @param center        the viewer's center, or null
     * @param division      the viewer's division, or null
     * @param visibleParts  parts the viewer can see (own part + managed parts)
     * @param visibleGroups groups the viewer can see (own group + managed groups)
     * @param manager       true when the viewer's org row stops at a level
     *                      (manages everything under that level)
     * @param projects      projects the viewer is PIC of (jan..current month)
     * @param projectPeerCreatorIds user ids of people sharing a project
     *                      with the viewer (PROJECT scope peers)
     */
    public record OrgVisibility(
            String part,
            String group,
            String center,
            String division,
            Set<String> visibleParts,
            Set<String> visibleGroups,
            boolean manager,
            Set<String> projects,
            Set<Long> projectPeerCreatorIds
    ) {
        public static OrgVisibility empty() {
            return new OrgVisibility(null, null, null, null, Set.of(), Set.of(),
                    false, Set.of(), Set.of());
        }
    }
}
