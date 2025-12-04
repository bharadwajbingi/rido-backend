package com.rido.auth.service;

import com.rido.auth.config.JwtConfig;
import com.rido.auth.crypto.JwtKeyStore;
import com.rido.auth.dto.TokenResponse;
import com.rido.auth.model.RefreshTokenEntity;
import com.rido.auth.repo.RefreshTokenRepository;
import com.rido.auth.util.HashUtils;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class TokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtKeyStore keyStore;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;
    private final int maxActiveSessions;

    public TokenService(
            RefreshTokenRepository refreshTokenRepository,
            JwtKeyStore keyStore,
            JwtConfig jwtConfig,
            @Value("${auth.login.max-active-sessions:5}") int maxActiveSessions
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.keyStore = keyStore;
        this.accessTtlSeconds = jwtConfig.accessTokenTtlSeconds();
        this.refreshTtlSeconds = jwtConfig.refreshTokenTtlSeconds();
        this.maxActiveSessions = maxActiveSessions;
    }

    public TokenResponse createTokens(UUID userId, String role, String deviceId, String ip, String userAgent) {

        // Enforce max active sessions limit
        List<RefreshTokenEntity> activeSessions = refreshTokenRepository.findActiveByUserIdOrderByCreatedAtAsc(userId);
        if (activeSessions.size() >= maxActiveSessions) {
            int tokensToRemove = activeSessions.size() - maxActiveSessions + 1;
            for (int i = 0; i < tokensToRemove; i++) {
                RefreshTokenEntity toRevoke = activeSessions.get(i);
                toRevoke.setRevoked(true);
                refreshTokenRepository.save(toRevoke);
            }
        }

        Instant now = Instant.now().plusMillis(5);
        Instant accessExp = now.plusSeconds(accessTtlSeconds);

        var keyPair = keyStore.getCurrentKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        String kid = keyStore.getCurrentKid();

        String access = Jwts.builder()
                .setHeaderParam("kid", kid)
                .setIssuer("rido-auth-service")
                .setAudience("rido-api")
                .setSubject(userId.toString())
                .claim("roles", List.of(role))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(accessExp))
                .setId(UUID.randomUUID().toString())
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();

        String plain = UUID.randomUUID().toString();
        String hash = HashUtils.sha256(plain);

        RefreshTokenEntity rt = new RefreshTokenEntity();
        rt.setUserId(userId);
        rt.setTokenHash(hash);
        rt.setExpiresAt(now.plusSeconds(refreshTtlSeconds));
        rt.setRevoked(false);
        rt.setDeviceId(deviceId);
        rt.setIp(ip);
        rt.setUserAgent(userAgent);
        rt.setJti(UUID.randomUUID());

        refreshTokenRepository.save(rt);

        return new TokenResponse(access, plain, accessTtlSeconds);
    }
}
