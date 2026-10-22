package com.skillmanagement.skill.dto;

import java.util.List;

/** DELETE /api/v1/skills body: the selected skill ids. */
public record SkillDeleteRequest(List<Long> ids) {
}
