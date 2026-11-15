package com.furelise.skillmanagement.home.service.impl;

import com.furelise.skillmanagement.auth.repository.LoginRepository;
import com.furelise.skillmanagement.feedback.model.Feedback;
import com.furelise.skillmanagement.feedback.repository.FeedbackRepository;
import com.furelise.skillmanagement.home.dto.ChartDimensions;
import com.furelise.skillmanagement.home.dto.ChartStats;
import com.furelise.skillmanagement.home.dto.CommunitySkillItem;
import com.furelise.skillmanagement.home.dto.CommunitySkills;
import com.furelise.skillmanagement.home.dto.HomeSkillItem;
import com.furelise.skillmanagement.home.dto.HomeSummary;
import com.furelise.skillmanagement.home.dto.NewRequestItem;
import com.furelise.skillmanagement.home.service.HomeService;
import com.furelise.skillmanagement.org.model.OrganizationMaster;
import com.furelise.skillmanagement.org.repository.OrganizationRepository;
import com.furelise.skillmanagement.skill.model.ProcessStage;
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.skill.model.SkillCategory;
import com.furelise.skillmanagement.skill.model.SkillCategoryMapping;
import com.furelise.skillmanagement.skill.model.SkillDownloadLog;
import com.furelise.skillmanagement.skill.model.SkillViewLog;
import com.furelise.skillmanagement.skill.repository.ProcessStageRepository;
import com.furelise.skillmanagement.skill.repository.SkillCategoryMappingRepository;
import com.furelise.skillmanagement.skill.repository.SkillCategoryRepository;
import com.furelise.skillmanagement.skill.repository.SkillDownloadLogRepository;
import com.furelise.skillmanagement.skill.repository.SkillRatingRepository;
import com.furelise.skillmanagement.skill.repository.SkillRepository;
import com.furelise.skillmanagement.skill.repository.SkillStageMappingRepository;
import com.furelise.skillmanagement.skill.repository.SkillSubscriptionRepository;
import com.furelise.skillmanagement.skill.repository.SkillViewLogRepository;
import com.furelise.skillmanagement.skill.service.SkillAccessHelper;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Home screen data (Home SRS §7) backed by the Skill Management tables.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeServiceImpl implements HomeService {

    private static final int HOT_LIMIT = 5;
    private static final int TRENDING_LIMIT = 5;
    private static final int NEW_LIMIT = 5;
    private static final Duration TRENDING_WINDOW = Duration.ofDays(14);

    private final SkillRepository skillRepository;
    private final SkillRatingRepository skillRatingRepository;
    private final SkillSubscriptionRepository skillSubscriptionRepository;
    private final SkillViewLogRepository skillViewLogRepository;
    private final SkillDownloadLogRepository skillDownloadLogRepository;
    private final SkillCategoryRepository skillCategoryRepository;
    private final SkillCategoryMappingRepository skillCategoryMappingRepository;
    private final ProcessStageRepository processStageRepository;
    private final SkillStageMappingRepository skillStageMappingRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final FeedbackRepository feedbackRepository;
    private final LoginRepository loginRepository;
    private final SkillAccessHelper skillAccessHelper;

    @Override
    public HomeSummary summary(User viewer, boolean isAdmin) {
        Long userId = viewer.getId();
        List<Skill> mySkills =
                skillRepository.findByCreatedByAndDeletedYnOrderByCreatedAtDesc(userId, "N");
        int mySkillsCount = mySkills.size();
        int myPublished = (int) skillRepository
                .countByCreatedByAndDeletedYnAndApprovalStatus(userId, "N", Skill.STATUS_APPROVED);
        int myPendingApproval = (int) skillRepository
                .countByCreatedByAndDeletedYnAndApprovalStatus(userId, "N", Skill.STATUS_PENDING_APPROVAL);
        int myDraft = (int) skillRepository
                .countByCreatedByAndDeletedYnAndApprovalStatus(userId, "N", Skill.STATUS_DRAFT);
        long myTotalViews = skillRepository.sumViewCountByCreatedBy(userId);

        // SRS BR-HOME-006: average of the average rating of my skills that have
        // >=1 rating (each skill's own average counts all raters, including me).
        Double myAverageRating = null;
        long myRatedSkillCount = 0;
        if (!mySkills.isEmpty()) {
            List<Long> mySkillIds = mySkills.stream().map(Skill::getId).toList();
            // Per-skill averages come from a single GROUP BY (avg per skill),
            // not from loading every rating row into the JVM.
            List<Double> skillAverages = skillRatingRepository
                    .ratingAggBySkillIdIn(mySkillIds).stream()
                    .map(row -> ((Number) row[1]).doubleValue())
                    .toList();
            myRatedSkillCount = skillAverages.size();
            if (!skillAverages.isEmpty()) {
                double avg = skillAverages.stream().mapToDouble(Double::doubleValue)
                        .average().orElse(0.0);
                myAverageRating = BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP).doubleValue();
            }
        }
        int subscribedSkills = (int) skillSubscriptionRepository.countByUserId(userId);
        Integer pendingReviews = isAdmin
                ? (int) skillRepository.countByApprovalStatusAndDeletedYn(
                        Skill.STATUS_PENDING_APPROVAL, "N")
                : null;
        // Feature Request module is not yet specified — open-request counters
        // are 0 until the module ships (Home SRS §7.1: admin-only fields).
        int myOpenRequests = 0;
        Integer pendingRequestReviews = isAdmin ? 0 : null;

        return new HomeSummary(
                mySkillsCount, myPublished, myPendingApproval, myDraft,
                (int) myTotalViews, myAverageRating,
                (int) myRatedSkillCount, subscribedSkills, myOpenRequests,
                pendingReviews, pendingRequestReviews);
    }

    @Override
    public List<HomeSkillItem> myRecentSkills(User viewer, int limit) {
        List<Skill> skills = skillRepository
                .findByCreatedByAndDeletedYnOrderByCreatedAtDesc(viewer.getId(), "N")
                .stream().limit(limit).toList();
        return toItems(skills);
    }

    @Override
    public CommunitySkills communitySkills(User viewer) {
        SkillAccessHelper.OrgVisibility viewerOrg = skillAccessHelper.visibility(viewer);
        Specification<Skill> visibleSpec = SkillRepository.visibleTo(viewer, viewerOrg);
        Instant trendingSince = Instant.now().minus(TRENDING_WINDOW);

        // BR-HOME-010: 5 visible APPROVED skills, view_count DESC, ties by
        // skill_id (user request 2026-10-21: community cards = approved only).
        Specification<Skill> approvedSpec = visibleSpec.and(
                (root, query, cb) -> cb.equal(root.get("approvalStatus"), Skill.STATUS_APPROVED));
        List<Skill> hotSkills = skillRepository.findAll(approvedSpec,
                PageRequest.of(0, HOT_LIMIT,
                        Sort.by(Sort.Order.desc("viewCount"),
                                Sort.Order.asc("id")))).getContent();

        // BR-HOME-011: 5 visible APPROVED skills updated within the last 14
        // days, view_count DESC, ties by skill_id.
        Specification<Skill> trendingSpec = approvedSpec.and(
                (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("updatedAt"), trendingSince));
        List<Skill> trendingSkills = skillRepository.findAll(trendingSpec,
                PageRequest.of(0, TRENDING_LIMIT,
                        Sort.by(Sort.Order.desc("viewCount"),
                                Sort.Order.asc("id")))).getContent();

        // BR-HOME-012: 5 visible APPROVED skills by created_at DESC, ties
        // by skill_id (user request 2026-10-20: New Skills = approved only,
        // top 5 per card).
        List<Skill> newSkills = skillRepository.findAll(approvedSpec,
                PageRequest.of(0, NEW_LIMIT,
                        Sort.by(Sort.Order.desc("createdAt"),
                                Sort.Order.asc("id")))).getContent();

        // Load ratings/creators/categories/stages only for the ≤20 skills we
        // actually show.
        List<Skill> shown = Stream.of(hotSkills, trendingSkills, newSkills)
                .flatMap(List::stream)
                .distinct()
                .toList();
        Map<Long, RatingAgg> ratings = loadRatings(shown);
        Map<Long, User> creators = loadCreators(shown);
        Map<Long, List<String>> categoriesBySkill = loadCategoryNames(shown);
        Map<Long, List<String>> stagesBySkill = loadStageNames(shown);

        List<CommunitySkillItem> hot = hotSkills.stream()
                .map(s -> toCommunityItem(s, ratings, creators, categoriesBySkill, stagesBySkill)).toList();
        List<CommunitySkillItem> trending = trendingSkills.stream()
                .map(s -> toCommunityItem(s, ratings, creators, categoriesBySkill, stagesBySkill)).toList();
        List<CommunitySkillItem> newItems = newSkills.stream()
                .map(s -> toCommunityItem(s, ratings, creators, categoriesBySkill, stagesBySkill)).toList();

        return new CommunitySkills(hot, trending, newItems);
    }

    @Override
    public List<NewRequestItem> newRequests(int limit) {
        // Home SRS v1.25 §7.3: 8 most recent Feedback (VOC) items — all
        // types, all users, created_at DESC, ties by feedback_id. The
        // screenshot column is never selected (multi-MB base64 — the
        // detail call fetches it separately).
        List<Feedback> feedbacks = feedbackRepository.findAllBy(
                PageRequest.of(0, limit,
                        Sort.by(Sort.Order.desc("createdAt"),
                                Sort.Order.asc("id")))).getContent();
        if (feedbacks.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = loadUsers(feedbacks);
        return feedbacks.stream()
                .map(f -> new NewRequestItem(
                        f.getId(),
                        f.getContent(),
                        f.getType(),
                        f.getStatus(),
                        users.get(f.getUserId()) != null ? users.get(f.getUserId()).getName() : null,
                        f.getCreatedAt()))
                .toList();
    }

    // ── Chart tab (Home_Chart_SRS) ──────────────────────────────────────────

    @Override
    public ChartDimensions chartDimensions() {
        // Groups: every non-blank group that has an employed='Y' row (a group
        // whose rows are all part-blank still shows up with an empty part set).
        Set<String> groups = new TreeSet<>();
        for (String g : organizationRepository.findDistinctGroupNameWhereEmployedY()) {
            if (g != null && !g.isBlank()) {
                groups.add(g.trim());
            }
        }
        Map<String, Set<String>> partsByGroup = new TreeMap<>();
        for (String g : groups) {
            partsByGroup.put(g, new LinkedHashSet<>());
        }
        for (Object[] gp : organizationRepository.findGroupPartWhereEmployedY()) {
            String g = (String) gp[0];
            String p = (String) gp[1];
            if (g == null || g.isBlank() || p == null || p.isBlank()) {
                continue;
            }
            Set<String> parts = partsByGroup.get(g.trim());
            if (parts != null) {
                parts.add(p.trim());
            }
        }
        Map<String, List<String>> ordered = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> e : partsByGroup.entrySet()) {
            ordered.put(e.getKey(), e.getValue().stream().sorted().toList());
        }
        return new ChartDimensions(ordered.keySet().stream().toList(), ordered);
    }

    @Override
    public ChartStats chartStats(String period, String dimension, String group) {
        boolean byPart = "part".equalsIgnoreCase(dimension);
        boolean byGroup = "group".equalsIgnoreCase(dimension);
        if (!byPart && !byGroup) {
            throw new IllegalArgumentException("dimension must be 'part' or 'group'");
        }
        Duration window;
        switch (period == null ? "" : period.toLowerCase(Locale.ROOT)) {
            case "1m" -> window = Duration.ofDays(30);
            case "3m" -> window = Duration.ofDays(90);
            case "6m" -> window = Duration.ofDays(180);
            case "1y" -> window = Duration.ofDays(365);
            default -> throw new IllegalArgumentException("period must be '1m', '3m', '6m' or '1y'");
        }
        String groupName = group == null ? "" : group.trim();
        if (byPart && groupName.isEmpty()) {
            throw new IllegalArgumentException("group is required when dimension is 'part'");
        }
        Instant end = Instant.now();
        Instant start = end.minus(window);
        return doChartStats(period, dimension, group, start, end);
    }

    /**
     * SQL-scoped chart stats: loads only the org rows / users / skills that can
     * form a bucket (no full-table findAll).
     */
    ChartStats doChartStats(String period, String dimension, String group,
                            Instant start, Instant end) {
        boolean byPart = "part".equalsIgnoreCase(dimension);
        String groupName = group == null ? "" : group.trim();

        // 1. Bucket users by org (employed='Y' only), via knox_id.
        Map<String, Set<Long>> usersByBucket = buildUsersByBucket(byPart, groupName);

        // 2. Skills approved within the window (approval refreshes updated_at).
        List<Skill> approvedSkills = skillRepository
                .findByApprovalStatusAndDeletedYnAndUpdatedAtBetween(
                        Skill.STATUS_APPROVED, "N", start, end);
        Map<Long, List<Skill>> approvedByCreator = approvedSkills.stream()
                .collect(Collectors.groupingBy(Skill::getCreatedBy));

        // Per-skill rating aggregates for the approved-in-window skills only.
        List<Long> approvedIds = approvedSkills.stream().map(Skill::getId).toList();
        Map<Long, Double> ratingAvgBySkill = new LinkedHashMap<>();
        if (!approvedIds.isEmpty()) {
            for (Object[] row : skillRatingRepository.ratingAggBySkillIdIn(approvedIds)) {
                ratingAvgBySkill.put((Long) row[0], ((Number) row[1]).doubleValue());
            }
        }

        // 3. Views + downloads within the window for all not-deleted skills
        //    of the bucket users.
        Set<Long> allBucketUserIds = new HashSet<>();
        usersByBucket.values().forEach(allBucketUserIds::addAll);
        Map<Long, Long> viewsByCreator = new LinkedHashMap<>();
        Map<Long, Long> downloadsByCreator = new LinkedHashMap<>();
        if (!allBucketUserIds.isEmpty()) {
            List<Skill> skills = skillRepository.findByDeletedYnAndCreatedByIn("N", allBucketUserIds);
            Map<Long, List<Skill>> skillsByCreator = skills.stream()
                    .collect(Collectors.groupingBy(Skill::getCreatedBy));
            List<Long> skillIds = skills.stream().map(Skill::getId).toList();
            if (!skillIds.isEmpty()) {
                Map<Long, Long> viewsPerSkill = skillViewLogRepository
                        .findBySkillIdInAndViewedAtBetween(skillIds, start, end).stream()
                        .collect(Collectors.groupingBy(
                                SkillViewLog::getSkillId,
                                Collectors.counting()));
                // Chart metric: "Download All" events only; the skill
                // creator's own downloads are excluded per skill below.
                Map<Long, List<Long>> downloadUsersBySkill = skillDownloadLogRepository
                        .findBySkillIdInAndDownloadedAtBetweenAndDownloadAllTrue(
                                skillIds, start, end).stream()
                        .collect(Collectors.groupingBy(
                                SkillDownloadLog::getSkillId,
                                Collectors.mapping(
                                        SkillDownloadLog::getUserId,
                                        Collectors.toList())));
                for (Map.Entry<Long, List<Skill>> e : skillsByCreator.entrySet()) {
                    long views = e.getValue().stream()
                            .mapToLong(s -> viewsPerSkill.getOrDefault(s.getId(), 0L))
                            .sum();
                    long downloads = e.getValue().stream()
                            .mapToLong(s -> downloadUsersBySkill
                                    .getOrDefault(s.getId(), List.of()).stream()
                                    .filter(uid -> !uid.equals(s.getCreatedBy()))
                                    .count())
                            .sum();
                    viewsByCreator.put(e.getKey(), views);
                    downloadsByCreator.put(e.getKey(), downloads);
                }
            }
        }

        // 4. Active users (>=1 SUCCESS login in the window) + new users.
        Set<Long> activeUserIds = new HashSet<>(
                loginRepository.findDistinctActiveUserIdsBetween(start, end));
        Set<Long> newUserIds = new HashSet<>(userRepository
                .findByCreatedAtBetween(start, end).stream().map(User::getId).toList());

        // 5. Assemble one row per bucket (ordered A→Z).
        List<ChartStats.Entity> entities = new ArrayList<>();
        for (Map.Entry<String, Set<Long>> e : usersByBucket.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            Set<Long> userIds = e.getValue();

            List<Skill> approved = userIds.stream()
                    .flatMap(id -> approvedByCreator.getOrDefault(id, List.of()).stream())
                    .toList();
            List<Double> avgs = approved.stream()
                    .map(s -> ratingAvgBySkill.get(s.getId()))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            Double avg = null;
            if (!avgs.isEmpty()) {
                avg = BigDecimal.valueOf(avgs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0))
                        .setScale(1, RoundingMode.HALF_UP).doubleValue();
            }
            long views = userIds.stream()
                    .mapToLong(id -> viewsByCreator.getOrDefault(id, 0L))
                    .sum();
            long downloads = userIds.stream()
                    .mapToLong(id -> downloadsByCreator.getOrDefault(id, 0L))
                    .sum();
            int active = (int) userIds.stream().filter(activeUserIds::contains).count();
            int fresh = (int) userIds.stream().filter(newUserIds::contains).count();

            entities.add(new ChartStats.Entity(e.getKey(), approved.size(), avg, views,
                    downloads, active, fresh));
        }
        entities.sort(Comparator.comparing(ChartStats.Entity::name));

        return new ChartStats(period.toLowerCase(Locale.ROOT),
                byPart ? "part" : "group", byPart ? groupName : null, entities);
    }

    /** Builds bucket → user-ids from org rows + users, loading only what can form a bucket. */
    private Map<String, Set<Long>> buildUsersByBucket(boolean byPart, String groupName) {
        List<String> knoxIds = organizationRepository.findDistinctKnoxIdWhereEmployedY();
        Map<String, List<User>> usersByKnox = new LinkedHashMap<>();
        if (!knoxIds.isEmpty()) {
            for (User u : userRepository.findByKnoxIdIgnoreCaseIn(knoxIds)) {
                if (u.getKnoxId() != null) {
                    usersByKnox.computeIfAbsent(u.getKnoxId().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(u);
                }
            }
        }
        List<OrganizationMaster> orgRows = byPart
                ? organizationRepository.findByEmployedAndGroupNameTrimmedIgnoreCase("Y", groupName)
                : organizationRepository.findByEmployed("Y");
        Map<String, Set<Long>> usersByBucket = new LinkedHashMap<>();
        for (OrganizationMaster o : orgRows) {
            String raw = byPart ? o.getPart() : o.getGroupName();
            if (raw == null || raw.isBlank()) {
                continue;
            }
            List<User> users = usersByKnox.get(o.getKnoxId() == null
                    ? "" : o.getKnoxId().toLowerCase(Locale.ROOT));
            if (users == null) {
                continue;
            }
            for (User u : users) {
                usersByBucket.computeIfAbsent(raw.trim(), k -> new LinkedHashSet<>()).add(u.getId());
            }
        }
        return usersByBucket;
    }

    // ── community card helpers ──────────────────────────────────────────────

    private CommunitySkillItem toCommunityItem(Skill s,
                                                Map<Long, RatingAgg> ratings,
                                                Map<Long, User> creators,
                                                Map<Long, List<String>> categoriesBySkill,
                                                Map<Long, List<String>> stagesBySkill) {
        RatingAgg agg = ratings.get(s.getId());
        User creator = creators.get(s.getCreatedBy());
        return new CommunitySkillItem(
                s.getId(),
                s.getName(),
                creator != null ? creator.getName() : null,
                s.getApprovalStatus(),
                s.getCreatedAt(),
                s.getViewCount(),
                agg != null ? agg.average() : null,
                agg != null ? (int) agg.count() : 0,
                categoriesBySkill.getOrDefault(s.getId(), List.of()),
                stagesBySkill.getOrDefault(s.getId(), List.of()));
    }

    /** Category names per skill id (all requested ids). */
    private Map<Long, List<String>> loadCategoryNames(List<Skill> skills) {
        List<Long> ids = skills.stream().map(Skill::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, SkillCategory> byId = skillCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(SkillCategory::getId, c -> c));
        return skillCategoryMappingRepository.findBySkillIdIn(ids).stream()
                .collect(Collectors.groupingBy(
                        SkillCategoryMapping::getSkillId,
                        Collectors.mapping(
                                m -> byId.get(m.getCategoryId()) != null
                                        ? byId.get(m.getCategoryId()).getName() : null,
                                Collectors.collectingAndThen(
                                        Collectors.toList(),
                                        list -> list.stream().filter(Objects::nonNull).toList()))));
    }

    /** Stage names per skill id, in pipeline order (all requested ids). */
    private Map<Long, List<String>> loadStageNames(List<Skill> skills) {
        List<Long> ids = skills.stream().map(Skill::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProcessStage> byId = processStageRepository.findAll().stream()
                .collect(Collectors.toMap(ProcessStage::getId, s -> s));
        Map<Long, List<Long>> grouped = SkillStageMappingRepository.groupBySkill(
                skillStageMappingRepository.findBySkillIdIn(ids));
        Map<Long, List<String>> result = new LinkedHashMap<>();
        grouped.forEach((skillId, stageIds) -> result.put(skillId,
                stageIds.stream()
                        .map(byId::get)
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(ProcessStage::getSortOrder))
                        .map(ProcessStage::getName)
                        .toList()));
        return result;
    }

    private Map<Long, User> loadCreators(List<Skill> skills) {
        Set<Long> ids = skills.stream().map(Skill::getCreatedBy).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private Map<Long, User> loadUsers(List<Feedback> feedbacks) {
        Set<Long> ids = feedbacks.stream().map(Feedback::getUserId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private Map<Long, RatingAgg> loadRatings(List<Skill> skills) {
        List<Long> ids = skills.stream().map(Skill::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return skillRatingRepository.ratingAggBySkillIdIn(ids).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> new RatingAgg(((Number) row[1]).doubleValue(), ((Number) row[2]).longValue())));
    }

    private record RatingAgg(double average, long count) {
    }

    /** Maps skills to HomeSkillItem rows, filling rating aggregates and creator names. */
    private List<HomeSkillItem> toItems(List<Skill> skills) {
        if (skills.isEmpty()) {
            return List.of();
        }
        Map<Long, RatingAgg> ratings = loadRatings(skills);
        Set<Long> creatorIds = skills.stream().map(Skill::getCreatedBy).collect(Collectors.toSet());
        Map<Long, User> creatorMap = userRepository.findAllById(creatorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return skills.stream().map(s -> {
            RatingAgg agg = ratings.get(s.getId());
            Double ratingScore = null;
            if (agg != null) {
                ratingScore = BigDecimal.valueOf(agg.average()).setScale(1, RoundingMode.HALF_UP).doubleValue();
            }
            User creator = creatorMap.get(s.getCreatedBy());
            return new HomeSkillItem(
                    String.valueOf(s.getId()),
                    s.getName(),
                    s.getApprovalStatus(),
                    creator != null ? creator.getName() : null,
                    s.getUpdatedAt(),
                    s.getViewCount(),
                    ratingScore,
                    agg != null ? (int) agg.count() : 0);
        }).toList();
    }
}
