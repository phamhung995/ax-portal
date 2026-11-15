package com.furelise.skillmanagement.home.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * GET /api/v1/home/community-skills payload (Home SRS §7.2):
 * hot (5), trending (5), new (5) — APPROVED only, visibility-scoped server-side.
 */
public record CommunitySkills(
        List<CommunitySkillItem> hot,
        List<CommunitySkillItem> trending,
        @JsonProperty("new") List<CommunitySkillItem> newSkills
) {
}
