package com.furelise.skillmanagement.reputation;

import com.furelise.skillmanagement.reputation.service.CompanionRateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fixed-window token bucket for the companion endpoint (GĐ6, BA Q5). */
class CompanionRateLimiterTest {

    @Test
    void allowsUpToLimitThenRefuses() {
        CompanionRateLimiter limiter = new CompanionRateLimiter(3, Duration.ofMinutes(1));
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void windowRollsOver() throws InterruptedException {
        CompanionRateLimiter limiter = new CompanionRateLimiter(1, Duration.ofMillis(200));
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
        Thread.sleep(250);
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }
}
