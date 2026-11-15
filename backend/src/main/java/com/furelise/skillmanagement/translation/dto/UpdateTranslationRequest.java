package com.furelise.skillmanagement.translation.dto;

import java.util.Map;

/**
 * Admin: update a key. Both fields optional — only provided fields change.
 * A blank value clears that language's translation.
 */
public record UpdateTranslationRequest(String description, Map<String, String> values) {
}
