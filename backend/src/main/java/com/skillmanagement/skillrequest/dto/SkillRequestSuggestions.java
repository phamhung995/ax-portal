package com.skillmanagement.skillrequest.dto;

import java.util.List;

/**
 * Similar-item suggestions for duplicate detection (option A):
 * skills and other requests matching the keyword by title/description/hashtags.
 */
public record SkillRequestSuggestions(
        List<SkillSuggestion> skills,
        List<RequestSuggestion> requests
) {
    public record SkillSuggestion(Long id, String name, String status, String creatorName) {
    }

    public record RequestSuggestion(Long id, String title, int voteCount) {
    }
}
