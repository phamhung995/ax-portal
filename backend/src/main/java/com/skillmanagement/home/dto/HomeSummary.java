package com.skillmanagement.home.dto;

/**
 * GET /api/v1/home/summary payload (Home SRS §7.1).
 * pendingReviews + pendingRequestReviews are only present for ADMIN_USER.
 * myOpenRequests = count of own open Feature Requests (informational —
 * no KPI card; the Feature Request module is not yet specified, so 0).
 */
public record HomeSummary(
        int mySkills,
        int myPublished,
        int myPendingApproval,
        int myDraft,
        int myTotalViews,
        Double myAverageRating,
        int myRatedSkillCount,
        int subscribedSkills,
        int myOpenRequests,
        Integer pendingReviews,
        Integer pendingRequestReviews
) {
}
