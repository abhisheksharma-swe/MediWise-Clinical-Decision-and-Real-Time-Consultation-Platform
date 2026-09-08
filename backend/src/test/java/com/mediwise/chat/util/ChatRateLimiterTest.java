package com.mediwise.chat.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChatRateLimiter — Sliding Window")
class ChatRateLimiterTest {

    @Test
    @DisplayName("allows up to the configured max events within the window")
    void allowsUpToMax() {
        ChatRateLimiter limiter = new ChatRateLimiter();
        Duration window = Duration.ofSeconds(10);

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.allow("user-1", 5, window)).isTrue();
        }
    }

    @Test
    @DisplayName("rejects the event once the max is exceeded within the window")
    void rejectsOverMax() {
        ChatRateLimiter limiter = new ChatRateLimiter();
        Duration window = Duration.ofSeconds(10);

        for (int i = 0; i < 5; i++) {
            limiter.allow("user-1", 5, window);
        }

        assertThat(limiter.allow("user-1", 5, window)).isFalse();
    }

    @Test
    @DisplayName("tracks each key independently — one user's traffic never limits another's")
    void isolatesByKey() {
        ChatRateLimiter limiter = new ChatRateLimiter();
        Duration window = Duration.ofSeconds(10);

        for (int i = 0; i < 5; i++) {
            limiter.allow("user-1", 5, window);
        }

        assertThat(limiter.allow("user-1", 5, window)).isFalse();
        assertThat(limiter.allow("user-2", 5, window)).isTrue();
    }

    @Test
    @DisplayName("allows again once the window has fully elapsed")
    void allowsAgainAfterWindowElapses() {
        ChatRateLimiter limiter = new ChatRateLimiter();
        Duration tinyWindow = Duration.ofMillis(50);

        assertThat(limiter.allow("user-1", 1, tinyWindow)).isTrue();
        assertThat(limiter.allow("user-1", 1, tinyWindow)).isFalse();

        await(tinyWindow.toMillis() + 20);

        assertThat(limiter.allow("user-1", 1, tinyWindow)).isTrue();
    }

    private void await(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
