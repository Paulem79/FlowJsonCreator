package net.paulem.fjc.gui.browse;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Works out which other mods must (or may) come along with a version: walks the required dependencies
 * transitively, picks for each the newest version fitting the same game version / loader, and skips
 * what mods.json already has.
 */
public final class DependencyResolver {
    /** A mod to add alongside the one the user picked. */
    public record Item(SearchResult project, VersionOption version, boolean required, String neededBy) {
    }

    /** {@code unresolved} lists dependencies that could not be resolved (unknown project, no compatible version). */
    public record Plan(List<Item> items, List<String> unresolved) {
        public boolean isEmpty() {
            return items.isEmpty() && unresolved.isEmpty();
        }
    }

    private record Node(String title, VersionOption version) {
    }

    private DependencyResolver() {
    }

    public static Plan resolve(ModSource source, SearchResult root, VersionOption rootVersion,
                               @Nullable String gameVersion, @Nullable String loader,
                               Predicate<SearchResult> alreadyInstalled) throws Exception {
        // Without a filter, follow the version the user picked.
        String gv = gameVersion != null ? gameVersion
                : rootVersion.gameVersions().stream().findFirst().orElse(null);
        String ld = loader != null ? loader
                : rootVersion.loaders().stream().filter(l -> Labels.isLoader(l.toLowerCase())).findFirst().orElse(null);

        List<Item> items = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        visited.add(root.key());

        Deque<Node> queue = new ArrayDeque<>();
        queue.add(new Node(root.title(), rootVersion));

        while (!queue.isEmpty()) {
            Node node = queue.poll();

            List<DependencyRef> refs;
            try {
                refs = source.dependencies(node.version());
            } catch (Exception e) {
                unresolved.add("Dépendances de " + node.title() + " illisibles (" + e.getMessage() + ")");
                continue;
            }

            for (DependencyRef ref : refs) {
                String key = source.category().name() + ":" + ref.projectId();
                if (!visited.add(key)) continue;

                try {
                    Optional<SearchResult> project = source.project(ref.projectId());
                    if (project.isEmpty()) {
                        if (ref.required()) unresolved.add("Projet " + ref.projectId() + " introuvable (requis par " + node.title() + ")");
                        continue;
                    }
                    SearchResult p = project.get();
                    if (alreadyInstalled.test(p)) continue;

                    Optional<VersionOption> version = latest(source, p, gv, ld);
                    if (version.isEmpty()) {
                        // An optional mod with nothing for this game version is just not on offer; a required one is a problem.
                        if (ref.required()) unresolved.add(p.title() + " (requis) : aucune version compatible");
                        continue;
                    }
                    items.add(new Item(p, version.get(), ref.required(), node.title()));
                    // Only required links are followed: the dependencies of an optional mod are its own business.
                    if (ref.required()) queue.add(new Node(p.title(), version.get()));
                } catch (Exception e) {
                    if (ref.required()) unresolved.add("Dépendance " + ref.projectId() + " (requise par " + node.title() + ") : " + e.getMessage());
                }
            }
        }

        // Required first, so the dialog leads with what actually matters.
        items.sort((a, b) -> Boolean.compare(b.required(), a.required()));
        return new Plan(items, unresolved);
    }

    private static Optional<VersionOption> latest(ModSource source, SearchResult project, @Nullable String gameVersion,
                                                  @Nullable String loader) throws Exception {
        if (gameVersion != null) return source.latestMatching(project, gameVersion, loader);

        List<VersionOption> versions = source.listVersions(project, null, loader);
        return versions.stream().filter(v -> v.releaseType().equalsIgnoreCase("release")).findFirst()
                .or(() -> versions.stream().findFirst());
    }
}
