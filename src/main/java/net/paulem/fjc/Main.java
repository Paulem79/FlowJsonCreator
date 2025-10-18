package net.paulem.fjc;

import atlantafx.base.theme.PrimerDark;
import io.github.matyrobbrt.curseforgeapi.CurseForgeAPI;
import javafx.scene.image.Image;
import net.paulem.fjc.gui.components.PropertiesViewerPopup;
import net.paulem.fjc.gui.content.containers.CurseforgeContainer;
import net.paulem.fjc.flow.mod.Mod;
import net.paulem.fjc.flow.ModsJson;
import net.paulem.fjc.gui.content.SearchType;
import net.paulem.fjc.gui.content.containers.ModrinthContainer;
import net.paulem.fjc.gui.content.containers.UrlContainer;
import net.paulem.fjc.utils.CFUtils;
import net.paulem.fjc.utils.ModrinthUtils;
import ovh.paulem.modrinthapi.Modrinth;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.jetbrains.annotations.Nullable;
import ovh.paulem.modrinthapi.types.project.Project;

import javax.security.auth.login.LoginException;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CopyOnWriteArrayList;

import static net.paulem.fjc.utils.JsonUtils.*;
import static net.paulem.fjc.utils.ManipulationUtils.checkOptArg;

import net.paulem.fjc.flow.mod.UrlMod;
import net.paulem.fjc.flow.mod.CurseForgeMod;
import net.paulem.fjc.flow.mod.ModrinthMod;

public class Main extends Application {
    public static @Nullable String CF_API_KEY;

    public static final Modrinth MODRINTH = new Modrinth(null, "paulem", "FlowJsonCreator", "1.4");

    public static ModsJson jsonContent;

    public static ListView<String> list;
    @Nullable
    public static CurseForgeAPI cfApi = null;

    public GridPane mainGrid;
    public GridPane subGrid;

    public ComboBox<String> searchType;
    public @Nullable String oldSearchValue;

    // --- Async update infra ---
    private static ScheduledExecutorService uiScheduler;
    private static ExecutorService bgExecutor;
    private static final CopyOnWriteArrayList<String> stagedItems = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean uiDirty = new AtomicBoolean(false);
    private static final AtomicLong listGeneration = new AtomicLong(0);
    private static volatile boolean executorsStarted = false;

