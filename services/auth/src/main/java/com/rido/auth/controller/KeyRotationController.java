package com.rido.auth.controller;

import com.rido.auth.crypto.JwtKeyStore;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * Public JWKS endpoint for JWT verification.
 * Key rotation is now handled by AdminController on port 9090.
 */
@RestController
@RequestMapping("/auth/keys")
public class KeyRotationController {

    private final JwtKeyStore keyStore;

    public KeyRotationController(JwtKeyStore keyStore) {
        this.keyStore = keyStore;
    }

    // ------------------------------
    // Public JWKS endpoint
    // ------------------------------
    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> wellKnownJwks() {
        return Map.of("keys", keyStore.getJwks());
    }

    @GetMapping("/jwks.json")
    public Map<String, Object> jwks() {
        return Map.of("keys", keyStore.getJwks());
    }
}
