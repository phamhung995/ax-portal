package com.skillmanagement.home.service;

import com.skillmanagement.home.dto.CommunitySkills;
import com.skillmanagement.home.dto.HomeActivity;
import com.skillmanagement.home.dto.HomeSkillItem;
import com.skillmanagement.home.dto.HomeSummary;
import com.skillmanagement.home.dto.NewRequestItem;
import com.skillmanagement.user.model.User;

import java.util.List;

/**
 * Home screen data (Home SRS §7) — real queries over the Skill Management tables.
 */
public interface HomeService {

    HomeSummary summary(User viewer, boolean isAdmin);

    List<HomeSkillItem> myRecentSkills(User viewer, int limit);

    /** Community skill cards: hot (5) / trending (5) / new (10) (Home SRS §7.2). */
    CommunitySkills communitySkills(User viewer);

    /**
     * 8 most recent Feature Requests with CATEGORY = BUG or ENHANCEMENT
     * (Home SRS §7.3). The Feature Request module is not yet specified —
     * returns an empty list for now.
     */
    List<NewRequestItem> newRequests(int limit);

    /**
     * Real activity data for the Home chart tab: weekly views, weekly
     * skill updates, and daily views over the last {@code weeks} weeks.
     */
    HomeActivity activity(User viewer, int weeks);
}
