package ru.gft.decentralizedmessenger.packet;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Mirrors api.Packet.Packet — a JSON-backed dictionary with padding helpers.
 * Wire format is length-prefixed JSON; staticPacket pads a packet up to maxLen with 'b'.
 */
public class Packet {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    protected Map<String, Object> data;

    public Packet(Map<String, Object> data) {
        // Always keep a mutable map — Python's Packet wraps a plain dict and
        // set() must work. Map.of(...) returns an immutable map, so copy it.
        this.data = new java.util.HashMap<>(data);
    }

    public Object get(String key) { return get(key, null); }

    public Object get(String key, Object def) {
        return data.getOrDefault(key, def);
    }

    public String getString(String key, String def) {
        Object v = data.get(key);
        return v == null ? def : v.toString();
    }

    public Map<String, Object> getAll() { return data; }

    public void set(String key, Object value) { data.put(key, value); }

    public String getStr() { return GSON.toJson(data); }

    public byte[] getBytes() { return getStr().getBytes(StandardCharsets.UTF_8); }

    public int length() { return getStr().length(); }

    public Object getItem(String name) { return data.get(name); }

    @Override
    public String toString() { return getStr(); }

    public static Packet fromRaw(byte[] rawPacket) {
        if (rawPacket == null) return new Packet(new java.util.HashMap<>());
        return fromRaw(new String(rawPacket, StandardCharsets.UTF_8));
    }

    public static Packet fromRaw(String rawPacket) {
        if (rawPacket == null || rawPacket.isEmpty()) return new Packet(new java.util.HashMap<>());
        try {
            Map<String, Object> m = GSON.fromJson(rawPacket, MAP_TYPE);
            if (m == null) m = new java.util.HashMap<>();
            return new Packet(m);
        } catch (Exception e) {
            return new Packet(new java.util.HashMap<>());
        }
    }

    public static Packet fromRaw(Map<String, Object> data) {
        return new Packet(data);
    }

    /** Pads the packet JSON up to maxLen with 'b' characters (matches Python staticPacket). */
    public static String staticPacket(Map<String, Object> data, int maxLen) {
        Packet p = new Packet(data);
        int pl = p.length();
        if (pl > maxLen) throw new RuntimeException("Length limit!");
        int remnant = maxLen - pl;
        StringBuilder sb = new StringBuilder(p.getStr());
        for (int i = 0; i < remnant; i++) sb.append('b');
        return sb.toString();
    }
}
