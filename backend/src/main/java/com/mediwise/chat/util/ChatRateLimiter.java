package com.mediwise.chat.util;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A minimal in-memory sliding-window rate limiter for chat/call STOMP traffic.
 * Good enough for a single-instance deployment; a multi-instance deployment
 * would need a shared store (e.g. Redis) instead, since this state is
 * per-JVM only.
 */
@Component
public class ChatRateLimiter {

    private final ConcurrentHashMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    /**
     * Returns true if the caller may proceed, false if they've exceeded
     * {@code maxEvents} within the last {@code window}.
     */
    public synchronized boolean allow(String key, int maxEvents, Duration window) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);
        Deque<Instant> timestamps = hits.computeIfAbsent(key, k -> new ArrayDeque<>());

        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxEvents) {
            return false;
        }
        timestamps.addLast(now);
        return true;
    }
}
