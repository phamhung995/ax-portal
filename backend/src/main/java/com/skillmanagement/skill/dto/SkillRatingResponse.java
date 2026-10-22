package com.skillmanagement.skill.dto;

/** Aggregate rating for a skill (grid/detail header). */
public record SkillRatingResponse(double averageRating, long totalRatings) {
}
