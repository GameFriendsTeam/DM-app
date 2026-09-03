package ru.gft.decentralizedmessenger.crypto;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bouncycastle.crypto.agreement.X25519Agreement;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Mirrors api.utils.Encryption.SecureEncryption.
 * X25519 (ECDH) + Ed25519 (signing) + AES-256-GCM, with a per-user trusted-key store.
 * Used for the client<->client end-to-end channel.
 */
public class SecureEncryption {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private final String username;

    private X25519PrivateKeyParameters x25519Private;
    private X25519PublicKeyParameters x25519Public;
    private Ed25519PrivateKeyParameters ed25519Private;
    private Ed25519PublicKeyParameters ed25519Public;
    private byte[] shared;

    private final Map<String, Map<String, Object>> trustedKeys;

    public SecureEncryption(String username) {
        this.username = username;
        this.trustedKeys = loadTrustedKeys();
    }

    // ---- key generation ----

    public void generateKeypair() {
        x25519Private = new X25519PrivateKeyParameters(new SecureRandom());
        x25519Public = x25519Private.generatePublicKey();
    }

    public void generateSigningKeypair() {
        Ed25519KeyPairGenerator gen = new Ed25519KeyPairGenerator();
        gen.init(new Ed25519KeyGenerationParameters(new SecureRandom()));
        var pair = gen.generateKeyPair();
        ed25519Private = (Ed25519PrivateKeyParameters) pair.getPrivate();
        ed25519Public = (Ed25519PublicKeyParameters) pair.getPublic();
    }

    public byte[] serializeX25519Public() { return x25519Public.getEncoded(); }
    public byte[] serializeEd25519Public() { return ed25519Public.getEncoded(); }

    public byte[] signMessage(byte[] message) {
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, ed25519Private);
        signer.update(message, 0, message.length);
        return signer.generateSignature();
    }

    public boolean verifySignature(byte[] message, byte[] signature, byte[] peerEd25519PublicBytes) {
        try {
            Ed25519PublicKeyParameters peerPublic = new Ed25519PublicKeyParameters(peerEd25519PublicBytes, 0);
            Ed25519Signer verifier = new Ed25519Signer();
            verifier.init(false, peerPublic);
            verifier.update(message, 0, message.length);
            return verifier.verifySignature(signature);
        } catch (Exception e) {
            return false;
        }
    }

    public String getFingerprint(byte[] keyBytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(keyBytes);
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16; i++) hex.append(String.format("%02X", hash[i]));
            String f = hex.toString();
            StringBuilder grouped = new StringBuilder();
            for (int i = 0; i < 32; i += 4) {
                if (i > 0) grouped.append(' ');
                grouped.append(f, i, i + 4);
            }
            return grouped.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public byte[] deriveSharedKey(X25519PublicKeyParameters peerX25519Public) {
        X25519Agreement agreement = new X25519Agreement();
        agreement.init(x25519Private);
        byte[] sharedSecret = new byte[32];
        agreement.calculateAgreement(peerX25519Public, sharedSecret, 0);

        HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
        hkdf.init(new HKDFParameters(sharedSecret, null, "secure-chat-v1".getBytes()));
        shared = new byte[32];
        hkdf.generateBytes(shared, 0, 32);
        return shared;
    }

    public byte[][] encryptMessage(byte[] plaintext) {
        if (shared == null) throw new SecurityException("Shared key not established");
        try {
            byte[] nonce = new byte[12];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(shared, "AES"),
                    new GCMParameterSpec(128, nonce));
            return new byte[][]{nonce, cipher.doFinal(plaintext)};
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public byte[] decryptMessage(byte[] nonce, byte[] ciphertext) {
        if (shared == null) throw new SecurityException("Shared key not established");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(shared, "AES"),
                    new GCMParameterSpec(128, nonce));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    // ---- trusted key store ----

    public boolean verifyPeerManually(String peerUsername, byte[] peerEd25519PublicBytes,
                                       byte[] peerX25519PublicBytes, java.util.Scanner stdin) {
        String myFp = getFingerprint(serializeEd25519Public());
        String peerFp = getFingerprint(peerEd25519PublicBytes);
        System.out.println("\n Verification of key by " + peerUsername);
        System.out.println("----------------------------------------");
        System.out.println("You're fingerprint: " + myFp);
        System.out.println("Fingerprint " + peerUsername + ": " + peerFp);
        System.out.println("----------------------------------------");
        System.out.print("\nFingerprints match? (yes/no): ");
        String response = stdin.nextLine().trim().toLowerCase();
        if (response.equals("yes")) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("ed25519_public", Base64.getEncoder().encodeToString(peerEd25519PublicBytes));
            entry.put("fingerprint", peerFp);
            entry.put("verified_at", Instant.now().getEpochSecond());
            if (peerX25519PublicBytes != null) {
                entry.put("x25519_public", Base64.getEncoder().encodeToString(peerX25519PublicBytes));
            }
            trustedKeys.put(peerUsername, entry);
            saveTrustedKeys();
            System.out.println("Key verified and saved");
            return true;
        }
        System.out.println("Verification cancelled");
        return false;
    }

    public TrustedPeerKey getTrustedPeerKey(String peerUsername) {
        Map<String, Object> keyData = trustedKeys.get(peerUsername);
        if (keyData == null) return null;
        return new TrustedPeerKey(
                Base64.getDecoder().decode((String) keyData.get("ed25519_public")),
                keyData.containsKey("x25519_public")
                        ? Base64.getDecoder().decode((String) keyData.get("x25519_public")) : new byte[0],
                (String) keyData.get("fingerprint"));
    }

    public static class TrustedPeerKey {
        public final byte[] ed25519Public;
        public final byte[] x25519Public;
        public final String fingerprint;
        public TrustedPeerKey(byte[] ed, byte[] x, String fp) {
            this.ed25519Public = ed; this.x25519Public = x; this.fingerprint = fp;
        }
    }

    private Map<String, Map<String, Object>> loadTrustedKeys() {
        try {
            Path p = Paths.get("keys", ".trusted_keys_" + username + ".json");
            if (!Files.exists(p)) return new HashMap<>();
            String content = Files.readString(p, StandardCharsets.UTF_8);
            Map<String, Map<String, Object>> m = GSON.fromJson(content, MAP_TYPE);
            return m != null ? m : new HashMap<>();
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private void saveTrustedKeys() {
        try {
            Path dir = Paths.get("keys");
            Files.createDirectories(dir);
            Path p = dir.resolve(".trusted_keys_" + username + ".json");
            Files.writeString(p, GSON.toJson(trustedKeys), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Could not save trusted keys", e);
        }
    }
}
