package com.skillmanagement.skillrequest.dto;

/**
 * Add-comment payload.
 *
 * @param content       comment text (required, max 1000)
 * @param linkedSkillId optional: an existing skill this comment points to
 * @param parentId      optional: the comment being replied to (threading, max depth 3)
 */
public record SkillRequestCommentRequest(String content, Long linkedSkillId, Long parentId) {
}
