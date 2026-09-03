package ru.gft.decentralizedmessenger.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Minimal pluggable logger (replacement for Python logging). */
public class Logger {
    private static volatile Level level = Level.INFO;
    private static volatile boolean fileEnabled = false;
    private static Path filePath;

    public enum Level { DEBUG, INFO, WARNING, ERROR }

    public static void setLevel(Level l) { level = l; }
    public static void enableFile(String path) {
        filePath = Paths.get(path);
        fileEnabled = true;
    }

    private static boolean enabled(Level l) {
        return l.ordinal() >= level.ordinal();
    }

    private static void log(Level l, String msg) {
        if (!enabled(l)) return;
        String line = "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) +
                "] [" + l + "] " + msg;
        System.out.println(line);
        if (fileEnabled && filePath != null) {
            try {
                Files.writeString(filePath, line + "\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {}
        }
    }

    public static void debug(String msg) { log(Level.DEBUG, msg); }
    public static void info(String msg) { log(Level.INFO, msg); }
    public static void warning(String msg) { log(Level.WARNING, msg); }
    public static void error(String msg) { log(Level.ERROR, msg); }
}
