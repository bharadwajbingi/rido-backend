package com.rido.gateway.filter

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

@Component("JwtAuthFilter")
class JwtAuthFilter(
    @Value("\${jwt.secret}") private val jwtSecret: String,

    @Qualifier("reactiveStringRedisTemplate")
    private val redis: ReactiveStringRedisTemplate

) : AbstractGatewayFilterFactory<JwtAuthFilter.Config>(Config::class.java) {

    class Config

    override fun apply(config: Config?): GatewayFilter {
        return GatewayFilter { exchange, chain ->

            val path = exchange.request.path.toString()

            // Public routes
            if (path.startsWith("/auth/login") ||
                path.startsWith("/auth/register") ||
                path.startsWith("/auth/refresh") || 
                path.startsWith("/auth/debug/unlock")
            ) {
                return@GatewayFilter chain.filter(exchange)
            }

            val token = extractToken(exchange.request.headers)
                ?: return@GatewayFilter unauthorized(exchange)

            val signingKey = Keys.hmacShaKeyFor(jwtSecret.toByteArray(StandardCharsets.UTF_8))

            val claims = try {
                Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .body
            } catch (e: Exception) {
                return@GatewayFilter unauthorized(exchange)
            }

            val jti = claims.id ?: return@GatewayFilter unauthorized(exchange)
            val userId = claims.subject ?: return@GatewayFilter unauthorized(exchange)

            redis.hasKey("auth:jti:blacklist:$jti")
                .flatMap { isBlacklisted ->
                    if (isBlacklisted) {
                        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
                        return@flatMap exchange.response.setComplete()
                    }

                    val mutated = exchange.mutate()
                        .request { it.header("X-User-ID", userId) }
                        .build()

                    chain.filter(mutated)
                }
        }
    }

    private fun extractToken(headers: HttpHeaders): String? {
        val raw = headers.getFirst(HttpHeaders.AUTHORIZATION) ?: return null
        if (!raw.startsWith("Bearer ")) return null
        return raw.substring(7)
    }

    private fun unauthorized(exchange: org.springframework.web.server.ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        return exchange.response.setComplete()
    }
}
