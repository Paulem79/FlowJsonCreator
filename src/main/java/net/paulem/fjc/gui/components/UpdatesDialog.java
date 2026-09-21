package net.paulem.fjc.gui.components;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import net.paulem.fjc.gui.browse.CardParts;
import net.paulem.fjc.gui.model.ModCategory;
import net.paulem.fjc.update.UpdateChecker;
import net.paulem.fjc.update.UpdateInfo;
import net.paulem.fjc.utils.ManipulationUtils;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Lists the available mod updates, each with its version change, release channel, date and (on demand) release
 * notes, and lets the user pick which ones to apply. Nothing changes until "Mettre à jour" is pressed.
 */
public class UpdatesDialog {
    /** An update with what the dialog needs to display it. */
    public record Row(UpdateInfo update, String title, @Nullable String iconUrl, ModCategory category) {
    }

    private final Stage owner;
    private final List<Row> rows;
    private final Consumer<List<UpdateInfo>> onApply;

    private final Stage stage = new Stage();
    private final Map<CheckBox, Row> boxes = new LinkedHashMap<>();
    private final Button applyBtn = new Button();

    public UpdatesDialog(Stage owner, List<Row> rows, Consumer<List<UpdateInfo>> onApply) {
        this.owner = owner;
        this.rows = rows;
        this.onApply = onApply;
    }

    public void show() {
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Mises à jour disponibles");
        stage.setScene(new Scene(buildUi(), 760, 640));
        stage.getScene().getStylesheets().addAll(owner.getScene().getStylesheets());
        stage.show();
    }

    private VBox buildUi() {
        Label title = new Label("Mises à jour disponibles (" + rows.size() + ")", CardParts.updateDiamond(16, null));
        title.setGraphicTextGap(8);
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 1.2em;");
        Label hint = CardParts.muted("Coche les mods à mettre à jour : leur version sera remplacée dans le mods.json.");

        Button selectAll = new Button("Tout sélectionner");
        selectAll.setOnAction(e -> setAll(true));
        Button selectNone = new Button("Aucun");
        selectNone.setOnAction(e -> setAll(false));
        HBox selection = new HBox(8, selectAll, selectNone);

        VBox list = new VBox(8);
        list.setPadding(new Insets(2, 8, 2, 0));
        for (Row row : rows) {
            CheckBox box = new CheckBox();
            box.setSelected(true);
            box.setOnAction(e -> refreshApply());
            boxes.put(box, row);
            list.getChildren().add(buildRow(row, box));
        }
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button cancel = new Button("Annuler");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> stage.close());
        applyBtn.setDefaultButton(true);
        applyBtn.setOnAction(e -> apply());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(8, spacer, cancel, applyBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        refreshApply();
        VBox root = new VBox(10, title, hint, selection, scroll, buttons);
        root.setPadding(new Insets(16));
        return root;
    }

    private Node buildRow(Row row, CheckBox box) {
        UpdateInfo update = row.update();

        Label name = new Label(row.title());
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 1.1em;");
        name.setMinWidth(Region.USE_PREF_SIZE);
        HBox top = new HBox(8, name, CardParts.sourcePill(row.category()), CardParts.tag(releaseLabel(update.releaseType())));
        top.setAlignment(Pos.CENTER_LEFT);

        Label to = new Label(update.targetLabel());
        to.setStyle("-fx-text-fill: #3fb950; -fx-font-weight: bold;");
        HBox versions = new HBox(6, clipped(CardParts.muted(update.currentLabel())), FontIcon.of(Material2AL.ARROW_FORWARD, 14), clipped(to));
        versions.setAlignment(Pos.CENTER_LEFT);
        if (update.date() != null) {
            versions.getChildren().add(CardParts.stat(Material2AL.HISTORY, ManipulationUtils.relativeDate(update.date())));
        }

        TextArea notes = new TextArea();
        notes.setEditable(false);
        notes.setWrapText(true);
        notes.setPrefRowCount(8);
        TitledPane notesPane = new TitledPane("Notes de version", notes);
        notesPane.setExpanded(false);
        boolean[] loaded = {false};
        notesPane.expandedProperty().addListener((obs, was, expanded) -> {
            if (!expanded || loaded[0]) return;
            loaded[0] = true;
            notes.setText("Chargement...");
            Thread t = new Thread(() -> {
                String text = UpdateChecker.loadChangelog(update);
                Platform.runLater(() -> notes.setText(text.isBlank() ? "Aucune note de version fournie." : text));
            }, "fjc-changelog");
            t.setDaemon(true);
            t.start();
        });

        VBox text = new VBox(4, top, versions, notesPane);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        box.setAlignment(Pos.TOP_LEFT);
        HBox card = new HBox(12, box, CardParts.iconBox(row.iconUrl(), row.category().getIcon()), text);
        card.getStyleClass().add("mod-card");
        card.setCursor(Cursor.DEFAULT);
        card.setStyle("-fx-border-color: " + CardParts.hex(row.category().getColor())
                + "; -fx-border-width: 0 0 0 3; -fx-border-radius: 8 0 0 8;");
        return card;
    }

    private static Label clipped(Label label) {
        label.setMinWidth(0);
        return label;
    }

    private static String releaseLabel(String type) {
        return switch (type.toLowerCase()) {
            case "release" -> "Release";
            case "beta" -> "Bêta";
            default -> "Alpha";
        };
    }

    private void setAll(boolean selected) {
        boxes.keySet().forEach(box -> box.setSelected(selected));
        refreshApply();
    }

    private List<UpdateInfo> selected() {
        List<UpdateInfo> chosen = new ArrayList<>();
        boxes.forEach((box, row) -> {
            if (box.isSelected()) chosen.add(row.update());
        });
        return chosen;
    }

    private void refreshApply() {
        int count = selected().size();
        applyBtn.setText(count == 0 ? "Mettre à jour" : "Mettre à jour (" + count + ")");
        applyBtn.setDisable(count == 0);
    }

    private void apply() {
        List<UpdateInfo> chosen = selected();
        stage.close();
        onApply.accept(chosen);
    }
}
