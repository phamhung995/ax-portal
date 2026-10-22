package com.skillmanagement.skill.dto;

import java.util.List;

/**
 * The staged (under review) version of a skill — shown to the creator and
 * admins so they can review the proposed changes before approving.
 * The main detail content always shows the CURRENT (live) version.
 */
public record SkillPendingVersionItem(
        String name,
        String description,
        String publicScope,
        List<String> hashtags,
        List<String> categories,
        List<String> stages,
        List<SkillFileItem> newFiles,
        List<SkillFileItem> removedFiles
) {}
