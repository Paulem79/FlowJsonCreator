package net.paulem.fjc.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.matyrobbrt.curseforgeapi.request.Response;
import io.github.matyrobbrt.curseforgeapi.request.query.FileListQuery;
import io.github.matyrobbrt.curseforgeapi.schemas.file.File;
import io.github.matyrobbrt.curseforgeapi.schemas.mod.ModLoaderType;
import net.paulem.fjc.Main;
import net.paulem.fjc.flow.mod.CurseForgeMod;
import net.paulem.fjc.flow.mod.Mod;
import net.paulem.fjc.flow.mod.ModrinthMod;
import net.paulem.fjc.gui.browse.Labels;
import net.paulem.fjc.utils.CFUtils;
import net.paulem.fjc.utils.HttpJson;
import org.jetbrains.annotations.Nullable;
import ovh.paulem.modrinthapi.Modrinth;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Looks for a newer version of a mod, staying on the modpack's Minecraft version and the installed loader, and never a
 * less stable release channel than the installed one (an installed release only ever updates to a release).
 * URL mods are pinned to a file and have nothing to ask, so they never have updates.
 */
public final class UpdateChecker {
    private UpdateChecker() {
    }

    /**
     * @param gameVersion the Minecraft version the modpack targets. Without it, the oldest stable version the
     *                    installed file supports is used, so an update never drops support for it.
     */
    public static Optional<UpdateInfo> check(Mod mod, @Nullable String gameVersion) throws Exception {
        if (mod instanceof ModrinthMod mr) return checkModrinth(mr, gameVersion);
        if (mod instanceof CurseForgeMod cf) return checkCurseForge(cf, gameVersion);
        return Optional.empty();
    }

    /** The release notes of the update, fetched now if the source serves them separately. May take a request. */
    public static String loadChangelog(UpdateInfo update) {
        if (update.changelog() != null) return update.changelog();
        if (update.target() instanceof CurseForgeMod cf && Main.cfApi != null) {
            try {
                Response<String> response = Main.cfApi.getHelper().getModFileChangelog(cf.projectID(), cf.fileID());
                return response.isEmpty() ? "" : htmlToText(response.get());
            } catch (Exception e) {
                return "";
            }
        }
        return "";
    }

    // ------------------------------------------------------------------
    // Modrinth
    // ------------------------------------------------------------------

    private static Optional<UpdateInfo> checkModrinth(ModrinthMod mr, @Nullable String targetGameVersion) throws IOException {
        JsonObject installed = installedModrinthVersion(mr);
        if (installed == null) return Optional.empty();

        Instant installedDate = instant(str(installed, "date_published"));
        if (installedDate == null) return Optional.empty();
        String installedId = str(installed, "id");
        int installedRank = rank(str(installed, "version_type"));

        StringBuilder url = new StringBuilder(Modrinth.MODRINTH_API_LINK + "/project/" + encode(mr.getProjectReference()) + "/version");
        String sep = "?";
        JsonElement loaders = installed.get("loaders");
        if (loaders != null && loaders.isJsonArray() && !loaders.getAsJsonArray().isEmpty()) {
            url.append(sep).append("loaders=").append(encode(loaders.toString()));
            sep = "&";
        }
        // One game version only: an installed file often lists a whole range (snapshots, next minor version...)
        // and filtering on all of them would offer builds for a Minecraft version the modpack is not on.
        List<String> installedVersions = new java.util.ArrayList<>();
        JsonElement versions = installed.get("game_versions");
        if (versions != null && versions.isJsonArray()) versions.getAsJsonArray().forEach(v -> installedVersions.add(v.getAsString()));
        String gameVersion = targetGameVersion != null ? targetGameVersion : oldestStable(installedVersions);
        if (gameVersion != null) {
            JsonArray wanted = new JsonArray();
            wanted.add(gameVersion);
            url.append(sep).append("game_versions=").append(encode(wanted.toString()));
        }

        JsonObject best = null;
        Instant bestDate = installedDate;
        for (JsonElement e : HttpJson.get(url.toString()).getAsJsonArray()) {
            JsonObject v = e.getAsJsonObject();
            Instant date = instant(str(v, "date_published"));
            if (date == null || !date.isAfter(bestDate)) continue;
            if (installedId.equals(str(v, "id")) || rank(str(v, "version_type")) > installedRank) continue;
            best = v;
            bestDate = date;
        }
        if (best == null) return Optional.empty();

        String number = str(best, "version_number");
        return Optional.of(new UpdateInfo(mr, new ModrinthMod(mr.getProjectReference(), number, str(best, "id")),
                mr.getVersionNumber(), number, str(best, "version_type"), bestDate, str(best, "changelog")));
    }

