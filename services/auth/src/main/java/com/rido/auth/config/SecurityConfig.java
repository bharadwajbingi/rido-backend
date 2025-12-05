package com.rido.auth.config;

import com.rido.auth.security.AdminAuthenticationFilter;
import com.rido.auth.security.JwtUserAuthenticationFilter;
import com.rido.auth.security.ServiceAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration with dual filter chains:
 * 1. Admin Filter Chain (port 9090) - No mTLS, admin JWT auth
 * 2. User Filter Chain (port 8081/8443) - mTLS + user JWT auth
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtUserAuthenticationFilter jwtFilter;
    private final ServiceAuthenticationFilter mtlsFilter;
    private final AdminAuthenticationFilter adminFilter;
    private final com.rido.auth.security.InputSanitizationFilter sanitizationFilter;

    public SecurityConfig(
            JwtUserAuthenticationFilter jwtFilter,
            ServiceAuthenticationFilter mtlsFilter,
            AdminAuthenticationFilter adminFilter,
            com.rido.auth.security.InputSanitizationFilter sanitizationFilter
    ) {
        this.jwtFilter = jwtFilter;
        this.mtlsFilter = mtlsFilter;
        this.adminFilter = adminFilter;
        this.sanitizationFilter = sanitizationFilter;
    }

    // =========================================================================
    // ADMIN FILTER CHAIN (Port 9090) - Higher priority
    // - No mTLS required
    // - Admin JWT authentication on protected endpoints
    // - No rate limits, no lockouts
    // =========================================================================
    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/admin/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public admin endpoints (no auth required)
                .requestMatchers("/admin/login").permitAll()
                .requestMatchers("/admin/health").permitAll()
                // All other admin endpoints require authentication (handled by filter)
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Unauthorized\"}");
                })
                .accessDeniedHandler((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Access Denied\"}");
                })
            )
            .httpBasic(h -> h.disable())
            .formLogin(f -> f.disable());

        // Input Sanitization
        http.addFilterBefore(sanitizationFilter, UsernamePasswordAuthenticationFilter.class);

        // Admin JWT filter for authenticated endpoints
        http.addFilterBefore(adminFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // =========================================================================
    // USER FILTER CHAIN (Port 8081/8443) - Lower priority, default
    // - mTLS required (gateway → auth)
    // - User JWT authentication
    // - Rate limits and brute-force protection applied
    // =========================================================================
    @Bean
    @Order(2)
    public SecurityFilterChain userFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public auth endpoints
                .requestMatchers("/auth/login").permitAll()
                .requestMatchers("/auth/register").permitAll()
                .requestMatchers("/auth/refresh").permitAll()
                .requestMatchers("/auth/logout").permitAll()
                .requestMatchers("/auth/check-username").permitAll()
                .requestMatchers("/auth/keys/**").permitAll()
                .requestMatchers("/.well-known/**").permitAll()
                // Actuator endpoints
                .requestMatchers("/actuator/**").permitAll()
                // Secure info endpoint (deprecated, keeping for backward compat)
                .requestMatchers("/secure/info").permitAll()
                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Unauthorized\"}");
                })
                .accessDeniedHandler((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Access Denied\"}");
                })
            )
            .httpBasic(h -> h.disable())
            .formLogin(f -> f.disable());

        // Input Sanitization
        http.addFilterBefore(sanitizationFilter, UsernamePasswordAuthenticationFilter.class);

        // Filter order:
        // 1. mTLS filter validates Gateway/Auth service identity
        http.addFilterBefore(mtlsFilter, UsernamePasswordAuthenticationFilter.class);
        // 2. JWT user authentication filter extracts userId, roles, jti
        http.addFilterAfter(jwtFilter, ServiceAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public org.springframework.validation.beanvalidation.LocalValidatorFactoryBean validator() {
        return new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(
                16,
                32,
                1,
                1 << 12,
                3
        );
    }
}
