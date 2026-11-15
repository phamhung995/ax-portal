package com.furelise.skillmanagement.skill.repository;

import com.furelise.skillmanagement.org.model.OrganizationMaster;
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.skill.model.SkillCoworkUser;
import com.furelise.skillmanagement.skill.model.SkillShare;
import com.furelise.skillmanagement.user.model.User;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.time.Instant;

/**
 * Repository for skills_master.
 * Visibility rules (status + scope) are enforced via Specifications —
 * see {@link #visibleTo}.
 */
public interface SkillRepository extends JpaRepository<Skill, Long>, JpaSpecificationExecutor<Skill> {

    /** Case-insensitive name uniqueness check for one creator (active skills only). */
    boolean existsByNameIgnoreCaseAndDeletedYnAndCreatedBy(String name, String deletedYn, Long createdBy);

    /** Case-insensitive name check for one creator, excluding one skill (edit duplicate check, active only). */
    boolean existsByNameIgnoreCaseAndIdNotAndDeletedYnAndCreatedBy(String name, Long id, String deletedYn, Long createdBy);

    List<Skill> findByCreatedBy(Long createdBy);

    List<Skill> findByIdIn(Collection<Long> ids);

    /** My skills (all statuses, not deleted), newest first. */
    List<Skill> findByCreatedByAndDeletedYnOrderByCreatedAtDesc(Long createdBy, String deletedYn);

    /** Count of my skills in a given status (not deleted). */
    long countByCreatedByAndDeletedYnAndApprovalStatus(Long createdBy, String deletedYn, String approvalStatus);

    /** Sum of view counts of my not-deleted skills. */
    @Query("SELECT COALESCE(SUM(s.viewCount), 0) FROM Skill s WHERE s.createdBy = :createdBy AND s.deletedYn = 'N'")
    long sumViewCountByCreatedBy(@Param("createdBy") Long createdBy);

    /** Count of PENDING_APPROVAL skills (admin banner). */
    long countByApprovalStatusAndDeletedYn(String approvalStatus, String deletedYn);

    /**
     * Not-deleted APPROVED skills whose approval happened within a window
     * (chart "skill approved" metric — the approval action refreshes
     * updated_at, so updated_at marks the approval time).
     */
    List<Skill> findByApprovalStatusAndDeletedYnAndUpdatedAtBetween(
            String approvalStatus, String deletedYn, Instant start, Instant end);

    /** Not-deleted skills of a set of creators (chart views/downloads). */
    List<Skill> findByDeletedYnAndCreatedByIn(String deletedYn, Collection<Long> createdByIn);

