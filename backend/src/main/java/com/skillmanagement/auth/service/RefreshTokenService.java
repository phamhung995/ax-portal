package com.skillmanagement.auth.service;

import com.skillmanagement.auth.dto.AuthenticationResponse;
import com.skillmanagement.auth.model.RefreshToken;
import com.skillmanagement.user.model.User;

public interface RefreshTokenService {

    RefreshToken createRefreshToken(User user);

    AuthenticationResponse rotateRefreshToken(String requestRefreshToken);

    void revokeToken(String token);

    void revokeAllUserTokens(Long userId);
}
