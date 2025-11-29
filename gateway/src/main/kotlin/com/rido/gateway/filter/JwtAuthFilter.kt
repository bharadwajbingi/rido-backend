package com.rido.gateway.filter

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

@Component
class JwtAuthFilter(
    @Value("\${jwt.secret}") private val jwtSecret: String
) : AbstractGatewayFilterFactory<JwtAuthFilter.Config>(Config::class.java) {

    class Config

    override fun apply(config: Config?): GatewayFilter {
        return GatewayFilter { exchange, chain ->

            val path = exchange.request.path.toString()

            // Public endpoints (no JWT needed)
            if (path.startsWith("/auth/login") ||
                path.startsWith("/auth/register") ||
                path.startsWith("/auth/refresh")||
                path.startsWith("/auth/logout")
            ) {
                return@GatewayFilter chain.filter(exchange)
            }

            val token = extractToken(exchange.request.headers)
                ?: return@GatewayFilter unauthorized(exchange)

            try {
                // KEEP THIS EXACT — UTF-8 key
                val key = Keys.hmacShaKeyFor(jwtSecret.toByteArray(StandardCharsets.UTF_8))

                val claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)

                val userId = claims.body.subject

                val mutatedExchange = exchange.mutate()
                    .request { it.header("X-User-ID", userId) }
                    .build()

                chain.filter(mutatedExchange)

            } catch (e: Exception) {
                unauthorized(exchange)
            }
        }
    }

    private fun extractToken(headers: HttpHeaders): String? {
        val auth = headers.getFirst(HttpHeaders.AUTHORIZATION) ?: return null
        if (!auth.startsWith("Bearer ")) return null
        return auth.substring(7)
    }

    private fun unauthorized(exchange: org.springframework.web.server.ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        return exchange.response.setComplete()
    }
}
