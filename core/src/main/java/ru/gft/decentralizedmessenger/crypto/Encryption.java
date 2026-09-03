package ru.gft.decentralizedmessenger.crypto;

import org.bouncycastle.crypto.agreement.X25519Agreement;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

/**
 * X25519 key agreement + AES-256-GCM. Mirrors api.utils.Encryption.Encryption.
 * Used for the client<->server channel.
 */
public class Encryption {
    private X25519PrivateKeyParameters privateKey;
    private X25519PublicKeyParameters publicKey;
    private byte[] shared;

    public void generateKeypair() {
        privateKey = new X25519PrivateKeyParameters(new SecureRandom());
        publicKey = privateKey.generatePublicKey();
    }

    public byte[] serializePublicKey() {
        return publicKey.getEncoded();
    }

    public static X25519PublicKeyParameters loadPublicKey(byte[] data) {
        return new X25519PublicKeyParameters(data, 0);
    }

    public byte[] deriveSharedKey(X25519PublicKeyParameters peerPublic) {
        X25519Agreement agreement = new X25519Agreement();
        agreement.init(privateKey);
        byte[] sharedSecret = new byte[32];
        agreement.calculateAgreement(peerPublic, sharedSecret, 0);

        HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
        hkdf.init(new HKDFParameters(sharedSecret, null, "e2ee-chat".getBytes()));
        shared = new byte[32];
        hkdf.generateBytes(shared, 0, 32);
        return shared;
    }

    /** Returns {nonce, ciphertext}. */
    public byte[][] encryptMessage(byte[] plaintext) {
        try {
            byte[] nonce = new byte[12];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(shared, "AES"),
                    new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return new byte[][]{nonce, ciphertext};
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public byte[] decryptMessage(byte[] nonce, byte[] ciphertext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(shared, "AES"),
                    new GCMParameterSpec(128, nonce));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    public boolean isReady() { return shared != null; }
}
