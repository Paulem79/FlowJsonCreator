package ovh.paulem.fjc.flow;

import java.util.List;

public class ModsJson {
    public List<UrlMod> mods;
    public List<CurseForgeMod> curseFiles;
    public List<ModrinthMod> modrinthMods;

    public ModsJson() {
        this.mods = List.of();
        this.curseFiles = List.of();
        this.modrinthMods = List.of();
    }

    public void addMod(Mod mod) {
        if (mod instanceof UrlMod urlMod) {
            this.mods.add(urlMod);
        } else if (mod instanceof CurseForgeMod curseForgeMod) {
            this.curseFiles.add(curseForgeMod);
        } else if (mod instanceof ModrinthMod modrinthMod) {
            this.modrinthMods.add(modrinthMod);
        }
    }

    public void removeMod(Mod mod) {
        if (mod instanceof UrlMod urlMod) {
            this.mods.remove(urlMod);
        } else if (mod instanceof CurseForgeMod curseForgeMod) {
            this.curseFiles.remove(curseForgeMod);
        } else if (mod instanceof ModrinthMod modrinthMod) {
            this.modrinthMods.remove(modrinthMod);
        }
    }
}
