package com.skillmanagement.skill.service.impl;

import com.skillmanagement.common.RateLimiter;
import org.springframework.stereotype.Service;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory sliding-window rate limiter.
 * Suitable for single-instance deployments; swap for Redis in multi-instance setups.
 */
@Service
public class SlidingWindowRateLimiter implements RateLimiter {

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    @Override
    public boolean allow(String key, int maxAttempts, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;
        Deque<Long> queue = hits.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        while (!queue.isEmpty() && now - queue.peekFirst() > windowMs) {
            queue.pollFirst();
        }
        if (queue.size() >= maxAttempts) {
            return false;
        }
        queue.addLast(now);
        return true;
    }
}
