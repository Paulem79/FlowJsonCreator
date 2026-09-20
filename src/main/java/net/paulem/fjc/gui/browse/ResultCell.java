package net.paulem.fjc.gui.browse;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import net.paulem.fjc.flow.mod.Mod;
import net.paulem.fjc.flow.mod.ModrinthMod;
import net.paulem.fjc.utils.ManipulationUtils;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

import java.util.List;

/**
 * The result card: icon, name/author/source, one-line description, tags on the left; install action, stats and
 * last update on the right. Clicking the card unfolds the list of versions matching the current filters.
 */
public class ResultCell extends ListCell<SearchResult> {
    /** What the cell needs from the panel that owns it. */
    public interface Host {
        @Nullable String gameVersion();

        /** The mods.json entry for this project (any version), or null when not installed. */
        @Nullable Mod installedMod(SearchResult result);

        boolean isExpanded(SearchResult result);

        /** Versions for an expanded card, or null while they are still loading. */
        @Nullable List<VersionOption> versionsFor(SearchResult result);

        void toggleExpanded(SearchResult result);

        void installLatest(SearchResult result);

        void installVersion(SearchResult result, VersionOption version);

        void showDetails(SearchResult result);
    }

    private static final int ICON_SIZE = 56;
    private static final int MAX_TAGS = 4;
    private static final int MAX_VERSION_ROWS = 30;

    private final Host host;

    public ResultCell(Host host) {
        this.host = host;
        setPrefWidth(0); // never ask the list for a horizontal scrollbar
    }

    @Override
    protected void updateItem(SearchResult r, boolean empty) {
        super.updateItem(r, empty);
        if (empty || r == null) {
            setGraphic(null);
            return;
        }

        VBox root = new VBox(4, buildCard(r));
        if (host.isExpanded(r)) root.getChildren().add(buildVersions(r));
        setGraphic(root);
    }

    // ------------------------------------------------------------------
    // Card
    // ------------------------------------------------------------------

    private Node buildCard(SearchResult r) {
        Color accent = r.source().getColor();

        Label title = new Label(r.title());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 1.1em;");
        title.setMinWidth(Region.USE_PREF_SIZE);
        HBox titleRow = new HBox(8, title);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        if (!r.author().isBlank()) {
            Label author = new Label("par " + r.author());
            author.getStyleClass().add("text-muted");
            titleRow.getChildren().add(author);
        }
        titleRow.getChildren().add(sourcePill(r, accent));
        titleRow.getChildren().add(FontIcon.of(host.isExpanded(r) ? Material2AL.EXPAND_LESS : Material2AL.EXPAND_MORE, 16));

        Label description = new Label(r.description());
        description.setMaxWidth(Double.MAX_VALUE);
        description.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);

        HBox tags = new HBox(6);
        List<String> all = r.tags();
        for (int i = 0; i < Math.min(MAX_TAGS, all.size()); i++) tags.getChildren().add(tag(all.get(i)));
        if (all.size() > MAX_TAGS) tags.getChildren().add(tag("+" + (all.size() - MAX_TAGS)));

        VBox info = new VBox(4, titleRow, description, tags);
        info.setMinWidth(0);
        HBox.setHgrow(info, Priority.ALWAYS);

