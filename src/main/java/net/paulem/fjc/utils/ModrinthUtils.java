package net.paulem.fjc.utils;

import net.paulem.fjc.Main;
import ovh.paulem.modrinthapi.types.project.Project;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ModrinthUtils {
    // Mémoïse les requêtes Modrinth pour éviter de refaire le même appel réseau
    // plusieurs fois quand plusieurs versions proviennent du même projet.
    private static final ConcurrentHashMap<String, Optional<Project>> PROJECT_CACHE = new ConcurrentHashMap<>();

    @Nullable
    public static Project getModFromSlug(String slug) {
        return PROJECT_CACHE.computeIfAbsent(slug, s -> {
            try {
                return Optional.ofNullable(Main.MODRINTH.getProject(s));
            } catch (URISyntaxException | IOException e) {
                return Optional.empty();
            }
        }).orElse(null);
    }
}
