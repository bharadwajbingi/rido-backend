package com.rido.profile.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.AuthenticationWebFilter
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .authorizeExchange { exchanges ->
                exchanges
                    .pathMatchers("/actuator/**").permitAll() // Health checks
                    .pathMatchers("/profile/admin/**").hasRole("ADMIN")
                    .pathMatchers("/profile/driver/**").hasAnyRole("DRIVER", "ADMIN")
                    .pathMatchers("/profile/rider/**").hasAnyRole("RIDER", "ADMIN")
                    .anyExchange().authenticated()
            }
            .addFilterAt(authenticationWebFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
            .build()
    }

    @Bean
    fun authenticationWebFilter(): AuthenticationWebFilter {
        val manager = ReactiveAuthenticationManager { authentication -> Mono.just(authentication) }
        val filter = AuthenticationWebFilter(manager)
        filter.setServerAuthenticationConverter(GatewayAuthenticationConverter())
        return filter
    }
}

class GatewayAuthenticationConverter : ServerAuthenticationConverter {
    override fun convert(exchange: ServerWebExchange): Mono<org.springframework.security.core.Authentication> {
        val userId = exchange.request.headers.getFirst("X-User-ID")
        val roles = exchange.request.headers.getFirst("X-Roles")

        if (userId != null && roles != null) {
            val authorities = roles.split(",")
                .map { SimpleGrantedAuthority("ROLE_$it") }
            
            val auth = UsernamePasswordAuthenticationToken(userId, null, authorities)
            return Mono.just(auth)
        }
        return Mono.empty()
    }
}
