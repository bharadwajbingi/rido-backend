package com.rido.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class TokenBlacklistService {

    private final StringRedisTemplate redis;
    private final String jwtSecret;

    public TokenBlacklistService(
            StringRedisTemplate redis,
            @Value("${jwt.secret}") String jwtSecret
    ) {
        this.redis = redis;
        this.jwtSecret = jwtSecret;
    }

    public void blacklist(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }

        try {
            var key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(accessToken)
                    .getBody();

            String jti = claims.getId();
            long expMillis = claims.getExpiration().getTime();
            long nowMillis = Instant.now().toEpochMilli();

            long secondsLeft = Math.max(1, (expMillis - nowMillis) / 1000);

            redis.opsForValue().set(
                    "auth:jti:blacklist:" + jti,
                    "1",
                    secondsLeft,
                    TimeUnit.SECONDS
            );

        } catch (Exception ignored) {
            // invalid token, no blacklist needed
        }
    }

    public boolean isBlacklisted(String jti) {
        Boolean exists = redis.hasKey("auth:jti:blacklist:" + jti);
        return exists != null && exists;
    }
}
