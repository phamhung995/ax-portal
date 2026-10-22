package com.skillmanagement.skillrequest.dto;

import java.util.List;

public record SkillRequestCreateRequest(
        String title,
        String description,
        List<String> hashtags
) {
}
