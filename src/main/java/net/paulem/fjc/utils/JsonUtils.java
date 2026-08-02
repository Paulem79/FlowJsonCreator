package net.paulem.fjc.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import net.paulem.fjc.flow.mod.CurseForgeManifest;
import net.paulem.fjc.flow.mod.Mod;
import net.paulem.fjc.flow.ModsJson;

import java.io.*;
import java.lang.reflect.Type;
import java.util.List;

import static net.paulem.fjc.Main.jsonContent;
import static net.paulem.fjc.Main.modsListPanel;
import static net.paulem.fjc.utils.FileUtils.getActualJar;

public class JsonUtils {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final File modsJson = getActualJar().getParent().resolve("mods.json").toFile();

    public static final Type CURSE_FORGE_MANIFEST_TYPE = new TypeToken<CurseForgeManifest>() {}.getType();

    public static void createJsonFile() throws IOException {
        boolean created = modsJson.createNewFile();
        if(created) {
            saveFile(new ModsJson());
            System.out.println("Le fichier " + modsJson.getName() + " a bien été créé !");
        }
    }

    public static ModsJson getJsonContent() throws IOException {
        System.out.println("Obtention du contenu de " + modsJson.getName() + " en cours...");
        try (JsonReader reader = new JsonReader(new FileReader(modsJson))) {
            ModsJson content = GSON.fromJson(reader, ModsJson.MODS_TYPE);
            if (content == null) content = new ModsJson();
            content.normalize();
            return content;
        }
    }

    public static synchronized void saveFile(ModsJson content) throws IOException {
        System.out.println("Sauvegarde dans le json " + modsJson.getName() + " en cours...");

        try (Writer writer = new FileWriter(modsJson)) {
            GSON.toJson(content, ModsJson.MODS_TYPE, writer);
        }
    }

    /**
     * Add a mod to the json file, update the list and save the file.
     * @param mod The mod to add.
     */
    public static void addMod(Mod mod) {
        jsonContent.addMod(mod);

        try {
            if (modsListPanel != null) modsListPanel.addMod(mod); // mise à jour incrémentale
            saveFile(jsonContent);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Add several mods at once, saving the file only once at the end instead of once per mod.
     * Used for bulk operations like importing a CurseForge manifest with hundreds of entries.
     * @param mods The mods to add.
     */
    public static void addMods(List<? extends Mod> mods) {
        for (Mod mod : mods) {
            jsonContent.addMod(mod);
            if (modsListPanel != null) modsListPanel.addMod(mod);
        }

        try {
            saveFile(jsonContent);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Remove a mod from the json file, update the list and save the file.
     * @param mod The mod to remove.
     */
    public static void removeMod(Mod mod) {
        jsonContent.removeMod(mod);

        try {
            if (modsListPanel != null) modsListPanel.removeMod(mod); // mise à jour incrémentale
            saveFile(jsonContent);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parse a CurseForge manifest.json file.
     * @param file The manifest.json file.
     * @return The parsed CurseForgeManifest.
     * @throws IOException if an I/O error occurs.
     */
    public static CurseForgeManifest parseCurseForgeManifest(File file) throws IOException {
        try (JsonReader reader = new JsonReader(new FileReader(file))) {
            return GSON.fromJson(reader, CURSE_FORGE_MANIFEST_TYPE);
        }
    }

    /**
     * Add mods from a CurseForge manifest.json file to the mods.json, in a single bulk save.
     * @param file The manifest.json file.
     * @throws IOException if an I/O error occurs.
     */
    public static void addCurseForgeManifest(File file) throws IOException {
        CurseForgeManifest manifest = parseCurseForgeManifest(file);
        addMods(manifest.files());
    }
}
