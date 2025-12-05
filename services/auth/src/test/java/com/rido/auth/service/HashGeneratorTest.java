package com.rido.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

public class HashGeneratorTest {
    @Test
    public void generateHash() {
        Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 4096, 3);
        String hash = encoder.encode("dummy_password");
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("generated_hash.txt"), hash);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }
}
