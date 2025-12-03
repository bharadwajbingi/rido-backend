package com.rido.auth.config;

import com.rido.auth.security.JwtUserAuthenticationFilter;
import com.rido.auth.security.ServiceAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtUserAuthenticationFilter jwtFilter;
    private final ServiceAuthenticationFilter mtlsFilter;

    public SecurityConfig(
            JwtUserAuthenticationFilter jwtFilter,
            ServiceAuthenticationFilter mtlsFilter
    ) {
        this.jwtFilter = jwtFilter;
        this.mtlsFilter = mtlsFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm ->
                    sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                 .requestMatchers("/auth/**").permitAll()

                // Internal admin setup (first admin bootstrap)
                .requestMatchers("/secure/info").permitAll()
                .requestMatchers("/internal/admin/create").permitAll()
                    // everything else requires JWT authentication
                    .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((req, res, e) -> {
                        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        res.setContentType("application/json");
                        res.getWriter().write("{\"error\":\"Unauthorized\"}");
                    })
                    .accessDeniedHandler((req, res, e) -> {
                        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        res.setContentType("application/json");
                        res.getWriter().write("{\"error\":\"Access Denied\"}");
                    })
            )
            .httpBasic(h -> h.disable())
            .formLogin(f -> f.disable());

        // ✨ filter order:
        // 1. mTLS filter validates Gateway/Auth service identity (internal calls)
        http.addFilterBefore(mtlsFilter, UsernamePasswordAuthenticationFilter.class);

        // 2. JWT user authentication filter extracts userId, roles, jti
        http.addFilterAfter(jwtFilter, ServiceAuthenticationFilter.class);

        return http.build();
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
