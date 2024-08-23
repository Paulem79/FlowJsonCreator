package io.github.paulem.fjc.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import io.github.paulem.fjc.flow.ModsJson;

import java.io.*;
import java.lang.reflect.Type;

import static io.github.paulem.fjc.utils.FileUtils.getActualJar;

public class JsonUtils {
    private static final Type MODS_TYPE = new TypeToken<ModsJson>() {
    }.getType();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File modsJson = getActualJar().getParent().resolve("mods.json").toFile();
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
}
