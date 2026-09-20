package net.paulem.fjc.gui.model;

import net.paulem.fjc.flow.mod.Mod;
import net.paulem.fjc.gui.browse.SearchResult;
import org.jetbrains.annotations.Nullable;

/**
 * A single row in the mods list: a display-ready snapshot bound to the underlying {@link Mod}
 * it was built from. Identity (equals/hashCode) is delegated to the underlying mod, never to the
 * display text, so lookups and removals survive a mod's name being re-resolved or containing
 * punctuation such as " - ".
 */
public final class ModEntry {
    public enum Status {
        LOADING,
        RESOLVED,
        ERROR
    }

    private final Mod source;
    private final ModCategory category;
    private final String title;
    @Nullable
    private final String subtitle;
    private final Status status;
    @Nullable
    private final SearchResult info;

    public ModEntry(Mod source, ModCategory category, String title, @Nullable String subtitle, Status status) {
        this(source, category, title, subtitle, status, null);
    }

    /** {@code info} is what the source API knows about the project (icon, description, tags, stats), once resolved. */
    public ModEntry(Mod source, ModCategory category, String title, @Nullable String subtitle, Status status,
                    @Nullable SearchResult info) {
        this.source = source;
        this.category = category;
        this.title = title;
        this.subtitle = subtitle;
        this.status = status;
        this.info = info;
    }

    public static ModEntry loading(Mod source, ModCategory category, String subtitle) {
        return new ModEntry(source, category, "Résolution en cours...", subtitle, Status.LOADING);
    }

    public ModEntry resolved(String title, @Nullable String subtitle) {
        return new ModEntry(source, category, title, subtitle, Status.RESOLVED);
    }

    public ModEntry error(String title, @Nullable String subtitle) {
        return new ModEntry(source, category, title, subtitle, Status.ERROR);
    }

    public Mod getSource() {
        return source;
    }

    public ModCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    @Nullable
    public String getSubtitle() {
        return subtitle;
    }

    public Status getStatus() {
        return status;
    }

    @Nullable
    public SearchResult getInfo() {
        return info;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModEntry other)) return false;
        return source.equals(other.source);
    }

    @Override
    public int hashCode() {
        return source.hashCode();
    }
}
