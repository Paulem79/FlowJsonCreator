package net.paulem.fjc.gui.browse;

import io.github.matyrobbrt.curseforgeapi.request.query.ModSearchQuery;

/** Sort orders offered in the search bar, with the matching server-side index/field of each source. */
public enum SortField {
    RELEVANCE("Pertinence", "relevance", ModSearchQuery.SortField.FEATURED),
    DOWNLOADS("Téléchargements", "downloads", ModSearchQuery.SortField.TOTAL_DOWNLOADS),
    FOLLOWS("Popularité", "follows", ModSearchQuery.SortField.POPULARITY),
    UPDATED("Dernière mise à jour", "updated", ModSearchQuery.SortField.LAST_UPDATED),
    NEWEST("Plus récents", "newest", ModSearchQuery.SortField.RELEASED_DATE),
    NAME("Nom (A → Z)", "relevance", ModSearchQuery.SortField.NAME);

    private final String label;
    private final String modrinthIndex;
    private final ModSearchQuery.SortField curseForgeField;

    SortField(String label, String modrinthIndex, ModSearchQuery.SortField curseForgeField) {
        this.label = label;
        this.modrinthIndex = modrinthIndex;
        this.curseForgeField = curseForgeField;
    }

    public String label() {
        return label;
    }

    public String modrinthIndex() {
        return modrinthIndex;
    }

    public ModSearchQuery.SortField curseForgeField() {
        return curseForgeField;
    }

    @Override
    public String toString() {
        return label;
    }
}
