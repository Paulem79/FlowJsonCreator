package io.github.paulem.fjc.flow;

import java.util.List;

public class ModsJson {
    public List<UrlMod> mods;
    public List<CurseFileInfo> curseFiles;

    public ModsJson() {
        this.mods = List.of();
        this.curseFiles = List.of();
    }
}
