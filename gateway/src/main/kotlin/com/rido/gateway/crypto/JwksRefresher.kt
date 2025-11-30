package com.rido.gateway.crypto

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class JwksRefresher(
    private val resolver: JwtKeyResolver
) {

    private val log = LoggerFactory.getLogger(JwksRefresher::class.java)

    private val client = WebClient.builder()
        .baseUrl("http://auth:8081")
        .build()

    // Refresh JWKS every 10 seconds
    @Scheduled(fixedDelay = 10000)
    fun refresh() {
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
                        }
                        log.info("🔁 JWKS refreshed ({} keys)", list.size)
                    } catch (ex: Exception) {
                        log.error("❌ JWKS parse failed: {}", ex.message)
                    }
                },
                { err ->
                    log.error("❌ JWKS refresh failed: {}", err.message)
                }
            )
    }
}
