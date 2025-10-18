package net.paulem.fjc.gui.content;

public enum SearchType {
    URL,
    MODRINTH,
    CURSEFORGE;

    public String toWord() {
        return this.name().substring(0, 1).toUpperCase() + this.name().substring(1).toLowerCase();
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
