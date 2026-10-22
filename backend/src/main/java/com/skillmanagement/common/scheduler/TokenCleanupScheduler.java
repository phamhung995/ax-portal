package com.skillmanagement.common.scheduler;

import com.skillmanagement.auth.repository.BlacklistedTokenRepository;
import com.skillmanagement.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;
    private final BlacklistedTokenRepository blacklistedTokenRepository;

    @Scheduled(cron = "${jwt.cleanup.cron:0 0 2 * * ?}")
    @Transactional
    public void purgeExpiredTokens() {
        Instant now = Instant.now();
        int deletedRefreshTokens = refreshTokenRepository.deleteByExpiryDateBefore(now);
        int deletedBlacklistedTokens = blacklistedTokenRepository.deleteByExpiryDateBefore(now);
        log.info("Token cleanup completed: purged {} expired refresh tokens and {} expired blacklisted tokens.",
                deletedRefreshTokens, deletedBlacklistedTokens);
    }
}
