package net.paulem.fjc.gui.browse;

import org.jetbrains.annotations.Nullable;

/** A mod another version relies on. Same source as the version that declares it. */
public record DependencyRef(String projectId, @Nullable String versionId, boolean required) {
}
