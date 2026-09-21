package net.paulem.fjc.export;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.matyrobbrt.curseforgeapi.schemas.file.File;
import io.github.matyrobbrt.curseforgeapi.schemas.file.FileDependency;
import io.github.matyrobbrt.curseforgeapi.schemas.file.FileRelationType;
import net.paulem.fjc.Main;
import net.paulem.fjc.flow.ModsJson;
import net.paulem.fjc.flow.mod.CurseForgeMod;
import net.paulem.fjc.flow.mod.ModrinthMod;
import net.paulem.fjc.flow.mod.UrlMod;
import net.paulem.fjc.gui.browse.Labels;
import net.paulem.fjc.gui.model.ModCategory;
import net.paulem.fjc.utils.CFUtils;
import net.paulem.fjc.utils.HttpJson;
import net.paulem.fjc.utils.JarMetadata;
import net.paulem.fjc.utils.ModrinthUtils;
import net.paulem.fjc.utils.ResolveCache;
import net.paulem.fjc.utils.UrlMetaCache;
import org.jetbrains.annotations.Nullable;
import ovh.paulem.modrinthapi.Modrinth;
import ovh.paulem.modrinthapi.types.project.Project;
import ovh.paulem.modrinthapi.types.project.ProjectSide;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a {@code mods/} zip out of the modpack, for a server or a client. Every mod is first {@link #resolve resolved}
 * (which jar, and does a server need it), then {@link #export exported}.
 * <p>
 * The side of a mod comes from, in order: the Modrinth project / CurseForge file tags, what the jar declares about
 * itself, and finally a scan of its bytecode (see {@link JarSideAnalyzer}). The jar is only downloaded for the
 * analysis when the first step could not tell.
 */
public final class ModExporter {
    private static final int DOWNLOAD_THREADS = 6;

    /** Called with (done, total, what is happening). May be invoked from any thread. */
    public interface Progress {
        void update(int done, int total, String message);
    }

    /** {@code failed} holds a message per mod that could not be added to the zip. */
    public record Report(int written, List<String> failed) {
    }

    // Filled by prefetch(): one bulk request per 100 mods instead of 2-3 requests per mod, which is what made
    // the APIs answer 429 on big modpacks.
    private static final Map<String, JsonObject> MODRINTH_VERSIONS = new ConcurrentHashMap<>();
    private static final Map<String, Project> MODRINTH_PROJECTS = new ConcurrentHashMap<>();
    private static final Map<Integer, File> CURSEFORGE_FILES = new ConcurrentHashMap<>();
    private static final int BULK = 100;

    private ModExporter() {
    }

    /** Bulk-loads the Modrinth versions/projects and CurseForge files of the entries. A failed batch just means per-mod requests later. */
    public static void prefetch(List<ExportEntry> entries) {
        List<String> versionIds = new ArrayList<>(), projectRefs = new ArrayList<>();
        List<Integer> fileIds = new ArrayList<>();
        for (ExportEntry e : entries) {
            if (e.mod() instanceof ModrinthMod mr) {
                if (!mr.getVersionId().isEmpty()) versionIds.add(mr.getVersionId());
                projectRefs.add(mr.getProjectReference());
            } else if (e.mod() instanceof CurseForgeMod cf) {
                fileIds.add(cf.fileID());
            }
        }

        for (int i = 0; i < versionIds.size(); i += BULK) {
            try {
                for (JsonElement v : bulkModrinth("versions", versionIds.subList(i, Math.min(i + BULK, versionIds.size())))) {
                    MODRINTH_VERSIONS.put(v.getAsJsonObject().get("id").getAsString(), v.getAsJsonObject());
                }
            } catch (Exception ignored) {
                // Fall back to one request per version
            }
        }
        for (int i = 0; i < projectRefs.size(); i += BULK) {
            try {
                for (JsonElement p : bulkModrinth("projects", projectRefs.subList(i, Math.min(i + BULK, projectRefs.size())))) {
                    try {
                        Project project = Project.fromJson(p.getAsJsonObject());
                        MODRINTH_PROJECTS.put(project.id(), project);
                        MODRINTH_PROJECTS.put(project.slug().toLowerCase(Locale.ROOT), project);
                    } catch (RuntimeException ignored) {
                        // A value the wrapper does not know: that project is fetched on its own later
                    }
                }
            } catch (Exception ignored) {
                // Fall back to one request per project
            }
        }
        if (Main.cfApi != null) {
            for (int i = 0; i < fileIds.size(); i += BULK) {
                try {
                    int[] ids = fileIds.subList(i, Math.min(i + BULK, fileIds.size())).stream().mapToInt(Integer::intValue).toArray();
                    var response = Main.cfApi.getHelper().getFiles(ids);
                    if (!response.isEmpty()) for (File f : response.get()) CURSEFORGE_FILES.put(f.id(), f);
                } catch (Exception ignored) {
                    // Fall back to one request per file
                }
            }
        }
    }

    private static JsonArray bulkModrinth(String endpoint, List<String> ids) throws IOException {
        JsonArray array = new JsonArray();
        ids.forEach(array::add);
        return HttpJson.get(Modrinth.MODRINTH_API_LINK + "/" + endpoint + "?ids=" + encode(array.toString())).getAsJsonArray();
    }

    public static List<ExportEntry> collect(ModsJson content) {
        List<ExportEntry> entries = new ArrayList<>();
        for (UrlMod url : content.mods) {
            JarMetadata cached = UrlMetaCache.get(url.sha1());
            entries.add(new ExportEntry(url, ModCategory.URL, cached != null && cached.name() != null ? cached.name() : url.name()));
        }
        for (CurseForgeMod cf : content.curseFiles) {
            String name = ResolveCache.getCurseForgeName(cf.projectID());
            entries.add(new ExportEntry(cf, ModCategory.CURSEFORGE, name != null ? name : "Mod CurseForge #" + cf.projectID()));
        }
        for (ModrinthMod mr : content.modrinthMods) {
            String name = ResolveCache.getModrinthName(mr.getProjectReference());
            entries.add(new ExportEntry(mr, ModCategory.MODRINTH, name != null ? name : mr.getProjectReference()));
        }
        return entries;
    }

    // ------------------------------------------------------------------
    // Resolution: file to download + side of the mod
    // ------------------------------------------------------------------

    /** Fills in the entry (never throws): on failure it ends up in the ERROR state with a message. */
    public static void resolve(ExportEntry entry, Path tmpDir) {
        try {
            SideVerdict tagVerdict;
            if (entry.mod() instanceof ModrinthMod mr) tagVerdict = resolveModrinth(entry, mr);
            else if (entry.mod() instanceof CurseForgeMod cf) tagVerdict = resolveCurseForge(entry, cf);
            else tagVerdict = resolveUrl(entry, (UrlMod) entry.mod());

            if (tagVerdict != null) {
                entry.setVerdict(tagVerdict);
            } else {
                // Platform tags said nothing: the jar has to speak for itself.
                java.io.File jar = ensureJar(entry, tmpDir);
                entry.setVerdict(JarSideAnalyzer.analyze(jar));
            }

            if (entry.loader() == null && entry.jar() != null) entry.setLoader(JarMetadata.read(entry.jar()).loader());
            entry.markReady();
        } catch (Exception e) {
            entry.markError(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private static @Nullable SideVerdict resolveModrinth(ExportEntry entry, ModrinthMod mr) throws Exception {
        Project project = MODRINTH_PROJECTS.get(mr.getProjectReference().toLowerCase(Locale.ROOT));
        if (project == null) project = ModrinthUtils.getModFromSlug(mr.getProjectReference());
        if (project != null) {
            entry.setTitle(project.title());
            entry.setProjectKey("modrinth:" + project.id());
        }

        JsonObject version = findModrinthVersion(mr);
        JsonElement deps = version.get("dependencies");
        if (deps != null && deps.isJsonArray()) {
            Set<String> required = new HashSet<>();
            for (JsonElement d : deps.getAsJsonArray()) {
                JsonObject o = d.getAsJsonObject();
                if (o.has("dependency_type") && "required".equals(o.get("dependency_type").getAsString())
                        && o.has("project_id") && !o.get("project_id").isJsonNull()) {
                    required.add("modrinth:" + o.get("project_id").getAsString());
                }
            }
            entry.setRequiredKeys(required);
        }
        JsonObject file = pickModrinthFile(version);
        entry.setFile(file.get("filename").getAsString(), file.get("url").getAsString());

        JsonElement loaders = version.get("loaders");
        if (loaders != null && loaders.isJsonArray()) {
            for (JsonElement l : loaders.getAsJsonArray()) {
                if (Labels.isLoader(l.getAsString())) {
                    entry.setLoader(Labels.loader(l.getAsString()));
                    break;
                }
            }
        }

        if (project == null) return null;
        ProjectSide server = project.serverSide();
        if (server == ProjectSide.UNSUPPORTED) return SideVerdict.clientOnly("Modrinth (server_side = unsupported)");
        if (server == ProjectSide.REQUIRED || server == ProjectSide.OPTIONAL) {
            return SideVerdict.serverCapable("Modrinth (server_side = " + server.toString().toLowerCase(Locale.ROOT) + ")");
        }
        return null;
    }

    private static JsonObject findModrinthVersion(ModrinthMod mr) throws Exception {
        JsonObject prefetched = MODRINTH_VERSIONS.get(mr.getVersionId());
        if (prefetched != null) return prefetched;
        if (!mr.getVersionId().isEmpty()) {
            return HttpJson.get(Modrinth.MODRINTH_API_LINK + "/version/" + encode(mr.getVersionId())).getAsJsonObject();
        }
        // Entries written before version ids were stored only have the version number.
        JsonArray versions = ModrinthUtils.listVersionsJson(mr.getProjectReference(), null, null);
        for (JsonElement v : versions) {
            JsonObject o = v.getAsJsonObject();
            if (mr.getVersionNumber().equals(o.get("version_number").getAsString())) return o;
        }
        throw new IOException("Version " + mr.getVersionNumber() + " introuvable sur Modrinth");
    }

    private static JsonObject pickModrinthFile(JsonObject version) throws IOException {
        JsonArray files = version.getAsJsonArray("files");
        JsonObject fallback = null;
        for (JsonElement f : files) {
            JsonObject o = f.getAsJsonObject();
            if (o.has("primary") && o.get("primary").getAsBoolean()) return o;
            if (fallback == null && o.get("filename").getAsString().endsWith(".jar")) fallback = o;
        }
        if (fallback == null && !files.isEmpty()) fallback = files.get(0).getAsJsonObject();
        if (fallback == null) throw new IOException("Aucun fichier pour cette version Modrinth");
        return fallback;
    }

    private static @Nullable SideVerdict resolveCurseForge(ExportEntry entry, CurseForgeMod cf) throws IOException {
        File file = CURSEFORGE_FILES.get(cf.fileID());
        if (file == null) file = CFUtils.getFileFromId(cf.projectID(), cf.fileID());
        if (file == null) throw new IOException("Fichier CurseForge #" + cf.fileID() + " introuvable");
        if (ResolveCache.getCurseForgeName(cf.projectID()) == null) entry.setTitle(file.displayName());
        entry.setProjectKey("curseforge:" + cf.projectID());
        if (file.dependencies() != null) {
            Set<String> required = new HashSet<>();
            for (FileDependency d : file.dependencies()) {
                if (d.relationType() == FileRelationType.REQUIRED_DEPENDENCY) required.add("curseforge:" + d.modId());
            }
            entry.setRequiredKeys(required);
        }

        // Authors can disable API downloads; the CDN still serves the file at a predictable address.
        String url = file.downloadUrl() != null ? file.downloadUrl()
                : "https://edge.forgecdn.net/files/" + (cf.fileID() / 1000) + "/" + (cf.fileID() % 1000) + "/"
                + URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        entry.setFile(file.fileName(), url);

        boolean client = false, server = false;
        for (String tag : file.gameVersions()) {
            if (tag.equalsIgnoreCase("Client")) client = true;
            else if (tag.equalsIgnoreCase("Server")) server = true;
            else if (entry.loader() == null && Labels.isLoader(tag.toLowerCase(Locale.ROOT))) {
                entry.setLoader(Labels.loader(tag.toLowerCase(Locale.ROOT)));
            }
        }
        if (server) return SideVerdict.serverCapable("CurseForge (tag Server)");
        if (client) return SideVerdict.clientOnly("CurseForge (tag Client)");
        return null;
    }

    private static @Nullable SideVerdict resolveUrl(ExportEntry entry, UrlMod url) {
        String name = url.name();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            String path = url.downloadURL();
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);
            String last = path.substring(path.lastIndexOf('/') + 1);
            name = last.toLowerCase(Locale.ROOT).endsWith(".jar") ? last : (name != null ? name : last) + ".jar";
        }
        entry.setFile(name, url.downloadURL());
        JarMetadata cached = UrlMetaCache.get(url.sha1());
        if (cached != null) entry.setLoader(cached.loader());
        return null; // a bare URL carries no platform tags
    }

    // ------------------------------------------------------------------
    // Download / zip
    // ------------------------------------------------------------------

    /** The jar of the entry in {@code tmpDir}, downloading it if it is not there yet. */
    static java.io.File ensureJar(ExportEntry entry, Path tmpDir) throws IOException {
        java.io.File existing = entry.jar();
        if (existing != null && existing.isFile()) return existing;
        if (entry.downloadUrl() == null) throw new IOException("Pas d'adresse de téléchargement");

        java.io.File target = tmpDir.resolve(UUID.randomUUID() + ".jar").toFile();
        download(entry.downloadUrl(), target);
        entry.setJar(target);
        return target;
    }

    private static void download(String address, java.io.File target) throws IOException {
        // A rate limit (429) or a server hiccup is waited out and retried rather than failing the mod
        for (int attempt = 1; ; attempt++) {
            HttpURLConnection con = (HttpURLConnection) new URL(address).openConnection();
            con.setConnectTimeout(15_000);
            con.setReadTimeout(30_000);
            con.setRequestProperty("User-Agent", Main.MODRINTH.getUserAgent());
            try {
                int code = con.getResponseCode();
                if ((code == 429 || code >= 500) && attempt < 6) {
                    HttpJson.sleepBackoff(attempt, con.getHeaderField("Retry-After"));
                    continue;
                }
                if (code >= 400) throw new IOException("HTTP " + code + " pour " + address);
                try (InputStream in = new BufferedInputStream(con.getInputStream());
                     OutputStream out = new FileOutputStream(target)) {
                    in.transferTo(out);
                }
                return;
            } catch (IOException e) {
                target.delete();
                throw e;
            } finally {
                con.disconnect();
            }
        }
    }

    /**
     * Writes the included entries into {@code zipFile} under {@code mods/}. Missing jars are downloaded first, in
     * parallel; a mod that cannot be fetched is skipped and reported rather than aborting the whole export.
     */
    public static Report export(List<ExportEntry> selected, Path tmpDir, java.io.File zipFile, Progress progress,
                                BooleanSupplier cancelled) throws IOException, InterruptedException {
        int total = selected.size();
        AtomicInteger done = new AtomicInteger();
        List<String> failed = new ArrayList<>();
        Set<ExportEntry> unavailable = new HashSet<>();

        ExecutorService pool = Executors.newFixedThreadPool(DOWNLOAD_THREADS, r -> {
            Thread t = new Thread(r, "fjc-export");
            t.setDaemon(true);
            return t;
        });
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (ExportEntry entry : selected) {
                futures.add(pool.submit(() -> {
                    if (cancelled.getAsBoolean()) return null;
                    try {
                        ensureJar(entry, tmpDir);
                    } catch (IOException e) {
                        synchronized (failed) {
                            failed.add(entry.title() + " : " + e.getMessage());
                            unavailable.add(entry);
                        }
                    }
                    progress.update(done.incrementAndGet(), total, "Téléchargement de " + entry.title());
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (java.util.concurrent.ExecutionException e) {
                    throw new IOException(e.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }
        if (cancelled.getAsBoolean()) throw new InterruptedException("Export annulé");

        Set<String> usedNames = new HashSet<>();
        int written = 0;
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zip.setLevel(Deflater.BEST_SPEED); // jars are already compressed
            for (ExportEntry entry : selected) {
                if (unavailable.contains(entry) || entry.jar() == null) continue;
                zip.putNextEntry(new ZipEntry("mods/" + uniqueName(entry.fileName(), usedNames)));
                Files.copy(entry.jar().toPath(), zip);
                zip.closeEntry();
                written++;
            }
        }
        return new Report(written, failed);
    }

    private static String uniqueName(@Nullable String fileName, Set<String> used) {
        String name = fileName == null ? "mod.jar" : fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
        String candidate = name;
        for (int i = 2; !used.add(candidate.toLowerCase(Locale.ROOT)); i++) {
            int dot = name.lastIndexOf('.');
            candidate = dot < 0 ? name + "-" + i : name.substring(0, dot) + "-" + i + name.substring(dot);
        }
        return candidate;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
