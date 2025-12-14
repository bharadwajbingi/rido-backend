package com.rido.auth.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.security.KeyPair;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtKeyStoreTest {

    @Mock
    private VaultTemplate vaultTemplate;

    private JwtKeyStore jwtKeyStore;

    @BeforeEach
    void setUp() {
        when(vaultTemplate.read(anyString())).thenReturn(null);
        jwtKeyStore = new JwtKeyStore(vaultTemplate);
        jwtKeyStore.init();
    }

    @Nested
    @DisplayName("Key Rotation Tests")
    class KeyRotation {

        @Test
        @DisplayName("Should generate new key pair on rotate")
        void shouldRotate_generatesNewKeyPair() {
            String originalKid = jwtKeyStore.getCurrentKid();

            jwtKeyStore.rotate();

            String newKid = jwtKeyStore.getCurrentKid();
            assertThat(newKid).isNotEqualTo(originalKid);
        }

        @Test
        @DisplayName("Should retain old key after rotation")
        void shouldRetainOldKey_afterRotation() {
            String originalKid = jwtKeyStore.getCurrentKid();
            KeyPair originalKeyPair = jwtKeyStore.getKeyPair(originalKid);

            jwtKeyStore.rotate();

            KeyPair retrievedOldKeyPair = jwtKeyStore.getKeyPair(originalKid);
            assertThat(retrievedOldKeyPair).isEqualTo(originalKeyPair);
        }
    }

    @Nested
    @DisplayName("Get Key Tests")
    class GetKey {

        @Test
        @DisplayName("Should get current key pair")
        void shouldGetCurrentKeyPair_returnsActiveKey() {
            KeyPair keyPair = jwtKeyStore.getCurrentKeyPair();

            assertThat(keyPair).isNotNull();
            assertThat(keyPair.getPrivate()).isNotNull();
            assertThat(keyPair.getPublic()).isNotNull();
        }

        @Test
        @DisplayName("Should get key pair by kid")
        void shouldGetKeyPair_byKid() {
            String kid = jwtKeyStore.getCurrentKid();

            KeyPair keyPair = jwtKeyStore.getKeyPair(kid);

            assertThat(keyPair).isNotNull();
        }

        @Test
        @DisplayName("Should return null for unknown kid")
        void shouldReturnNull_forUnknownKid() {
            KeyPair keyPair = jwtKeyStore.getKeyPair("unknown-kid");

            assertThat(keyPair).isNull();
        }

        @Test
        @DisplayName("Should get public key by kid")
        void shouldGetPublicKey_byKid() {
            String kid = jwtKeyStore.getCurrentKid();

            assertThat(jwtKeyStore.getPublic(kid)).isNotNull();
        }

        @Test
        @DisplayName("Should get private key by kid")
        void shouldGetPrivateKey_byKid() {
            String kid = jwtKeyStore.getCurrentKid();

            assertThat(jwtKeyStore.getPrivate(kid)).isNotNull();
        }
    }

    @Nested
    @DisplayName("JWKS Tests")
    class Jwks {

        @Test
        @DisplayName("Should generate JWKS for all keys")
        void shouldGenerateJwks_forAllKeys() {
            jwtKeyStore.rotate();

            List<Map<String, Object>> jwks = jwtKeyStore.getJwks();

            assertThat(jwks).hasSize(2);
            assertThat(jwks).allSatisfy(jwk -> {
                assertThat(jwk).containsKey("kty");
                assertThat(jwk).containsKey("kid");
                assertThat(jwk).containsKey("alg");
                assertThat(jwk).containsKey("use");
                assertThat(jwk).containsKey("n");
                assertThat(jwk).containsKey("e");
                assertThat(jwk.get("kty")).isEqualTo("RSA");
                assertThat(jwk.get("alg")).isEqualTo("RS256");
                assertThat(jwk.get("use")).isEqualTo("sig");
            });
        }
    }

    @Nested
    @DisplayName("Resolver Tests")
    class Resolver {

        @Test
        @DisplayName("Should return signing key resolver")
        void shouldReturnResolver() {
            assertThat(jwtKeyStore.getResolver()).isNotNull();
        }
    }
}
