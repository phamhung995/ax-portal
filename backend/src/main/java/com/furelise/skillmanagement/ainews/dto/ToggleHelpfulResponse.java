package com.furelise.skillmanagement.ainews.dto;

/**
 * Response body for POST /{newsId}/comments/{commentId}/helpful.
 * Replaces {@code Map.of("helpfulCount", count)} — same JSON shape,
 * but type-safe (typos in the key are caught at compile time).
 */
public record ToggleHelpfulResponse(int helpfulCount) {
}
