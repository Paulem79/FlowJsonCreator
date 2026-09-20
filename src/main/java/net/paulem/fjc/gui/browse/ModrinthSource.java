package net.paulem.fjc.gui.browse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.paulem.fjc.flow.mod.ModrinthMod;
import net.paulem.fjc.gui.model.ModCategory;
import net.paulem.fjc.utils.HttpJson;
import net.paulem.fjc.utils.ModrinthUtils;
import org.jetbrains.annotations.Nullable;
import ovh.paulem.modrinthapi.Modrinth;
import ovh.paulem.modrinthapi.types.project.Project;
import ovh.paulem.modrinthapi.types.project.ProjectSide;
import ovh.paulem.modrinthapi.types.version.ListVersions;
import ovh.paulem.modrinthapi.types.version.Version;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Modrinth search. The request is done by hand (instead of {@code Modrinth#searchProject}) because the wrapper
 * keeps its query parameters in a static map, which is not safe when both sources are queried in parallel,
 * and because its strict enums throw on values Modrinth adds over time.
 */
public class ModrinthSource implements ModSource {
    @Override
    public ModCategory category() {
        return ModCategory.MODRINTH;
    }

    @Override
    public SearchPage search(SearchQuery q) throws IOException {
        JsonArray facets = new JsonArray();
        facets.add(facet("project_type:mod"));
        if (q.gameVersion() != null) facets.add(facet("versions:" + q.gameVersion()));
        if (q.loader() != null) facets.add(facet("categories:" + q.loader().toLowerCase()));
        if (q.category() != null) facets.add(facet("categories:" + q.category()));

        StringBuilder url = new StringBuilder(Modrinth.MODRINTH_API_LINK + "/search?");
        if (!q.query().isBlank()) url.append("query=").append(encode(q.query().trim())).append('&');
        url.append("facets=").append(encode(facets.toString()))
                .append("&index=").append(q.sort().modrinthIndex())
                .append("&offset=").append(q.offset())
                .append("&limit=").append(q.limit());

        JsonObject json = HttpJson.get(url.toString()).getAsJsonObject();
        List<SearchResult> results = new ArrayList<>();
        for (JsonElement hit : json.getAsJsonArray("hits")) {
            results.add(parseHit(hit.getAsJsonObject()));
        }
        int total = json.get("total_hits").getAsInt();
        return new SearchPage(results, q.offset() + results.size() < total);
    }

    @Override
    public List<VersionOption> listVersions(SearchResult r, @Nullable String gameVersion, @Nullable String loader) throws Exception {
        // Raw JSON alongside the parsed versions: the wrapper drops the ids of version dependencies.
        JsonArray json = ModrinthUtils.listVersionsJson(r.id(), gameVersion, loader);
        List<Version> versions = ListVersions.fromJson(json).versions();

        List<VersionOption> options = new ArrayList<>();
        for (int i = 0; i < versions.size(); i++) {
            Version v = versions.get(i);
            options.add(new VersionOption(v.id(), v.name(), v.versionNumber(), v.versionType().name(),
                    v.gameVersions(), v.loaders(), parseInstant(v.datePublished()), v.downloads(),
                    new ModrinthMod(v.projectId(), v.versionNumber(), v.id()),
                    parseDependencies(json.get(i).getAsJsonObject())));
        }
        return options;
    }

    @Override
    public Optional<SearchResult> project(String id) {
        Project p = ModrinthUtils.getModFromSlug(id);
        return p == null ? Optional.empty() : Optional.of(fromProject(p));
    }

    /** A full project (as opposed to a search hit) turned into the same shape, e.g. for the modpack cards. */
    public static SearchResult fromProject(Project p) {
        List<String> tags = new ArrayList<>();
        boolean clientOk = p.clientSide() != ProjectSide.UNSUPPORTED, serverOk = p.serverSide() != ProjectSide.UNSUPPORTED;
        if (clientOk && serverOk) tags.add("Client ou serveur");
        else if (clientOk) tags.add("Client");
        else if (serverOk) tags.add("Serveur");

        List<String> loaders = new ArrayList<>();
        for (String slug : p.categories()) {
            if (Labels.isLoader(slug)) loaders.add(Labels.loader(slug));
            else tags.add(Labels.category(slug));
        }
        // A full project lists its loaders in a field of their own rather than among the categories.
        if (p.loaders() != null) {
            for (String slug : p.loaders()) {
                String label = Labels.loader(slug);
                if (Labels.isLoader(slug) && !loaders.contains(label)) loaders.add(label);
            }
        }
        tags.addAll(loaders);

        return new SearchResult(ModCategory.MODRINTH, p.id(), p.slug(), p.title(), p.description(), "",
                p.iconUrl(), p.downloads(), p.followers(), tags, parseInstant(p.updated()), parseInstant(p.published()), p);
    }

    @Override
    public List<DependencyRef> dependencies(VersionOption version) throws IOException {
        List<DependencyRef> resolved = new ArrayList<>();
        for (DependencyRef d : version.dependencies()) {
            if (!d.projectId().isEmpty()) {
                resolved.add(d);
                continue;
            }
            String projectId = nullableStr(HttpJson.get(Modrinth.MODRINTH_API_LINK + "/version/" + encode(d.versionId())).getAsJsonObject(), "project_id");
            if (projectId != null) resolved.add(new DependencyRef(projectId, d.versionId(), d.required()));
        }
        return resolved;
    }

    /** Only required/optional links matter: embedded ones ship inside the jar, incompatible ones are not installs. */
    private static List<DependencyRef> parseDependencies(JsonObject version) {
        List<DependencyRef> deps = new ArrayList<>();
        JsonElement array = version.get("dependencies");
        if (array == null || !array.isJsonArray()) return deps;

        for (JsonElement e : array.getAsJsonArray()) {
            JsonObject d = e.getAsJsonObject();
            String type = nullableStr(d, "dependency_type");
            if (!"required".equals(type) && !"optional".equals(type)) continue;

            String projectId = nullableStr(d, "project_id");
            String versionId = nullableStr(d, "version_id");
            // A dependency may name only a version: the project is looked up lazily, see dependencies(VersionOption).
            if (projectId != null || versionId != null) {
                deps.add(new DependencyRef(projectId == null ? "" : projectId, versionId, "required".equals(type)));
            }
        }
        return deps;
    }

    private static SearchResult parseHit(JsonObject h) {
        List<String> tags = new ArrayList<>();
        String client = str(h, "client_side", "unknown");
        String server = str(h, "server_side", "unknown");
        boolean clientOk = !client.equals("unsupported"), serverOk = !server.equals("unsupported");
        if (clientOk && serverOk) tags.add("Client ou serveur");
        else if (clientOk) tags.add("Client");
        else if (serverOk) tags.add("Serveur");

        List<String> loaders = new ArrayList<>();
        JsonElement display = h.get("display_categories");
        if (display != null && display.isJsonArray()) {
            for (JsonElement e : display.getAsJsonArray()) {
                String slug = e.getAsString();
                if (Labels.isLoader(slug)) loaders.add(Labels.loader(slug));
                else tags.add(Labels.category(slug));
            }
        }
        tags.addAll(loaders);

        return new SearchResult(ModCategory.MODRINTH,
                str(h, "project_id", ""), str(h, "slug", ""), str(h, "title", ""),
                str(h, "description", ""), str(h, "author", ""), nullableStr(h, "icon_url"),
                lng(h, "downloads"), lng(h, "follows"), tags,
                parseInstant(nullableStr(h, "date_modified")), parseInstant(nullableStr(h, "date_created")), null);
    }

    private static JsonArray facet(String value) {
        JsonArray group = new JsonArray();
        group.add(value);
        return group;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static String str(JsonObject o, String key, String fallback) {
        String v = nullableStr(o, key);
        return v != null ? v : fallback;
    }

    static @Nullable String nullableStr(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    static long lng(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? 0 : e.getAsLong();
    }

    static @Nullable Instant parseInstant(@Nullable String s) {
        if (s == null) return null;
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
