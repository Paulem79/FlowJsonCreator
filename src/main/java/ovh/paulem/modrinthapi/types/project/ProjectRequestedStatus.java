package ovh.paulem.modrinthapi.types.project;

import org.jetbrains.annotations.Nullable;

public enum ProjectRequestedStatus {
    APPROVED("approved"),
    ARCHIVED("archived"),
    DRAFT("draft"),
    UNLISTED("unlisted"),
    PRIVATE("private");

    private final String text;

    ProjectRequestedStatus(final String text) {
        this.text = text;
    }

    @Nullable
    public static ProjectRequestedStatus getEnum(@Nullable String text) {
        if(text == null) return null;
        return ProjectRequestedStatus.valueOf(ProjectRequestedStatus.class, text.toUpperCase());
    }

    @Override
    public String toString() {
        return text;
    }
}