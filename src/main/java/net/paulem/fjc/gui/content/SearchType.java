package net.paulem.fjc.gui.content;

import net.paulem.fjc.gui.model.ModCategory;

public enum SearchType {
    URL,
    MODRINTH,
    CURSEFORGE;

    public String toWord() {
        return this.name().substring(0, 1).toUpperCase() + this.name().substring(1).toLowerCase();
    }

    /** The names of this enum and {@link ModCategory} are kept in sync on purpose, so icons/colors stay consistent everywhere. */
    public ModCategory toCategory() {
        return ModCategory.valueOf(name());
    }

    public static SearchType fromString(String type) {
        for (SearchType searchType : SearchType.values()) {
            if (searchType.name().equalsIgnoreCase(type)) {
                return searchType;
            }
        }
        throw new IllegalArgumentException("Invalid SearchType: " + type);
    }
}