    /**
     * Visibility Specification: a skill is visible to the viewer when
     * deleted_yn = 'N' AND the status rule passes AND the scope rule passes
     * (admin bypasses the scope rule).
     *
     * <p>Scope rule (non-admin), hierarchy Division &gt; Center &gt; Group &gt; Part:
     * <ul>
     *   <li>PRIVATE -> viewer is the creator</li>
     *   <li>PUBLIC  -> everyone</li>
     *   <li>PART    -> the creator's part (org row, employed='Y') is in the
     *                  viewer's visible parts (own part + parts under the
     *                  levels the viewer manages)</li>
     *   <li>GROUP   -> same for groups</li>
     * </ul>
     * The creator's org row is resolved via a subquery joining
     * organization_master to "user" on knox_id, restricted to
     * user.id = skill.created_by.
     *
     * @param viewer the logged-in user
     * @param v      the viewer's org visibility facts (see SkillAccessHelper)
     */
    static Specification<Skill> visibleTo(User viewer,
                                          com.furelise.skillmanagement.skill.service.SkillAccessHelper.OrgVisibility v) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("deletedYn"), "N"));

            boolean isAdmin = viewer.getRole() != null
                    && "ADMIN_USER".equals(viewer.getRole().getName());
            Long viewerId = viewer.getId();

            if (!isAdmin) {
                // --- Status rule: own skills all statuses; others' APPROVED
                //     only — plus skills with a pending VERSION UPDATE
                //     (pending_metadata set) or a pending SHARE/SCOPE change
                //     (pending_reason set), which still serve the current
                //     live state to everyone else. ---
                Predicate coworkOk = coworkViewer(root, query, cb, viewerId);
                predicates.add(cb.or(
                        cb.equal(root.get("createdBy"), viewerId),
                        cb.equal(root.get("approvalStatus"), Skill.STATUS_APPROVED),
                        coworkOk,
                        cb.and(
                                cb.equal(root.get("approvalStatus"), Skill.STATUS_PENDING_APPROVAL),
                                cb.isNotNull(root.get("pendingMetadata"))),
                        cb.and(
                                cb.equal(root.get("approvalStatus"), Skill.STATUS_PENDING_APPROVAL),
                                cb.isNotNull(root.get("pendingReason")))));

                // --- Scope rule ---
                Predicate privateOk = cb.and(
                        cb.equal(root.get("publicScope"), Skill.SCOPE_PRIVATE),
                        cb.equal(root.get("createdBy"), viewerId));
                Predicate publicOk = cb.equal(root.get("publicScope"), Skill.SCOPE_PUBLIC);
                Predicate partOk = cb.and(
                        cb.equal(root.get("publicScope"), Skill.SCOPE_PART),
                        orgOverlap(root, query, cb, "part", v.visibleParts()));
                Predicate groupOk = cb.and(
                        cb.equal(root.get("publicScope"), Skill.SCOPE_GROUP),
                        orgOverlap(root, query, cb, "groupName", v.visibleGroups()));
                // PROJECT: the creator's PIC projects (any month column
                // from jan up to the current month, computed in Java — see
                // SkillAccessHelper.picSystemsByKnox)
                // overlap the viewer's projects, OR the viewer is an org
                // manager of the creator (manager sees project skills of
                // their members).
                Predicate projectOk = cb.and(
                        cb.equal(root.get("publicScope"), Skill.SCOPE_PROJECT),
                        cb.or(
                                projectCreatorIds(root, cb, v.projectPeerCreatorIds()),
                                v.manager()
                                        ? orgOverlap(root, query, cb, "part", v.visibleParts())
                                        : cb.disjunction()));
                // Shared skills: an explicit USER share to the viewer, or a
                // PROJECT share to one of the viewer's projects. Shares only
                // exist on PROJECT-scope skills.
                Predicate shareOk = cb.or(
                        sharedUserViewer(root, query, cb, viewerId),
                        sharedProjectViewer(root, query, cb, v.projects()));
                predicates.add(cb.or(privateOk, publicOk, partOk, groupOk, projectOk, coworkOk, shareOk));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * EXISTS (SELECT 1 FROM skill_cowork_users c
     *         WHERE c.skill_id = skill.id AND c.user_id = viewer).
     * A cowork user sees the skill regardless of its scope/status, so they
     * can manage it (edit, submit, version update) like the creator.
     */
    static Predicate coworkViewer(Root<Skill> root,
                                  jakarta.persistence.criteria.CriteriaQuery<?> query,
                                  CriteriaBuilder cb,
                                  Long viewerId) {
        Subquery<Integer> sq = query.subquery(Integer.class);
        Root<SkillCoworkUser> cowork = sq.from(SkillCoworkUser.class);
        sq.select(cb.literal(1)).where(
                cb.equal(cowork.get("skillId"), root.get("id")),
                cb.equal(cowork.get("userId"), viewerId));
        return cb.exists(sq);
    }

    /**
     * Status filter matching the grid's <b>display</b> status, so the
     * filter always returns what the viewer sees on the badge
     * (Skill Management SRS §3, rule "display status").
     *
     * <p>Display status of a skill to {@code viewer}:
     * <ul>
     *   <li>viewer is admin, or viewer is the creator → the real
     *       {@code approval_status};</li>
     *   <li>otherwise → another user's skill with a pending version update
     *       ({@code PENDING_APPROVAL} + {@code pending_metadata}) still
     *       serves its approved version, so it displays as
     *       {@code APPROVED} — it matches the APPROVED filter and does NOT
     *       match the PENDING_APPROVAL filter.</li>
     * </ul>
     *
     * <p>Must stay in sync with the frontend {@code displayStatus} in
     * SkillManagementView.vue and with {@link #visibleTo} (whose status rule
     * defines which skills are visible at all).
     */
    static Specification<Skill> statusFilter(User viewer, Collection<String> statuses) {
        return (root, query, cb) -> {
            boolean isAdmin = viewer.getRole() != null
                    && "ADMIN_USER".equals(viewer.getRole().getName());
            if (isAdmin) {
                return root.get("approvalStatus").in(statuses);
            }
            Long viewerId = viewer.getId();
            // Own skills: always shown with the real status.
            Predicate own = cb.and(
                    cb.equal(root.get("createdBy"), viewerId),
                    root.get("approvalStatus").in(statuses));
            // Cowork skills: shown with the real status (the cowork user
            // manages the skill like the creator).
            Predicate cowork = cb.and(
                    coworkViewer(root, query, cb, viewerId),
                    root.get("approvalStatus").in(statuses));
            // Other skills: real status match — EXCEPT a pending version
            // update (PENDING_APPROVAL + pending_metadata) or a pending
            // SHARE/SCOPE change (PENDING_APPROVAL + pending_reason), which
            // display as APPROVED to non-admins and therefore must NOT match
            // the PENDING_APPROVAL filter (they are covered by the APPROVED
            // branch below).
            Predicate othersReal = cb.and(
                    cb.notEqual(root.get("createdBy"), viewerId),
                    root.get("approvalStatus").in(statuses),
                    cb.or(
                            cb.notEqual(root.get("approvalStatus"), Skill.STATUS_PENDING_APPROVAL),
                            cb.and(
                                    cb.isNull(root.get("pendingMetadata")),
                                    cb.isNull(root.get("pendingReason")))));
            List<Predicate> branches = new ArrayList<>(List.of(own, cowork, othersReal));
            // Other skills with a pending version update or a pending
            // share/scope change display as APPROVED.
            if (statuses.contains(Skill.STATUS_APPROVED)) {
                branches.add(cb.and(
                        cb.notEqual(root.get("createdBy"), viewerId),
                        cb.equal(root.get("approvalStatus"), Skill.STATUS_PENDING_APPROVAL),
                        cb.or(
                                cb.isNotNull(root.get("pendingMetadata")),
                                cb.isNotNull(root.get("pendingReason")))));
            }
            return cb.or(branches.toArray(new Predicate[0]));
        };
    }

    /**
     * EXISTS (SELECT 1 FROM organization_master o, "user" u
     *         WHERE u.id = skill.created_by
     *           AND u.knox_id = o.knox_id
     *           AND o.employed = 'Y'
     *           AND LOWER(o.{orgColumn}) IN (viewer values)).
     */
    static Predicate orgOverlap(Root<Skill> root,
                                jakarta.persistence.criteria.CriteriaQuery<?> query,
                                CriteriaBuilder cb,
                                String orgColumn,
                                Collection<String> viewerValues) {
        if (viewerValues == null || viewerValues.isEmpty()) {
            return cb.disjunction();
        }
        Subquery<Integer> sq = query.subquery(Integer.class);
        Root<OrganizationMaster> org = sq.from(OrganizationMaster.class);
        Root<User> creatorUser = sq.from(User.class);
        List<String> lowerValues =
                viewerValues.stream().map(v -> v.toLowerCase(Locale.ROOT)).toList();
        sq.select(cb.literal(1)).where(
                cb.equal(creatorUser.get("id"), root.get("createdBy")),
                cb.equal(creatorUser.get("knoxId"), org.get("knoxId")),
                cb.equal(org.get("employed"), "Y"),
                cb.lower(org.get(orgColumn)).in(lowerValues));
        return cb.exists(sq);
    }

    /**
     * skill.created_by IN (the given creator ids). Disjunction (never
     * matches) when the set is empty.
     */
    static Predicate projectCreatorIds(Root<Skill> root,
                                       CriteriaBuilder cb,
                                       Collection<Long> creatorIds) {
        if (creatorIds == null || creatorIds.isEmpty()) {
            return cb.disjunction();
        }
        return root.get("createdBy").in(creatorIds);
    }

    /**
     * EXISTS (SELECT 1 FROM skill_share sh
     *         WHERE sh.skill_id = skill.id
     *           AND sh.share_type = 'USER' AND sh.user_id = viewer
     *           AND sh.approval_status = 'APPROVED').
     * Only APPROVED shares grant visibility — PENDING/REJECTED shares do not.
     */
    static Predicate sharedUserViewer(Root<Skill> root,
                                      jakarta.persistence.criteria.CriteriaQuery<?> query,
                                      CriteriaBuilder cb,
                                      Long viewerId) {
        Subquery<Integer> sq = query.subquery(Integer.class);
        Root<SkillShare> share = sq.from(SkillShare.class);
        sq.select(cb.literal(1)).where(
                cb.equal(share.get("skillId"), root.get("id")),
                cb.equal(share.get("shareType"), SkillShare.TYPE_USER),
                cb.equal(share.get("userId"), viewerId),
                cb.equal(share.get("approvalStatus"), SkillShare.STATUS_APPROVED));
        return cb.exists(sq);
    }

    /**
     * EXISTS (SELECT 1 FROM skill_share sh
     *         WHERE sh.skill_id = skill.id
     *           AND sh.share_type = 'PROJECT'
     *           AND LOWER(sh.project_key) IN (viewer's projects)
     *           AND sh.approval_status = 'APPROVED').
     * Only APPROVED shares grant visibility. Disjunction (never matches)
     * when the viewer has no projects.
     */
    static Predicate sharedProjectViewer(Root<Skill> root,
                                         jakarta.persistence.criteria.CriteriaQuery<?> query,
                                         CriteriaBuilder cb,
                                         Collection<String> viewerProjects) {
        if (viewerProjects == null || viewerProjects.isEmpty()) {
            return cb.disjunction();
        }
        List<String> lowerProjects =
                viewerProjects.stream().map(p -> p.toLowerCase(Locale.ROOT)).toList();
        Subquery<Integer> sq = query.subquery(Integer.class);
        Root<SkillShare> share = sq.from(SkillShare.class);
        sq.select(cb.literal(1)).where(
                cb.equal(share.get("skillId"), root.get("id")),
                cb.equal(share.get("shareType"), SkillShare.TYPE_PROJECT),
                cb.lower(share.get("projectKey")).in(lowerProjects),
                cb.equal(share.get("approvalStatus"), SkillShare.STATUS_APPROVED));
        return cb.exists(sq);
    }
}
