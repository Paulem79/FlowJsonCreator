package net.paulem.fjc.flow;

import com.google.gson.reflect.TypeToken;
import net.paulem.fjc.flow.mod.CurseForgeMod;
import net.paulem.fjc.flow.mod.Mod;
import net.paulem.fjc.flow.mod.ModrinthMod;
import net.paulem.fjc.flow.mod.UrlMod;

import java.lang.reflect.Type;
import java.util.List;

public class ModsJson {
    public static final Type MODS_TYPE = new TypeToken<ModsJson>() {
    }.getType();

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
