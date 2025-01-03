package io.github.paulem.modrinthapi.types.project;

import org.jetbrains.annotations.Nullable;

public enum ProjectStatus {
    APPROVED("approved"),
    LISTED("listed"),
    ARCHIVED("archived"),
    REJECTED("rejected"),
    DRAFT("draft"),
    UNLISTED("unlisted"),
    PROCESSING("processing"),
    WITHHELD("withheld"),
    SCHEDULED("scheduled"),
    PRIVATE("private"),
    UNKNOWN("unknown");

    private final String text;

    ProjectStatus(final String text) {
        this.text = text;
    }

    @Nullable
    public static ProjectStatus getEnum(@Nullable String text) {
        if(text == null) return null;
        return ProjectStatus.valueOf(ProjectStatus.class, text.toUpperCase());
    }

    @Override
    public String toString() {
        return text;
    }
}
