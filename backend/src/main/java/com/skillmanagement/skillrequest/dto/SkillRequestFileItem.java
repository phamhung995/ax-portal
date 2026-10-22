package com.skillmanagement.skillrequest.dto;

/** One attachment of a skill request post (image or non-image file). */
public record SkillRequestFileItem(
        Long id,
        String fileName,
        Long fileSize,
        /** True for image attachments (rendered in the post's image grid). */
        boolean isImage,
        /** Public URL of an image attachment (null for non-image files). */
        String url
) {
}
