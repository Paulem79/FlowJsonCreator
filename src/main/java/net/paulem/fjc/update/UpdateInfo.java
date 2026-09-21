package net.paulem.fjc.update;

import net.paulem.fjc.flow.mod.Mod;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * A newer version of a mod of the modpack. {@code changelog} is null when it was not fetched with the version
 * (CurseForge serves it separately): see {@link UpdateChecker#loadChangelog(UpdateInfo)}.
 */
public record UpdateInfo(Mod current, Mod target, String currentLabel, String targetLabel, String releaseType,
                         @Nullable Instant date, @Nullable String changelog) {
}
