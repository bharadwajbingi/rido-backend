package com.rido.gateway.filter

import com.fasterxml.jackson.databind.ObjectMapper
import com.rido.gateway.crypto.JwtKeyResolver
import io.jsonwebtoken.Jwts
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.*

@Component("JwtAuthFilter")
class JwtAuthFilter(
    private val redis: ReactiveStringRedisTemplate,
    private val keyResolver: JwtKeyResolver
) : AbstractGatewayFilterFactory<JwtAuthFilter.Config>(Config::class.java) {

    class Config

    override fun apply(config: Config?): GatewayFilter {
        return GatewayFilter { exchange, chain ->

            val path = exchange.request.path.toString()

            // Public endpoints (NO AUTH)
            if (
                path.startsWith("/auth/login") ||
                path.startsWith("/auth/register") ||
                path.startsWith("/auth/refresh") ||
                path.startsWith("/auth/logout") ||
                path.startsWith("/auth/.well-known") ||
                path.startsWith("/auth/.well-known") ||
                path.startsWith("/auth/keys/jwks.json")

            ) {
                return@GatewayFilter chain.filter(exchange)
            }

            // Extract JWT
            val token = extractToken(exchange.request.headers)
                ?: return@GatewayFilter unauthorized(exchange)

            // Decode header
            val parts = token.split(".")
            if (parts.size != 3) return@GatewayFilter unauthorized(exchange)

            val headerJson = try {
                String(Base64.getUrlDecoder().decode(parts[0]))
            } catch (e: Exception) {
                return@GatewayFilter unauthorized(exchange)
            }

            val header = try {
                ObjectMapper().readValue(headerJson, Map::class.java) as Map<String, Any?>
            } catch (e: Exception) {
                return@GatewayFilter unauthorized(exchange)
            }

            val kid = header["kid"] as? String ?: return@GatewayFilter unauthorized(exchange)
            val publicKey = keyResolver.resolve(kid) ?: return@GatewayFilter unauthorized(exchange)

            // Parse claims
            val claims = try {
                Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .build()
                    .parseClaimsJws(token)
                    .body
            } catch (e: Exception) {
                return@GatewayFilter unauthorized(exchange)
            }

            val jti = claims.id ?: return@GatewayFilter unauthorized(exchange)
            val userId = claims.subject ?: return@GatewayFilter unauthorized(exchange)

            // Check blacklist
            redis.hasKey("auth:jti:blacklist:$jti")
                .flatMap { isBlacklisted ->
                    if (isBlacklisted) {
                        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
                        return@flatMap exchange.response.setComplete()
                    }

                    // ---------------------------------------------------------
                    // Extract roles from JWT (THIS FIXES YOUR ADMIN 403 ISSUE)
                    // ---------------------------------------------------------
                    val roles = (claims["roles"] as? List<*>)
                        ?.map { SimpleGrantedAuthority("ROLE_$it") }
                        ?: emptyList()

                    val auth = UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        roles
                    )

                    // Add user ID header for downstream services
                    val mutated = exchange.mutate()
                        .request { it.header("X-User-ID", userId) }
                        .build()

                    // Propagate security context
                    return@flatMap chain.filter(mutated)
                        .contextWrite(
                            ReactiveSecurityContextHolder.withAuthentication(auth)
                        )
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
