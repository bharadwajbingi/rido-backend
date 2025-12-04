package com.rido.gateway.crypto

import org.springframework.stereotype.Component
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.math.BigInteger
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Component
class JwtKeyResolver {

    private val keys = ConcurrentHashMap<String, RSAPublicKey>()

    fun put(kid: String, pub: RSAPublicKey) {
        keys[kid] = pub
    }

    fun resolve(kid: String): RSAPublicKey? = keys[kid]

    // Convert JWKS n/e → RSAPublicKey
    fun addFromJwk(kid: String, n: String, e: String) {
        val modulus = BigInteger(1, Base64.getUrlDecoder().decode(n))
        val exponent = BigInteger(1, Base64.getUrlDecoder().decode(e))

        val spec = RSAPublicKeySpec(modulus, exponent)
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(spec) as RSAPublicKey

        put(kid, publicKey)
    }
}
