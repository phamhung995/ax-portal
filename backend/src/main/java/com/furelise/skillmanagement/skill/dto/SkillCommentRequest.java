package com.furelise.skillmanagement.skill.dto;

public record SkillCommentRequest(
        String content,
        /** null = top-level comment, else the id of the top-level comment to reply to. */
        Long parentId
) {
}
