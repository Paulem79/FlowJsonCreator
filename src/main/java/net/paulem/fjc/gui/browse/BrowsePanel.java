package net.paulem.fjc.gui.browse;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import net.paulem.fjc.Main;
import net.paulem.fjc.flow.mod.Mod;
import net.paulem.fjc.gui.components.PropertiesViewerPopup;
import net.paulem.fjc.gui.content.containers.SearchContainer;
import net.paulem.fjc.gui.content.containers.UrlContainer;
import net.paulem.fjc.gui.model.ModCategory;
import net.paulem.fjc.utils.JsonUtils;
import net.paulem.fjc.utils.ModrinthUtils;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.prefs.Preferences;

/**
 * Full-width mod browser: one search over Modrinth and CurseForge together, filters fed by the real option lists,
 * sortable results shown as cards, and a one-click "Install" that writes the newest matching version to mods.json.
 */
public class BrowsePanel extends VBox {
    private static final int PAGE_SIZE = 25;
    private static final String ALL_VERSIONS = "Toutes les versions";
    private static final String ALL_LOADERS = "Tous les loaders";

    private static final Preferences PREFS = Preferences.userNodeForPackage(Main.class);
    private static final String PREF_VERSION = "browseGameVersion";
    private static final String PREF_LOADER = "browseLoader";
    private static final String PREF_SORT = "browseSort";
    private static final String PREF_MODRINTH = "browseModrinth";
    private static final String PREF_CURSEFORGE = "browseCurseForge";

