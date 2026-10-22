package com.skillmanagement.auth.dto;

/**
 * DTO for authentication responses containing access and refresh tokens.
 * Retains 'token' for 100% backward compatibility with frontend clients and existing calls.
 */
public record AuthenticationResponse(
        String token,
        String accessToken,
        String refreshToken,
        String tokenType
) {
    public AuthenticationResponse(String accessToken, String refreshToken) {
        this(accessToken, accessToken, refreshToken, "Bearer");
    }

    public AuthenticationResponse(String token) {
        this(token, token, null, "Bearer");
    }
}
