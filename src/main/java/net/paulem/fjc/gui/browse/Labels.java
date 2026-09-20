package net.paulem.fjc.gui.browse;

import java.util.Map;

/** French display names for Modrinth category / loader slugs. */
public final class Labels {
    private static final Map<String, String> LOADERS = Map.of(
            "fabric", "Fabric", "forge", "Forge", "neoforge", "NeoForge", "quilt", "Quilt");

    private static final Map<String, String> CATEGORIES = Map.ofEntries(
            Map.entry("adventure", "Aventure"), Map.entry("cursed", "Maudit"),
            Map.entry("decoration", "Décoration"), Map.entry("economy", "Économie"),
            Map.entry("equipment", "Équipement"), Map.entry("food", "Nourriture"),
            Map.entry("game-mechanics", "Mécaniques de jeu"), Map.entry("library", "Bibliothèque"),
            Map.entry("magic", "Magie"), Map.entry("management", "Gestion"),
            Map.entry("minigame", "Mini-jeu"), Map.entry("mobs", "Créatures"),
            Map.entry("optimization", "Optimisation"), Map.entry("social", "Social"),
            Map.entry("storage", "Stockage"), Map.entry("technology", "Technologie"),
            Map.entry("transportation", "Transport"), Map.entry("utility", "Utile"),
            Map.entry("worldgen", "Génération du monde"));

    private Labels() {
    }

    public static boolean isLoader(String slug) {
        return LOADERS.containsKey(slug);
    }

    public static String loader(String slug) {
        return LOADERS.getOrDefault(slug, slug);
    }

    public static String category(String slug) {
        String label = CATEGORIES.get(slug);
        if (label != null) return label;
        return slug.isEmpty() ? slug : Character.toUpperCase(slug.charAt(0)) + slug.substring(1);
    }
}
