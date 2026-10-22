package com.skillmanagement.home.dto;

/**
 * GET /api/v1/home/activity payload — real activity data for the Home
 * chart tab (views + updates over the last N weeks).
 *
 * <p>All arrays are ordered oldest-first (index 0 = oldest week/day).
 * The frontend maps indices to display labels (W6, W5, … / calendar dates).
 *
 * @param weeks       number of weeks covered
 * @param weeklyViews  per-week view counts (length = weeks)
 * @param weeklyUpdates per-week skill-update counts (length = weeks)
 * @param dailyViews   per-day view counts (length = weeks × 7)
 */
public record HomeActivity(
        int weeks,
        int[] weeklyViews,
        int[] weeklyUpdates,
        int[] dailyViews
) {
}
