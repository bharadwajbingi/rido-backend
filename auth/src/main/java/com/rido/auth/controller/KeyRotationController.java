package com.rido.auth.controller;

import com.rido.auth.crypto.JwtKeyStore;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;

@RestController
public class KeyRotationController {

    private final JwtKeyStore keyStore;

    public KeyRotationController(JwtKeyStore keyStore) {
        this.keyStore = keyStore;
    }

    @GetMapping("/auth/.well-known/jwks.json")
    public Map<String, Object> wellKnownJwks() {
        return Map.of("keys", keyStore.getJwks());
    }

    @GetMapping("/auth/keys/jwks.json")
    public Map<String, Object> jwks() {
        return Map.of("keys", keyStore.getJwks());
    }


    @PostMapping("/auth/keys/rotate")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> rotateKey() {
        keyStore.rotate();
        return Map.of(
            "status", "ok",
            "newKid", keyStore.getCurrentKid()
        );
    }
}
