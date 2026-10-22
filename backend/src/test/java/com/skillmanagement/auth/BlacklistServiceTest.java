package com.skillmanagement.auth;

import com.skillmanagement.auth.model.BlacklistedToken;
import com.skillmanagement.auth.repository.BlacklistedTokenRepository;
import com.skillmanagement.auth.service.BlacklistService;
import com.skillmanagement.auth.service.JwtService;
import com.skillmanagement.auth.service.impl.BlacklistServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlacklistServiceTest {

    @Mock
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @Mock
    private JwtService jwtService;

    private BlacklistService blacklistService;

    @BeforeEach
    void setUp() {
        blacklistService = new BlacklistServiceImpl(blacklistedTokenRepository, jwtService);
    }

    @Test
    @DisplayName("blacklistToken should extract JTI & expiration and save to repository")
    void testBlacklistTokenSuccess() {
        String token = "valid.jwt.token";
        String jti = "uuid-1234";
        Date futureDate = new Date(System.currentTimeMillis() + 60000);

        when(jwtService.extractJti(token)).thenReturn(jti);
        when(jwtService.extractExpiration(token)).thenReturn(futureDate);

        blacklistService.blacklistToken(token);

        ArgumentCaptor<BlacklistedToken> captor = ArgumentCaptor.forClass(BlacklistedToken.class);
        verify(blacklistedTokenRepository).save(captor.capture());
        assertEquals(jti, captor.getValue().getJti());
    }

    @Test
    @DisplayName("isTokenBlacklisted should return true when JTI exists in repository")
    void testIsTokenBlacklistedTrue() {
        String token = "valid.jwt.token";
        String jti = "uuid-1234";

        when(jwtService.extractJti(token)).thenReturn(jti);
        when(blacklistedTokenRepository.existsByJti(jti)).thenReturn(true);

        assertTrue(blacklistService.isTokenBlacklisted(token));
    }

    @Test
    @DisplayName("isTokenBlacklisted should return false when JTI does not exist in repository")
    void testIsTokenBlacklistedFalse() {
        String token = "valid.jwt.token";
        String jti = "uuid-1234";

        when(jwtService.extractJti(token)).thenReturn(jti);
        when(blacklistedTokenRepository.existsByJti(jti)).thenReturn(false);

        assertFalse(blacklistService.isTokenBlacklisted(token));
    }
}
