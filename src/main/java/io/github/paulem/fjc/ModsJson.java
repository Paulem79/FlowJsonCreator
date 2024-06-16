package io.github.paulem.fjc;

import io.github.paulem.fjc.flow.CurseFileInfo;
import io.github.paulem.fjc.flow.Mod;

import java.util.List;

public class ModsJson {
    public List<Mod> mods;
    public List<CurseFileInfo> curseFiles;

    public ModsJson(List<Mod> mods, List<CurseFileInfo> curseFiles) {
        this.mods = mods;
        this.curseFiles = curseFiles;
    }

    public ModsJson() {
        this.mods = List.of();
        this.curseFiles = List.of();
    }
}
