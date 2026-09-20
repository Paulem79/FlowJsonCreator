package net.paulem.fjc.gui.browse;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import net.paulem.fjc.gui.model.ModCategory;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

/** Visual building blocks shared by the search result cards and the modpack cards, so both look alike. */
public final class CardParts {
    public static final int ICON_SIZE = 56;

    private CardParts() {
    }

    /** Rounded icon box; shows {@code fallback} until (or unless) the image at {@code url} loads. */
    public static Node iconBox(@Nullable String url, Ikon fallback) {
        FontIcon placeholder = FontIcon.of(fallback, 28);
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

        if (url != null) {
            IconCache.get(url).thenAccept(icon -> icon.ifPresent(img -> Platform.runLater(() -> {
                view.setImage(img);
                placeholder.setVisible(false);
            })));
        }
        HBox wrapper = new HBox(box);
        wrapper.setAlignment(Pos.TOP_LEFT);
        return wrapper;
    }

    /** Small colored pill naming the source (Modrinth / CurseForge / URL). */
    public static Label sourcePill(ModCategory source) {
        Color accent = source.getColor();
        Label pill = new Label(source.getLabel());
        pill.setStyle(String.format("-fx-background-color: rgba(%d,%d,%d,0.18); -fx-text-fill: %s; "
                        + "-fx-background-radius: 10; -fx-padding: 1 8; -fx-font-size: 0.8em; -fx-font-weight: bold;",
                (int) Math.round(accent.getRed() * 255), (int) Math.round(accent.getGreen() * 255),
                (int) Math.round(accent.getBlue() * 255), hex(accent)));
        pill.setMinWidth(Region.USE_PREF_SIZE);
        return pill;
    }

    public static Label tag(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("mod-tag");
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    public static HBox stat(Ikon icon, String text) {
        FontIcon fontIcon = FontIcon.of(icon, 14);
        fontIcon.getStyleClass().add("text-muted");
        Label label = new Label(text);
        label.setTextAlignment(TextAlignment.RIGHT);
        HBox box = new HBox(4, fontIcon, label);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    public static Label muted(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("text-muted");
        return label;
    }

    public static String hex(Color c) {
        return String.format("#%02x%02x%02x", (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
    }
}