    private record CategoryOption(@Nullable String slug, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final Stage stage;
    private final Map<ModCategory, ModSource> sources = new EnumMap<>(ModCategory.class);

    private final TextField searchField = new TextField();
    private final ComboBox<String> versionBox = new ComboBox<>();
    private final CheckBox snapshotsBox = new CheckBox("Snapshots");
    private final ComboBox<String> loaderBox = new ComboBox<>();
    private final ComboBox<CategoryOption> categoryBox = new ComboBox<>();
    private final ComboBox<SortField> sortBox = new ComboBox<>();
    private final ToggleButton modrinthToggle = new ToggleButton("Modrinth");
    private final ToggleButton curseForgeToggle = new ToggleButton("CurseForge");
    private final ProgressIndicator busy = new ProgressIndicator();
    private final Label statusLabel = new Label();
    private final Label placeholder = new Label("Recherche en cours...");

    private final ObservableList<SearchResult> items = FXCollections.observableArrayList();
    private final ListView<SearchResult> listView = new ListView<>(items);

    private final PauseTransition debounce = new PauseTransition(Duration.millis(350));
    private final AtomicLong generation = new AtomicLong();
    private final AtomicInteger pending = new AtomicInteger();
    /** Search pages currently being fetched; infinite scroll never starts a page while one is in flight. */
    private final AtomicInteger pageLoads = new AtomicInteger();

    // Only touched on the FX thread
    private final Map<ModCategory, List<SearchResult>> perSource = new EnumMap<>(ModCategory.class);
    private final Map<ModCategory, Boolean> hasMore = new EnumMap<>(ModCategory.class);
    private final Map<ModCategory, String> errors = new EnumMap<>(ModCategory.class);
    private final Set<String> expanded = new HashSet<>();
    private final Map<String, List<VersionOption>> versionsCache = new ConcurrentHashMap<>();

    private FilterOptions.Data options;
    private boolean ready = false;

    public BrowsePanel(Stage stage) {
        this.stage = stage;
        sources.put(ModCategory.MODRINTH, new ModrinthSource());
        sources.put(ModCategory.CURSEFORGE, new CurseForgeSource());

        setSpacing(10);
        setPadding(new Insets(12, 0, 0, 0));

        buildUi();

        // Refresh the "Installé" badges whenever mods.json changes (also from the modpack tab).
        JsonUtils.addChangeListener(listView::refresh);

        SearchContainer.EXECUTOR.submit(() -> {
            FilterOptions.Data data = FilterOptions.load();
            Platform.runLater(() -> applyOptions(data));
        });
    }

    // ------------------------------------------------------------------
    // UI
    // ------------------------------------------------------------------

    private void buildUi() {
        searchField.setPromptText("Rechercher un mod sur Modrinth et CurseForge...");
        searchField.setPrefColumnCount(24);
        HBox searchBox = new HBox(6, FontIcon.of(Material2MZ.SEARCH, 16), searchField);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        sortBox.getItems().addAll(SortField.values());
        sortBox.setValue(SortField.valueOf(PREFS.get(PREF_SORT, SortField.RELEVANCE.name())));
        sortBox.setTooltip(new Tooltip("Trier les résultats"));

        modrinthToggle.setGraphic(FontIcon.of(ModCategory.MODRINTH.getIcon(), 14, ModCategory.MODRINTH.getColor()));
        modrinthToggle.setSelected(PREFS.getBoolean(PREF_MODRINTH, true));
        curseForgeToggle.setGraphic(FontIcon.of(ModCategory.CURSEFORGE.getIcon(), 14, ModCategory.CURSEFORGE.getColor()));
        curseForgeToggle.setSelected(PREFS.getBoolean(PREF_CURSEFORGE, true));
        if (Main.cfApi == null) {
            curseForgeToggle.setSelected(false);
            curseForgeToggle.setDisable(true);
            curseForgeToggle.setTooltip(new Tooltip("Clé API CurseForge indisponible"));
        }

        Button urlButton = new Button("Ajouter par URL", FontIcon.of(ModCategory.URL.getIcon(), 14, ModCategory.URL.getColor()));
        urlButton.setOnAction(e -> openUrlDialog());

        busy.setPrefSize(18, 18);
        busy.setMaxSize(18, 18);
        busy.setVisible(false);

        versionBox.setPromptText("Version de Minecraft");
        versionBox.getItems().add(ALL_VERSIONS);
        versionBox.setValue(ALL_VERSIONS);
        versionBox.setVisibleRowCount(14);
        loaderBox.getItems().add(ALL_LOADERS);
        loaderBox.setValue(ALL_LOADERS);
        categoryBox.getItems().add(new CategoryOption(null, "Toutes les catégories"));
        categoryBox.setValue(categoryBox.getItems().get(0));
        categoryBox.setVisibleRowCount(14);
        categoryBox.setTooltip(new Tooltip("Les catégories sont propres à Modrinth : CurseForge est ignoré quand une est choisie"));

        Button resetButton = new Button("Réinitialiser", FontIcon.of(Material2MZ.REFRESH, 14));
        resetButton.setOnAction(e -> {
            searchField.clear();
            versionBox.setValue(ALL_VERSIONS);
            loaderBox.setValue(ALL_LOADERS);
            categoryBox.setValue(categoryBox.getItems().get(0));
            sortBox.setValue(SortField.RELEVANCE);
        });

        FlowPane filters = new FlowPane(10, 8, searchBox, sortBox, modrinthToggle, curseForgeToggle, urlButton, busy);
        filters.setAlignment(Pos.CENTER_LEFT);
        FlowPane filters2 = new FlowPane(10, 8, versionBox, snapshotsBox, loaderBox, categoryBox, resetButton);
        filters2.setAlignment(Pos.CENTER_LEFT);

        listView.getStyleClass().add("browse-list");
        listView.setCellFactory(lv -> new ResultCell(host));
        listView.setPlaceholder(placeholder);
        listView.skinProperty().addListener((obs, oldSkin, newSkin) -> hookInfiniteScroll());
        VBox.setVgrow(listView, Priority.ALWAYS);

        statusLabel.getStyleClass().add("text-muted");
        HBox footer = new HBox(12, statusLabel);
        footer.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(filters, filters2, listView, footer);

        // -------- events --------
        debounce.setOnFinished(e -> onFilterChanged());
        searchField.textProperty().addListener((obs, o, n) -> debounce.playFromStart());
        searchField.setOnAction(e -> {
            debounce.stop();
            onFilterChanged();
        });
        for (var box : List.of(versionBox, loaderBox, categoryBox, sortBox)) {
            box.setOnAction(e -> onFilterChanged());
        }
        modrinthToggle.setOnAction(e -> onFilterChanged());
        curseForgeToggle.setOnAction(e -> onFilterChanged());
        snapshotsBox.setOnAction(e -> {
            String current = versionBox.getValue();
            fillVersions();
            if (versionBox.getItems().contains(current)) versionBox.setValue(current);
            else versionBox.setValue(ALL_VERSIONS);
        });
    }

    private void applyOptions(FilterOptions.Data data) {
        this.options = data;
        fillVersions();
        loaderBox.getItems().addAll(data.loaders());
        for (String[] category : data.categories()) categoryBox.getItems().add(new CategoryOption(category[0], category[1]));

        String savedVersion = PREFS.get(PREF_VERSION, ALL_VERSIONS);
        if (!versionBox.getItems().contains(savedVersion) && data.snapshots().contains(savedVersion)) {
            snapshotsBox.setSelected(true);
            fillVersions();
        }
        if (versionBox.getItems().contains(savedVersion)) versionBox.setValue(savedVersion);
        String savedLoader = PREFS.get(PREF_LOADER, ALL_LOADERS);
        if (loaderBox.getItems().contains(savedLoader)) loaderBox.setValue(savedLoader);

        ready = true;
        search();
    }

    private void fillVersions() {
        boolean wasReady = ready;
        ready = false; // clearing/refilling must not trigger searches
        versionBox.getItems().setAll(ALL_VERSIONS);
        versionBox.getItems().addAll(options.releases());
        if (snapshotsBox.isSelected()) versionBox.getItems().addAll(options.snapshots());
        versionBox.setValue(ALL_VERSIONS);
        ready = wasReady;
    }

    private void openUrlDialog() {
        Stage dialog = new Stage();
        dialog.initOwner(stage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Ajouter un mod par URL");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        new UrlContainer(dialog, grid);

        dialog.setScene(new Scene(grid, 560, 140));
        dialog.show();
    }

    // ------------------------------------------------------------------
    // Filters -> query
    // ------------------------------------------------------------------

    private @Nullable String selectedGameVersion() {
        String v = versionBox.getValue();
        return v == null || v.equals(ALL_VERSIONS) ? null : v;
    }

    private @Nullable String selectedLoader() {
        String v = loaderBox.getValue();
        return v == null || v.equals(ALL_LOADERS) ? null : v;
    }

    private @Nullable String selectedCategory() {
        CategoryOption option = categoryBox.getValue();
        return option == null ? null : option.slug();
    }

    private List<ModSource> activeSources() {
        List<ModSource> active = new ArrayList<>();
        if (modrinthToggle.isSelected()) active.add(sources.get(ModCategory.MODRINTH));
        if (curseForgeToggle.isSelected() && selectedCategory() == null) active.add(sources.get(ModCategory.CURSEFORGE));
        return active;
    }

    private void onFilterChanged() {
        if (!ready) return;
        PREFS.put(PREF_VERSION, versionBox.getValue() == null ? ALL_VERSIONS : versionBox.getValue());
        PREFS.put(PREF_LOADER, loaderBox.getValue() == null ? ALL_LOADERS : loaderBox.getValue());
        PREFS.put(PREF_SORT, sortBox.getValue().name());
        PREFS.putBoolean(PREF_MODRINTH, modrinthToggle.isSelected());
        PREFS.putBoolean(PREF_CURSEFORGE, curseForgeToggle.isSelected());
        search();
    }

    // ------------------------------------------------------------------
    // Search / pagination
    // ------------------------------------------------------------------

    private SearchQuery baseQuery(int offset) {
        return new SearchQuery(searchField.getText() == null ? "" : searchField.getText(), selectedGameVersion(),
                selectedLoader(), selectedCategory(), sortBox.getValue(), offset, PAGE_SIZE);
    }

    private void search() {
        long gen = generation.incrementAndGet();
        perSource.clear();
        hasMore.clear();
        errors.clear();
        versionsCache.clear();
        expanded.clear();
        items.clear();

        List<ModSource> active = activeSources();
        if (active.isEmpty()) {
            placeholder.setText("Active au moins une source (Modrinth / CurseForge).");
            statusLabel.setText("");
            return;
        }
        placeholder.setText("Recherche en cours...");
        for (ModSource source : active) fetch(gen, source, 0);
    }

    private void loadMore() {
        if (pageLoads.get() > 0) return;
        long gen = generation.get();
        for (ModSource source : activeSources()) {
            if (Boolean.TRUE.equals(hasMore.get(source.category()))) {
                fetch(gen, source, perSource.getOrDefault(source.category(), List.of()).size());
            }
        }
    }

    private void fetch(long gen, ModSource source, int offset) {
        SearchQuery query = baseQuery(offset);
        pageLoads.incrementAndGet();
        setBusy(pending.incrementAndGet() > 0);
        SearchContainer.EXECUTOR.submit(() -> {
            SearchPage page = null;
            String error = null;
            try {
                page = source.search(query);
            } catch (Throwable t) {
                error = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            }
            SearchPage finalPage = page;
            String finalError = error;
            Platform.runLater(() -> {
                pageLoads.decrementAndGet();
                setBusy(pending.decrementAndGet() > 0);
                if (gen != generation.get()) return; // a newer search replaced this one
                ModCategory category = source.category();
                if (finalPage != null) {
                    perSource.computeIfAbsent(category, k -> new ArrayList<>()).addAll(finalPage.results());
                    hasMore.put(category, finalPage.hasMore());
                    errors.remove(category);
                } else {
                    hasMore.put(category, false);
                    errors.put(category, finalError);
                }
                rebuild();
            });
        });
    }

    private void setBusy(boolean busyNow) {
        busy.setVisible(busyNow);
    }

    /** Merges what each source returned so far into the single displayed list. */
    private void rebuild() {
        List<List<SearchResult>> lists = new ArrayList<>();
        for (ModSource source : activeSources()) lists.add(perSource.getOrDefault(source.category(), List.of()));

        List<SearchResult> merged = new ArrayList<>();
        SortField sort = sortBox.getValue();
        if (sort == SortField.RELEVANCE) {
            // No comparable relevance score across sources: alternate by rank.
            int max = lists.stream().mapToInt(List::size).max().orElse(0);
            for (int i = 0; i < max; i++) {
                for (List<SearchResult> list : lists) if (i < list.size()) merged.add(list.get(i));
            }
        } else {
            lists.forEach(merged::addAll);
            merged.sort(comparatorFor(sort));
        }
        // Append when the new page only extends the list: resetting the items would throw the scroll position back.
        int common = 0;
        while (common < items.size() && common < merged.size() && items.get(common) == merged.get(common)) common++;
        if (common == items.size()) items.addAll(merged.subList(common, merged.size()));
        else items.setAll(merged);

        if (merged.isEmpty()) {
            placeholder.setText(pending.get() > 0 ? "Recherche en cours..." : "Aucun résultat.");
        }
        StringBuilder status = new StringBuilder(merged.size() + " résultat(s)");
        errors.forEach((category, message) -> status.append("  ·  ").append(category.getLabel()).append(" : erreur (").append(message).append(")"));
        if (curseForgeToggle.isSelected() && selectedCategory() != null) {
            status.append("  ·  CurseForge ignoré (catégorie Modrinth)");
        }
        statusLabel.setText(status.toString());
        // Once laid out, keep loading if the list is still too short to scroll.
        Platform.runLater(() -> {
            listView.applyCss();
            listView.layout();
            maybeLoadMore();
        });
    }

    private boolean anyHasMore() {
        return activeSources().stream().anyMatch(s -> Boolean.TRUE.equals(hasMore.get(s.category())));
    }

    // ------------------------------------------------------------------
    // Infinite scroll
    // ------------------------------------------------------------------

    private @Nullable ScrollBar verticalBar() {
        for (Node node : listView.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar bar && bar.getOrientation() == Orientation.VERTICAL) return bar;
        }
        return null;
    }

    private void hookInfiniteScroll() {
        ScrollBar bar = verticalBar();
        if (bar == null) return;
        bar.valueProperty().addListener((obs, oldV, newV) -> maybeLoadMore());
        bar.visibleProperty().addListener((obs, oldV, newV) -> maybeLoadMore());
    }

    /** Loads the next page when the user reached the bottom, or when the list is too short to have a scrollbar. */
    private void maybeLoadMore() {
        if (!ready || pageLoads.get() > 0 || !anyHasMore()) return;
        ScrollBar bar = verticalBar();
        if (bar == null) return;
        if (!bar.isVisible() || bar.getValue() >= bar.getMax() * 0.995) loadMore();
    }

    private static Comparator<SearchResult> comparatorFor(SortField sort) {
        Comparator<java.time.Instant> newestFirst = Comparator.nullsLast(Comparator.reverseOrder());
        return switch (sort) {
            case DOWNLOADS -> Comparator.comparingLong(SearchResult::downloads).reversed();
            case FOLLOWS -> Comparator.comparingLong(SearchResult::follows).reversed();
            case UPDATED -> Comparator.comparing(SearchResult::dateModified, newestFirst);
            case NEWEST -> Comparator.comparing(SearchResult::dateCreated, newestFirst);
            case NAME -> Comparator.comparing(r -> r.title().toLowerCase());
            default -> (a, b) -> 0;
        };
    }

    // ------------------------------------------------------------------
    // Card actions
    // ------------------------------------------------------------------

    private final ResultCell.Host host = new ResultCell.Host() {
        @Override
        public @Nullable String gameVersion() {
            return selectedGameVersion();
        }

        @Override
        public @Nullable Mod installedMod(SearchResult r) {
            if (Main.jsonContent == null) return null;
            return switch (r.source()) {
                case MODRINTH -> Main.jsonContent.findModrinth(r.id(), r.slug());
                case CURSEFORGE -> Main.jsonContent.findCurseForge(Integer.parseInt(r.id()));
                default -> null;
            };
        }

        @Override
        public boolean isExpanded(SearchResult r) {
            return expanded.contains(r.key());
        }

        @Override
        public @Nullable List<VersionOption> versionsFor(SearchResult r) {
            return versionsCache.get(r.key());
        }

        @Override
        public void toggleExpanded(SearchResult r) {
            String key = r.key();
            if (!expanded.remove(key)) {
                expanded.add(key);
                if (!versionsCache.containsKey(key)) loadVersions(r);
            }
            listView.refresh();
        }

        @Override
        public void installLatest(SearchResult r) {
            BrowsePanel.this.installLatest(r);
        }

        @Override
        public void installVersion(SearchResult r, VersionOption v) {
            BrowsePanel.this.installVersion(r, v);
        }

        @Override
        public void showDetails(SearchResult r) {
            BrowsePanel.this.showDetails(r);
        }
    };

    private void loadVersions(SearchResult r) {
        String gameVersion = selectedGameVersion(), loader = selectedLoader();
        long gen = generation.get();
        SearchContainer.EXECUTOR.submit(() -> {
            List<VersionOption> versions;
            try {
                versions = sources.get(r.source()).listVersions(r, gameVersion, loader);
            } catch (Throwable t) {
                versions = List.of();
                String message = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                Platform.runLater(() -> statusLabel.setText("Impossible de lister les versions de " + r.title() + " : " + message));
            }
            List<VersionOption> result = versions;
            Platform.runLater(() -> {
                if (gen != generation.get()) return;
                versionsCache.put(r.key(), result);
                listView.refresh();
            });
        });
    }

    /** "Installer" / "Changer": newest version matching the current game version + loader filters. */
    private void installLatest(SearchResult r) {
        String gameVersion = selectedGameVersion(), loader = selectedLoader();
        if (gameVersion == null) return; // the button is disabled in that case

        setBusy(pending.incrementAndGet() > 0);
        SearchContainer.EXECUTOR.submit(() -> {
            Optional<VersionOption> found = Optional.empty();
            String error = null;
            try {
                found = sources.get(r.source()).latestMatching(r, gameVersion, loader);
            } catch (Throwable t) {
                error = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            }
            Optional<VersionOption> version = found;
            String finalError = error;
            Platform.runLater(() -> {
                setBusy(pending.decrementAndGet() > 0);
                if (finalError != null) {
                    alert(Alert.AlertType.ERROR, "Impossible de récupérer les versions", finalError);
                } else if (version.isEmpty()) {
                    alert(Alert.AlertType.INFORMATION, "Aucune version compatible",
                            r.title() + " n'a pas de version pour Minecraft " + gameVersion
                                    + (loader != null ? " avec " + loader : "") + " sur " + r.source().getLabel() + ".");
                } else {
                    installVersion(r, version.get());
                }
            });
        });
    }

    /**
     * Installs a version. Its dependencies are resolved first and, when there are any, the user is asked which
     * to add along with it; cancelling that dialog installs nothing.
     */
    private void installVersion(SearchResult r, VersionOption version) {
        Mod existing = host.installedMod(r);
        if (existing != null && existing.equals(version.mod())) {
            statusLabel.setText(r.title() + " est déjà installé dans cette version.");
            return;
        }

        ModSource source = sources.get(r.source());
        String gameVersion = selectedGameVersion(), loader = selectedLoader();
        statusLabel.setText("Recherche des dépendances de " + r.title() + "...");
        setBusy(pending.incrementAndGet() > 0);

        SearchContainer.EXECUTOR.submit(() -> {
            DependencyResolver.Plan plan;
            try {
                plan = DependencyResolver.resolve(source, r, version, gameVersion, loader, p -> host.installedMod(p) != null);
            } catch (Throwable t) {
                String message = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                plan = new DependencyResolver.Plan(List.of(), List.of("Vérification des dépendances impossible : " + message));
            }
            DependencyResolver.Plan finalPlan = plan;
            Platform.runLater(() -> {
                setBusy(pending.decrementAndGet() > 0);
                if (finalPlan.isEmpty()) {
                    commitInstall(r, version);
                    return;
                }
                DependencyDialog.ask(stage, r, version, finalPlan).ifPresentOrElse(chosen -> {
                    commitInstall(r, version);
                    for (DependencyResolver.Item item : chosen) commitInstall(item.project(), item.version());
                    if (!chosen.isEmpty()) {
                        statusLabel.setText(r.title() + " ajouté avec " + chosen.size() + " dépendance(s).");
                    }
                }, () -> statusLabel.setText("Installation de " + r.title() + " annulée."));
            });
        });
    }

    /** Writes the version to mods.json, replacing whatever version of this project was there. */
    private void commitInstall(SearchResult r, VersionOption version) {
        try {
            Mod existing = host.installedMod(r);
            if (existing != null && existing.equals(version.mod())) return;
            if (existing != null) JsonUtils.removeMod(existing);
            JsonUtils.addMod(version.mod());
            statusLabel.setText(r.title() + " : " + version.displayName() + " ajouté au modpack.");
            listView.refresh();
        } catch (RuntimeException e) {
            alert(Alert.AlertType.ERROR, "Installation impossible", e.getMessage());
        }
    }

    private void showDetails(SearchResult r) {
        if (r.raw() != null) {
            new PropertiesViewerPopup(stage).showPopup(r.raw());
            return;
        }
        SearchContainer.EXECUTOR.submit(() -> {
            Object project = ModrinthUtils.getModFromSlug(r.id());
            if (project != null) Platform.runLater(() -> new PropertiesViewerPopup(stage).showPopup(project));
        });
    }

    private void alert(Alert.AlertType type, String header, @Nullable String message) {
        Alert alert = new Alert(type);
        alert.setTitle(type == Alert.AlertType.ERROR ? "Erreur" : "Information");
        alert.setHeaderText(header);
        alert.setContentText(message != null ? message : "Erreur inconnue.");
        alert.initOwner(stage);
        alert.showAndWait();
    }
}
