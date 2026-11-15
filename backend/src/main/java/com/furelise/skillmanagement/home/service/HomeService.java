package com.furelise.skillmanagement.home.service;

import com.furelise.skillmanagement.home.dto.ChartDimensions;
import com.furelise.skillmanagement.home.dto.ChartStats;
import com.furelise.skillmanagement.home.dto.CommunitySkills;
import com.furelise.skillmanagement.home.dto.HomeSkillItem;
import com.furelise.skillmanagement.home.dto.HomeSummary;
import com.furelise.skillmanagement.home.dto.NewRequestItem;
import com.furelise.skillmanagement.user.model.User;

import java.util.List;

/**
 * Home screen data (Home SRS §7) — real queries over the Skill Management tables.
 */
public interface HomeService {

    HomeSummary summary(User viewer, boolean isAdmin);

    List<HomeSkillItem> myRecentSkills(User viewer, int limit);

    /** Community skill cards: hot (5) / trending (5) / new (5) — APPROVED only (Home SRS §7.2). */
    CommunitySkills communitySkills(User viewer);

    /**
     * 8 most recent Feedback (VOC) items — all types (BUG / SUGGESTION /
     * QUESTION), all users (Home SRS v1.25 §7.3). The screenshot column is
     * never loaded (multi-MB base64 — detail call only).
     */
    List<NewRequestItem> newRequests(int limit);

    /**
     * Selector data for the Chart tab (Home_Chart_SRS §6.1): distinct groups
     * + parts per group (org rows employed='Y').
     */
    ChartDimensions chartDimensions();

    /**
     * Chart tab metrics per bucket (Home_Chart_SRS §6.2).
     *
     * @param period    "1m" / "3m" / "6m" / "1y" (anything else → IllegalArgumentException)
     * @param dimension "group" / "part" (anything else → IllegalArgumentException)
     * @param group     required when dimension = "part"
     */
    ChartStats chartStats(String period, String dimension, String group);
}
