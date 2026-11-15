package com.furelise.skillmanagement.translation.dto;

import java.util.Map;

/**
 * Admin: create a new key. values keys restricted to en/vi/ko;
 * missing or blank values are not stored.
 */
public record CreateTranslationRequest(String key, String description, Map<String, String> values) {
}
