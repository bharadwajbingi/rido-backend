package com.rido.gateway.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.rido.gateway.crypto.JwtKeyResolver
import io.jsonwebtoken.Jwts
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.*
import java.security.PublicKey

@Component
class JwtValidator(
    private val keyResolver: JwtKeyResolver
) {

    private val mapper = ObjectMapper()

    fun validate(token: String): Mono<Map<String, Any>> {
        return try {

            // ==========================================
            // 1️⃣ Decode JWT header manually (Base64URL)
            // ==========================================
            val headerPart = token.substringBefore(".")
            val headerBytes = Base64.getUrlDecoder().decode(headerPart)

            @Suppress("UNCHECKED_CAST")
            val header: Map<String, Any> =
                mapper.readValue(headerBytes, Map::class.java) as Map<String, Any>

            val kid = header["kid"] as? String
                ?: return Mono.error(RuntimeException("missing kid"))

            // ==========================================
            // 2️⃣ Resolve RSA Public Key from JWKS cache
            // ==========================================
            val publicKey: PublicKey = keyResolver.resolve(kid)
                ?: return Mono.error(RuntimeException("unknown kid"))

            // ==========================================
            // 3️⃣ Parse full signed JWT (RS256)
            // ==========================================
            val claims = Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(token)
                .body

            // ==========================================
            // 4️⃣ Build result
            // ==========================================
            val result = mutableMapOf<String, Any>(
                "userId" to claims.subject,
                "jti" to claims.id,
                "exp" to claims.expiration.time
            )

            @Suppress("UNCHECKED_CAST")
            val roles = claims["roles"] as? List<String> ?: emptyList()
            result["roles"] = roles

            Mono.just(result)

        } catch (e: Exception) {
            Mono.error(e)
        }
    }
}
