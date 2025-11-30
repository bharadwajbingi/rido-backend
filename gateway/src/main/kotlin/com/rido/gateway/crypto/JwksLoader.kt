package com.rido.gateway.crypto

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class JwksLoader(
    private val resolver: JwtKeyResolver
) {

    private val log = LoggerFactory.getLogger(JwksLoader::class.java)

    private val client = WebClient.builder()
        .baseUrl("http://auth:8081")
        .build()

    @PostConstruct
    fun initialLoad() {
        loadKeys()
    }

    // 🔥 Run every 10 seconds
    @Scheduled(fixedDelay = 10000)
    fun reloadKeys() {
        loadKeys()
    }

    private fun loadKeys() {
        log.info("🔐 Loading JWKS from auth-service...")

        client.get()
            .uri("/auth/keys/jwks.json")
            .retrieve()
            .bodyToMono(Map::class.java)
            .subscribe(
                { jwks ->
                    try {
                        val list = jwks["keys"] as List<Map<String, String>>

                        list.forEach {
                            val kid = it["kid"]!!
                            val n = it["n"]!!
                            val e = it["e"]!!

                            resolver.addFromJwk(kid, n, e)

                            log.info("🔑 Loaded RSA public key (kid={})", kid)
                        }

                        log.info("✅ JWKS loaded successfully ({} keys)", list.size)
                    } catch (ex: Exception) {
                        log.error("❌ Failed parsing JWKS: {}", ex.message)
                    }
                },
                { err ->
                    log.error("❌ JWKS fetch failed: {}", err.message)
                }
            )
    }
}
