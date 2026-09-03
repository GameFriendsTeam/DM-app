package ru.gft.decentralizedmessenger.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/** JSON-backed config file (replacement for api.utils.Other.Config). */
public class Config {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private final String filename;
    private final Map<String, Object> data = new HashMap<>();

    public Config(String filename) {
        this.filename = filename;
    }

    public Object get(String key, Object def) {
        return data.getOrDefault(key, def);
    }

    public String getString(String key, String def) {
        Object v = data.get(key);
        return v == null ? def : v.toString();
    }

    public Integer getInt(String key, Integer def) {
        Object v = data.get(key);
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return def; }
    }

    public void set(String key, Object value) {
        data.put(key, value);
    }

    public void save() throws IOException {
        Path p = Paths.get(filename);
        Files.writeString(p, GSON.toJson(data), StandardCharsets.UTF_8);
    }

    public void load() throws IOException {
        Path p = Paths.get(filename);
        if (!Files.exists(p)) return;
        String content = Files.readString(p, StandardCharsets.UTF_8);
        Map<String, Object> loaded = GSON.fromJson(content, MAP_TYPE);
        if (loaded != null) data.putAll(loaded);
    }
}
