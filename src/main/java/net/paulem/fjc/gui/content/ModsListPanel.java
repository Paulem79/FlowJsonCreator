package net.paulem.fjc.gui.content;

import io.github.matyrobbrt.curseforgeapi.schemas.file.File;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import net.paulem.fjc.flow.ModsJson;
import net.paulem.fjc.flow.mod.CurseForgeMod;
import net.paulem.fjc.flow.mod.Mod;
import net.paulem.fjc.flow.mod.ModrinthMod;
import net.paulem.fjc.flow.mod.UrlMod;
import net.paulem.fjc.gui.browse.CardParts;
import net.paulem.fjc.gui.browse.CurseForgeSource;
import net.paulem.fjc.gui.browse.ModrinthSource;
import net.paulem.fjc.gui.browse.SearchResult;
import net.paulem.fjc.gui.components.PropertiesViewerPopup;
import net.paulem.fjc.gui.components.UpdatesDialog;
import net.paulem.fjc.gui.model.ModCategory;
import net.paulem.fjc.gui.model.ModEntry;
import net.paulem.fjc.update.UpdateChecker;
import net.paulem.fjc.update.UpdateInfo;
import net.paulem.fjc.utils.CFUtils;
import net.paulem.fjc.utils.FileUtils;
import net.paulem.fjc.utils.JarMetadata;
import net.paulem.fjc.utils.JsonUtils;
import net.paulem.fjc.utils.ModrinthUtils;
import net.paulem.fjc.utils.ResolveCache;
import net.paulem.fjc.utils.UrlMetaCache;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;
import ovh.paulem.modrinthapi.types.project.Project;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * The mods of the modpack (everything currently in mods.json) as searchable, sortable cards styled like the
 * search results, with icons/descriptions/stats resolved in the background from the CurseForge/Modrinth APIs.
 * <p>
 * Every mod is tracked by a {@link Mod} identity (never by a parsed display string), which is
 * what makes add/remove/search safe even for mods whose resolved name contains punctuation.
 */
