package ru.gft.decentralizedmessenger.packet;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import ru.gft.decentralizedmessenger.crypto.Encryption;
import ru.gft.decentralizedmessenger.util.Base64Util;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Mirrors api.EncryptedPacket.EncryptedPacket.
 * Serializes the inner JSON dict as an AES-GCM encrypted [nonce, ciphertext] array,
 * padded with ":encryptedbbb..." up to maxLen.
 */
public class EncryptedPacket extends Packet {
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<String>>() {}.getType();

    private final Encryption encrypt;
    private String cachedStr = null;

    public EncryptedPacket(Map<String, Object> data, Encryption encrypt) {
        super(data);
        this.encrypt = encrypt;
    }

    @Override
    public void set(String key, Object value) {
        data.put(key, value);
        cachedStr = null;
    }

    @Override
    public String getStr() {
        if (cachedStr == null) {
            String normal = GSON.toJson(data);
            byte[][] enc = encrypt.encryptMessage(normal.getBytes(StandardCharsets.UTF_8));
            cachedStr = GSON.toJson(new String[]{
                    Base64Util.bytesToBase64(enc[0]),
                    Base64Util.bytesToBase64(enc[1])
            });
        }
        return cachedStr;
    }

    public static EncryptedPacket fromRaw(byte[] encrypted, Encryption encrypt) {
        return fromRaw(new String(encrypted, StandardCharsets.UTF_8), encrypt);
    }

    public static EncryptedPacket fromRaw(String encrypted, Encryption encrypt) {
        List<String> parts = GSON.fromJson(encrypted, LIST_TYPE);
        byte[] nonce = Base64Util.base64ToBytes(parts.get(0));
        byte[] ciphertext = Base64Util.base64ToBytes(parts.get(1));
        byte[] plain = encrypt.decryptMessage(nonce, ciphertext);
        Map<String, Object> m = GSON.fromJson(new String(plain, StandardCharsets.UTF_8), MAP_TYPE);
        return new EncryptedPacket(m, encrypt);
    }

    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    public static String staticPacket(Map<String, Object> data, int maxLen, Encryption encrypt) {
        EncryptedPacket p = new EncryptedPacket(data, encrypt);
        int pl = p.length();
        String encAlert = "encrypted";
        int encLen = encAlert.length();
        if (pl + encLen > maxLen) throw new RuntimeException("Length limit!");
        int remnant = maxLen - pl - encLen;
        StringBuilder sb = new StringBuilder(p.getStr());
        sb.append(":").append(encAlert);
        for (int i = 0; i < remnant - 1; i++) sb.append('b');
        return sb.toString();
    }

    /** Cuts the ":encryptedbbb..." padding and returns the clean JSON array [nonce, ciphertext]. */
    public static String extractPayload(String raw) {
        int packetEnd = raw.lastIndexOf(']');
        return raw.substring(0, packetEnd + 1);
    }
}
