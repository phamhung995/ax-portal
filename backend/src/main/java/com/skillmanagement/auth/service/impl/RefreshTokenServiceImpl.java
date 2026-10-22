package com.skillmanagement.auth.service.impl;

import com.skillmanagement.auth.dto.AuthenticationResponse;
import com.skillmanagement.exception.TokenRefreshException;
import com.skillmanagement.exception.TokenReuseException;
import com.skillmanagement.auth.model.RefreshToken;
import com.skillmanagement.user.model.User;
import com.skillmanagement.auth.repository.RefreshTokenRepository;
import com.skillmanagement.auth.service.JwtService;
import com.skillmanagement.auth.service.RefreshTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final long refreshExpirationMs;

    public RefreshTokenServiceImpl(
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            @Value("${jwt.refresh-expiration:86400000}") long refreshExpirationMs
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    @Override
    @Transactional
    public RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(refreshExpirationMs))
                .revoked(false)
                .createdAt(Instant.now())
                .build();
        return refreshTokenRepository.save(refreshToken);
    }

    @Override
    @Transactional
    public AuthenticationResponse rotateRefreshToken(String requestRefreshToken) {
        RefreshToken token = refreshTokenRepository.findByToken(requestRefreshToken)
                .orElseThrow(() -> new TokenRefreshException("Refresh token không tồn tại trong hệ thống."));

        if (token.isRevoked()) {
            log.error("Cảnh báo an ninh: Phát hiện Refresh Token cũ bị tái sử dụng! User ID: {}", token.getUser().getId());
            refreshTokenRepository.deleteByUserId(token.getUser().getId());
            throw new TokenReuseException("Cảnh báo bảo mật: Phiên làm việc đã bị hủy do phát hiện token bị tái sử dụng trái phép.");
        }

        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new TokenRefreshException("Refresh token đã hết hạn. Vui lòng đăng nhập lại.");
        }

        User user = token.getUser();
        refreshTokenRepository.delete(token);

        RefreshToken newRefreshToken = createRefreshToken(user);
        String newAccessToken = jwtService.generateToken(user);

        return new AuthenticationResponse(newAccessToken, newRefreshToken.getToken());
    }

    @Override
    @Transactional
    public void revokeToken(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(refreshTokenRepository::delete);
    }

    @Override
    @Transactional
    public void revokeAllUserTokens(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }
}
