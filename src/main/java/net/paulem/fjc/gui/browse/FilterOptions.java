package net.paulem.fjc.gui.browse;

import com.google.gson.JsonElement;
import net.paulem.fjc.utils.HttpJson;
import ovh.paulem.modrinthapi.Modrinth;

import java.util.ArrayList;
import java.util.List;

/**
 * The choices offered by the filter bar, fetched from Modrinth's tag endpoints so the user only ever picks a
 * value that exists (instead of typing a game version by hand). Falls back to a static list when offline.
 */
public final class FilterOptions {
    public record Data(List<String> releases, List<String> snapshots, List<String> loaders, List<String[]> categories) {
    }

    /** Loaders both Modrinth and CurseForge understand. */
    public static final List<String> LOADERS = List.of("Fabric", "Forge", "NeoForge", "Quilt");

    private static final List<String> FALLBACK_VERSIONS = List.of("1.21.1", "1.20.6", "1.20.4", "1.20.1", "1.19.4",
            "1.19.2", "1.18.2", "1.16.5", "1.12.2");

    private FilterOptions() {
    }

    public static Data load() {
        List<String> releases = new ArrayList<>();
        List<String> snapshots = new ArrayList<>();
        try {
            for (JsonElement e : HttpJson.get(Modrinth.MODRINTH_API_LINK + "/tag/game_version").getAsJsonArray()) {
                var o = e.getAsJsonObject();
                String version = o.get("version").getAsString();
                if (o.get("version_type").getAsString().equals("release")) releases.add(version);
                else snapshots.add(version);
            }
        } catch (Exception ignored) {
            releases.addAll(FALLBACK_VERSIONS);
        }

        List<String[]> categories = new ArrayList<>();
        try {
            for (JsonElement e : HttpJson.get(Modrinth.MODRINTH_API_LINK + "/tag/category").getAsJsonArray()) {
                var o = e.getAsJsonObject();
                if (o.get("project_type").getAsString().equals("mod")) {
                    String slug = o.get("name").getAsString();
                    categories.add(new String[]{slug, Labels.category(slug)});
                }
            }
            categories.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));
        } catch (Exception ignored) {
            // no category filter when Modrinth's tag list is unreachable
        }

        return new Data(releases, snapshots, LOADERS, categories);
    }
}
