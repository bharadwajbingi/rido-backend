package com.rido.auth.config;

import com.rido.auth.security.SecurityContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final SecurityContextFilter securityContextFilter;
    private final com.rido.auth.security.ServiceAuthenticationFilter serviceAuthenticationFilter;

    public SecurityConfig(SecurityContextFilter securityContextFilter,
                          com.rido.auth.security.ServiceAuthenticationFilter serviceAuthenticationFilter) {
        this.securityContextFilter = securityContextFilter;
        this.serviceAuthenticationFilter = serviceAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/auth/register",
                    "/auth/login",
                    "/auth/refresh",
                    "/auth/logout",
                    "/auth/check-username",

                    "/auth/keys/.well-known/jwks.json",
                    "/auth/keys/jwks.json",

                    "/internal/admin/create"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(form -> form.disable());

        // 🔥 ADD CUSTOM SECURITY CONTEXT FILTERS
        // Service auth filter runs first to authenticate service-to-service calls
        http.addFilterBefore(serviceAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        // Security context filter runs after to extract user context from headers
        http.addFilterAfter(securityContextFilter, com.rido.auth.security.ServiceAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(
                16,  // salt length
                32,  // hash length
                1,   // parallelism
                1 << 12, // memory
                3    // iterations
        );
    }
}
