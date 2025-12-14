package com.rido.auth.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashUtilsTest {

    @Nested
    @DisplayName("SHA-256 Hash Tests")
    class Sha256 {

        @Test
        @DisplayName("Should generate deterministic hash")
        void shouldGenerateDeterministicHash() {
            String input = "test-input-string";

            String hash1 = HashUtils.sha256(input);
            String hash2 = HashUtils.sha256(input);

            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("Should generate different hashes for different inputs")
        void shouldGenerateDifferentHashes_forDifferentInputs() {
            String hash1 = HashUtils.sha256("input1");
            String hash2 = HashUtils.sha256("input2");

            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("Should generate hex-encoded hash")
        void shouldGenerateHexEncodedHash() {
            String hash = HashUtils.sha256("test");

            assertThat(hash).matches("^[a-f0-9]+$");
            assertThat(hash).hasSize(64);
        }

        @Test
        @DisplayName("Should handle empty string")
        void shouldHandleEmptyString() {
            String hash = HashUtils.sha256("");

            assertThat(hash).isNotEmpty();
            assertThat(hash).hasSize(64);
        }

        @Test
        @DisplayName("Should handle unicode characters")
        void shouldHandleUnicodeCharacters() {
            String hash = HashUtils.sha256("你好世界🌍");

            assertThat(hash).isNotEmpty();
            assertThat(hash).hasSize(64);
        }
    }
}
