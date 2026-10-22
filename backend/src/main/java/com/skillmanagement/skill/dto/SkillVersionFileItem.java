package com.skillmanagement.skill.dto;

/**
 * One file of a snapshotted skill version (Versions tab).
 */
public record SkillVersionFileItem(
        Long id,
        String fileName,
        Long fileSize
) {}
