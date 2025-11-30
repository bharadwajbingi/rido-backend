package com.rido.auth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rido.auth.crypto.JwtKeyStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.logstash.logback.argument.StructuredArguments.kv;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);

    private final StringRedisTemplate redis;
    private final JwtKeyStore keyStore;

    private final Counter blacklistCounter;
    private final Counter blacklistInvalidTokenCounter;

    private final ObjectMapper mapper = new ObjectMapper();

    public TokenBlacklistService(
            StringRedisTemplate redis,
            JwtKeyStore keyStore,
            MeterRegistry registry
    ) {
        this.redis = redis;
        this.keyStore = keyStore;

        this.blacklistCounter = Counter.builder("auth.token.blacklist")
                .description("Number of access tokens blacklisted")
                .register(registry);

        this.blacklistInvalidTokenCounter = Counter.builder("auth.token.blacklist.invalid")
                .description("Invalid tokens encountered during blacklist attempts")
                .register(registry);
    }

    // ======================================================
    // ✅ BLACKLIST ACCESS TOKEN (MANUAL JWT DECODE)
    // ======================================================
    public void blacklist(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) return;

        try {
            // -------------------------------------------
            // Decode parts manually
            // -------------------------------------------
            String[] parts = accessToken.split("\\.");
            if (parts.length != 3) {
                log.warn("blacklist_invalid_format");
                blacklistInvalidTokenCounter.increment();
                return;
            }

            // HEADER
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            Map<String, Object> header = mapper.readValue(headerJson, new TypeReference<>() {});

            String kid = (String) header.get("kid");
            if (kid == null) {
                log.warn("blacklist_missing_kid");
                blacklistInvalidTokenCounter.increment();
                return;
            }

            if (keyStore.getKeyPair(kid) == null) {
                log.warn("blacklist_unknown_kid", kv("kid", kid));
                blacklistInvalidTokenCounter.increment();
                return;
            }

            // PAYLOAD
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            Map<String, Object> payload = mapper.readValue(payloadJson, new TypeReference<>() {});

            String jti = (String) payload.get("jti");
            Number expNumber = (Number) payload.get("exp");

            if (jti == null || expNumber == null) {
                log.warn("blacklist_missing_fields");
                blacklistInvalidTokenCounter.increment();
                return;
            }

            long expSec = expNumber.longValue();
            long nowSec = Instant.now().getEpochSecond();
            long ttl = Math.max(1, expSec - nowSec);

            // -------------------------------------------
            // Save blacklist entry
            // -------------------------------------------
            redis.opsForValue().set(
                    "auth:jti:blacklist:" + jti,
                    "1",
                    ttl,
                    TimeUnit.SECONDS
            );

            blacklistCounter.increment();
            log.info("blacklisted_access_token",
                    kv("kid", kid),
                    kv("jti", jti),
                    kv("ttl_seconds", ttl));

        } catch (Exception e) {
            log.warn("blacklist_failed_invalid_token", kv("error", e.getMessage()));
            blacklistInvalidTokenCounter.increment();
        }
    }

    // ======================================================
    // ✅ CHECK IF JTI IS BLACKLISTED
    // ======================================================
    public boolean isBlacklisted(String jti) {
        Boolean exists = redis.hasKey("auth:jti:blacklist:" + jti);
        return exists != null && exists;
    }
}
