package com.rido.gateway.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

@Component
class JwtValidator(
    @Value("\${jwt.secret}") private val jwtSecret: String,

    @Qualifier("reactiveStringRedisTemplate")
    private val redis: ReactiveStringRedisTemplate
) {

    fun validateAndGetClaims(token: String): Mono<Claims> {

        val signingKey = Keys.hmacShaKeyFor(jwtSecret.toByteArray(StandardCharsets.UTF_8))

        return redis.hasKey("auth:jti:blacklist:$token")
            .flatMap { isBlacklisted ->

                if (isBlacklisted) {
                    return@flatMap Mono.error<Claims>(RuntimeException("Token blacklisted"))
                }

                // Parse JWT
                return@flatMap Mono.fromCallable {
                    Jwts.parserBuilder()
                        .setSigningKey(signingKey)
                        .build()
                        .parseClaimsJws(token)
                        .body
                }
            }
    }
}
