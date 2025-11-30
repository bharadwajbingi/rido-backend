package com.rido.auth.controller;

import com.rido.auth.crypto.JwtKeyStore;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth/keys")
public class KeyRotationController {

    private final JwtKeyStore keyStore;

    public KeyRotationController(JwtKeyStore keyStore) {
        this.keyStore = keyStore;
    }

    @PostMapping("/rotate")
    public Map<String, String> rotateKey() {
        keyStore.rotate();
        return Map.of(
            "status", "ok",
            "newKid", keyStore.getCurrentKid()
        );
    }

    @GetMapping("/jwks.json")
    public Map<String, Object> jwks() {
        return Map.of("keys", keyStore.getJwks());
    }
}
