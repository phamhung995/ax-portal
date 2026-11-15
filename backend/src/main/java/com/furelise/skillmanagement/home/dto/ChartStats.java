package com.furelise.skillmanagement.home.dto;

import java.util.List;

/**
 * Chart tab data (Home_Chart_SRS §6.2): one row per bucket (Group or Part)
 * with the 5 metrics computed over the selected look-back window.
 */
public record ChartStats(
        String period,
        String dimension,
        String group,
        List<Entity> entities) {

    /**
     * @param name           Group or Part name
     * @param approvedSkills Skills approved within the window by the bucket's users
     * @param avgRating      Average of the per-skill average ratings of those skills,
     *                       1 decimal; null when no such skill has a rating
     * @param totalViews     skill_view_log rows within the window for the bucket's skills
     * @param totalDownloads skill_download_log rows within the window for the bucket's skills
     * @param activeUsers    Distinct bucket users with >=1 SUCCESS login in the window
     * @param newUsers       Bucket users created within the window
     */
    public record Entity(
            String name,
            int approvedSkills,
            Double avgRating,
            long totalViews,
            long totalDownloads,
            int activeUsers,
            int newUsers) {
    }
}
