package com.furelise.skillmanagement.ainews.dto;

/**
 * Response body for POST /{newsId}/like.
 * Replaces {@code Map.of("likeCount", count)} — same JSON shape,
 * but type-safe.
 */
public record ToggleLikeResponse(int likeCount) {
}
