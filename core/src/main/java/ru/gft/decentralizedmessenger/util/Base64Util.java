package ru.gft.decentralizedmessenger.util;

import java.util.Base64;

/** Base64 helpers mirroring api.utils.Other.bytes_to_base64 / base64_to_bytes. */
public final class Base64Util {
    private Base64Util() {}

    public static String bytesToBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public static byte[] base64ToBytes(String data) {
        return Base64.getDecoder().decode(data);
    }
}
