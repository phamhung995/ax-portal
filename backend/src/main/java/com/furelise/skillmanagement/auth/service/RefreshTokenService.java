package com.furelise.skillmanagement.auth.service;

import com.furelise.skillmanagement.auth.dto.AuthenticationResponse;
import com.furelise.skillmanagement.auth.model.RefreshToken;
import com.furelise.skillmanagement.user.model.User;

public interface RefreshTokenService {

    RefreshToken createRefreshToken(User user);

    AuthenticationResponse rotateRefreshToken(String requestRefreshToken);

    void revokeToken(String token);

    void revokeAllUserTokens(Long userId);
}
