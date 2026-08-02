package net.paulem.fjc.gui.model;

import javafx.scene.paint.Color;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.material2.Material2AL;

/**
 * The three sources a mod can come from, each with its own display label, icon and accent color.
 */
public enum ModCategory {
    MODRINTH("Modrinth", Material2AL.EXTENSION, Color.web("#1bd96a")),
    CURSEFORGE("CurseForge", Material2AL.CLOUD_DOWNLOAD, Color.web("#f16436")),
    URL("URL", Material2AL.LINK, Color.web("#5aa9e6"));

    private final String label;
    private final Ikon icon;
    private final Color color;

    ModCategory(String label, Ikon icon, Color color) {
        this.label = label;
        this.icon = icon;
        this.color = color;
    }

    public String getLabel() {
        return label;
    }

    public Ikon getIcon() {
        return icon;
    }

    public Color getColor() {
        return color;
    }
}
