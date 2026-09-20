package net.paulem.fjc.gui.content;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import net.paulem.fjc.gui.browse.CardParts;
import net.paulem.fjc.gui.browse.SearchResult;
import net.paulem.fjc.gui.model.ModCategory;
import net.paulem.fjc.gui.model.ModEntry;
import net.paulem.fjc.utils.ManipulationUtils;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

import java.util.List;

/**
 * A mod of the modpack as a card, styled like the search results: icon, name, source, description, tags on the
 * left; details/delete actions, installed version, stats and last update on the right.
 */
public class ModCardCell extends ListCell<ModEntry> {
    /** What the cell needs from the panel that owns it. */
    public interface Host {
        void showDetails(ModEntry entry);

        void confirmAndRemove(ModEntry entry);
    }

    private static final int MAX_TAGS = 4;

    private final Host host;

    public ModCardCell(Host host) {
        this.host = host;
        setPrefWidth(0); // never ask the list for a horizontal scrollbar

        ContextMenu menu = new ContextMenu();
        MenuItem view = new MenuItem("Voir les détails", FontIcon.of(Material2AL.INFO, 14));
        view.setOnAction(e -> {
            if (getItem() != null) host.showDetails(getItem());
        });
        MenuItem delete = new MenuItem("Supprimer", FontIcon.of(Material2AL.DELETE, 14));
        delete.setOnAction(e -> {
            if (getItem() != null) host.confirmAndRemove(getItem());
        });
        menu.getItems().addAll(view, delete);
        setContextMenu(menu);
    }

    @Override
    protected void updateItem(ModEntry entry, boolean empty) {
        super.updateItem(entry, empty);
        if (empty || entry == null) {
            setGraphic(null);
            return;
        }
        setGraphic(buildCard(entry));
    }

    private Node buildCard(ModEntry entry) {
        SearchResult info = entry.getInfo();
        ModCategory category = entry.getCategory();
        Color accent = category.getColor();

        // ---- title row ----
        Label title = new Label(entry.getTitle());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 1.1em;");
        title.setMinWidth(Region.USE_PREF_SIZE);
        HBox titleRow = new HBox(8, title);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        if (info != null && !info.author().isBlank()) titleRow.getChildren().add(CardParts.muted("par " + info.author()));
        titleRow.getChildren().add(CardParts.sourcePill(category));
        switch (entry.getStatus()) {
            case LOADING -> {
                ProgressIndicator spinner = new ProgressIndicator();
                spinner.setPrefSize(14, 14);
                spinner.setMaxSize(14, 14);
                titleRow.getChildren().add(spinner);
            }
            case ERROR -> titleRow.getChildren().add(FontIcon.of(Material2MZ.WARNING, 16, Color.web("#e0a028")));
            case RESOLVED -> {
            }
        }

        // ---- description ----
        Label description = new Label(descriptionOf(entry, info));
        description.setMaxWidth(Double.MAX_VALUE);
        description.setTextOverrun(OverrunStyle.ELLIPSIS);
        if (entry.getStatus() == ModEntry.Status.ERROR) description.setStyle("-fx-text-fill: #e0a028;");

        // ---- tags ----
        HBox tags = new HBox(6);
        List<String> all = info != null ? info.tags() : category == ModCategory.URL ? List.of("Fichier jar") : List.of();
        for (int i = 0; i < Math.min(MAX_TAGS, all.size()); i++) tags.getChildren().add(CardParts.tag(all.get(i)));
        if (all.size() > MAX_TAGS) tags.getChildren().add(CardParts.tag("+" + (all.size() - MAX_TAGS)));

        VBox text = new VBox(4, titleRow, description);
        if (!tags.getChildren().isEmpty()) text.getChildren().add(tags);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox card = new HBox(12, CardParts.iconBox(info != null ? info.iconUrl() : null, category.getIcon()), text, buildRight(entry, info));
        card.getStyleClass().add("mod-card");
        card.setStyle("-fx-border-color: " + CardParts.hex(accent) + "; -fx-border-width: 0 0 0 3; -fx-border-radius: 8 0 0 8;");
        card.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) host.showDetails(entry);
        });
        return card;
    }

    private static String descriptionOf(ModEntry entry, SearchResult info) {
        return switch (entry.getStatus()) {
            case LOADING -> "Résolution en cours...";
            case ERROR -> entry.getSubtitle() != null ? entry.getSubtitle() : "Impossible de récupérer ce mod";
            case RESOLVED -> info != null && !info.description().isBlank() ? info.description()
                    : entry.getCategory() == ModCategory.URL ? "Ajouté par URL" : "";
        };
    }

    private Node buildRight(ModEntry entry, SearchResult info) {
        VBox right = new VBox(6);
        right.setAlignment(Pos.TOP_RIGHT);
        right.setMinWidth(220);
        right.setPrefWidth(220);

        Button infoButton = new Button();
        infoButton.setGraphic(FontIcon.of(Material2AL.INFO, 14));
        infoButton.getStyleClass().add("button-icon");
        infoButton.setTooltip(new Tooltip("Voir les détails"));
        infoButton.setOnAction(e -> host.showDetails(entry));

        Button deleteButton = new Button();
        deleteButton.setGraphic(FontIcon.of(Material2AL.DELETE, 14, Color.web("#e05252")));
        deleteButton.getStyleClass().add("button-icon");
        deleteButton.setTooltip(new Tooltip("Retirer du modpack"));
        deleteButton.setOnAction(e -> host.confirmAndRemove(entry));

        HBox actions = new HBox(6, infoButton, deleteButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        right.getChildren().add(actions);

        // What the modpack actually contains: the pinned version / file / size.
        if (entry.getStatus() == ModEntry.Status.RESOLVED && entry.getSubtitle() != null && !entry.getSubtitle().isBlank()) {
            Label version = CardParts.muted(entry.getSubtitle());
            version.setTextOverrun(OverrunStyle.ELLIPSIS);
            version.setMaxWidth(Double.MAX_VALUE);
            version.setAlignment(Pos.CENTER_RIGHT);
            version.setTooltip(new Tooltip(entry.getSubtitle()));
            right.getChildren().add(version);
        }

        if (info != null) {
            HBox stats = new HBox(12, CardParts.stat(Material2AL.CLOUD_DOWNLOAD, ManipulationUtils.formatCount(info.downloads())));
            if (info.follows() > 0) stats.getChildren().add(CardParts.stat(Material2AL.FAVORITE_BORDER, ManipulationUtils.formatCount(info.follows())));
            stats.setAlignment(Pos.CENTER_RIGHT);
            right.getChildren().add(stats);

            if (info.dateModified() != null) {
                HBox date = CardParts.stat(Material2AL.HISTORY, ManipulationUtils.relativeDate(info.dateModified()));
                date.setAlignment(Pos.CENTER_RIGHT);
                right.getChildren().add(date);
            }
        }
        return right;
    }
}