    // Mini logger debug
    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("fjc.debug", "true"));
    private static void debug(String message) {
        if (DEBUG) System.err.println("[DEBUG] " + message);
    }
    private static void debug(String message, Throwable t) {
        if (DEBUG) {
            System.err.println("[DEBUG] " + message);
            t.printStackTrace(System.err);
        }
    }

    private static synchronized void ensureExecutorsStarted() {
        if (executorsStarted) return;
        bgExecutor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
        uiScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "fjc-ui-throttler");
            t.setDaemon(true);
            return t;
        });
        // Cadence: 3 mises à jour max / seconde => toutes ~333ms
        uiScheduler.scheduleAtFixedRate(() -> {
            if (!uiDirty.getAndSet(false)) return; // Rien à pousser
            // Snapshot thread-safe
            List<String> snapshot = List.copyOf(stagedItems);
            if (list == null) return;
            Platform.runLater(() -> list.setItems(FXCollections.observableArrayList(snapshot)));
        }, 0, 333, TimeUnit.MILLISECONDS);
        executorsStarted = true;
    }

    @Override
    public void start(Stage stage) {
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());

        stage.setTitle("FlowJsonCreator v1.4");
        stage.setFullScreen(false);
        stage.centerOnScreen();
        stage.getIcons().add(new Image("assets/icons.png"));

        mainGrid = new GridPane();
        mainGrid.setAlignment(Pos.CENTER);
        mainGrid.setHgap(10);
        mainGrid.setVgap(10);
        mainGrid.setPadding(new Insets(25));

        subGrid = new GridPane();
        subGrid.setPadding(new Insets(0, 10, 0, 10));
        mainGrid.add(subGrid, 0, 2);

        Text title = new Text("FlowJsonCreator");
        title.setFont(Font.font("Tahoma", FontWeight.NORMAL, 20));
        mainGrid.add(title, 0, 0, 2, 1);

        // -------- SEARCH --------
        HBox searchBox = new HBox(10);
        mainGrid.add(searchBox, 0, 1, 2, 1);

        Label searchLabel = new Label("Rechercher avec :");
        searchLabel.setTranslateY(5);
        searchBox.getChildren().add(searchLabel);

        // Search type chooser
        searchType = new ComboBox<>();

        List<String> searchTypeWords = Arrays.stream(SearchType.values()).map(SearchType::toWord).toList();
        searchType.getItems().addAll(searchTypeWords);
        searchBox.getChildren().add(searchType);
        // -------- END SEARCH --------

        // -------- Mods.json viewer --------
        VBox modsJsonBox = new VBox(10);
        mainGrid.add(modsJsonBox, 2, 0, 2, 3);

        HBox modsViewerBox = new HBox(10);
        modsJsonBox.getChildren().add(modsViewerBox);

        Label modsJsonLabel = new Label("Mods.json");
        modsJsonLabel.setTranslateY(5);
        modsViewerBox.getChildren().add(modsJsonLabel);

        Button btn = new Button("Ouvrir");
        modsViewerBox.getChildren().add(btn);
        btn.setOnAction(actionEvent -> {
            try {
                Desktop.getDesktop().open(modsJson.getParentFile());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        Button importBtn = new Button("Importer Manifest");
        modsViewerBox.getChildren().add(importBtn);
        importBtn.setOnAction(actionEvent -> {
            FileDialog fileChooser = new FileDialog((Frame)null);
            fileChooser.setTitle("Sélectionner un manifest.json");
            fileChooser.setFilenameFilter((dir, name) -> name.equals("manifest.json"));
            fileChooser.setVisible(true);
            File selectedFile = new File(fileChooser.getDirectory(), fileChooser.getFile());
            try {
                addCurseForgeManifest(selectedFile);
            } catch (IOException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Erreur");
                alert.setHeaderText("Erreur lors de l'importation du manifest");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            }
        });

        list = new ListView<>();
        modsJsonBox.getChildren().add(list);

        // Démarre les exécutors avant la première mise à jour
        ensureExecutorsStarted();
        updateList();
        // -------- END Mods.json viewer --------

        Scene scene = new Scene(mainGrid, 750, 480);
        stage.setScene(scene);

        stage.show();

        // -------- EVENTS --------
        searchType.setOnAction(event -> {
            if(searchType.getValue().equals(oldSearchValue)) return;

            switch (SearchType.fromString(searchType.getValue())) {
                case URL -> new UrlContainer(stage, subGrid);
                case MODRINTH -> new ModrinthContainer(stage, subGrid);
                case CURSEFORGE -> new CurseforgeContainer(stage, subGrid);
            }
        });

        list.setOnMouseClicked(mouseEvent -> {
            @Nullable String selectedItem = list.getSelectionModel().getSelectedItem();
            if(selectedItem == null) return;

            // Double click to remove item
            if(mouseEvent.getButton().equals(MouseButton.PRIMARY)
                    && mouseEvent.getClickCount() == 2){

                list.getItems().remove(selectedItem);
                removeMod(selectedItem);
            } else if(mouseEvent.getButton().equals(MouseButton.SECONDARY)) {

                Mod mod = getModFromString(selectedItem);
                if(mod == null) return;
                new PropertiesViewerPopup(stage)
                        .showPopup(mod);
            }
        });
        // -------- END EVENTS --------
    }

    /**
     * Update the list view with the current mods.
     */
    public static void updateList() {
        ensureExecutorsStarted();

        // Incrémente la génération pour invalider les anciennes tâches
        final long generation = listGeneration.incrementAndGet();

        // Réinitialise le buffer et ajoute les éléments non-bloquants
        stagedItems.clear();

        jsonContent.mods.forEach(mod -> stagedItems.add(mod.name()));
        uiDirty.set(true); // première mise à jour rapide avec les noms simples

        // CurseForge: placeholders + résolution en arrière-plan
        jsonContent.curseFiles.forEach(cf -> {
            final String placeholder = "CF " + cf.projectID() + " - " + cf.fileID() + " (chargement...)";
            stagedItems.add(placeholder);
            uiDirty.set(true);

            bgExecutor.submit(() -> {
                try {
                    io.github.matyrobbrt.curseforgeapi.schemas.mod.Mod modFromId = CFUtils.getModFromId(cf.projectID());
                    if (generation != listGeneration.get()) return; // tâche obsolète
                    if (modFromId == null) {
                        debug("CF introuvable pour projectID=" + cf.projectID() + ", fileID=" + cf.fileID());
                        int idx = stagedItems.indexOf(placeholder);
                        String failed = "CF " + cf.projectID() + " - " + cf.fileID() + " (introuvable)";
                        if (idx >= 0) stagedItems.set(idx, failed); else stagedItems.add(failed);
                        uiDirty.set(true);
                        return;
                    }
                    final String resolved = "CF " + modFromId.name() + " - " + cf.projectID() + " - " + cf.fileID();
                    int idx = stagedItems.indexOf(placeholder);
                    if (idx >= 0) stagedItems.set(idx, resolved); else stagedItems.add(resolved);
                    uiDirty.set(true);
                } catch (Exception ex) {
                    if (generation != listGeneration.get()) return; // tâche obsolète
                    debug("Erreur réseau CF pour projectID=" + cf.projectID() + ", fileID=" + cf.fileID(), ex);
                    int idx = stagedItems.indexOf(placeholder);
                    String failed = "CF " + cf.projectID() + " - " + cf.fileID() + " (introuvable)";
                    if (idx >= 0) stagedItems.set(idx, failed); else stagedItems.add(failed);
                    uiDirty.set(true);
                }
            });
        });

        // Modrinth: placeholders + résolution en arrière-plan
        jsonContent.modrinthMods.forEach(mr -> {
            final String placeholder = "MOD " + mr.getProjectReference() + " - " + mr.getVersionNumber() + " (chargement...)";
            stagedItems.add(placeholder);
            uiDirty.set(true);

            bgExecutor.submit(() -> {
                try {
                    Project modFromSlug = ModrinthUtils.getModFromSlug(mr.getProjectReference());
                    if (generation != listGeneration.get()) return; // tâche obsolète
                    if (modFromSlug == null) {
                        debug("Modrinth introuvable pour slug=" + mr.getProjectReference() + ", version=" + mr.getVersionNumber());
                        int idx = stagedItems.indexOf(placeholder);
                        String failed = "MOD " + mr.getProjectReference() + " - " + mr.getVersionNumber() + " (introuvable)";
                        if (idx >= 0) stagedItems.set(idx, failed); else stagedItems.add(failed);
                        uiDirty.set(true);
                        return;
                    }
                    final String resolved = "MOD " + modFromSlug.title() + " - " + mr.getProjectReference() + " - " + mr.getVersionNumber();
                    int idx = stagedItems.indexOf(placeholder);
                    if (idx >= 0) stagedItems.set(idx, resolved); else stagedItems.add(resolved);
                    uiDirty.set(true);
                } catch (Exception ex) {
                    if (generation != listGeneration.get()) return; // tâche obsolète
                    debug("Erreur réseau Modrinth pour slug=" + mr.getProjectReference() + ", version=" + mr.getVersionNumber(), ex);
                    int idx = stagedItems.indexOf(placeholder);
                    String failed = "MOD " + mr.getProjectReference() + " - " + mr.getVersionNumber() + " (introuvable)";
                    if (idx >= 0) stagedItems.set(idx, failed); else stagedItems.add(failed);
                    uiDirty.set(true);
                }
            });
        });
    }

    /** Ajoute un seul élément à la liste (mise à jour incrémentale). */
    public static void addListItem(Mod mod) {
        ensureExecutorsStarted();
        if (mod instanceof UrlMod url) {
            stagedItems.add(url.name());
            uiDirty.set(true);
            return;
        }
        if (mod instanceof CurseForgeMod cf) {
            final String placeholder = "CF " + cf.projectID() + " - " + cf.fileID() + " (chargement...)";
            stagedItems.add(placeholder);
            uiDirty.set(true);
            bgExecutor.submit(() -> {
                try {
                    io.github.matyrobbrt.curseforgeapi.schemas.mod.Mod modFromId = CFUtils.getModFromId(cf.projectID());
                    if (modFromId == null) {
                        int idx = stagedItems.indexOf(placeholder);
                        String failed = "CF " + cf.projectID() + " - " + cf.fileID() + " (introuvable)";
                        if (idx >= 0) stagedItems.set(idx, failed); else stagedItems.add(failed);
                        uiDirty.set(true);
                        return;
                    }
                    final String resolved = "CF " + modFromId.name() + " - " + cf.projectID() + " - " + cf.fileID();
                    int idx = stagedItems.indexOf(placeholder);
                    if (idx >= 0) stagedItems.set(idx, resolved); else stagedItems.add(resolved);
                    uiDirty.set(true);
                } catch (Exception ex) {
                    int idx = stagedItems.indexOf(placeholder);
                    String failed = "CF " + cf.projectID() + " - " + cf.fileID() + " (introuvable)";
                    if (idx >= 0) stagedItems.set(idx, failed); else stagedItems.add(failed);
                    uiDirty.set(true);
                }
            });
            return;
        }
        if (mod instanceof ModrinthMod mr) {
            final String placeholder = "MOD " + mr.getProjectReference() + " - " + mr.getVersionNumber() + " (chargement...)";
            stagedItems.add(placeholder);
            uiDirty.set(true);
            bgExecutor.submit(() -> {
                try {
                    Project modFromSlug = ModrinthUtils.getModFromSlug(mr.getProjectReference());
                    if (modFromSlug == null) {
                        int idx = stagedItems.indexOf(placeholder);
                        String failed = "MOD " + mr.getProjectReference() + " - " + mr.getVersionNumber() + " (introuvable)";
                        if (idx >= 0) stagedItems.set(idx, failed); else stagedItems.add(failed);
                        uiDirty.set(true);
                        return;
                    }
                    final String resolved = "MOD " + modFromSlug.title() + " - " + mr.getProjectReference() + " - " + mr.getVersionNumber();
                    int idx = stagedItems.indexOf(placeholder);
                    if (idx >= 0) stagedItems.set(idx, resolved); else stagedItems.add(resolved);
                    uiDirty.set(true);
                } catch (Exception ex) {
                    int idx = stagedItems.indexOf(placeholder);
                    String failed = "MOD " + mr.getProjectReference() + " - " + mr.getVersionNumber() + " (introuvable)";
                    if (idx >= 0) stagedItems.set(idx, failed); else stagedItems.add(failed);
                    uiDirty.set(true);
                }
            });
        }
    }

    /** Retire un seul élément de la liste (mise à jour incrémentale). */
    public static void removeListItem(Mod mod, @Nullable String originalItem) {
        ensureExecutorsStarted();
        if (originalItem != null) {
            stagedItems.remove(originalItem);
        }
        if (mod instanceof UrlMod url) {
            stagedItems.remove(url.name());
        } else if (mod instanceof CurseForgeMod cf) {
            String marker = " " + cf.projectID() + " - " + cf.fileID();
            stagedItems.removeIf(s -> s.startsWith("CF ") && s.contains(marker));
        } else if (mod instanceof ModrinthMod mr) {
            String marker = " " + mr.getProjectReference() + " - " + mr.getVersionNumber();
            stagedItems.removeIf(s -> s.startsWith("MOD ") && s.contains(marker));
        }
        uiDirty.set(true);
    }

    public static void main(String[] args) throws IOException {
        createJsonFile();
        jsonContent = getJsonContent();

        OptionParser parser = new OptionParser();
        parser.accepts("cfKey").withOptionalArg().ofType(String.class);
        OptionSet options = parser.parse(args);

        CF_API_KEY = Objects.requireNonNullElse(checkOptArg(options, "cfKey"), "$2a$10$pEf8ZqqpXN3mWm.nZgjA0.dvobnxeWxPeffkd9dHBEabweZQhvqKi"); // Sorry Flow, I had to do it
        try {
            cfApi = CurseForgeAPI.builder()
                    .apiKey(CF_API_KEY)
                    .build();
        } catch (LoginException e) {
            cfApi = null;
        }

        launch(args);
    }

    @Override
    public void stop() throws Exception {
        saveFile(jsonContent);

        // Arrêt propre des exécutors
        if (uiScheduler != null) {
            uiScheduler.shutdownNow();
        }
        if (bgExecutor != null) {
            bgExecutor.shutdownNow();
        }

        super.stop();
    }
}