        HBox card = new HBox(12, iconBox(r), info, buildRight(r, accent));
        card.getStyleClass().add("mod-card");
        card.setStyle("-fx-border-color: " + hex(accent) + "; -fx-border-width: 0 0 0 3; -fx-border-radius: 8 0 0 8;");
        card.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) host.toggleExpanded(r);
            else if (e.getButton() == MouseButton.SECONDARY) host.showDetails(r);
        });
        return card;
    }

    private Node iconBox(SearchResult r) {
        FontIcon placeholder = FontIcon.of(Material2AL.EXTENSION, 28);
        placeholder.getStyleClass().add("text-muted");

        ImageView view = new ImageView();
        view.setFitWidth(ICON_SIZE);
        view.setFitHeight(ICON_SIZE);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        Rectangle clip = new Rectangle(ICON_SIZE, ICON_SIZE);
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        view.setClip(clip);

        StackPane box = new StackPane(placeholder, view);
        box.setMinSize(ICON_SIZE, ICON_SIZE);
        box.setPrefSize(ICON_SIZE, ICON_SIZE);
        box.setMaxSize(ICON_SIZE, ICON_SIZE);
        box.getStyleClass().add("mod-icon");
        box.setAlignment(Pos.CENTER);

        if (r.iconUrl() != null) {
            IconCache.get(r.iconUrl()).thenAccept(icon -> icon.ifPresent(img -> Platform.runLater(() -> {
                view.setImage(img);
                placeholder.setVisible(false);
            })));
        }
        HBox wrapper = new HBox(box);
        wrapper.setAlignment(Pos.TOP_LEFT);
        return wrapper;
    }

    private Node buildRight(SearchResult r, Color accent) {
        VBox right = new VBox(6);
        right.setAlignment(Pos.TOP_RIGHT);
        right.setMinWidth(220);
        right.setPrefWidth(220);

        right.getChildren().add(buildAction(r));

        HBox stats = new HBox(12, stat(Material2AL.CLOUD_DOWNLOAD, ManipulationUtils.formatCount(r.downloads())));
        if (r.follows() > 0) stats.getChildren().add(stat(Material2AL.FAVORITE_BORDER, ManipulationUtils.formatCount(r.follows())));
        stats.setAlignment(Pos.CENTER_RIGHT);
        right.getChildren().add(stats);

        if (r.dateModified() != null) {
            HBox date = stat(Material2AL.HISTORY, ManipulationUtils.relativeDate(r.dateModified()));
            date.setAlignment(Pos.CENTER_RIGHT);
            right.getChildren().add(date);
        }
        return right;
    }

    private Node buildAction(SearchResult r) {
        Mod installed = host.installedMod(r);
        String gameVersion = host.gameVersion();

        if (installed == null) {
            Button install = new Button("Installer", FontIcon.of(Material2AL.ADD, 14));
            install.getStyleClass().addAll("success", "button-outlined");
            install.setOnAction(e -> host.installLatest(r));
            return withTooltipWhenBlocked(install, gameVersion == null,
                    "Choisis d'abord une version de Minecraft dans les filtres");
        }

        Label badge = new Label("Installé", FontIcon.of(Material2AL.CHECK, 14));
        badge.getStyleClass().add("installed-badge");
        badge.setMinWidth(Region.USE_PREF_SIZE);
        if (installed instanceof ModrinthMod mr) badge.setTooltip(new Tooltip("Version installée : " + mr.getVersionNumber()));

        Button change = new Button("Changer", FontIcon.of(Material2MZ.SWAP_HORIZ, 14));
        change.getStyleClass().add("button-outlined");
        change.setOnAction(e -> host.installLatest(r));
        HBox box = new HBox(8, badge, withTooltipWhenBlocked(change, gameVersion == null,
                "Choisis d'abord une version de Minecraft pour changer de version"));
        box.setAlignment(Pos.CENTER_RIGHT);
        return box;
    }

    /** Disabled controls never show their own tooltip, so it goes on a wrapper. */
    private static Node withTooltipWhenBlocked(Button button, boolean blocked, String reason) {
        button.setDisable(blocked);
        HBox wrapper = new HBox(button);
        wrapper.setAlignment(Pos.CENTER_RIGHT);
        if (blocked) {
            Tooltip tip = new Tooltip(reason);
            tip.setShowDelay(Duration.millis(200));
            Tooltip.install(wrapper, tip);
        }
        return wrapper;
    }

    // ------------------------------------------------------------------
    // Versions (unfolded part)
    // ------------------------------------------------------------------

    private Node buildVersions(SearchResult r) {
        VBox box = new VBox(4);
        box.getStyleClass().add("mod-versions");
        box.setPadding(new Insets(6, 6, 6, 24));

        List<VersionOption> versions = host.versionsFor(r);
        if (versions == null) {
            box.getChildren().add(muted("Chargement des versions..."));
            return box;
        }
        if (versions.isEmpty()) {
            box.getChildren().add(muted("Aucune version ne correspond aux filtres."));
            return box;
        }

        Mod installed = host.installedMod(r);
        for (int i = 0; i < Math.min(MAX_VERSION_ROWS, versions.size()); i++) {
            box.getChildren().add(versionRow(r, versions.get(i), installed));
        }
        if (versions.size() > MAX_VERSION_ROWS) {
            box.getChildren().add(muted("... et " + (versions.size() - MAX_VERSION_ROWS) + " autres (affine avec les filtres)"));
        }
        return box;
    }

    private Node versionRow(SearchResult r, VersionOption v, @Nullable Mod installed) {
        Label type = new Label(v.releaseType().substring(0, 1).toUpperCase() + v.releaseType().substring(1).toLowerCase());
        type.setMinWidth(52);
        type.setAlignment(Pos.CENTER);
        type.setStyle("-fx-font-size: 0.8em; -fx-background-radius: 10; -fx-padding: 1 8; " + switch (v.releaseType().toUpperCase()) {
            case "RELEASE" -> "-fx-background-color: rgba(46,160,67,0.2); -fx-text-fill: #3fb950;";
            case "BETA" -> "-fx-background-color: rgba(210,153,34,0.2); -fx-text-fill: #d29922;";
            default -> "-fx-background-color: rgba(248,81,73,0.2); -fx-text-fill: #f85149;";
        });

        Label name = new Label(v.displayName());
        name.setStyle("-fx-font-weight: bold;");
        name.setMinWidth(0);
        Label sub = new Label(subtitle(v));
        sub.getStyleClass().add("text-muted");
        sub.setStyle("-fx-font-size: 0.85em;");
        sub.setMinWidth(0);
        VBox text = new VBox(1, name, sub);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox row = new HBox(10, type, text);
        row.setAlignment(Pos.CENTER_LEFT);

        if (v.downloads() > 0) row.getChildren().add(stat(Material2AL.CLOUD_DOWNLOAD, ManipulationUtils.formatCount(v.downloads())));
        if (v.date() != null) row.getChildren().add(stat(Material2AL.HISTORY, ManipulationUtils.relativeDate(v.date())));

        boolean isInstalled = installed != null && installed.equals(v.mod());
        Button button;
        if (isInstalled) {
            button = new Button("Installée", FontIcon.of(Material2AL.CHECK, 14));
            button.setDisable(true);
        } else {
            button = new Button(installed != null ? "Remplacer" : "Installer",
                    FontIcon.of(installed != null ? Material2MZ.SWAP_HORIZ : Material2AL.ADD, 14));
            button.getStyleClass().addAll("success", "button-outlined");
            button.setOnAction(e -> host.installVersion(r, v));
        }
        row.getChildren().add(button);
        return row;
    }

    private static String subtitle(VersionOption v) {
        StringBuilder sb = new StringBuilder(v.versionNumber());
        if (!v.gameVersions().isEmpty()) {
            sb.append("  ·  ").append(String.join(", ", v.gameVersions().stream().limit(3).toList()));
            if (v.gameVersions().size() > 3) sb.append(" +").append(v.gameVersions().size() - 3);
        }
        if (!v.loaders().isEmpty()) {
            sb.append("  ·  ").append(String.join(", ", v.loaders().stream().map(Labels::loader).limit(3).toList()));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Small building blocks
    // ------------------------------------------------------------------

    private static Label sourcePill(SearchResult r, Color accent) {
        Label pill = new Label(r.source().getLabel());
        pill.setStyle(String.format("-fx-background-color: rgba(%d,%d,%d,0.18); -fx-text-fill: %s; "
                        + "-fx-background-radius: 10; -fx-padding: 1 8; -fx-font-size: 0.8em; -fx-font-weight: bold;",
                (int) Math.round(accent.getRed() * 255), (int) Math.round(accent.getGreen() * 255),
                (int) Math.round(accent.getBlue() * 255), hex(accent)));
        pill.setMinWidth(Region.USE_PREF_SIZE);
        return pill;
    }

    private static Label tag(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("mod-tag");
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    private static HBox stat(Ikon icon, String text) {
        FontIcon fontIcon = FontIcon.of(icon, 14);
        fontIcon.getStyleClass().add("text-muted");
        Label label = new Label(text);
        label.setTextAlignment(TextAlignment.RIGHT);
        HBox box = new HBox(4, fontIcon, label);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Label muted(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("text-muted");
        return label;
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
    }
}
