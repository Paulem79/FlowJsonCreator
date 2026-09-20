package net.paulem.fjc.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * What a mod jar says about itself: name, version, description, authors and icon, read from
 * fabric.mod.json, quilt.mod.json, (neoforge.)mods.toml or mcmod.info. {@code iconDataUri} is the icon
 * downscaled to a small PNG as a {@code data:} URI, so it can be cached as plain text.
 */
public record JarMetadata(@Nullable String name, @Nullable String version, @Nullable String description,
                          @Nullable String author, @Nullable String loader, @Nullable String iconDataUri) {
    private static final int ICON_MAX = 128;

    public static JarMetadata empty() {
        return new JarMetadata(null, null, null, null, null, null);
    }

    /** Never throws: an unreadable or unrecognised jar simply yields (partly) empty metadata. */
    public static JarMetadata read(File jar) {
        try (ZipFile zip = new ZipFile(jar)) {
            JarMetadata meta = readFabric(zip);
            if (meta == null) meta = readQuilt(zip);
            if (meta == null) meta = readToml(zip, "META-INF/neoforge.mods.toml", "NeoForge");
            if (meta == null) meta = readToml(zip, "META-INF/mods.toml", "Forge");
            if (meta == null) meta = readMcmod(zip);
            return meta != null ? meta : empty();
        } catch (Exception e) {
            return empty();
        }
    }

    // ------------------------------------------------------------------
    // Fabric / Quilt
    // ------------------------------------------------------------------

    private static @Nullable JarMetadata readFabric(ZipFile zip) throws IOException {
        JsonObject json = readJson(zip, "fabric.mod.json");
        if (json == null) return null;

        String icon = null;
        JsonElement iconEl = json.get("icon");
        if (iconEl != null && iconEl.isJsonPrimitive()) {
            icon = iconEl.getAsString();
        } else if (iconEl != null && iconEl.isJsonObject()) {
            // Several sizes: take the largest
            int best = -1;
            for (var e : iconEl.getAsJsonObject().entrySet()) {
                try {
                    int size = Integer.parseInt(e.getKey());
                    if (size > best) {
                        best = size;
                        icon = e.getValue().getAsString();
                    }
                } catch (RuntimeException ignored) {
                }
            }
        }

        String author = null;
        JsonElement authors = json.get("authors");
        if (authors != null && authors.isJsonArray() && !authors.getAsJsonArray().isEmpty()) {
            author = personName(authors.getAsJsonArray().get(0));
        }
        return new JarMetadata(str(json, "name"), str(json, "version"), str(json, "description"), author, "Fabric",
                iconOf(zip, icon));
    }

    private static @Nullable JarMetadata readQuilt(ZipFile zip) throws IOException {
        JsonObject json = readJson(zip, "quilt.mod.json");
        if (json == null || !json.has("quilt_loader")) return null;
        JsonObject loader = json.getAsJsonObject("quilt_loader");
        JsonObject meta = loader.has("metadata") && loader.get("metadata").isJsonObject()
                ? loader.getAsJsonObject("metadata") : new JsonObject();

        String author = null;
        JsonElement contributors = meta.get("contributors");
        if (contributors != null && contributors.isJsonObject() && !contributors.getAsJsonObject().isEmpty()) {
            author = contributors.getAsJsonObject().keySet().iterator().next();
        }
        return new JarMetadata(str(meta, "name"), str(loader, "version"), str(meta, "description"), author, "Quilt",
                iconOf(zip, str(meta, "icon")));
    }

    // ------------------------------------------------------------------
    // Forge / NeoForge (mods.toml) - only a handful of flat keys are needed, so no TOML parser
    // ------------------------------------------------------------------

    private static @Nullable JarMetadata readToml(ZipFile zip, String path, String loader) throws IOException {
        String text = readText(zip, path);
        if (text == null) return null;

        // Keys of the first [[mods]] block (up to the next table); `authors` may also sit at the top level.
        int start = text.indexOf("[[mods]]");
        String block = start < 0 ? text : text.substring(start + 8);
        int end = block.indexOf("\n[");
        if (end >= 0) block = block.substring(0, end);

        String version = tomlValue(block, "version");
        if (version != null && version.contains("${")) version = manifestVersion(zip);
        String author = tomlValue(block, "authors");
        if (author == null) author = tomlValue(text, "authors");
        String logo = tomlValue(block, "logoFile");
        if (logo == null) logo = tomlValue(text, "logoFile");

        return new JarMetadata(tomlValue(block, "displayName"), version, tomlValue(block, "description"), author, loader,
                iconOf(zip, logo));
    }

    private static @Nullable String tomlValue(String toml, String key) {
        Matcher m = Pattern.compile("(?m)^\\s*" + key + "\\s*=\\s*(?:'''(.*?)'''|\"\"\"(.*?)\"\"\"|\"((?:[^\"\\\\]|\\\\.)*)\"|'([^']*)')",
                Pattern.DOTALL).matcher(toml);
        if (!m.find()) return null;
        for (int i = 1; i <= 4; i++) {
            if (m.group(i) != null) return clean(m.group(i));
        }
        return null;
    }

    private static @Nullable String manifestVersion(ZipFile zip) {
        try {
            ZipEntry entry = zip.getEntry("META-INF/MANIFEST.MF");
            if (entry == null) return null;
            try (InputStream in = zip.getInputStream(entry)) {
                return clean(new Manifest(in).getMainAttributes().getValue("Implementation-Version"));
            }
        } catch (IOException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Legacy Forge (mcmod.info)
    // ------------------------------------------------------------------

    private static @Nullable JarMetadata readMcmod(ZipFile zip) throws IOException {
        String text = readText(zip, "mcmod.info");
        if (text == null) return null;
        JsonElement root = JsonParser.parseString(text);
        JsonElement first = root;
        if (root.isJsonObject() && root.getAsJsonObject().has("modList")) first = root.getAsJsonObject().get("modList");
        if (first.isJsonArray()) {
            JsonArray array = first.getAsJsonArray();
            if (array.isEmpty()) return null;
            first = array.get(0);
        }
        if (!first.isJsonObject()) return null;
        JsonObject mod = first.getAsJsonObject();

        String author = null;
        JsonElement authors = mod.has("authorList") ? mod.get("authorList") : mod.get("authors");
        if (authors != null && authors.isJsonArray() && !authors.getAsJsonArray().isEmpty()) {
            author = personName(authors.getAsJsonArray().get(0));
        }
        return new JarMetadata(str(mod, "name"), str(mod, "version"), str(mod, "description"), author, "Forge",
                iconOf(zip, str(mod, "logoFile")));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static @Nullable JsonObject readJson(ZipFile zip, String path) throws IOException {
        String text = readText(zip, path);
        if (text == null) return null;
        JsonElement el = JsonParser.parseString(text);
        return el.isJsonObject() ? el.getAsJsonObject() : null;
    }

    private static @Nullable String readText(ZipFile zip, String path) throws IOException {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null) return null;
        try (InputStream in = zip.getInputStream(entry)) {
            String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        }
    }

    private static @Nullable String str(JsonObject json, String key) {
        JsonElement el = json.get(key);
        return el != null && el.isJsonPrimitive() ? clean(el.getAsString()) : null;
    }

    private static @Nullable String personName(JsonElement el) {
        if (el.isJsonPrimitive()) return clean(el.getAsString());
        if (el.isJsonObject()) return str(el.getAsJsonObject(), "name");
        return null;
    }

    private static @Nullable String clean(@Nullable String s) {
        if (s == null) return null;
        String t = s.replaceAll("\\s+", " ").trim();
        return t.isEmpty() ? null : t;
    }

    private static @Nullable String iconOf(ZipFile zip, @Nullable String path) {
        if (path == null || path.isBlank()) return null;
        try {
            String p = path.startsWith("/") ? path.substring(1) : path;
            ZipEntry entry = zip.getEntry(p);
            if (entry == null) return null;
            BufferedImage src;
            try (InputStream in = zip.getInputStream(entry)) {
                src = ImageIO.read(new ByteArrayInputStream(in.readAllBytes()));
            }
            if (src == null) return null;

            double scale = Math.min(1.0, (double) ICON_MAX / Math.max(src.getWidth(), src.getHeight()));
            int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
            int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, w, h, null);
            g.dispose();

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bytes);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    /** Decodes a {@code data:image/...;base64,} URI produced by this class. */
    public static byte[] decodeDataUri(String dataUri) {
        return Base64.getDecoder().decode(dataUri.substring(dataUri.indexOf(',') + 1));
    }

    public List<String> tags() {
        List<String> tags = new ArrayList<>();
        if (loader != null) tags.add(loader);
        return tags;
    }
}
