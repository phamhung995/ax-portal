package com.furelise.skillmanagement.translation.dto;

import java.time.Instant;
import java.util.Map;

/**
 * One translation key with its per-language values (admin list row).
 */
public record TranslationKeyItem(
        Long id,
        String key,
        String description,
        Map<String, String> values,
        Instant updatedAt,
        String updatedBy) {
}
