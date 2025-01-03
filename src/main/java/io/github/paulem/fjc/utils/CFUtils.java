package io.github.paulem.fjc.utils;

import io.github.matyrobbrt.curseforgeapi.schemas.file.File;
import io.github.matyrobbrt.curseforgeapi.schemas.mod.Mod;
import io.github.matyrobbrt.curseforgeapi.util.CurseForgeException;
import org.jetbrains.annotations.Nullable;

import static io.github.paulem.fjc.gui.Main.cfApi;

public class CFUtils {
    @Nullable
    public static Mod getModFromId(int modId) {
        try {
            return cfApi.getHelper().getMod(modId).orElse(null);
        } catch (CurseForgeException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    public static File getFileFromId(int modId, int fileId) {
        try {
            return cfApi.getHelper().getModFile(modId, fileId).orElse(null);
        } catch (CurseForgeException e) {
            throw new RuntimeException(e);
        }
    }
}
