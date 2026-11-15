package com.furelise.skillmanagement.skillrequest.dto;

/**
 * A user candidate for the @mention autocomplete (id + display name only —
 * never email or other PII). Same shape as the AI News mention item.
 */
public record MentionUserItem(Long id, String name) {
}
