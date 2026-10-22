package com.skillmanagement.home.service.impl;

import com.skillmanagement.home.dto.CommunitySkillItem;
import com.skillmanagement.home.dto.CommunitySkills;
import com.skillmanagement.home.dto.HomeActivity;
import com.skillmanagement.home.dto.HomeSkillItem;
import com.skillmanagement.home.dto.HomeSummary;
import com.skillmanagement.home.dto.NewRequestItem;
import com.skillmanagement.home.service.HomeService;
import com.skillmanagement.org.model.OrganizationMaster;
import com.skillmanagement.org.repository.OrganizationRepository;
import com.skillmanagement.skill.model.Skill;
import com.skillmanagement.skill.repository.SkillRatingRepository;
import com.skillmanagement.skill.repository.SkillRepository;
import com.skillmanagement.skill.repository.SkillSubscriptionRepository;
import com.skillmanagement.skill.repository.SkillViewLogRepository;
import com.skillmanagement.user.model.User;
import com.skillmanagement.user.repository.UserRepository;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private static final int NEW_LIMIT = 10;
    private static final Duration TRENDING_WINDOW = Duration.ofDays(14);

    private final SkillRepository skillRepository;
    private final SkillRatingRepository skillRatingRepository;
    private final SkillSubscriptionRepository skillSubscriptionRepository;
    private final SkillViewLogRepository skillViewLogRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;

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
        Set<String> parts = viewerOrgParts(viewer);
        Set<String> groups = viewerOrgGroups(viewer);
        Specification<Skill> visibleSpec = SkillRepository.visibleTo(viewer, parts, groups);
        Instant trendingSince = Instant.now().minus(TRENDING_WINDOW);

        // BR-HOME-010: 5 visible skills, view_count DESC, ties by skill_id.
        List<Skill> hotSkills = skillRepository.findAll(visibleSpec,
                PageRequest.of(0, HOT_LIMIT,
                        Sort.by(Sort.Order.desc("viewCount"),
                                Sort.Order.asc("id")))).getContent();

        // BR-HOME-011: 5 visible skills updated within the last 14 days,
        // view_count DESC, ties by skill_id.
        Specification<Skill> trendingSpec = visibleSpec.and(
                (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("updatedAt"), trendingSince));
        List<Skill> trendingSkills = skillRepository.findAll(trendingSpec,
                PageRequest.of(0, TRENDING_LIMIT,
                        Sort.by(Sort.Order.desc("viewCount"),
                                Sort.Order.asc("id")))).getContent();

        // BR-HOME-012: 10 visible skills by created_at DESC, ties by skill_id.
        List<Skill> newSkills = skillRepository.findAll(visibleSpec,
                PageRequest.of(0, NEW_LIMIT,
                        Sort.by(Sort.Order.desc("createdAt"),
                                Sort.Order.asc("id")))).getContent();

        // Load ratings/creators only for the ≤20 skills we actually show.
        List<Skill> shown = Stream.of(hotSkills, trendingSkills, newSkills)
                .flatMap(List::stream)
                .distinct()
                .toList();
        Map<Long, RatingAgg> ratings = loadRatings(shown);
        Map<Long, User> creators = loadCreators(shown);

        List<CommunitySkillItem> hot = hotSkills.stream()
                .map(s -> toCommunityItem(s, ratings, creators)).toList();
        List<CommunitySkillItem> trending = trendingSkills.stream()
                .map(s -> toCommunityItem(s, ratings, creators)).toList();
        List<CommunitySkillItem> newItems = newSkills.stream()
                .map(s -> toCommunityItem(s, ratings, creators)).toList();

        return new CommunitySkills(hot, trending, newItems);
    }

    @Override
    public List<NewRequestItem> newRequests(int limit) {
        // Feature Request module is not yet specified (no SRS in repo) —
        // the New Request card renders its empty state until it ships.
        return List.of();
    }

    @Override
    public HomeActivity activity(User viewer, int weeks) {
        int cappedWeeks = Math.max(1, Math.min(weeks, 52));
        int days = cappedWeeks * 7;
        int[] weeklyViews = new int[cappedWeeks];
        int[] weeklyUpdates = new int[cappedWeeks];
        int[] dailyViews = new int[days];

        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.LocalDate today = java.time.LocalDate.now(zone);
        java.time.LocalDate startDate = today.minusDays(days - 1);
        Instant since = startDate.atStartOfDay(zone).toInstant();
        String tz = zone.getId();

        // Views: daily counts from skill_view_log for the viewer's own skills
        // (grouped in SQL — no raw timestamps loaded into the JVM).
        List<Long> mySkillIds = skillRepository
                .findByCreatedByAndDeletedYnOrderByCreatedAtDesc(viewer.getId(), "N")
                .stream().map(Skill::getId).toList();
        if (!mySkillIds.isEmpty()) {
            for (Object[] row : skillViewLogRepository.countViewsByDaySince(mySkillIds, since, tz)) {
                java.time.LocalDate d = (java.time.LocalDate) row[0];
                long count = ((Number) row[1]).longValue();
                long idx = java.time.temporal.ChronoUnit.DAYS.between(startDate, d);
                if (idx >= 0 && idx < days) {
                    dailyViews[(int) idx] += (int) count;
                    int w = (int) (idx / 7);
                    if (w < cappedWeeks) weeklyViews[w] += (int) count;
                }
            }
        }

        // Updates: daily counts from skills_master updated_at for the viewer's skills.
        for (Object[] row : skillRepository.countUpdatedByDaySince(viewer.getId(), since, tz)) {
            java.time.LocalDate d = (java.time.LocalDate) row[0];
            long count = ((Number) row[1]).longValue();
            long idx = java.time.temporal.ChronoUnit.DAYS.between(startDate, d);
            if (idx >= 0 && idx < days) {
                int w = (int) (idx / 7);
                if (w < cappedWeeks) weeklyUpdates[w] += (int) count;
            }
        }

        return new HomeActivity(cappedWeeks, weeklyViews, weeklyUpdates, dailyViews);
    }

    // ── community card helpers ──────────────────────────────────────────────

    private CommunitySkillItem toCommunityItem(Skill s,
                                                Map<Long, RatingAgg> ratings,
                                                Map<Long, User> creators) {
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
                agg != null ? (int) agg.count() : 0);
    }

    private Map<Long, User> loadCreators(List<Skill> skills) {
        Set<Long> ids = skills.stream().map(Skill::getCreatedBy).collect(Collectors.toSet());
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

    private Set<String> viewerOrgParts(User viewer) {
        return orgRows(viewer).stream()
                .map(o -> o.getPart().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private Set<String> viewerOrgGroups(User viewer) {
        return orgRows(viewer).stream()
                .map(o -> o.getGroupName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private List<OrganizationMaster> orgRows(User viewer) {
        return organizationRepository.findByKnoxIdIgnoreCase(viewer.getKnoxId()).stream()
                .filter(o -> "Y".equals(o.getEmployed()))
                .toList();
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
