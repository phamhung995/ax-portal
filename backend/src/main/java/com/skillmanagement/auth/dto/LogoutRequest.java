package com.skillmanagement.auth.dto;

public record LogoutRequest(
        String refreshToken
) {}
