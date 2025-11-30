package com.rido.gateway.security

import com.rido.gateway.crypto.JwtKeyResolver
import io.jsonwebtoken.Jwts
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.security.PublicKey

@Component
class JwtValidator(
    private val keyResolver: JwtKeyResolver
) {

    fun validate(token: String): Mono<Map<String, Any>> {
        return try {

            // ==========================================
            // 1️⃣ Parse header to extract KID
            // ==========================================
            val header = Jwts.parserBuilder()
                .build()
                .parseClaimsJwt(token)
                .header

            val kid = header["kid"] as String?

                ?: return Mono.error(RuntimeException("missing kid"))

            // ==========================================
            // 2️⃣ Resolve RSA Public Key using JWKS / Cache
            // ==========================================
            val publicKey: PublicKey = keyResolver.resolve(kid)
                ?: return Mono.error(RuntimeException("unknown kid"))

            // ==========================================
            // 3️⃣ Parse RS256 JWT with correct key
            // ==========================================
            val claims = Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(token)
                .body

            val result = mapOf(
                "userId" to claims.subject,
                "jti" to claims.id,
                "exp" to claims.expiration.time
            )

            Mono.just(result)

        } catch (e: Exception) {
            Mono.error(e)
        }
    }
}
