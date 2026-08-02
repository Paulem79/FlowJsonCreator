package net.paulem.fjc.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import org.jetbrains.annotations.Nullable;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static net.paulem.fjc.utils.FileUtils.getActualJar;

/**
 * Persists resolved CurseForge/Modrinth display names next to mods.json so the app can show
 * real mod names instantly on the next launch instead of "(chargement...)" placeholders while
 * it waits on the network - the resolved names are refreshed silently in the background and the
 * cache is rewritten if anything changed.
 */
public class ResolveCache {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final java.io.File CACHE_FILE = getActualJar().getParent().resolve(".fjc_names_cache.json").toFile();

    private static final ConcurrentHashMap<String, String> curseForgeNames = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> modrinthNames = new ConcurrentHashMap<>();

    private static volatile boolean dirty = false;
    private static volatile boolean loaded = false;

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!CACHE_FILE.exists()) return;

        try (JsonReader reader = new JsonReader(new FileReader(CACHE_FILE))) {
            CacheData data = GSON.fromJson(reader, CacheData.class);
            if (data != null) {
                if (data.curseforge != null) curseForgeNames.putAll(data.curseforge);
                if (data.modrinth != null) modrinthNames.putAll(data.modrinth);
            }
        } catch (IOException e) {
            // Cache corrompu ou illisible : on repart d'un cache vide, pas grave.
        }
    }

    @Nullable
    public static String getCurseForgeName(int projectId) {
        ensureLoaded();
        return curseForgeNames.get(String.valueOf(projectId));
    }

    @Nullable
    public static String getModrinthName(String slug) {
        ensureLoaded();
        return modrinthNames.get(slug);
    }

    public static void putCurseForgeName(int projectId, String name) {
        ensureLoaded();
        String previous = curseForgeNames.put(String.valueOf(projectId), name);
        if (!name.equals(previous)) {
            dirty = true;
            scheduleSave();
        }
    }

    public static void putModrinthName(String slug, String name) {
        ensureLoaded();
        String previous = modrinthNames.put(slug, name);
        if (!name.equals(previous)) {
            dirty = true;
            scheduleSave();
        }
    }

    private static synchronized void scheduleSave() {
        if (!dirty) return;
        dirty = false;
        try (Writer writer = new FileWriter(CACHE_FILE)) {
            CacheData data = new CacheData();
            data.curseforge = curseForgeNames;
            data.modrinth = modrinthNames;
            GSON.toJson(data, writer);
        } catch (IOException e) {
            // Non bloquant : le cache n'est qu'une optimisation d'affichage.
        }
    }

    private static class CacheData {
        Map<String, String> curseforge;
        Map<String, String> modrinth;
    }
}
