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
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.*

@Component
class JwtAuthGatewayFilterFactory(
    private val redis: ReactiveStringRedisTemplate,
    private val keyResolver: JwtKeyResolver
) : AbstractGatewayFilterFactory<JwtAuthGatewayFilterFactory.Config>(Config::class.java) {

    class Config

    private val EXPECTED_ISS = "rido-auth-service"
    private val EXPECTED_AUD = "rido-api"
    private val EXPECTED_ALG = "RS256"

    override fun apply(config: Config?): GatewayFilter {
        return GatewayFilter { exchange, chain ->

            val path = exchange.request.path.toString()
            println("JwtAuthFilter: Processing request for path: $path")

            // 🔥 Skip ALL /auth/** routes — Auth service validates JWT itself
            if (path.startsWith("/auth/")) {
                println("JwtAuthFilter: Skipping all auth routes: $path")

                return@GatewayFilter chain.filter(exchange)
            }

            // Extract JWT
            val token = extractToken(exchange.request.headers)
                ?: return@GatewayFilter unauthorized(exchange)

            // Decode JWT header
            val header = decodeHeader(token)
                ?: return@GatewayFilter unauthorized(exchange)

            val alg = header["alg"] as? String ?: return@GatewayFilter unauthorized(exchange)
            if (alg != EXPECTED_ALG) return@GatewayFilter unauthorized(exchange)

            val kid = header["kid"] as? String ?: return@GatewayFilter unauthorized(exchange)
            val publicKey = keyResolver.resolve(kid) ?: return@GatewayFilter unauthorized(exchange)

            // Parse JWT
            val claims = try {
                Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .build()
                    .parseClaimsJws(token)
                    .body
            } catch (e: Exception) {
                return@GatewayFilter unauthorized(exchange)
            }

            // Validate ISS & AUD
            if (claims.issuer != EXPECTED_ISS) return@GatewayFilter unauthorized(exchange)
            if (claims.audience == null || !claims.audience.contains(EXPECTED_AUD)) {
                return@GatewayFilter unauthorized(exchange)
            }

            val jti = claims.id ?: return@GatewayFilter unauthorized(exchange)
            val userId = claims.subject ?: return@GatewayFilter unauthorized(exchange)

            redis.hasKey("auth:jti:blacklist:$jti")
                .flatMap { isBlacklisted ->
                    if (isBlacklisted) return@flatMap unauthorized(exchange)

                    val rolesList = (claims["roles"] as? List<*>)?.map { it.toString() } ?: emptyList()
                    val authorities = rolesList.map { SimpleGrantedAuthority("ROLE_$it") }

                    val auth = UsernamePasswordAuthenticationToken(userId, null, authorities)
                    val securityContext = SecurityContextImpl(auth)

                    // Inject headers for downstream services (NOT auth)
                    val mutatedRequest = exchange.request.mutate()
                        .header("X-User-ID", userId)
                        .header("X-User-Role", rolesList.joinToString(","))
                        .build()

                    val newEx = exchange.mutate().request(mutatedRequest).build()

                    chain.filter(newEx)
                        .contextWrite(
                            ReactiveSecurityContextHolder.withSecurityContext(
                                Mono.just(securityContext)
                            )
                        )
                }
        }
    }

    // -----------------------------
    // Helpers (MUST be outside apply())
    // -----------------------------

    private fun extractToken(headers: HttpHeaders): String? {
        val raw = headers.getFirst(HttpHeaders.AUTHORIZATION) ?: return null
        if (!raw.startsWith("Bearer ")) return null
        return raw.substring(7)
    }

    private fun decodeHeader(token: String): Map<String, Any?>? {
        return try {
            val headerPart = token.substringBefore(".")
            val json = String(Base64.getUrlDecoder().decode(headerPart))
            ObjectMapper().readValue(json, Map::class.java) as Map<String, Any?>
        } catch (e: Exception) {
            null
        }
    }

    private fun unauthorized(exchange: org.springframework.web.server.ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        return exchange.response.setComplete()
    }
}
