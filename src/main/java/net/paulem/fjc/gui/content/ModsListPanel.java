package net.paulem.fjc.gui.content;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import net.paulem.fjc.flow.mod.CurseForgeMod;
import net.paulem.fjc.flow.mod.Mod;
import net.paulem.fjc.flow.mod.ModrinthMod;
import net.paulem.fjc.flow.mod.UrlMod;
import net.paulem.fjc.flow.ModsJson;
import net.paulem.fjc.gui.components.PropertiesViewerPopup;
import net.paulem.fjc.gui.model.ModCategory;
import net.paulem.fjc.gui.model.ModEntry;
import net.paulem.fjc.utils.CFUtils;
import net.paulem.fjc.utils.JsonUtils;
import net.paulem.fjc.utils.ModrinthUtils;
import net.paulem.fjc.utils.ResolveCache;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;
import ovh.paulem.modrinthapi.types.project.Project;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The visual heart of the app: a searchable, sortable, foldable-by-category list of every mod
 * currently in mods.json, with live icons and background name resolution against the
 * CurseForge/Modrinth APIs.
 * <p>
 * Every mod is tracked by a {@link Mod} identity (never by a parsed display string), which is
 * what makes add/remove/search safe even for mods whose resolved name contains punctuation.
 */
public class ModsListPanel extends VBox {
    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("fjc.debug", "false"));

    private final Stage stage;

    private final Map<Mod, ModEntry> allEntries = new ConcurrentHashMap<>();
    private final Map<ModCategory, CategorySection> sections = new EnumMap<>(ModCategory.class);

    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicLong generation = new AtomicLong(0);

    private ExecutorService bgExecutor;
    private ScheduledExecutorService uiScheduler;
    private volatile boolean executorsStarted = false;

    private final TextField searchField = new TextField();
    private final Label totalCountLabel = new Label();
    private final Button sortButton = new Button();
    private boolean sortAscending = true;

    public ModsListPanel(Stage stage) {
        this.stage = stage;
        setSpacing(8);
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
        // ---- Toolbar: search + sort + refresh + total count ----
        HBox toolbar = new HBox(8);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        FontIcon searchIcon = FontIcon.of(Material2MZ.SEARCH, 16, Color.GRAY);
        searchField.setPromptText("Rechercher un mod...");
        searchField.setPrefColumnCount(14);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        HBox searchBox = new HBox(6, searchIcon, searchField);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchBox, Priority.ALWAYS);

        Button clearSearchBtn = new Button();
        clearSearchBtn.setGraphic(FontIcon.of(Material2AL.CLEAR, 14));
        clearSearchBtn.getStyleClass().add("button-icon");
        clearSearchBtn.setTooltip(new Tooltip("Effacer la recherche"));
        clearSearchBtn.setOnAction(e -> searchField.clear());

        sortButton.setGraphic(FontIcon.of(Material2MZ.SORT_BY_ALPHA, 16));
        sortButton.setTooltip(new Tooltip("Trier de A à Z / Z à A"));
        sortButton.getStyleClass().add("button-icon");
        sortButton.setOnAction(e -> {
            sortAscending = !sortAscending;
            applySort();
        });

        Button refreshBtn = new Button();
        refreshBtn.setGraphic(FontIcon.of(Material2MZ.REFRESH, 16));
        refreshBtn.setTooltip(new Tooltip("Rafraîchir les noms depuis Modrinth/CurseForge"));
        refreshBtn.getStyleClass().add("button-icon");
        refreshBtn.setOnAction(e -> refreshAll());

        toolbar.getChildren().addAll(searchBox, clearSearchBtn, sortButton, refreshBtn);

        totalCountLabel.getStyleClass().add("text-muted");

        // ---- One foldable section per category ----
        VBox sectionsBox = new VBox(6);
        for (ModCategory category : ModCategory.values()) {
            CategorySection section = new CategorySection(category);
            sections.put(category, section);
            sectionsBox.getChildren().add(section.pane);
        }

        ScrollPane scrollPane = new ScrollPane(sectionsBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(320);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().addAll(toolbar, scrollPane, totalCountLabel);

        searchField.textProperty().addListener((obs, oldV, newV) -> onSearchChanged(newV));
        applySort();
    }

    private class CategorySection {
        final ModCategory category;
        final ObservableList<ModEntry> master = FXCollections.observableArrayList();
        final FilteredList<ModEntry> filtered = new FilteredList<>(master, e -> true);
        final SortedList<ModEntry> sorted = new SortedList<>(filtered);
        final ListView<ModEntry> listView = new ListView<>(sorted);
        final TitledPane pane = new TitledPane();
        final Label countLabel = new Label("0");
        final Label emptyLabel;
        boolean lastUserExpanded = true;
        boolean programmaticExpand = false;

        CategorySection(ModCategory category) {
            this.category = category;

            FontIcon icon = FontIcon.of(category.getIcon(), 16, category.getColor());
            Label titleLabel = new Label(category.getLabel());
            titleLabel.setStyle("-fx-font-weight: bold;");
            countLabel.getStyleClass().add("text-muted");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox header = new HBox(8, icon, titleLabel, countLabel);
            header.setAlignment(Pos.CENTER_LEFT);

            pane.setGraphic(header);
            pane.setText(null);
            pane.setExpanded(true);
            pane.setAnimated(false);

            emptyLabel = new Label("Aucun mod " + category.getLabel() + " pour le moment.");
            emptyLabel.getStyleClass().add("text-muted");
            emptyLabel.setPadding(new Insets(6, 0, 6, 4));

            listView.setPrefHeight(140);
            listView.setPlaceholder(emptyLabel);
            listView.setCellFactory(lv -> new ModEntryCell());

            VBox content = new VBox(listView);
            pane.setContent(content);

            countLabel.textProperty().bind(Bindings.size(filtered).asString("(%d)"));

            pane.expandedProperty().addListener((obs, wasExpanded, isExpanded) -> {
                if (!programmaticExpand && searchField.getText().isBlank()) {
                    lastUserExpanded = isExpanded;
                }
            });

            listView.setOnKeyPressed(ke -> {
                if (ke.getCode() == KeyCode.DELETE || ke.getCode() == KeyCode.BACK_SPACE) {
                    ModEntry selected = listView.getSelectionModel().getSelectedItem();
                    if (selected != null) confirmAndRemove(selected);
                }
            });

            listView.setOnMouseClicked(me -> {
                ModEntry selected = listView.getSelectionModel().getSelectedItem();
                if (selected == null) return;
                if (me.getButton() == MouseButton.PRIMARY && me.getClickCount() == 2) {
                    showProperties(selected);
                }
            });
        }

        void setExpandedProgrammatically(boolean expanded) {
            programmaticExpand = true;
            pane.setExpanded(expanded);
            programmaticExpand = false;
        }
    }

    private class ModEntryCell extends ListCell<ModEntry> {
        private final FontIcon statusIcon = new FontIcon();
        private final ProgressIndicator spinner = new ProgressIndicator();
        private final Label titleLabel = new Label();
        private final Label subtitleLabel = new Label();
        private final VBox textBox = new VBox(1, titleLabel, subtitleLabel);
        private final Region spacer = new Region();
        private final Button infoBtn = new Button();
        private final Button deleteBtn = new Button();
        private final HBox graphic = new HBox(8);

        ModEntryCell() {
            spinner.setPrefSize(14, 14);
            spinner.setMaxSize(14, 14);

            titleLabel.getStyleClass().add("mod-title");
            subtitleLabel.getStyleClass().add("text-muted");
            subtitleLabel.setStyle("-fx-font-size: 0.85em;");

            HBox.setHgrow(spacer, Priority.ALWAYS);

            infoBtn.setGraphic(FontIcon.of(Material2AL.INFO, 14));
            infoBtn.getStyleClass().add("button-icon");
            infoBtn.setTooltip(new Tooltip("Voir les détails"));
            infoBtn.setOnAction(e -> {
                ModEntry item = getItem();
                if (item != null) showProperties(item);
            });

            deleteBtn.setGraphic(FontIcon.of(Material2AL.DELETE, 14, Color.web("#e05252")));
            deleteBtn.getStyleClass().add("button-icon");
            deleteBtn.setTooltip(new Tooltip("Supprimer"));
            deleteBtn.setOnAction(e -> {
                ModEntry item = getItem();
                if (item != null) confirmAndRemove(item);
            });

            graphic.setAlignment(Pos.CENTER_LEFT);
            graphic.getChildren().addAll(textBox, spacer, infoBtn, deleteBtn);

            ContextMenu menu = new ContextMenu();
            MenuItem viewItem = new MenuItem("Voir les détails", FontIcon.of(Material2AL.INFO, 14));
            viewItem.setOnAction(e -> {
                ModEntry item = getItem();
                if (item != null) showProperties(item);
            });
            MenuItem deleteItem = new MenuItem("Supprimer", FontIcon.of(Material2AL.DELETE, 14));
            deleteItem.setOnAction(e -> {
                ModEntry item = getItem();
                if (item != null) confirmAndRemove(item);
            });
            menu.getItems().addAll(viewItem, deleteItem);
            setContextMenu(menu);
        }

        @Override
        protected void updateItem(ModEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setGraphic(null);
                setTooltip(null);
                return;
            }

            titleLabel.setText(entry.getTitle());
            subtitleLabel.setText(entry.getSubtitle() == null ? "" : entry.getSubtitle());
            subtitleLabel.setManaged(entry.getSubtitle() != null && !entry.getSubtitle().isBlank());
            subtitleLabel.setVisible(subtitleLabel.isManaged());

            graphic.getChildren().remove(spinner);
            graphic.getChildren().remove(statusIcon);
            switch (entry.getStatus()) {
                case LOADING -> graphic.getChildren().add(0, spinner);
                case ERROR -> {
                    statusIcon.setIconCode(Material2MZ.WARNING);
                    statusIcon.setIconColor(Color.web("#e0a028"));
                    statusIcon.setIconSize(16);
                    graphic.getChildren().add(0, statusIcon);
                }
                case RESOLVED -> {
                }
            }

            setTooltip(new Tooltip(entry.getTitle() + (entry.getSubtitle() != null ? "\n" + entry.getSubtitle() : "")));
            setGraphic(graphic);
        }
    }

    // ------------------------------------------------------------------
    // Search / sort / fold behaviour
    // ------------------------------------------------------------------

    private void onSearchChanged(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        boolean searching = !q.isEmpty();

        for (CategorySection section : sections.values()) {
            section.filtered.setPredicate(entry -> matches(entry, q));

            if (searching) {
                section.setExpandedProgrammatically(!section.filtered.isEmpty());
            } else {
                section.setExpandedProgrammatically(section.lastUserExpanded);
            }
        }
    }

    private boolean matches(ModEntry entry, String query) {
        if (query.isEmpty()) return true;
        if (entry.getTitle().toLowerCase().contains(query)) return true;
        return entry.getSubtitle() != null && entry.getSubtitle().toLowerCase().contains(query);
    }

    private void applySort() {
        sortButton.setTooltip(new Tooltip(sortAscending ? "Trié de A à Z (cliquer pour Z à A)" : "Trié de Z à A (cliquer pour A à Z)"));
        Comparator<ModEntry> cmp = Comparator.comparing(e -> e.getTitle().toLowerCase());
        if (!sortAscending) cmp = cmp.reversed();
        for (CategorySection section : sections.values()) {
            section.sorted.setComparator(cmp);
        }
    }

    // ------------------------------------------------------------------
    // Deletion / details
    // ------------------------------------------------------------------

    private void confirmAndRemove(ModEntry entry) {
        Alert confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setTitle("Supprimer un mod");
        confirm.setHeaderText("Supprimer \"" + entry.getTitle() + "\" ?");
        confirm.setContentText("Cette action retire le mod du mods.json. Elle est irréversible.");
        confirm.initOwner(stage);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                JsonUtils.removeMod(entry.getSource());
            }
        });
    }

    private void showProperties(ModEntry entry) {
        new PropertiesViewerPopup(stage).showPopup(entry.getSource());
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
        Map<ModCategory, List<ModEntry>> byCategory = new EnumMap<>(ModCategory.class);
        for (ModCategory category : ModCategory.values()) byCategory.put(category, new java.util.ArrayList<>());
        for (ModEntry entry : allEntries.values()) byCategory.get(entry.getCategory()).add(entry);

        Platform.runLater(() -> {
            for (ModCategory category : ModCategory.values()) {
                sections.get(category).master.setAll(byCategory.get(category));
            }
            totalCountLabel.setText(allEntries.size() + " mod(s) au total");
        });
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
            putEntry(new ModEntry(url, ModCategory.URL, url.name(), formatSize(url.size()), ModEntry.Status.RESOLVED));
        }

        for (CurseForgeMod cf : content.curseFiles) {
            resolveCurseForge(cf, gen, forceRefresh);
        }

        for (ModrinthMod mr : content.modrinthMods) {
            resolveModrinth(mr, gen, forceRefresh);
        }
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
                putEntry(new ModEntry(cf, ModCategory.CURSEFORGE, resolved.name(), subtitle, ModEntry.Status.RESOLVED));
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
                putEntry(new ModEntry(mr, ModCategory.MODRINTH, resolved.title(), subtitle, ModEntry.Status.RESOLVED));
            } catch (Exception ex) {
                if (gen != generation.get()) return;
                debug("Erreur réseau Modrinth pour slug=" + mr.getProjectReference() + ", version=" + mr.getVersionNumber(), ex);
                putEntry(new ModEntry(mr, ModCategory.MODRINTH, "Mod Modrinth " + mr.getProjectReference(), subtitle + " • erreur réseau", ModEntry.Status.ERROR));
            }
        });
    }

    /** Incrementally add a single freshly-added mod (from a search container or a manifest import). */
    public void addMod(Mod mod) {
        ensureExecutorsStarted();
        long gen = generation.get();
        if (mod instanceof UrlMod url) {
            putEntry(new ModEntry(url, ModCategory.URL, url.name(), formatSize(url.size()), ModEntry.Status.RESOLVED));
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
    }
}
