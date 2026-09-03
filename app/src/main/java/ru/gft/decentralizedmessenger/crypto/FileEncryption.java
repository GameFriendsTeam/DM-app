package ru.gft.decentralizedmessenger.crypto;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Mirrors api.utils.Encryption.FileEncryption (Fernet).
 * Format: 0x80 | timestamp(8, big-endian) | IV(16) | ciphertext(AES-128-CBC, PKCS7) | HMAC-SHA256(32).
 * The signing key and encryption key are derived from a 32-byte key via HKDF-style splitting
 * (signing key = SHA256(key | 0), encryption key = SHA256(key | 1)) — matching Fernet's spec.
 */
public class FileEncryption {
    private final byte[] key;

    public FileEncryption() {
        this.key = new byte[32];
        new SecureRandom().nextBytes(this.key);
    }

    public FileEncryption(byte[] key) {
        this.key = key;
    }

    public byte[] getKey() { return key; }

    private byte[][] splitKey() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(key);
            md.update((byte) 0);
            byte[] signingKey = md.digest();
            md.reset();
            md.update(key);
            md.update((byte) 1);
            byte[] encKey = md.digest();
            return new byte[][]{signingKey, encKey};
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] encrypt(byte[] data) {
        try {
            byte[][] keys = splitKey();
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keys[1], "AES"), new IvParameterSpec(iv));
            byte[] ciphertext = cipher.doFinal(data);

            long timestamp = java.time.Instant.now().getEpochSecond();
            ByteBuffer buf = ByteBuffer.allocate(1 + 8 + 16 + ciphertext.length);
            buf.order(ByteOrder.BIG_ENDIAN);
            buf.put((byte) 0x80);
            buf.putLong(timestamp);
            buf.put(iv);
            buf.put(ciphertext);
            byte[] body = buf.array();

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keys[0], "HmacSHA256"));
            byte[] hmac = mac.doFinal(body);
            byte[] result = new byte[body.length + 32];
            System.arraycopy(body, 0, result, 0, body.length);
            System.arraycopy(hmac, 0, result, body.length, 32);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("File encryption failed", e);
        }
    }

    public byte[] decrypt(byte[] token) {
        try {
            if (token.length < 1 + 8 + 16 + 32) throw new SecurityException("Token too short");
            byte[][] keys = splitKey();
            int bodyLen = token.length - 32;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keys[0], "HmacSHA256"));
            byte[] expected = mac.doFinal(Arrays.copyOf(token, bodyLen));
            if (!MessageDigest.isEqual(expected, Arrays.copyOfRange(token, bodyLen, token.length))) {
                throw new SecurityException("HMAC mismatch");
            }
            byte[] iv = Arrays.copyOfRange(token, 9, 25);
            byte[] ciphertext = Arrays.copyOfRange(token, 25, bodyLen);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keys[1], "AES"), new IvParameterSpec(iv));
            return cipher.doFinal(ciphertext);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("File decryption failed: " + e.getMessage());
        }
    }
}
