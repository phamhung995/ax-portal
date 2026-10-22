package com.skillmanagement.user.dto;

import jakarta.validation.constraints.NotBlank;

public record PreferencesRequest(
        @NotBlank String language,
        @NotBlank String timezone,
        @NotBlank String dateFormat
) {
}
