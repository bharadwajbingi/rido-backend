package com.rido.gateway.crypto

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class JwksLoader(
    private val resolver: JwtKeyResolver,
    @Qualifier("jwksWebClient") private val jwksWebClient: WebClient
) {

    private val log = LoggerFactory.getLogger(JwksLoader::class.java)

    @org.springframework.beans.factory.annotation.Value("\${JWKS_URL}")
    private lateinit var jwksUrl: String

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

        jwksWebClient.get()
            .uri(jwksUrl)
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
