package com.furelise.skillmanagement.ainews.dto;

/**
 * A user candidate for the @mention autocomplete (id + display name only —
 * never email or other PII).
 */
public record MentionUserItem(Long id, String name) {
}
