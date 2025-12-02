package com.rido.auth.crypto;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JwtKeyStore {

    // Holds active + previous keys
    private final Map<String, KeyPair> keys = new ConcurrentHashMap<>();
    private volatile String activeKid;
    
    private final org.springframework.vault.core.VaultTemplate vaultTemplate;
    private static final String VAULT_KEYS_PATH = "secret/data/auth/keys";

    public JwtKeyStore(org.springframework.vault.core.VaultTemplate vaultTemplate) {
        this.vaultTemplate = vaultTemplate;
    }

    // ===========================
    // INIT: load or generate keys
    // ===========================
    @PostConstruct
    public void init() {
        try {
            // Try to load from Vault
            var response = vaultTemplate.read(VAULT_KEYS_PATH);
            
            if (response != null && response.getData() != null) {
                Map<String, Object> data = (Map<String, Object>) response.getData().get("data");
                if (data != null && !data.isEmpty()) {
                    loadKeysFromVault(data);
                    return;
                }
            }
            
            // If no keys found, generate new ones
            rotate();
            
        } catch (Exception e) {
            // Fallback to local generation if Vault fails (e.g. during tests without Vault)
            System.err.println("Warning: Failed to load keys from Vault: " + e.getMessage());
            rotate();
        }
    }

    private void loadKeysFromVault(Map<String, Object> data) {
        try {
            String kid = (String) data.get("kid");
            String privKeyStr = (String) data.get("privateKey");
            String pubKeyStr = (String) data.get("publicKey");

            KeyFactory kf = KeyFactory.getInstance("RSA");
            
            byte[] privBytes = Base64.getDecoder().decode(privKeyStr);
            PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(privBytes);
            PrivateKey privKey = kf.generatePrivate(privSpec);

            byte[] pubBytes = Base64.getDecoder().decode(pubKeyStr);
            X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(pubBytes);
            PublicKey pubKey = kf.generatePublic(pubSpec);

            KeyPair kp = new KeyPair(pubKey, privKey);
            
            keys.put(kid, kp);
            activeKid = kid;
            
            System.out.println("Loaded JWT signing key from Vault. KID: " + kid);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse keys from Vault", e);
        }
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
            
            // Persist to Vault
            storeKeyInVault(kid, keyPair);

        } catch (Exception e) {
            throw new RuntimeException("Failed to rotate signing keys", e);
        }
    }

    private void storeKeyInVault(String kid, KeyPair kp) {
        try {
            String privKey = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
            String pubKey = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());

            Map<String, Object> secrets = new HashMap<>();
            secrets.put("kid", kid);
            secrets.put("privateKey", privKey);
            secrets.put("publicKey", pubKey);
            
            Map<String, Object> data = new HashMap<>();
            data.put("data", secrets);

            vaultTemplate.write(VAULT_KEYS_PATH, data);
            System.out.println("Persisted new JWT signing key to Vault. KID: " + kid);
            
        } catch (Exception e) {
            System.err.println("Warning: Failed to persist key to Vault: " + e.getMessage());
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
