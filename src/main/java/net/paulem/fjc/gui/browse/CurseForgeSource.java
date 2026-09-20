package net.paulem.fjc.gui.browse;

import io.github.matyrobbrt.curseforgeapi.request.Response;
import io.github.matyrobbrt.curseforgeapi.request.query.FileListQuery;
import io.github.matyrobbrt.curseforgeapi.request.query.ModSearchQuery;
import io.github.matyrobbrt.curseforgeapi.schemas.PaginatedData;
import io.github.matyrobbrt.curseforgeapi.schemas.file.File;
import io.github.matyrobbrt.curseforgeapi.schemas.file.FileDependency;
import io.github.matyrobbrt.curseforgeapi.schemas.file.FileIndex;
import io.github.matyrobbrt.curseforgeapi.schemas.file.FileRelationType;
import io.github.matyrobbrt.curseforgeapi.schemas.mod.Mod;
import io.github.matyrobbrt.curseforgeapi.schemas.mod.ModLoaderType;
import io.github.matyrobbrt.curseforgeapi.util.Constants;
import net.paulem.fjc.Main;
import net.paulem.fjc.flow.mod.CurseForgeMod;
import net.paulem.fjc.gui.model.ModCategory;
import net.paulem.fjc.utils.CFUtils;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class CurseForgeSource implements ModSource {
    @Override
    public ModCategory category() {
        return ModCategory.CURSEFORGE;
    }

    /** CurseForge has its own category taxonomy, so the (Modrinth) category filter cannot be applied to it. */
    @Override
    public boolean supportsCategoryFilter() {
        return false;
    }

    @Override
    public SearchPage search(SearchQuery q) throws Exception {
        if (Main.cfApi == null) return new SearchPage(List.of(), false);

        ModSearchQuery query = ModSearchQuery.of(Constants.GameIDs.MINECRAFT)
                .classId(6) // mods only
                .sortField(q.sort().curseForgeField())
                .sortOrder(q.sort() == SortField.NAME ? ModSearchQuery.SortOrder.ASCENDENT : ModSearchQuery.SortOrder.DESCENDENT);
        if (!q.query().isBlank()) query.searchFilter(q.query().trim());
        if (q.gameVersion() != null) query.gameVersion(q.gameVersion());
        ModLoaderType loader = toLoaderType(q.loader());
        if (loader != null) query.modLoaderTypes(List.of(loader));
        query.pageSize(q.limit());
        query.index(q.offset());

        Response<PaginatedData<List<Mod>>> response = Main.cfApi.getHelper().searchModsPaginated(query);
        if (response.isEmpty()) return new SearchPage(List.of(), false);

        PaginatedData<List<Mod>> page = response.get();
        List<SearchResult> results = new ArrayList<>();
        for (Mod mod : page.data()) results.add(toResult(mod));

        Integer total = page.pagination().totalCount();
        boolean hasMore = total != null ? q.offset() + results.size() < total : results.size() >= q.limit();
        return new SearchPage(results, hasMore);
    }

    @Override
    public List<VersionOption> listVersions(SearchResult r, @Nullable String gameVersion, @Nullable String loaderName) throws Exception {
        if (Main.cfApi == null) return List.of();

        FileListQuery query = FileListQuery.of();
        if (gameVersion != null) query.gameVersion(gameVersion);
        ModLoaderType loader = toLoaderType(loaderName);
        if (loader != null) query.modLoaderType(loader);
        query.pageSize(50);

        Response<List<File>> response = Main.cfApi.getHelper().getModFiles(Integer.parseInt(r.id()), query);
        if (response.isEmpty()) return List.of();

        List<VersionOption> options = new ArrayList<>();
        for (File f : response.get()) {
            List<String> loaders = f.gameVersions().stream().filter(v -> Labels.isLoader(v.toLowerCase())).toList();
            List<String> gameVersions = f.gameVersions().stream().filter(v -> !Labels.isLoader(v.toLowerCase())).toList();
            options.add(new VersionOption(String.valueOf(f.id()), f.displayName(), f.fileName(), f.releaseType().name(),
                    gameVersions, loaders, safeInstant(f.fileDate()), f.downloadCount(), new CurseForgeMod(f.modId(), f.id()),
                    toRefs(f)));
        }
        options.sort(Comparator.comparing(VersionOption::date, Comparator.nullsLast(Comparator.reverseOrder())));
        return options;
    }

    /** Uses the mod's {@code latestFilesIndexes} (already in the search hit) so "Install" needs no extra request. */
    @Override
    public Optional<VersionOption> latestMatching(SearchResult r, String gameVersion, @Nullable String loaderName) throws Exception {
        if (r.raw() instanceof Mod mod && mod.latestFilesIndexes() != null) {
            ModLoaderType wanted = toLoaderType(loaderName);
            List<FileIndex> matches = mod.latestFilesIndexes().stream()
                    .filter(i -> gameVersion.equals(i.gameVersion()))
                    .filter(i -> wanted == null || loaderOf(i) == wanted)
                    .toList();
            Optional<FileIndex> best = matches.stream().filter(i -> i.releaseType().name().equals("RELEASE")).findFirst()
                    .or(() -> matches.stream().findFirst());
            if (best.isPresent()) {
                FileIndex i = best.get();
                ModLoaderType l = loaderOf(i);
                return Optional.of(new VersionOption(String.valueOf(i.fileId()), i.filename(), i.filename(), i.releaseType().name(),
                        List.of(i.gameVersion()), l == null ? List.of() : List.of(Labels.loader(l.name().toLowerCase())),
                        null, 0, new CurseForgeMod(mod.id(), i.fileId()), List.of()));
            }
        }
        return ModSource.super.latestMatching(r, gameVersion, loaderName);
    }

    @Override
    public Optional<SearchResult> project(String id) {
        Mod mod = CFUtils.getModFromId(Integer.parseInt(id));
        return mod == null ? Optional.empty() : Optional.of(toResult(mod));
    }

    /** Search-hit versions come from {@code latestFilesIndexes}, which carry no dependencies: fetch the file. */
    @Override
    public List<DependencyRef> dependencies(VersionOption version) {
        if (!version.dependencies().isEmpty() || !(version.mod() instanceof CurseForgeMod cf)) return version.dependencies();
        File file = CFUtils.getFileFromId(cf.projectID(), cf.fileID());
        return file == null ? List.of() : toRefs(file);
    }

    private static List<DependencyRef> toRefs(File file) {
        List<DependencyRef> refs = new ArrayList<>();
        if (file.dependencies() == null) return refs;
        for (FileDependency d : file.dependencies()) {
            boolean required = d.relationType() == FileRelationType.REQUIRED_DEPENDENCY;
            if (required || d.relationType() == FileRelationType.OPTIONAL_DEPENDENCY) {
                refs.add(new DependencyRef(String.valueOf(d.modId()), null, required));
            }
        }
        return refs;
    }

    public static SearchResult toResult(Mod mod) {
        List<String> tags = new ArrayList<>();
        if (mod.categories() != null) mod.categories().forEach(c -> tags.add(c.name()));

        if (mod.latestFilesIndexes() != null) {
            mod.latestFilesIndexes().stream().map(CurseForgeSource::loaderOf)
                    .filter(l -> l != null && Labels.isLoader(l.name().toLowerCase()))
                    .map(l -> Labels.loader(l.name().toLowerCase())).distinct().forEach(tags::add);
        }

        String author = mod.authors() == null || mod.authors().isEmpty() ? "" : mod.authors().get(0).name();
        String icon = mod.logo() == null ? null : (mod.logo().thumbnailUrl() != null ? mod.logo().thumbnailUrl() : mod.logo().url());
        return new SearchResult(ModCategory.CURSEFORGE, String.valueOf(mod.id()), mod.slug(), mod.name(),
                mod.summary() == null ? "" : mod.summary(), author, icon, mod.downloadCount(),
                mod.thumbsUpCount() == null ? 0 : mod.thumbsUpCount(), tags,
                safeInstant(mod.dateModified()), safeInstant(mod.dateCreated()), mod);
    }

    private static @Nullable ModLoaderType loaderOf(FileIndex index) {
        if (index.modLoader() == null) return null;
        try {
            return ModLoaderType.byId(index.modLoader());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static @Nullable ModLoaderType toLoaderType(@Nullable String loader) {
        if (loader == null) return null;
        try {
            return ModLoaderType.valueOf(loader.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static @Nullable Instant safeInstant(@Nullable String s) {
        if (s == null) return null;
        try {
            return Instant.parse(s);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
