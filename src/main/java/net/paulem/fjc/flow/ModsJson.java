package net.paulem.fjc.flow;

import com.google.gson.reflect.TypeToken;
import net.paulem.fjc.flow.mod.CurseForgeMod;
import net.paulem.fjc.flow.mod.Mod;
import net.paulem.fjc.flow.mod.ModrinthMod;
import net.paulem.fjc.flow.mod.UrlMod;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ModsJson {
    public static final Type MODS_TYPE = new TypeToken<ModsJson>() {
    }.getType();

    public List<UrlMod> mods;
    public List<CurseForgeMod> curseFiles;
    public List<ModrinthMod> modrinthMods;

    public ModsJson() {
        this.mods = new CopyOnWriteArrayList<>();
        this.curseFiles = new CopyOnWriteArrayList<>();
        this.modrinthMods = new CopyOnWriteArrayList<>();
    }

    /**
     * Gson bypasses this constructor's field assignments for keys present in the JSON,
     * but leaves them untouched (possibly null) for missing/malformed keys. Call this
     * right after deserialization to guarantee every list is a real, mutable, thread-safe
     * collection, whatever the source JSON looked like.
     */
    public void normalize() {
        if (this.mods == null) this.mods = new CopyOnWriteArrayList<>();
        else if (!(this.mods instanceof CopyOnWriteArrayList<?>)) this.mods = new CopyOnWriteArrayList<>(this.mods);

        if (this.curseFiles == null) this.curseFiles = new CopyOnWriteArrayList<>();
        else if (!(this.curseFiles instanceof CopyOnWriteArrayList<?>)) this.curseFiles = new CopyOnWriteArrayList<>(this.curseFiles);

        if (this.modrinthMods == null) this.modrinthMods = new CopyOnWriteArrayList<>();
        else if (!(this.modrinthMods instanceof CopyOnWriteArrayList<?>)) this.modrinthMods = new CopyOnWriteArrayList<>(this.modrinthMods);
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

    /** Total number of mods across all categories. */
    public int size() {
        return this.mods.size() + this.curseFiles.size() + this.modrinthMods.size();
    }
}
