
package com.rido.auth.service;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

public class HashGenerator {
    public static void main(String[] args) {
        // Matches SecurityConfig.java:
        // saltLength=16, hashLength=32, parallelism=1, memory=4096 (1<<12), iterations=3
        Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 4096, 3);
        String hash = encoder.encode("dummy_password_for_timing_mitigation");
        System.out.println("GENERATED_HASH=" + hash);
    }
}
