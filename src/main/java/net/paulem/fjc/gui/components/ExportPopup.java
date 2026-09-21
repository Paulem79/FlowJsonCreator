package net.paulem.fjc.gui.components;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import net.paulem.fjc.Main;
import net.paulem.fjc.export.ExportEntry;
import net.paulem.fjc.export.ModExporter;
import net.paulem.fjc.export.SideVerdict;
import net.paulem.fjc.utils.JsonUtils;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Exports the modpack's mods as a .zip (a {@code mods/} folder), either for a server (client-only mods left out)
 * or for a client (everything). Opening the popup starts analysing every mod; each row shows the verdict and how it
 * was reached, and can be overridden with its checkbox before exporting.
 */
public class ExportPopup {
    private static final int ANALYSIS_THREADS = 8;

    private final Stage owner;
    private final Stage stage = new Stage();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private final ObservableList<ExportEntry> items = FXCollections.observableArrayList();
    private final ListView<ExportEntry> listView = new ListView<>(items);
    private final RadioButton serverRadio = new RadioButton("Serveur");
    private final RadioButton clientRadio = new RadioButton("Client (inclut les mods serveur)");
    private final Label summary = new Label();
    private final Label status = new Label();
    private final ProgressBar progress = new ProgressBar(0);
    private final Button exportBtn = new Button("Exporter en .zip", FontIcon.of(Material2AL.ARCHIVE, 14));

    private Path tmpDir;
    private ExecutorService pool;
    private volatile boolean analysisDone = false;
    private volatile boolean exporting = false;

    public ExportPopup(Stage owner) {
        this.owner = owner;
    }

    public void show() {
        try {
            tmpDir = Files.createTempDirectory("fjc-export-");
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Impossible de créer un dossier temporaire : " + e.getMessage()).showAndWait();
            return;
        }

        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Exporter les mods");
        stage.setScene(new Scene(buildUi(), 720, 620));
        stage.setOnHidden(e -> cleanup());
        stage.show();

        items.setAll(ModExporter.collect(Main.jsonContent));
        items.sort(Comparator.comparing(entry -> entry.title().toLowerCase()));
        startAnalysis();
    }

