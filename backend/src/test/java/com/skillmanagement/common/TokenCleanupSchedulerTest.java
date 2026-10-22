package com.skillmanagement.common;

import com.skillmanagement.auth.repository.BlacklistedTokenRepository;
import com.skillmanagement.auth.repository.RefreshTokenRepository;
import com.skillmanagement.common.scheduler.TokenCleanupScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenCleanupSchedulerTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @InjectMocks
    private TokenCleanupScheduler tokenCleanupScheduler;

    @Test
    @DisplayName("purgeExpiredTokens should delete expired refresh and blacklisted tokens")
    void testPurgeExpiredTokens() {
        when(refreshTokenRepository.deleteByExpiryDateBefore(any(Instant.class))).thenReturn(5);
        when(blacklistedTokenRepository.deleteByExpiryDateBefore(any(Instant.class))).thenReturn(10);

        tokenCleanupScheduler.purgeExpiredTokens();

        verify(refreshTokenRepository).deleteByExpiryDateBefore(any(Instant.class));
        verify(blacklistedTokenRepository).deleteByExpiryDateBefore(any(Instant.class));
    }
}
