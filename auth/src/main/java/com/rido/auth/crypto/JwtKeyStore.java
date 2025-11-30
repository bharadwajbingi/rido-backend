package com.rido.auth.crypto;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JwtKeyStore {

    // Holds active + previous keys
    private final Map<String, KeyPair> keys = new ConcurrentHashMap<>();

    // Current active KID
    private volatile String activeKid;

    // ===========================
    // INIT: generate first key
    // ===========================
    @PostConstruct
    public void init() {
        rotate(); // generate initial key
    }

    // ===========================
    // GENERATE NEW KEYPAIR
    // ===========================
    public synchronized void rotate() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);

            KeyPair keyPair = gen.generateKeyPair();
            String kid = UUID.randomUUID().toString();

            keys.put(kid, keyPair);
            activeKid = kid;

        } catch (Exception e) {
            throw new RuntimeException("Failed to rotate signing keys", e);
        }
    }

    // ===========================
    // GET CURRENT SIGNING KEY
    // ===========================
    public KeyPair getCurrentKeyPair() {
        return keys.get(activeKid);
    }

    public String getCurrentKid() {
        return activeKid;
    }

    // ===========================
    // GET KEY BY KID
    // ===========================
    public KeyPair getKeyPair(String kid) {
        return keys.get(kid);
    }

    public RSAPrivateKey getPrivate(String kid) {
        return (RSAPrivateKey) keys.get(kid).getPrivate();
    }

    public RSAPublicKey getPublic(String kid) {
        return (RSAPublicKey) keys.get(kid).getPublic();
    }

    // ===========================
    // JWKS endpoint (public keys only)
    // ===========================
    public List<Map<String, Object>> getJwks() {
        List<Map<String, Object>> jwks = new ArrayList<>();

        keys.forEach((kid, pair) -> {
            RSAPublicKey pub = (RSAPublicKey) pair.getPublic();

            Map<String, Object> jwk = new HashMap<>();
            jwk.put("kty", "RSA");
            jwk.put("kid", kid);
            jwk.put("alg", "RS256");
            jwk.put("use", "sig");
            jwk.put("n", Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getModulus().toByteArray()));
            jwk.put("e", Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getPublicExponent().toByteArray()));

            jwks.add(jwk);
        });

        return jwks;
    }
}
