package net.paulem.fjc.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import net.paulem.fjc.flow.mod.CurseForgeManifest;
import net.paulem.fjc.flow.mod.CurseForgeMod;
import net.paulem.fjc.flow.mod.Mod;
import net.paulem.fjc.flow.ModsJson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.Type;

import static net.paulem.fjc.Main.jsonContent;
import static net.paulem.fjc.Main.addListItem;
import static net.paulem.fjc.Main.removeListItem;
import static net.paulem.fjc.utils.FileUtils.getActualJar;

public class JsonUtils {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final File modsJson = getActualJar().getParent().resolve("mods.json").toFile();
    private static JsonReader reader;

    public static final Type CURSE_FORGE_MANIFEST_TYPE = new TypeToken<CurseForgeManifest>() {}.getType();

    public static void createJsonFile() throws IOException {
        boolean created = modsJson.createNewFile();
        if(created) {
            saveFile(new ModsJson());
            System.out.println("Le fichier " + modsJson.getName() + " a bien été créé !");
        }

        reader = new JsonReader(new FileReader(modsJson));
    }

    public static ModsJson getJsonContent() {
        System.out.println("Obtention du contenu de " + modsJson.getName() + " en cours...");
        return GSON.fromJson(reader, ModsJson.MODS_TYPE);
    }

    public static void saveFile(ModsJson content) throws IOException {
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
            addListItem(mod); // mise à jour incrémentale
            saveFile(jsonContent);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Remove a mod from the json file, update the list and save the file.
     * @param item The mod to remove.
     */
    public static void removeMod(String item) {
        Mod mod = getModFromString(item);
        jsonContent.removeMod(mod);

        try {
            removeListItem(mod, item); // mise à jour incrémentale
            saveFile(jsonContent);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get a mod from a string.
     * @param item The string to get the mod from.
     * @return The mod.
     */
    @Nullable
    public static Mod getModFromString(@NotNull String item) {
        if(item.startsWith("CF ")) {
            String[] split = item.split(" - ");
            int projectID = Integer.parseInt(split[1]);
            int fileID = Integer.parseInt(split[2]);
            return jsonContent.curseFiles.stream()
                    .filter(mod -> mod.projectID() == projectID && mod.fileID() == fileID)
                    .findFirst()
                    .orElse(null);
        } else if(item.startsWith("MOD ")) {
            String[] split = item.split(" - ");
            String projectReference = split[1];
            String versionNumber = split[2];
            return jsonContent.modrinthMods.stream()
                    .filter(mod -> mod.getProjectReference().equals(projectReference) && mod.getVersionNumber().equals(versionNumber))
                    .findFirst()
                    .orElse(null);
        } else {
            return jsonContent.mods.stream()
                    .filter(mod -> mod.name().equals(item))
                    .findFirst()
                    .orElse(null);
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
     * Add mods from a CurseForge manifest.json file to the mods.json.
     * @param file The manifest.json file.
     * @throws IOException if an I/O error occurs.
     */
    public static void addCurseForgeManifest(File file) throws IOException {
        CurseForgeManifest manifest = parseCurseForgeManifest(file);
        for (CurseForgeMod mod : manifest.files()) {
            addMod(mod);
        }
    }
}
