package net.paulem.fjc.export;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Works out from a mod jar alone whether it is needed on a server. Two levels, most reliable first:
 * <ol>
 *   <li>what the mod declares: fabric.mod.json / quilt.mod.json {@code environment}, entrypoints and mixin configs,
 *       (neoforge.)mods.toml dependency {@code side}, legacy {@code @Mod(clientSideOnly)};</li>
 *   <li>a scan of the bytecode for references to client-only classes, flagged as a heuristic.</li>
 * </ol>
 */
public final class JarSideAnalyzer {
    private static final int MAX_CLASS_BYTES = 4 * 1024 * 1024;

    /** Classes only present on a client. */
    private static final List<String> CLIENT_MARKERS = List.of(
            "net/minecraft/client/", "com/mojang/blaze3d/", "net/minecraftforge/client/",
            "net/neoforged/neoforge/client/", "net/fabricmc/fabric/api/client/", "net/fabricmc/fabric/impl/client/",
            "net/fabricmc/api/ClientModInitializer");

    /** Things a mod only does when it means to act on a server (or on both sides). */
    private static final List<String> COMMON_MARKERS = List.of(
            "net/minecraft/server/", "net/minecraftforge/server", "net/neoforged/neoforge/server",
            "net/minecraftforge/event/server", "net/neoforged/neoforge/event/server",
            "net/minecraftforge/registries", "net/neoforged/neoforge/registries", "DeferredRegister", "RegistryObject",
            "net/minecraftforge/network/NetworkRegistry", "net/minecraftforge/network/simple/SimpleChannel",
            "net/neoforged/neoforge/network/", "ServerPlayNetworking", "ServerLifecycleEvents", "ServerTickEvents",
            "net/fabricmc/api/DedicatedServerModInitializer", "net/fabricmc/fabric/api/command/");

    private static final Pattern DEP_HEADER = Pattern.compile("(?m)^\\s*\\[\\[dependencies\\.[^\\]]*\\]\\]");

    private JarSideAnalyzer() {
    }

    /** Never throws: an unreadable jar is reported as "could not tell", which a server export keeps. */
    public static SideVerdict analyze(File jar) {
        try (ZipFile zip = new ZipFile(jar)) {
            List<SideVerdict> declared = new ArrayList<>();
            addIfPresent(declared, fabric(zip));
            addIfPresent(declared, quilt(zip));
            addIfPresent(declared, toml(zip, "META-INF/neoforge.mods.toml"));
            addIfPresent(declared, toml(zip, "META-INF/mods.toml"));

            // A multi-loader jar only counts as client-only if every loader says so.
            for (SideVerdict v : declared) if (!v.isClientOnly()) return v;
            if (!declared.isEmpty()) return declared.get(0);

            return scanBytecode(zip);
        } catch (Exception e) {
            return SideVerdict.serverCapable("Jar illisible").asHeuristic();
        }
    }

    private static void addIfPresent(List<SideVerdict> list, @Nullable SideVerdict v) {
        if (v != null) list.add(v);
    }

    // ------------------------------------------------------------------
    // Fabric / Quilt
    // ------------------------------------------------------------------

    private static @Nullable SideVerdict fabric(ZipFile zip) throws IOException {
        JsonObject json = readJson(zip, "fabric.mod.json");
        if (json == null) return null;
        String method = "fabric.mod.json";

        String env = string(json, "environment");
        if ("client".equals(env)) return SideVerdict.clientOnly(method + " (environment)");
        if ("server".equals(env) || "*".equals(env)) return SideVerdict.serverCapable(method + " (environment)");

        // No explicit environment: look at what the mod actually hooks into.
        boolean hasMain = false, hasClient = false;
        JsonElement entrypoints = json.get("entrypoints");
        if (entrypoints != null && entrypoints.isJsonObject()) {
            JsonObject e = entrypoints.getAsJsonObject();
            hasMain = nonEmpty(e.get("main")) || nonEmpty(e.get("server"));
            hasClient = nonEmpty(e.get("client"));
        }
        if (hasMain) return SideVerdict.serverCapable(method + " (point d'entrée main/server)");

        int commonMixins = 0, clientMixins = 0;
        JsonElement mixins = json.get("mixins");
        if (mixins != null && mixins.isJsonArray()) {
            for (JsonElement m : mixins.getAsJsonArray()) {
                String config;
                boolean forcedClient = false;
                if (m.isJsonPrimitive()) {
                    config = m.getAsString();
                } else if (m.isJsonObject()) {
                    config = string(m.getAsJsonObject(), "config");
                    forcedClient = "client".equals(string(m.getAsJsonObject(), "environment"));
                } else continue;
                if (forcedClient) {
                    clientMixins++;
                    continue;
                }
                JsonObject cfg = config == null ? null : readJson(zip, config);
                if (cfg == null) continue;
                if (nonEmpty(cfg.get("mixins")) || nonEmpty(cfg.get("server"))) commonMixins++;
                if (nonEmpty(cfg.get("client"))) clientMixins++;
            }
        }
        if ((hasClient || clientMixins > 0) && commonMixins == 0) {
            return SideVerdict.clientOnly(method + " (points d'entrée et mixins client uniquement)");
        }
        return null;
    }

