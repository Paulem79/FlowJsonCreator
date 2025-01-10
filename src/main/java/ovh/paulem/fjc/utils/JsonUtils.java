package ovh.paulem.fjc.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import ovh.paulem.fjc.flow.Mod;
import ovh.paulem.fjc.flow.ModsJson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.Type;

import static ovh.paulem.fjc.gui.Main.jsonContent;
import static ovh.paulem.fjc.gui.Main.updateList;
import static ovh.paulem.fjc.utils.FileUtils.getActualJar;

public class JsonUtils {
    private static final Type MODS_TYPE = new TypeToken<ModsJson>() {
    }.getType();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final File modsJson = getActualJar().getParent().getParent().resolve("mods.json").toFile();
    private static JsonReader reader;

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
        return GSON.fromJson(reader, MODS_TYPE);
    }

    public static void saveFile(ModsJson content) throws IOException {
        System.out.println("Sauvegarde dans le json " + modsJson.getName() + " en cours...");

        try (Writer writer = new FileWriter(modsJson)) {
            GSON.toJson(content, MODS_TYPE, writer);
        }
    }

    /**
     * Add a mod to the json file, update the list and save the file.
     * @param mod The mod to add.
     */
    public static void addMod(Mod mod) {
        jsonContent.addMod(mod);

        try {
            updateList();
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
            updateList();
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
}
