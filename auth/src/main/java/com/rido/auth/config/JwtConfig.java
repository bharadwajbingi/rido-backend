package com.rido.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtConfig(
        String secret,
        long accessTokenTtlSeconds,
        long refreshTokenTtlSeconds
) {}
