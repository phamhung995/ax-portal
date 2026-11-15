package com.furelise.skillmanagement.ainews.dto;

/**
 * Response of POST /{newsId}/view — the distinct-viewer count of the news
 * item after recording this user's view.
 */
public record RecordViewResponse(int viewCount) {
}