    private VBox buildUi() {
        Label title = new Label("Exporter les mods en .zip", FontIcon.of(Material2AL.ARCHIVE, 18));
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 1.2em;");

        ToggleGroup group = new ToggleGroup();
        serverRadio.setToggleGroup(group);
        clientRadio.setToggleGroup(group);
        serverRadio.setSelected(true);
        group.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                oldV.setSelected(true); // one of the two always stays selected
                return;
            }
            applyDefaults();
        });

        Label serverHint = new Label("Sans les mods réservés au client (shaders, minimap, interface...)");
        serverHint.getStyleClass().add("text-muted");
        Label clientHint = new Label("Tous les mods du modpack");
        clientHint.getStyleClass().add("text-muted");
        VBox modeBox = new VBox(4, new VBox(0, serverRadio, indented(serverHint)), new VBox(0, clientRadio, indented(clientHint)));

        listView.setCellFactory(lv -> new EntryCell());
        listView.setPlaceholder(new Label("Aucun mod dans le modpack."));
        VBox.setVgrow(listView, Priority.ALWAYS);

        summary.getStyleClass().add("text-muted");
        status.getStyleClass().add("text-muted");
        progress.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(progress, Priority.ALWAYS);

        exportBtn.setDefaultButton(true);
        exportBtn.setDisable(true);
        exportBtn.setOnAction(e -> onExport());
        Button closeBtn = new Button("Fermer");
        closeBtn.setOnAction(e -> stage.close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(8, summary, spacer, closeBtn, exportBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10, title, modeBox, listView, new VBox(4, progress, status), buttons);
        root.setPadding(new Insets(16));
        return root;
    }

    private static HBox indented(Label label) {
        HBox box = new HBox(label);
        box.setPadding(new Insets(0, 0, 0, 24));
        return box;
    }

    // ------------------------------------------------------------------
    // Analysis
    // ------------------------------------------------------------------

    private void startAnalysis() {
        List<ExportEntry> entries = new ArrayList<>(items);
        int total = entries.size();
        if (total == 0) {
            finishAnalysis();
            return;
        }

        AtomicInteger done = new AtomicInteger();
        pool = Executors.newFixedThreadPool(ANALYSIS_THREADS, r -> {
            Thread t = new Thread(r, "fjc-analysis");
            t.setDaemon(true);
            return t;
        });
        status.setText("Récupération des informations (Modrinth / CurseForge)...");
        progress.setProgress(-1);
        // The bulk requests come first, on their own, so the per-mod tasks below find everything already loaded
        pool.submit(() -> {
            ModExporter.prefetch(entries);
            Platform.runLater(() -> {
                progress.setProgress(0);
                status.setText("Analyse des mods (0/" + total + ")...");
            });
            for (ExportEntry entry : entries) {
                pool.submit(() -> {
                    if (cancelled.get()) return;
                    ModExporter.resolve(entry, tmpDir);
                    int n = done.incrementAndGet();
                    Platform.runLater(() -> {
                        ExportEntry.applyDefaults(items, serverRadio.isSelected());
                        progress.setProgress((double) n / total);
                        status.setText("Analyse des mods (" + n + "/" + total + ")...");
                        listView.refresh();
                        updateSummary();
                        if (n == total) finishAnalysis();
                    });
                });
            }
        });
    }

    private void finishAnalysis() {
        analysisDone = true;
        progress.setProgress(0);
        long guesses = items.stream().filter(e -> e.verdict() != null && e.verdict().heuristic()).count();
        long errors = items.stream().filter(e -> e.state() == ExportEntry.State.ERROR).count();
        StringBuilder text = new StringBuilder("Analyse terminée.");
        if (guesses > 0) text.append(' ').append(guesses).append(" verdict(s) déduit(s) du code (à vérifier).");
        if (errors > 0) text.append(' ').append(errors).append(" mod(s) en erreur (ignorés).");
        status.setText(text.toString());
        exportBtn.setDisable(exporting);
        updateSummary();
    }

    private void applyDefaults() {
        boolean server = serverRadio.isSelected();
        ExportEntry.applyDefaults(items, server);
        listView.refresh();
        updateSummary();
    }

    private void updateSummary() {
        long included = items.stream().filter(ExportEntry::isIncluded).count();
        summary.setText(included + " mod(s) sur " + items.size() + " dans le zip");
    }

    // ------------------------------------------------------------------
    // Export
    // ------------------------------------------------------------------

    private void onExport() {
        boolean server = serverRadio.isSelected();
        List<ExportEntry> selected = items.stream().filter(ExportEntry::isIncluded).toList();
        if (selected.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "Aucun mod n'est sélectionné.").showAndWait();
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Enregistrer le zip");
        chooser.setInitialFileName(server ? "mods-serveur.zip" : "mods-client.zip");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archive zip", "*.zip"));
        if (JsonUtils.modsJson.getParentFile() != null) chooser.setInitialDirectory(JsonUtils.modsJson.getParentFile());
        File target = chooser.showSaveDialog(stage);
        if (target == null) return;

        exporting = true;
        exportBtn.setDisable(true);
        serverRadio.setDisable(true);
        clientRadio.setDisable(true);
        progress.setProgress(0);

        Thread worker = new Thread(() -> {
            try {
                ModExporter.Report report = ModExporter.export(selected, tmpDir, target,
                        (done, total, message) -> Platform.runLater(() -> {
                            progress.setProgress((double) done / total);
                            status.setText(message + " (" + done + "/" + total + ")");
                        }), cancelled::get);
                Platform.runLater(() -> onExportDone(report, target));
            } catch (InterruptedException e) {
                target.delete();
                Platform.runLater(this::resetAfterExport);
            } catch (Exception e) {
                target.delete();
                Platform.runLater(() -> {
                    resetAfterExport();
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.initOwner(stage);
                    alert.setTitle("Erreur");
                    alert.setHeaderText("L'export a échoué");
                    alert.setContentText(e.getMessage());
                    alert.showAndWait();
                });
            }
        }, "fjc-export-zip");
        worker.setDaemon(true);
        worker.start();
    }

    private void onExportDone(ModExporter.Report report, File target) {
        resetAfterExport();
        status.setText(report.written() + " mod(s) écrit(s) dans " + target.getName());

        Alert alert = new Alert(report.failed().isEmpty() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
        alert.initOwner(stage);
        alert.setTitle("Export terminé");
        alert.setHeaderText(report.written() + " mod(s) exporté(s) dans " + target.getName());
        if (!report.failed().isEmpty()) {
            alert.setContentText(report.failed().size() + " mod(s) n'ont pas pu être téléchargés et sont absents du zip :\n\n"
                    + String.join("\n", report.failed()));
        } else {
            alert.setContentText("Les jars sont dans le dossier mods/ de l'archive.");
        }
        alert.showAndWait();
    }

    private void resetAfterExport() {
        exporting = false;
        exportBtn.setDisable(!analysisDone);
        serverRadio.setDisable(false);
        clientRadio.setDisable(false);
        progress.setProgress(0);
    }

    private void cleanup() {
        cancelled.set(true);
        if (pool != null) pool.shutdownNow();
        Thread cleaner = new Thread(() -> {
            try (var files = Files.walk(tmpDir)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (IOException ignored) {
            }
        }, "fjc-export-cleanup");
        cleaner.setDaemon(true);
        cleaner.start();
    }

    // ------------------------------------------------------------------
    // Row
    // ------------------------------------------------------------------

    private class EntryCell extends ListCell<ExportEntry> {
        private final CheckBox check = new CheckBox();
        private final Label name = new Label();
        private final Label detail = new Label();
        private final Label badge = new Label();
        private final HBox row;

        EntryCell() {
            name.setStyle("-fx-font-weight: bold;");
            detail.getStyleClass().add("text-muted");
            detail.setStyle("-fx-font-size: 0.85em;");
            badge.setStyle("-fx-font-size: 0.85em; -fx-padding: 2 8 2 8; -fx-background-radius: 10;");

            VBox text = new VBox(1, name, detail);
            HBox.setHgrow(text, Priority.ALWAYS);
            row = new HBox(10, check, text, badge);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(4, 6, 4, 6));

            check.setOnAction(e -> {
                ExportEntry entry = getItem();
                if (entry == null) return;
                entry.setIncluded(check.isSelected());
                updateSummary();
            });
        }

        @Override
        protected void updateItem(ExportEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setGraphic(null);
                setTooltip(null);
                return;
            }

            name.setText(entry.title());
            check.setSelected(entry.isIncluded());
            check.setDisable(entry.state() == ExportEntry.State.ERROR);

            String source = entry.category().getLabel() + (entry.loader() != null ? " • " + entry.loader() : "");
            SideVerdict verdict = entry.verdict();
            switch (entry.state()) {
                case PENDING -> {
                    detail.setText(source + " • analyse en cours...");
                    setBadge("Analyse...", "#8b949e");
                }
                case ERROR -> {
                    detail.setText(source + " • " + entry.error());
                    setBadge("Erreur", "#f85149");
                }
                case READY -> {
                    String how = verdict == null ? "" : " • " + verdict.method();
                    detail.setText(source + how);
                    if (verdict == null) {
                        setBadge("Inconnu", "#8b949e");
                    } else if (verdict.isClientOnly()) {
                        setBadge(verdict.heuristic() ? "Client ?" : "Client", "#d29922");
                    } else {
                        setBadge(verdict.heuristic() ? "Serveur ?" : "Client + serveur", "#3fb950");
                    }
                }
            }
            if (verdict != null && verdict.heuristic()) {
                setTooltip(new Tooltip("Déduit par analyse du code : vérifie avant d'exporter."));
            } else {
                setTooltip(null);
            }
            setGraphic(row);
        }

        private void setBadge(String text, String color) {
            badge.setText(text);
            badge.setStyle("-fx-font-size: 0.85em; -fx-padding: 2 8 2 8; -fx-background-radius: 10; -fx-text-fill: " + color
                    + "; -fx-border-color: " + color + "; -fx-border-radius: 10;");
        }
    }
}
