package com.rido.auth.controller;

import com.rido.auth.crypto.JwtKeyStore;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.Map;

@RestController
@RequestMapping("/auth/keys")   // <-- correct root
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

    // ------------------------------
    // ADMIN-ONLY KEY ROTATION
    // ------------------------------
    @PostMapping("/rotate")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> rotateKey() {
        keyStore.rotate();
        return Map.of(
            "status", "ok",
            "newKid", keyStore.getCurrentKid()
        );
    }
}
