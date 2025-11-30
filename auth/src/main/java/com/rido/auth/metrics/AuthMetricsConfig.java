package com.rido.auth.metrics;

import com.rido.auth.repo.RefreshTokenRepository;
import com.rido.auth.repo.UserRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AuthMetricsConfig {

    public AuthMetricsConfig(
            MeterRegistry registry,
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository
    ) {
        Gauge.builder("auth.refresh.active", () -> refreshTokenRepository.countByRevokedFalse())
                .description("Number of active (non-revoked) refresh tokens")
                .register(registry);

        Gauge.builder("auth.accounts.locked", () -> userRepository.countByLockedUntilAfter(Instant.now()))
                .description("Number of currently locked user accounts")
                .register(registry);
    }
}
