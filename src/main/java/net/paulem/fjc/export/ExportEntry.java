package net.paulem.fjc.export;

import net.paulem.fjc.flow.mod.Mod;
import net.paulem.fjc.gui.model.ModCategory;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * One mod of the modpack on its way to a .zip: what it is, where its jar lives, and whether it belongs on a server.
 * Filled in by {@link ModExporter#resolve} on a background thread, read by the UI on the FX thread.
 */
public final class ExportEntry {
    public enum State {PENDING, READY, ERROR}

    private final Mod mod;
    private final ModCategory category;

    private volatile String title;
    private volatile State state = State.PENDING;
    private volatile @Nullable String fileName;
    private volatile @Nullable String downloadUrl;
    private volatile @Nullable String loader;
    private volatile @Nullable SideVerdict verdict;
    private volatile @Nullable String error;
    private volatile @Nullable File jar;
    private volatile boolean include = true;
    private volatile @Nullable String projectKey;
    private volatile Set<String> requiredKeys = Set.of();

    public ExportEntry(Mod mod, ModCategory category, String title) {
        this.mod = mod;
        this.category = category;
        this.title = title;
    }

    public Mod mod() {
        return mod;
    }

    public ModCategory category() {
        return category;
    }

    public String title() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public State state() {
        return state;
    }

    public @Nullable String fileName() {
        return fileName;
    }

    public @Nullable String downloadUrl() {
        return downloadUrl;
    }

    public @Nullable String loader() {
        return loader;
    }

    public void setLoader(@Nullable String loader) {
        this.loader = loader;
    }

    public @Nullable SideVerdict verdict() {
        return verdict;
    }

    public void setVerdict(@Nullable SideVerdict verdict) {
        this.verdict = verdict;
    }

    public @Nullable String error() {
        return error;
    }

    public @Nullable File jar() {
        return jar;
    }

    public void setJar(@Nullable File jar) {
        this.jar = jar;
    }

    public boolean isIncluded() {
        return include;
    }

    public void setIncluded(boolean include) {
        this.include = include;
    }

    void setFile(String fileName, String downloadUrl) {
        this.fileName = fileName;
        this.downloadUrl = downloadUrl;
    }

    void markReady() {
        this.state = State.READY;
    }

    void markError(String message) {
        this.error = message;
        this.state = State.ERROR;
        this.include = false;
    }

    /** Identifies the project across entries, e.g. {@code modrinth:AANobbMI} or {@code curseforge:238222}. */
    public @Nullable String projectKey() {
        return projectKey;
    }

    void setProjectKey(@Nullable String projectKey) {
        this.projectKey = projectKey;
    }

    /** Project keys of the mods this one requires, as declared by its platform. */
    public Set<String> requiredKeys() {
        return requiredKeys;
    }

    void setRequiredKeys(Set<String> keys) {
        this.requiredKeys = Set.copyOf(keys);
    }

    /** Server exports leave out what is client-only; client exports take everything. */
    public boolean defaultIncluded(boolean serverExport) {
        if (state == State.ERROR) return false;
        SideVerdict v = verdict;
        return !serverExport || v == null || !v.isClientOnly();
    }

    /**
     * Default selection for a whole modpack: like {@link #defaultIncluded}, except that a client-only mod stays in
     * a server export when an included mod requires it (transitively), since the server would not start without it.
     */
    public static void applyDefaults(Collection<ExportEntry> entries, boolean serverExport) {
        Map<String, ExportEntry> byKey = new HashMap<>();
        for (ExportEntry e : entries) if (e.projectKey != null) byKey.put(e.projectKey, e);

        Deque<ExportEntry> pending = new ArrayDeque<>();
        for (ExportEntry e : entries) {
            e.include = e.defaultIncluded(serverExport);
            if (e.include) pending.add(e);
        }
        while (!pending.isEmpty()) {
            for (String key : pending.poll().requiredKeys) {
                ExportEntry dep = byKey.get(key);
                if (dep != null && !dep.include && dep.state != State.ERROR) {
                    dep.include = true;
                    pending.add(dep);
                }
            }
        }
    }
}
