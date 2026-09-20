package net.paulem.fjc.gui.browse;

import net.paulem.fjc.gui.model.ModCategory;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * A mod hit, whatever its source, reduced to what the result card needs.
 * {@code raw} is the source's own object when it is at hand (CurseForge {@code Mod}), else null.
 */
public record SearchResult(ModCategory source, String id, String slug, String title, String description,
                           String author, @Nullable String iconUrl, long downloads, long follows,
                           List<String> tags, @Nullable Instant dateModified, @Nullable Instant dateCreated,
                           @Nullable Object raw) {
    public String key() {
        return source.name() + ":" + id;
    }
}
