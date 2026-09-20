package net.paulem.fjc.gui.browse;

import net.paulem.fjc.gui.model.ModCategory;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public interface ModSource {
    ModCategory category();

    /** Whether this source can apply the given category filter (categories differ between platforms). */
    default boolean supportsCategoryFilter() {
        return true;
    }

    SearchPage search(SearchQuery query) throws Exception;

    /** The project with this id, for resolving a dependency. */
    Optional<SearchResult> project(String id) throws Exception;

    /** The dependencies of a version (fetched if the version does not carry them already). */
    default List<DependencyRef> dependencies(VersionOption version) throws Exception {
        return version.dependencies();
    }

    /** Versions of the mod matching the filters, newest first. */
    List<VersionOption> listVersions(SearchResult result, @Nullable String gameVersion, @Nullable String loader) throws Exception;

    /** The version "Install" should pick: newest release matching the filters, else newest of any type. */
    default Optional<VersionOption> latestMatching(SearchResult result, String gameVersion, @Nullable String loader) throws Exception {
        List<VersionOption> versions = listVersions(result, gameVersion, loader);
        return versions.stream().filter(v -> v.releaseType().equalsIgnoreCase("release")).findFirst()
                .or(() -> versions.stream().findFirst());
    }
}
