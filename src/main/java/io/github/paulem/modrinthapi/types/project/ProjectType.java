package io.github.paulem.modrinthapi.types.project;

public enum ProjectType {
    MOD("mod"),
    MODPACK("modpack"),
    RESOURCEPACK("resourcepack"),
    SHADER("shader");

    private final String text;

    ProjectType(final String text) {
        this.text = text;
    }

    public static ProjectType getEnum(String text) {
        return ProjectType.valueOf(ProjectType.class, text.toUpperCase());
    }

    @Override
    public String toString() {
        return text;
    }
}
