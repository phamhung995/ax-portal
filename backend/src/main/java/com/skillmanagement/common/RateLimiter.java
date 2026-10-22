package com.skillmanagement.common;

/**
 * Simple in-memory rate limiter (Settings SRS NFR-SEC-05:
 * password change max 5 attempts / 15 minutes / user).
 */
public interface RateLimiter {

    /** @return true when the action is allowed. */
    boolean allow(String key, int maxAttempts, int windowSeconds);
}