public class ModsListPanel extends VBox {
    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("fjc.debug", "false"));

    private enum Sort {
        NAME_ASC("Nom (A → Z)"),
        NAME_DESC("Nom (Z → A)"),
        DOWNLOADS("Téléchargements"),
        UPDATED("Dernière mise à jour"),
        SOURCE("Source");

        private final String label;

        Sort(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final Stage stage;

    private final Map<Mod, ModEntry> allEntries = new ConcurrentHashMap<>();

    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicLong generation = new AtomicLong(0);
    /** URL mods whose jar was already fetched this session, so a failed fetch is not retried on every reload. */
    private final Set<String> urlFetchAttempted = ConcurrentHashMap.newKeySet();

    /** Newer versions found by the update check, by installed mod. */
    private final Map<Mod, UpdateInfo> updates = new ConcurrentHashMap<>();
    private final AtomicLong updateGeneration = new AtomicLong(0);

    private ExecutorService bgExecutor;
    private ExecutorService updateExecutor;
    private ScheduledExecutorService uiScheduler;
    private volatile boolean executorsStarted = false;

    private final TextField searchField = new TextField();
    private final ComboBox<Sort> sortBox = new ComboBox<>();
    private final Map<ModCategory, ToggleButton> sourceToggles = new EnumMap<>(ModCategory.class);
    private final Label totalCountLabel = new Label();
    private final Label placeholder = new Label();
    private final Button updateAllBtn = new Button();
    private final Label updateStatus = new Label();
    /** How many updates the list was last refreshed for, so the cards are not rebuilt on every finished check. */
    private int shownUpdates = 0;

    // Only touched on the FX thread
    private final ObservableList<ModEntry> master = FXCollections.observableArrayList();
    private final FilteredList<ModEntry> filtered = new FilteredList<>(master, e -> true);
    private final SortedList<ModEntry> sorted = new SortedList<>(filtered);
    private final ListView<ModEntry> listView = new ListView<>(sorted);

    private final ModCardCell.Host cardHost = new ModCardCell.Host() {
        @Override
        public void showDetails(ModEntry entry) {
            showProperties(entry);
        }

        @Override
        public void confirmAndRemove(ModEntry entry) {
            ModsListPanel.this.confirmAndRemove(entry);
        }

        @Override
        public UpdateInfo updateFor(ModEntry entry) {
            return updates.get(entry.getSource());
        }
    };

    public ModsListPanel(Stage stage) {
        this.stage = stage;
        setSpacing(10);
        buildUi();
    }

    private static void debug(String message) {
        if (DEBUG) System.err.println("[DEBUG] " + message);
    }

    private static void debug(String message, Throwable t) {
        if (DEBUG) {
            System.err.println("[DEBUG] " + message);
            t.printStackTrace(System.err);
        }
    }

    // ------------------------------------------------------------------
    // UI construction
    // ------------------------------------------------------------------

    private void buildUi() {
        searchField.setPromptText("Rechercher dans le modpack...");
        searchField.setPrefColumnCount(24);
        HBox searchBox = new HBox(6, FontIcon.of(Material2MZ.SEARCH, 16), searchField);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        sortBox.getItems().addAll(Sort.values());
        sortBox.setValue(Sort.NAME_ASC);
        sortBox.setTooltip(new Tooltip("Trier les mods"));

        // Which sources to show: same colored buttons as in the search tab.
        FlowPane toolbar = new FlowPane(10, 8, searchBox, sortBox);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        for (ModCategory category : ModCategory.values()) {
            ToggleButton toggle = new ToggleButton(category.getLabel(), FontIcon.of(category.getIcon(), 14, category.getColor()));
            toggle.setSelected(true);
            toggle.setTooltip(new Tooltip("Afficher / masquer les mods " + category.getLabel()));
            toggle.setOnAction(e -> applyFilter());
            sourceToggles.put(category, toggle);
            toolbar.getChildren().add(toggle);
        }

        Button refreshBtn = new Button("Actualiser", FontIcon.of(Material2MZ.REFRESH, 14));
        refreshBtn.setTooltip(new Tooltip("Rafraîchir les infos depuis Modrinth/CurseForge"));
        refreshBtn.setOnAction(e -> refreshAll());
        toolbar.getChildren().add(refreshBtn);

        updateAllBtn.setOnAction(e -> openUpdatesDialog());
        updateStatus.getStyleClass().add("text-muted");
        toolbar.getChildren().addAll(updateAllBtn, updateStatus);
        refreshUpdateUi(0, 0);

        totalCountLabel.getStyleClass().add("text-muted");
        placeholder.getStyleClass().add("text-muted");

        listView.getStyleClass().add("browse-list");
        listView.setCellFactory(lv -> new ModCardCell(cardHost));
        listView.setPlaceholder(placeholder);
        VBox.setVgrow(listView, Priority.ALWAYS);
        listView.setOnKeyPressed(ke -> {
            if (ke.getCode() == KeyCode.DELETE || ke.getCode() == KeyCode.BACK_SPACE) {
                ModEntry selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null) confirmAndRemove(selected);
            }
        });

        getChildren().addAll(toolbar, listView, totalCountLabel);

        searchField.textProperty().addListener((obs, oldV, newV) -> applyFilter());
        sortBox.setOnAction(e -> applySort());
        filtered.addListener((javafx.collections.ListChangeListener<ModEntry>) c -> updateCount());
        applySort();
        updateCount();
    }

    // ------------------------------------------------------------------
    // Search / sort / source filter
    // ------------------------------------------------------------------

    private void applyFilter() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        filtered.setPredicate(entry -> {
            ToggleButton toggle = sourceToggles.get(entry.getCategory());
            if (toggle != null && !toggle.isSelected()) return false;
            return matches(entry, q);
        });
        updateCount();
    }

    private boolean matches(ModEntry entry, String query) {
        if (query.isEmpty()) return true;
        if (entry.getTitle().toLowerCase().contains(query)) return true;
        if (entry.getSubtitle() != null && entry.getSubtitle().toLowerCase().contains(query)) return true;

        SearchResult info = entry.getInfo();
        if (info == null) return false;
        return info.description().toLowerCase().contains(query)
                || info.author().toLowerCase().contains(query)
                || info.tags().stream().anyMatch(tag -> tag.toLowerCase().contains(query));
    }

    private void applySort() {
        Comparator<ModEntry> byName = Comparator.comparing(e -> e.getTitle().toLowerCase());
        Comparator<ModEntry> cmp = switch (sortBox.getValue()) {
            case NAME_ASC -> byName;
            case NAME_DESC -> byName.reversed();
            case DOWNLOADS -> Comparator.comparingLong((ModEntry e) -> e.getInfo() == null ? -1 : e.getInfo().downloads())
                    .reversed().thenComparing(byName);
            case UPDATED -> Comparator.comparing((ModEntry e) -> e.getInfo() == null ? null : e.getInfo().dateModified(),
                    Comparator.nullsLast(Comparator.<java.time.Instant>reverseOrder())).thenComparing(byName);
            case SOURCE -> Comparator.comparing(ModEntry::getCategory).thenComparing(byName);
        };
        sorted.setComparator(cmp);
    }

    private void updateCount() {
        int total = master.size();
        int shown = filtered.size();
        totalCountLabel.setText(shown == total ? total + " mod(s) au total" : shown + " affiché(s) sur " + total + " mod(s)");
        placeholder.setText(total == 0
                ? "Aucun mod dans le modpack pour le moment. Ajoutes-en depuis l'onglet Recherche."
                : "Aucun mod ne correspond à ta recherche.");
    }

    // ------------------------------------------------------------------
    // Deletion / details
    // ------------------------------------------------------------------

    private void confirmAndRemove(ModEntry entry) {
        Alert confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setTitle("Retirer un mod");
        confirm.setHeaderText("Retirer \"" + entry.getTitle() + "\" ?");
        confirm.setContentText("Cette action retire le mod du mods.json. Elle est irréversible.");
        confirm.initOwner(stage);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                JsonUtils.removeMod(entry.getSource());
            }
        });
    }

    private void showProperties(ModEntry entry) {
        // The resolved project has far more to show than the bare mods.json reference.
        SearchResult info = entry.getInfo();
        new PropertiesViewerPopup(stage).showPopup(info != null && info.raw() != null ? info.raw() : entry.getSource());
    }

    // ------------------------------------------------------------------
    // Data loading / resolution
    // ------------------------------------------------------------------

    private synchronized void ensureExecutorsStarted() {
        if (executorsStarted) return;
        int cores = Runtime.getRuntime().availableProcessors();
        // I/O-bound (HTTP) workload: a larger pool than the CPU count meaningfully speeds up
        // resolving names for modpacks with many mods, since threads mostly wait on the network.
        int poolSize = Math.min(32, Math.max(8, cores * 4));
        bgExecutor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "fjc-resolver");
            t.setDaemon(true);
            return t;
        });
        // Few threads: each check is 2-3 requests and Modrinth rate-limits (HttpJson waits those out)
        updateExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "fjc-update-check");
            t.setDaemon(true);
            return t;
        });
        uiScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "fjc-ui-throttler");
            t.setDaemon(true);
            return t;
        });
        uiScheduler.scheduleAtFixedRate(this::pushIfDirty, 0, 200, TimeUnit.MILLISECONDS);
        executorsStarted = true;
    }

    private void pushIfDirty() {
        if (!dirty.getAndSet(false)) return;
        Map<Mod, ModEntry> snapshot = new HashMap<>(allEntries);
        Platform.runLater(() -> applySnapshot(snapshot));
    }

    /**
     * Brings the displayed list in line with the resolved entries by adding, replacing and removing only what
     * changed (rather than resetting the whole list), so the scroll position survives while names resolve.
     */
    private void applySnapshot(Map<Mod, ModEntry> snapshot) {
        Map<Mod, Integer> index = new HashMap<>();
        for (int i = 0; i < master.size(); i++) index.put(master.get(i).getSource(), i);

        boolean changed = false;
        List<ModEntry> toAdd = new ArrayList<>();
        for (ModEntry entry : snapshot.values()) {
            Integer i = index.get(entry.getSource());
            if (i == null) {
                toAdd.add(entry);
            } else if (master.get(i) != entry) {
                master.set(i, entry);
                changed = true;
            }
        }
        if (!toAdd.isEmpty()) {
            master.addAll(toAdd);
            changed = true;
        }
        if (master.removeIf(entry -> !snapshot.containsKey(entry.getSource()))) changed = true;

        // ModEntry equality is by mod identity, so cells would not notice a replaced (equal) entry on their own.
        if (changed) listView.refresh();
        updateCount();
    }

    private void putEntry(ModEntry entry) {
        allEntries.put(entry.getSource(), entry);
        dirty.set(true);
    }

    /** Builds the full list from scratch (startup), or forces a re-resolution bypassing the cache (refresh). */
    public void loadInitial(ModsJson content) {
        load(content, false);
    }

    public void refreshAll() {
        load(null, true);
    }

    private void load(@Nullable ModsJson contentOverride, boolean forceRefresh) {
        ensureExecutorsStarted();
        final long gen = generation.incrementAndGet();
        ModsJson content = contentOverride != null ? contentOverride : net.paulem.fjc.Main.jsonContent;

        if (!forceRefresh) allEntries.clear();

        for (UrlMod url : content.mods) {
            resolveUrl(url, gen);
        }
        UrlMetaCache.retainOnly(content.mods.stream().map(UrlMod::sha1).collect(Collectors.toSet()));

        for (CurseForgeMod cf : content.curseFiles) {
            resolveCurseForge(cf, gen, forceRefresh);
        }

        for (ModrinthMod mr : content.modrinthMods) {
            resolveModrinth(mr, gen, forceRefresh);
        }

        checkForUpdates(content);
    }

    // ------------------------------------------------------------------
    // Updates
    // ------------------------------------------------------------------

    /** Looks for newer versions of every Modrinth/CurseForge mod in the background; cards get a diamond as results arrive. */
    private void checkForUpdates(ModsJson content) {
        long gen = updateGeneration.incrementAndGet();
        updates.clear();

        List<Mod> targets = new ArrayList<>(content.modrinthMods);
        targets.addAll(content.curseFiles);
        int total = targets.size();
        AtomicInteger done = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        refreshUpdateUiLater(0, total, 0);

        for (Mod mod : targets) {
            updateExecutor.submit(() -> {
                try {
                    UpdateChecker.check(mod).ifPresent(update -> {
                        if (gen == updateGeneration.get()) updates.put(mod, update);
                    });
                } catch (Exception ex) {
                    failed.incrementAndGet();
                    debug("Vérification de mise à jour impossible pour " + mod, ex);
                }
                if (gen != updateGeneration.get()) return;
                refreshUpdateUiLater(done.incrementAndGet(), total, failed.get());
            });
        }
    }

    private void refreshUpdateUiLater(int checked, int total, int failed) {
        Platform.runLater(() -> refreshUpdateUi(checked, total, failed));
    }

    private void refreshUpdateUi(int checked, int total) {
        refreshUpdateUi(checked, total, 0);
    }

    /** FX thread. {@code checked < total} means the check is still running. */
    private void refreshUpdateUi(int checked, int total, int failed) {
        int count = updates.size();
        boolean running = checked < total;

        updateAllBtn.setGraphic(CardParts.updateDiamond(12, null));
        updateAllBtn.setText(count == 0 ? "Tout mettre à jour" : "Tout mettre à jour (" + count + ")");
        updateAllBtn.setDisable(count == 0);
        updateAllBtn.setTooltip(new Tooltip(count == 0 ? "Aucune mise à jour disponible" : "Choisir les mises à jour à appliquer"));

        if (running) {
            updateStatus.setText("Recherche de mises à jour... " + checked + "/" + total);
        } else if (failed > 0) {
            updateStatus.setText(failed + " vérification(s) échouée(s)");
        } else {
            updateStatus.setText(total == 0 || count > 0 ? "" : "Tout est à jour");
        }

        // Cards read the update map when built: rebuild them only when it changed
        if (count != shownUpdates || !running) {
            shownUpdates = count;
            listView.refresh();
        }
    }

    private void openUpdatesDialog() {
        List<UpdatesDialog.Row> rows = new ArrayList<>();
        for (UpdateInfo update : updates.values()) {
            ModEntry entry = allEntries.get(update.current());
            if (entry == null) continue; // removed since the check
            rows.add(new UpdatesDialog.Row(update, entry.getTitle(),
                    entry.getInfo() != null ? entry.getInfo().iconUrl() : null, entry.getCategory()));
        }
        if (rows.isEmpty()) return;
        rows.sort(Comparator.comparing(row -> row.title().toLowerCase()));

        new UpdatesDialog(stage, rows, this::applyUpdates).show();
    }

    private void applyUpdates(List<UpdateInfo> chosen) {
        Map<Mod, Mod> replacements = new java.util.LinkedHashMap<>();
        for (UpdateInfo update : chosen) replacements.put(update.current(), update.target());
        JsonUtils.replaceMods(replacements);
        refreshUpdateUi(1, 1);
    }

    /**
     * URL mods have no API to ask: what we know comes from the jar itself. Cached metadata is used right away;
     * otherwise (mods.json written before this cache existed) the jar is fetched once in the background.
     */
    private void resolveUrl(UrlMod url, long gen) {
        JarMetadata cached = UrlMetaCache.get(url.sha1());
        putEntry(urlEntry(url, cached));
        if (cached != null || !urlFetchAttempted.add(url.sha1())) return;

        bgExecutor.submit(() -> {
            java.io.File tmp = null;
            try {
                tmp = java.io.File.createTempFile("fjc-meta-", ".jar");
                FileUtils.downloadFile(url.downloadURL(), tmp);
                JarMetadata meta = JarMetadata.read(tmp);
                UrlMetaCache.put(url.sha1(), meta);
                // The mod may have been removed, or the list reloaded, while the jar was downloading
                if (gen != generation.get() || !allEntries.containsKey(url)) return;
                putEntry(urlEntry(url, meta));
            } catch (Exception ex) {
                debug("Métadonnées introuvables pour " + url.downloadURL(), ex);
            } finally {
                if (tmp != null) tmp.delete();
            }
        });
    }

    private static ModEntry urlEntry(UrlMod url, @Nullable JarMetadata meta) {
        String size = formatSize(url.size());
        if (meta == null) return new ModEntry(url, ModCategory.URL, url.name(), size, ModEntry.Status.RESOLVED);

        String title = meta.name() != null ? meta.name() : url.name();
        String subtitle = meta.version() != null ? (size != null ? meta.version() + " • " + size : meta.version()) : size;
        if (meta.description() == null && meta.author() == null && meta.iconDataUri() == null && meta.loader() == null) {
            return new ModEntry(url, ModCategory.URL, title, subtitle, ModEntry.Status.RESOLVED);
        }
        List<String> tags = new ArrayList<>(meta.tags());
        tags.add("Fichier jar");
        SearchResult info = new SearchResult(ModCategory.URL, url.sha1(), url.name(), title,
                meta.description() == null ? "" : meta.description(), meta.author() == null ? "" : meta.author(),
                meta.iconDataUri(), 0, 0, tags, null, null, null);
        return new ModEntry(url, ModCategory.URL, title, subtitle, ModEntry.Status.RESOLVED, info);
    }

    private void resolveCurseForge(CurseForgeMod cf, long gen, boolean forceRefresh) {
        String subtitle = "Fichier #" + cf.fileID();
        String cached = forceRefresh ? null : ResolveCache.getCurseForgeName(cf.projectID());
        if (cached != null) {
            putEntry(new ModEntry(cf, ModCategory.CURSEFORGE, cached, subtitle, ModEntry.Status.RESOLVED));
        } else {
            putEntry(ModEntry.loading(cf, ModCategory.CURSEFORGE, subtitle));
        }

        bgExecutor.submit(() -> {
            try {
                io.github.matyrobbrt.curseforgeapi.schemas.mod.Mod resolved = CFUtils.getModFromId(cf.projectID());
                if (gen != generation.get()) return;
                if (resolved == null) {
                    debug("CF introuvable pour projectID=" + cf.projectID() + ", fileID=" + cf.fileID());
                    putEntry(new ModEntry(cf, ModCategory.CURSEFORGE, "Mod CurseForge #" + cf.projectID(), subtitle + " • introuvable", ModEntry.Status.ERROR));
                    return;
                }
                ResolveCache.putCurseForgeName(cf.projectID(), resolved.name());

                // The pinned file's own name says more than its id; keep the id if that lookup fails.
                File file = CFUtils.getFileFromId(cf.projectID(), cf.fileID());
                String version = file != null ? file.displayName() : subtitle;
                if (gen != generation.get()) return;
                putEntry(new ModEntry(cf, ModCategory.CURSEFORGE, resolved.name(), version, ModEntry.Status.RESOLVED,
                        CurseForgeSource.toResult(resolved)));
            } catch (Exception ex) {
                if (gen != generation.get()) return;
                debug("Erreur réseau CF pour projectID=" + cf.projectID() + ", fileID=" + cf.fileID(), ex);
                putEntry(new ModEntry(cf, ModCategory.CURSEFORGE, "Mod CurseForge #" + cf.projectID(), subtitle + " • erreur réseau", ModEntry.Status.ERROR));
            }
        });
    }

    private void resolveModrinth(ModrinthMod mr, long gen, boolean forceRefresh) {
        String subtitle = "Version " + mr.getVersionNumber();
        String cached = forceRefresh ? null : ResolveCache.getModrinthName(mr.getProjectReference());
        if (cached != null) {
            putEntry(new ModEntry(mr, ModCategory.MODRINTH, cached, subtitle, ModEntry.Status.RESOLVED));
        } else {
            putEntry(ModEntry.loading(mr, ModCategory.MODRINTH, subtitle));
        }

        bgExecutor.submit(() -> {
            try {
                Project resolved = ModrinthUtils.getModFromSlug(mr.getProjectReference());
                if (gen != generation.get()) return;
                if (resolved == null) {
                    debug("Modrinth introuvable pour slug=" + mr.getProjectReference() + ", version=" + mr.getVersionNumber());
                    putEntry(new ModEntry(mr, ModCategory.MODRINTH, "Mod Modrinth " + mr.getProjectReference(), subtitle + " • introuvable", ModEntry.Status.ERROR));
                    return;
                }
                ResolveCache.putModrinthName(mr.getProjectReference(), resolved.title());
                putEntry(new ModEntry(mr, ModCategory.MODRINTH, resolved.title(), subtitle, ModEntry.Status.RESOLVED,
                        ModrinthSource.fromProject(resolved)));
            } catch (Exception ex) {
                if (gen != generation.get()) return;
                debug("Erreur réseau Modrinth pour slug=" + mr.getProjectReference() + ", version=" + mr.getVersionNumber(), ex);
                putEntry(new ModEntry(mr, ModCategory.MODRINTH, "Mod Modrinth " + mr.getProjectReference(), subtitle + " • erreur réseau", ModEntry.Status.ERROR));
            }
        });
    }

    /** Incrementally add a single freshly-added mod (from the search tab or a manifest import). */
    public void addMod(Mod mod) {
        ensureExecutorsStarted();
        long gen = generation.get();
        if (mod instanceof UrlMod url) {
            resolveUrl(url, gen);
        } else if (mod instanceof CurseForgeMod cf) {
            resolveCurseForge(cf, gen, false);
        } else if (mod instanceof ModrinthMod mr) {
            resolveModrinth(mr, gen, false);
        }
    }

    /** Incrementally remove a single mod. */
    public void removeMod(Mod mod) {
        if (mod == null) return;
        allEntries.remove(mod);
        dirty.set(true);
        if (updates.remove(mod) != null) Platform.runLater(() -> refreshUpdateUi(1, 1));
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) return null;
        if (bytes < 1024) return bytes + " o";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f Ko", kb);
        double mb = kb / 1024.0;
        return String.format("%.1f Mo", mb);
    }

    public void shutdown() {
        if (uiScheduler != null) uiScheduler.shutdownNow();
        if (bgExecutor != null) bgExecutor.shutdownNow();
        if (updateExecutor != null) updateExecutor.shutdownNow();
    }
}
