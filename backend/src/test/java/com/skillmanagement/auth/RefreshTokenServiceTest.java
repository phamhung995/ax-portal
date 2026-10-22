package com.skillmanagement.auth;

import com.skillmanagement.auth.dto.AuthenticationResponse;
import com.skillmanagement.exception.TokenRefreshException;
import com.skillmanagement.exception.TokenReuseException;
import com.skillmanagement.auth.model.RefreshToken;
import com.skillmanagement.model.Role;
import com.skillmanagement.user.model.User;
import com.skillmanagement.auth.repository.RefreshTokenRepository;
import com.skillmanagement.auth.service.JwtService;
import com.skillmanagement.auth.service.RefreshTokenService;
import com.skillmanagement.auth.service.impl.RefreshTokenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtService jwtService;

    private RefreshTokenService refreshTokenService;
    private User testUser;
    private static final long REFRESH_EXPIRATION_MS = 604800000L; // 7 days

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenServiceImpl(refreshTokenRepository, jwtService, REFRESH_EXPIRATION_MS);
        testUser = User.builder()
                .id(1L)
                .name("Session User")
                .email("session@example.com")
                .role(Role.builder().name("USER").build())
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("createRefreshToken should save and return token with 7 days expiry")
    void testCreateRefreshToken() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        RefreshToken token = refreshTokenService.createRefreshToken(testUser);

        assertNotNull(token);
        assertEquals(testUser, token.getUser());
        assertFalse(token.isRevoked());
        assertTrue(token.getExpiryDate().isAfter(Instant.now().plus(6, ChronoUnit.DAYS)));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("rotateRefreshToken should succeed with valid token and generate new token pair")
    void testRotateRefreshTokenSuccess() {
        String oldTokenStr = "valid-old-token";
        RefreshToken existingToken = RefreshToken.builder()
                .id(10L)
                .token(oldTokenStr)
                .user(testUser)
                .expiryDate(Instant.now().plus(2, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken(oldTokenStr)).thenReturn(Optional.of(existingToken));
        when(jwtService.generateToken(testUser)).thenReturn("new.access.token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        AuthenticationResponse response = refreshTokenService.rotateRefreshToken(oldTokenStr);

        assertNotNull(response);
        assertEquals("new.access.token", response.accessToken());
        assertNotEquals(oldTokenStr, response.refreshToken());
        verify(refreshTokenRepository).delete(existingToken);
    }

    @Test
    @DisplayName("rotateRefreshToken on revoked token should trigger TokenReuseException and delete user sessions")
    void testTokenReuseAttackDetection() {
        String compromisedTokenStr = "compromised-token";
        RefreshToken revokedToken = RefreshToken.builder()
                .id(10L)
                .token(compromisedTokenStr)
                .user(testUser)
                .expiryDate(Instant.now().plus(2, ChronoUnit.DAYS))
                .revoked(true)
                .build();

        when(refreshTokenRepository.findByToken(compromisedTokenStr)).thenReturn(Optional.of(revokedToken));

        assertThrows(TokenReuseException.class, () -> refreshTokenService.rotateRefreshToken(compromisedTokenStr));
        verify(refreshTokenRepository).deleteByUserId(testUser.getId());
    }

    @Test
    @DisplayName("rotateRefreshToken on expired token should throw TokenRefreshException")
    void testExpiredRefreshToken() {
        String expiredTokenStr = "expired-token";
        RefreshToken expiredToken = RefreshToken.builder()
                .id(10L)
                .token(expiredTokenStr)
                .user(testUser)
                .expiryDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken(expiredTokenStr)).thenReturn(Optional.of(expiredToken));

        assertThrows(TokenRefreshException.class, () -> refreshTokenService.rotateRefreshToken(expiredTokenStr));
        verify(refreshTokenRepository).delete(expiredToken);
    }
}
