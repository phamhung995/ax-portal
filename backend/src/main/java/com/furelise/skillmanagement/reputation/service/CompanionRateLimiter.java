package com.furelise.skillmanagement.reputation.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Fixed-window token bucket for the companion install-events endpoint
 * (GĐ6, BA Q5): at most N accepted calls per window, then HTTP 429 until
 * the window rolls over. Single bucket — there is exactly one companion
 * token, so per-token and global limiting are the same thing here.
 * Thread-safe; the window is fixed (not sliding) on purpose: simple,
 * predictable, and good enough for a machine-to-machine reporter.
 */
@Component
public class CompanionRateLimiter {

    private final int maxPerWindow;
    private final long windowNanos;
    private final Object lock = new Object();
    private long windowStartNanos;
    private int used;

    public CompanionRateLimiter(
            @Value("${ax-portal.companion.rate-limit-per-minute:120}") int maxPerMinute) {
        this(maxPerMinute, Duration.ofMinutes(1));
    }

    /** Public for tests (short window instead of a minute). */
    public CompanionRateLimiter(int maxPerWindow, Duration window) {
        this.maxPerWindow = maxPerWindow;
        this.windowNanos = window.toNanos();
    }

    /**
     * Try to consume one call from the current window. Returns false when
     * the window is exhausted (caller answers 429); a new window starts
     * automatically once the old one has elapsed.
     */
    public boolean tryAcquire() {
        synchronized (lock) {
            long now = System.nanoTime();
            if (windowStartNanos == 0 || now - windowStartNanos >= windowNanos) {
                windowStartNanos = now;
                used = 0;
            }
            if (used < maxPerWindow) {
                used++;
                return true;
            }
            return false;
        }
    }
}
