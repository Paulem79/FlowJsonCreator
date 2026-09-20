package net.paulem.fjc.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static net.paulem.fjc.utils.FileUtils.getActualJar;

/**
 * Persists what was read from the jars of URL-added mods (name, description, icon as base64...) next to mods.json,
 * keyed by the jar's SHA-1. That way the icon and description show instantly on every launch without downloading
 * the jar again; entries of mods that left the modpack are dropped by {@link #retainOnly(Set)}.
 */
public final class UrlMetaCache {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CACHE_FILE = getActualJar().getParent().resolve(".fjc_url_meta_cache.json").toFile();

    private static final Map<String, JarMetadata> ENTRIES = new ConcurrentHashMap<>();
    private static boolean loaded = false;

    private UrlMetaCache() {
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!CACHE_FILE.exists()) return;
        try {
            JsonElement root = JsonParser.parseString(Files.readString(CACHE_FILE.toPath(), StandardCharsets.UTF_8));
            if (!root.isJsonObject()) return;
            for (var e : root.getAsJsonObject().entrySet()) {
                if (e.getValue().isJsonObject()) ENTRIES.put(e.getKey(), fromJson(e.getValue().getAsJsonObject()));
            }
        } catch (IOException | RuntimeException e) {
            // Unreadable cache: start over, it is only a display optimisation.
        }
    }

    public static @Nullable JarMetadata get(String sha1) {
        ensureLoaded();
        return ENTRIES.get(sha1);
    }

    public static void put(String sha1, JarMetadata meta) {
        ensureLoaded();
        ENTRIES.put(sha1, meta);
        save();
    }

    /** Forgets the mods that are no longer in the modpack. */
    public static void retainOnly(Set<String> sha1s) {
        ensureLoaded();
        if (ENTRIES.keySet().retainAll(sha1s)) save();
    }

    private static synchronized void save() {
        JsonObject root = new JsonObject();
        ENTRIES.forEach((sha1, meta) -> root.add(sha1, toJson(meta)));
        try {
            Files.writeString(CACHE_FILE.toPath(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Non-blocking
        }
    }

    private static JsonObject toJson(JarMetadata m) {
        JsonObject o = new JsonObject();
        o.addProperty("name", m.name());
        o.addProperty("version", m.version());
        o.addProperty("description", m.description());
        o.addProperty("author", m.author());
        o.addProperty("loader", m.loader());
        o.addProperty("icon", m.iconDataUri());
        return o;
    }

    private static JarMetadata fromJson(JsonObject o) {
        return new JarMetadata(str(o, "name"), str(o, "version"), str(o, "description"), str(o, "author"),
                str(o, "loader"), str(o, "icon"));
    }

    private static @Nullable String str(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return el == null || el.isJsonNull() ? null : el.getAsString();
    }
}
