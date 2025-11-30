package com.rido.auth.rate;

import com.rido.auth.exception.TooManyRequestsException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

@Service
public class RateLimiterService {

    private final StringRedisTemplate redis;

    public RateLimiterService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void checkRateLimit(String key, int maxRequests, int windowSeconds) {

        long now = Instant.now().toEpochMilli();
        long windowStart = now - (windowSeconds * 1000L);

        String redisKey = "rate:" + key;

        redis.opsForZSet().removeRangeByScore(redisKey, 0, windowStart);

        Long count = redis.opsForZSet().count(redisKey, windowStart, now);

        if (count != null && count >= maxRequests) {
            throw new TooManyRequestsException("Too many requests, slow down.");
        }

        redis.opsForZSet().add(redisKey, now + "-" + UUID.randomUUID(), now);
        redis.expire(redisKey, Duration.ofSeconds(windowSeconds + 2));
    }
}
