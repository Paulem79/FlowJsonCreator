package net.paulem.fjc.gui.browse;

import net.paulem.fjc.flow.mod.Mod;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/** One installable file/version of a mod; {@link #mod()} is what gets written to mods.json. */
public record VersionOption(String id, String displayName, String versionNumber, String releaseType,
                            List<String> gameVersions, List<String> loaders, @Nullable Instant date,
                            long downloads, Mod mod, List<DependencyRef> dependencies) {
}
