package com.rido.auth.rate;

import com.rido.auth.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ZSetOperations<String, String> zSetOps;

    private RateLimiterService rateLimiterService;

    private static final String KEY = "user:123";
    private static final int MAX_REQUESTS = 10;
    private static final int WINDOW_SECONDS = 60;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService(redis);
    }

    @Nested
    @DisplayName("Check Rate Limit Tests")
    class CheckRateLimit {

        @Test
        @DisplayName("Should allow request when under limit")
        void shouldAllow_whenUnderLimit() {
            when(redis.opsForZSet()).thenReturn(zSetOps);
            when(zSetOps.count(eq("rate:" + KEY), anyDouble(), anyDouble())).thenReturn(5L);

            assertThatCode(() -> rateLimiterService.checkRateLimit(KEY, MAX_REQUESTS, WINDOW_SECONDS))
                    .doesNotThrowAnyException();

            verify(zSetOps).add(eq("rate:" + KEY), anyString(), anyDouble());
        }

        @Test
        @DisplayName("Should throw when limit exceeded")
        void shouldThrow_whenLimitExceeded() {
            when(redis.opsForZSet()).thenReturn(zSetOps);
            when(zSetOps.count(eq("rate:" + KEY), anyDouble(), anyDouble())).thenReturn(10L);

            assertThatThrownBy(() -> rateLimiterService.checkRateLimit(KEY, MAX_REQUESTS, WINDOW_SECONDS))
                    .isInstanceOf(TooManyRequestsException.class)
                    .hasMessageContaining("Too many requests");
        }

        @Test
        @DisplayName("Should clean old entries outside window")
        void shouldCleanOldEntries_outsideWindow() {
            when(redis.opsForZSet()).thenReturn(zSetOps);
            when(zSetOps.count(eq("rate:" + KEY), anyDouble(), anyDouble())).thenReturn(0L);

            rateLimiterService.checkRateLimit(KEY, MAX_REQUESTS, WINDOW_SECONDS);

            verify(zSetOps).removeRangeByScore(eq("rate:" + KEY), eq(0D), anyDouble());
        }

        @Test
        @DisplayName("Should set expiry on rate limit key")
        void shouldSetExpiry_onRateLimitKey() {
            when(redis.opsForZSet()).thenReturn(zSetOps);
            when(zSetOps.count(eq("rate:" + KEY), anyDouble(), anyDouble())).thenReturn(0L);

            rateLimiterService.checkRateLimit(KEY, MAX_REQUESTS, WINDOW_SECONDS);

            verify(redis).expire(eq("rate:" + KEY), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("Reset Tests")
    class Reset {

        @Test
        @DisplayName("Should delete rate limit key on reset")
        void shouldReset_whenResetCalled() {
            rateLimiterService.reset(KEY);

            verify(redis).delete("rate:" + KEY);
        }

        @Test
        @DisplayName("Should not throw when Redis fails during reset")
        void shouldNotThrow_whenRedisFails() {
            doThrow(new RuntimeException("Redis down")).when(redis).delete(anyString());

            assertThatCode(() -> rateLimiterService.reset(KEY))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Fallback Tests")
    class Fallback {

        @Test
        @DisplayName("Should fail open when Redis is down")
        void shouldFailOpen_whenRedisDown() {
            assertThatCode(() -> rateLimiterService.checkRateLimitFallback(
                    KEY, MAX_REQUESTS, WINDOW_SECONDS, new RuntimeException("Redis down")
            )).doesNotThrowAnyException();
        }
    }
}
