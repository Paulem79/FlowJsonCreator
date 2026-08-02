package net.paulem.fjc.gui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2MZ;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A small dialog listing every field of an object (a Mod, a CurseForge/Modrinth API result...)
 * as a clean, copyable label/value grid, instead of a raw text dump.
 */
public class PropertiesViewerPopup extends Stage {
    private final GridPane grid;

    public PropertiesViewerPopup(Stage owner) {
        super();
        this.initModality(Modality.APPLICATION_MODAL);
        this.initOwner(owner);
        this.setTitle("Détails");

        VBox dialogVbox = new VBox(10);
        dialogVbox.setPadding(new Insets(14));

        grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(120);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, valueColumn);

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        dialogVbox.getChildren().add(scrollPane);

        Scene dialogScene = new Scene(dialogVbox, 520, 320);
        this.setScene(dialogScene);
    }

    public void showPopup(@NotNull Object object) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (Field field : object.getClass().getDeclaredFields()) {
            if (field.isSynthetic()) continue;
            try {
                field.setAccessible(true);
                Object value = field.get(object);
                fields.put(field.getName(), value == null ? "" : value.toString());
            } catch (ReflectiveOperationException | RuntimeException e) {
                fields.put(field.getName(), "(inaccessible)");
            }
        }

        int row = 0;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            Label nameLabel = new Label(entry.getKey());
            nameLabel.setStyle("-fx-font-weight: bold;");

            Label valueLabel = new Label(entry.getValue());
            valueLabel.setWrapText(true);

            Button copyBtn = new Button();
            copyBtn.setGraphic(FontIcon.of(Material2MZ.SAVE, 12));
            copyBtn.getStyleClass().add("button-icon");
            copyBtn.setTooltip(new Tooltip("Copier la valeur"));
            copyBtn.setOnAction(e -> {
                ClipboardContent content = new ClipboardContent();
                content.putString(entry.getValue());
                Clipboard.getSystemClipboard().setContent(content);
            });

            HBox valueBox = new HBox(6, valueLabel, copyBtn);
            valueBox.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(valueLabel, Priority.ALWAYS);

            grid.add(nameLabel, 0, row);
            grid.add(valueBox, 1, row);
            row++;
        }

        show();
    }
}
