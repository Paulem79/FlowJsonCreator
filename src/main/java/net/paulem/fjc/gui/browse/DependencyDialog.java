package net.paulem.fjc.gui.browse;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Asks before adding dependencies. Required ones are ticked, optional ones are not; nothing is written
 * until the user confirms, and cancelling installs nothing at all (not even the mod itself).
 */
public final class DependencyDialog {
    private DependencyDialog() {
    }

    /** @return the dependencies to install alongside the mod, or empty if the user cancelled. */
    public static Optional<List<DependencyResolver.Item>> ask(Stage owner, SearchResult root, VersionOption version,
                                                              DependencyResolver.Plan plan) {
        VBox list = new VBox(8);
        list.setPadding(new Insets(4, 8, 4, 0));

        List<CheckBox> boxes = new ArrayList<>();
        for (DependencyResolver.Item item : plan.items()) {
            CheckBox box = new CheckBox(item.project().title() + "  ·  " + item.version().displayName()
                    + (item.required() ? "  (requis)" : "  (optionnel)"));
            box.setSelected(item.required());
            box.setWrapText(true);
            boxes.add(box);
            list.getChildren().add(box);
        }

        if (!plan.unresolved().isEmpty()) {
            Label warning = new Label("Non installables automatiquement :\n - " + String.join("\n - ", plan.unresolved()));
            warning.setWrapText(true);
            warning.setStyle("-fx-text-fill: #d29922;");
            list.getChildren().add(warning);
        }

        // Some mods list a dozen optional dependencies: keep the dialog a sane height.
        ScrollPane content = new ScrollPane(list);
        content.setFitToWidth(true);
        content.setPrefViewportWidth(480);
        content.setPrefViewportHeight(Math.min(320, 40 + 30 * (double) (plan.items().size() + plan.unresolved().size())));
        content.setStyle("-fx-background-color: transparent;");

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(owner);
        alert.setTitle("Dépendances");
        alert.setHeaderText(root.title() + " (" + version.displayName() + ") utilise d'autres mods");
        alert.getDialogPane().setContent(content);
        alert.getButtonTypes().setAll(
                new ButtonType("Installer", javafx.scene.control.ButtonBar.ButtonData.OK_DONE),
                ButtonType.CANCEL);

        Optional<ButtonType> answer = alert.showAndWait();
        if (answer.isEmpty() || answer.get() == ButtonType.CANCEL) return Optional.empty();

        List<DependencyResolver.Item> chosen = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).isSelected()) chosen.add(plan.items().get(i));
        }
        return Optional.of(chosen);
    }
}