    /** The installed version's details; mods.json entries with no version id are found by their version number. */
    @Nullable
    private static JsonObject installedModrinthVersion(ModrinthMod mr) throws IOException {
        if (!mr.getVersionId().isBlank()) {
            try {
                return HttpJson.get(Modrinth.MODRINTH_API_LINK + "/version/" + encode(mr.getVersionId())).getAsJsonObject();
            } catch (FileNotFoundException e) {
                // Version deleted from Modrinth: fall back to its number
            }
        }
        JsonArray all = HttpJson.get(Modrinth.MODRINTH_API_LINK + "/project/" + encode(mr.getProjectReference()) + "/version").getAsJsonArray();
        for (JsonElement e : all) {
            if (mr.getVersionNumber().equals(str(e.getAsJsonObject(), "version_number"))) return e.getAsJsonObject();
        }
        return null;
    }

    // ------------------------------------------------------------------
    // CurseForge
    // ------------------------------------------------------------------

    private static Optional<UpdateInfo> checkCurseForge(CurseForgeMod cf, @Nullable String targetGameVersion) throws Exception {
        if (Main.cfApi == null) return Optional.empty();
        File installed = CFUtils.getFileFromId(cf.projectID(), cf.fileID());
        if (installed == null) return Optional.empty();

        Instant installedDate = instant(installed.fileDate());
        if (installedDate == null) return Optional.empty();
        int installedRank = rank(installed.releaseType().name());

        // A CurseForge file lists Minecraft versions, loaders and "Client"/"Server"/"Java 17" in one flat list.
        List<String> gameVersions = installed.gameVersions().stream().filter(v -> v.matches("\\d+\\.\\d+.*")).toList();
        List<String> loaders = installed.gameVersions().stream().map(String::toLowerCase).filter(Labels::isLoader).distinct().toList();

        // One game version only, see checkModrinth
        String gameVersion = targetGameVersion != null ? targetGameVersion : oldestStable(gameVersions);
        Map<Integer, File> candidates = new HashMap<>();
        FileListQuery query = FileListQuery.of();
        if (gameVersion != null) query.gameVersion(gameVersion);
        ModLoaderType loader = loaders.size() == 1 ? toLoaderType(loaders.get(0)) : null;
        if (loader != null) query.modLoaderType(loader);
        query.pageSize(50);

        Response<List<File>> response = Main.cfApi.getHelper().getModFiles(cf.projectID(), query);
        if (!response.isEmpty()) {
            for (File f : response.get()) candidates.put(f.id(), f);
        }

        File best = null;
        Instant bestDate = installedDate;
        for (File f : candidates.values()) {
            Instant date = instant(f.fileDate());
            if (date == null || !date.isAfter(bestDate) || f.id() == installed.id() || !f.isAvailable()) continue;
            if (rank(f.releaseType().name()) > installedRank) continue;
            if (gameVersion != null && !f.gameVersions().contains(gameVersion)) continue;
            if (!loaders.isEmpty() && f.gameVersions().stream().map(String::toLowerCase).noneMatch(loaders::contains)) continue;
            best = f;
            bestDate = date;
        }
        if (best == null) return Optional.empty();

        return Optional.of(new UpdateInfo(cf, new CurseForgeMod(cf.projectID(), best.id()), installed.displayName(),
                best.displayName(), best.releaseType().name(), bestDate, null));
    }

    private static @Nullable ModLoaderType toLoaderType(String loader) {
        try {
            return ModLoaderType.valueOf(loader.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** The lowest plain release number ("1.20.1", "26.2"; no snapshots) of the list, or null if there is none. */
    private static @Nullable String oldestStable(List<String> versions) {
        return versions.stream().filter(v -> v.matches("\\d+(\\.\\d+)+"))
                .min(UpdateChecker::compareVersions).orElse(null);
    }

    private static int compareVersions(String a, String b) {
        String[] pa = a.split("\\."), pb = b.split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int x = i < pa.length ? Integer.parseInt(pa[i]) : 0, y = i < pb.length ? Integer.parseInt(pb[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    /** Lower is more stable: release, then beta, then alpha. */
    private static int rank(String releaseType) {
        return switch (releaseType.toLowerCase()) {
            case "release" -> 0;
            case "beta" -> 1;
            default -> 2;
        };
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static @Nullable Instant instant(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** CurseForge changelogs are HTML; this keeps the line structure and drops the markup. */
    private static String htmlToText(String html) {
        return html.replaceAll("(?i)<br\\s*/?>|</p>|</li>|</h\\d>|</div>", "\n")
                .replaceAll("(?i)<li[^>]*>", "• ")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
                .replaceAll("\n{3,}", "\n\n").trim();
    }
}