    private static @Nullable SideVerdict quilt(ZipFile zip) throws IOException {
        JsonObject json = readJson(zip, "quilt.mod.json");
        if (json == null) return null;
        JsonElement mc = json.get("minecraft");
        if (mc == null || !mc.isJsonObject()) return null;
        String env = string(mc.getAsJsonObject(), "environment");
        if ("client".equals(env)) return SideVerdict.clientOnly("quilt.mod.json (environment)");
        if ("dedicated_server".equals(env) || "*".equals(env)) return SideVerdict.serverCapable("quilt.mod.json (environment)");
        return null;
    }

    // ------------------------------------------------------------------
    // Forge / NeoForge (mods.toml): the side is declared on the dependency on minecraft / the loader itself
    // ------------------------------------------------------------------

    private static @Nullable SideVerdict toml(ZipFile zip, String path) throws IOException {
        String text = readText(zip, path);
        if (text == null) return null;
        String method = path.substring(path.lastIndexOf('/') + 1);

        Matcher header = DEP_HEADER.matcher(text);
        List<Integer> starts = new ArrayList<>();
        while (header.find()) starts.add(header.start());

        boolean anyClient = false, anyServerCapable = false;
        for (int i = 0; i < starts.size(); i++) {
            int end = i + 1 < starts.size() ? starts.get(i + 1) : text.length();
            String block = text.substring(starts.get(i), end);
            String modId = tomlValue(block, "modId");
            if (modId == null || !List.of("minecraft", "forge", "neoforge").contains(modId.toLowerCase())) continue;
            String side = tomlValue(block, "side");
            if (side == null) continue;
            if (side.equalsIgnoreCase("CLIENT")) anyClient = true;
            else anyServerCapable = true; // BOTH / SERVER
        }
        if (anyServerCapable) return SideVerdict.serverCapable(method + " (side)");
        if (anyClient) return SideVerdict.clientOnly(method + " (side = CLIENT)");
        // No side declared means "BOTH" by default, which many client mods leave as is: not conclusive.
        return null;
    }

    private static @Nullable String tomlValue(String block, String key) {
        Matcher m = Pattern.compile("(?m)^\\s*" + key + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')").matcher(block);
        if (!m.find()) return null;
        return m.group(1) != null ? m.group(1) : m.group(2);
    }

    // ------------------------------------------------------------------
    // Bytecode
    // ------------------------------------------------------------------

    private static SideVerdict scanBytecode(ZipFile zip) throws IOException {
        boolean client = false, common = false;

        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory() || !entry.getName().endsWith(".class") || entry.getSize() > MAX_CLASS_BYTES) continue;

            // Class names live as UTF-8 in the constant pool; Latin-1 keeps every byte one char, so plain ASCII
            // markers match without needing to parse the class file.
            String content;
            try (InputStream in = zip.getInputStream(entry)) {
                content = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            }

            // Legacy Forge: @Mod(clientSideOnly = true) stores the element name in the class.
            if (content.contains("clientSideOnly") && content.contains("net/minecraftforge/fml/common/Mod")) {
                return SideVerdict.clientOnly("@Mod(clientSideOnly)");
            }
            if (!client) client = containsAny(content, CLIENT_MARKERS);
            if (!common) common = containsAny(content, COMMON_MARKERS);
            if (common) break; // a server-side hook settles it: the mod is not client-only
        }

        if (common) return SideVerdict.serverCapable("Analyse du bytecode (code serveur/commun)").asHeuristic();
        if (client) return SideVerdict.clientOnly("Analyse du bytecode (uniquement des classes client)").asHeuristic();
        return SideVerdict.serverCapable("Analyse du bytecode (aucune classe client)").asHeuristic();
    }

    private static boolean containsAny(String content, List<String> markers) {
        for (String marker : markers) if (content.contains(marker)) return true;
        return false;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static boolean nonEmpty(@Nullable JsonElement el) {
        if (el == null || el.isJsonNull()) return false;
        if (el.isJsonArray()) return !el.getAsJsonArray().isEmpty();
        if (el.isJsonObject()) return !el.getAsJsonObject().isEmpty();
        return true;
    }

    private static @Nullable String string(JsonObject json, String key) {
        JsonElement el = json.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : null;
    }

    private static @Nullable JsonObject readJson(ZipFile zip, String path) throws IOException {
        String text = readText(zip, path);
        if (text == null) return null;
        try {
            JsonElement el = JsonParser.parseString(text);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static @Nullable String readText(ZipFile zip, String path) throws IOException {
        ZipEntry entry = zip.getEntry(path.startsWith("/") ? path.substring(1) : path);
        if (entry == null) return null;
        try (InputStream in = zip.getInputStream(entry)) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        }
    }
}
