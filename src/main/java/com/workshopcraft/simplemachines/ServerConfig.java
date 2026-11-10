package com.workshopcraft.simplemachines;

import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** config/simplemachines-server.properties. Missing keys are written with their defaults; edits apply within a second. */
public final class ServerConfig {
    public static final String TERRAIN_DAMAGE = "overvoltage_terrain_damage";
    public static final String CHUNK_LOADING = "entangled_chunk_loading";
    public static final String FE_PER_CHUNK = "entangled_fe_per_chunk";
    public static final String MAX_RANGE = "entangled_max_range";
    public static final String MAX_CHUNKS = "entangled_max_chunks_per_player";
    public static final String LOAD_OFFLINE = "entangled_load_when_owner_offline";
    public static final int DEFAULT_FE_PER_CHUNK = 32, DEFAULT_MAX_RANGE = 5, DEFAULT_MAX_CHUNKS = 75;
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(TERRAIN_DAMAGE, "true");
        DEFAULTS.put(CHUNK_LOADING, "true");
        DEFAULTS.put(FE_PER_CHUNK, String.valueOf(DEFAULT_FE_PER_CHUNK));
        DEFAULTS.put(MAX_RANGE, String.valueOf(DEFAULT_MAX_RANGE));
        DEFAULTS.put(MAX_CHUNKS, String.valueOf(DEFAULT_MAX_CHUNKS));
        DEFAULTS.put(LOAD_OFFLINE, "true");
    }

    private static Properties props = new Properties();
    private static long readAt = Long.MIN_VALUE;
    private static long modified = Long.MIN_VALUE;

    private ServerConfig() {
    }

    /** Adds a key with its default, for features that only exist on some versions. */
    public static void define(String key, String value) {
        DEFAULTS.putIfAbsent(key, value);
    }

    private static synchronized Properties load(MinecraftServer server) {
        final long now = System.nanoTime();
        if (readAt != Long.MIN_VALUE && now - readAt < 1_000_000_000L) return props;
        readAt = now;
        final Path file = server.getServerDirectory().resolve("config").resolve("simplemachines-server.properties");
        try {
            final long stamp = Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : -1;
            if (stamp == modified) return props;
            final Properties p = new Properties();
            if (stamp >= 0) {
                try (Reader r = Files.newBufferedReader(file)) {
                    p.load(r);
                }
            }
            boolean missing = false;
            for (Map.Entry<String, String> e : DEFAULTS.entrySet()) {
                if (!p.containsKey(e.getKey())) {
                    p.setProperty(e.getKey(), e.getValue());
                    missing = true;
                }
            }
            if (missing) {
                Files.createDirectories(file.getParent());
                try (Writer w = Files.newBufferedWriter(file)) {
                    p.store(w, "Simple Machines server settings");
                }
            }
            modified = Files.getLastModifiedTime(file).toMillis();
            props = p;
        } catch (IOException e) {
            return props;
        }
        return props;
    }

    /** Reads every key once so a fresh server writes the whole file. */
    public static void init(MinecraftServer server) {
        readAt = Long.MIN_VALUE;
        load(server);
    }

    public static String string(MinecraftServer server, String key) {
        return load(server).getProperty(key, DEFAULTS.getOrDefault(key, "")).trim();
    }

    public static boolean bool(MinecraftServer server, String key) {
        return !"false".equalsIgnoreCase(load(server).getProperty(key, DEFAULTS.get(key)).trim());
    }

    public static int integer(MinecraftServer server, String key) {
        try {
            return Math.max(0, Integer.parseInt(load(server).getProperty(key, DEFAULTS.get(key)).trim()));
        } catch (NumberFormatException e) {
            return Integer.parseInt(DEFAULTS.get(key));
        }
    }
}
