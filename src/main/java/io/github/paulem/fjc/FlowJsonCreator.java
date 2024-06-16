package io.github.paulem.fjc;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import io.github.matyrobbrt.curseforgeapi.CurseForgeAPI;
import io.github.matyrobbrt.curseforgeapi.request.Requests;
import io.github.matyrobbrt.curseforgeapi.request.Response;
import io.github.matyrobbrt.curseforgeapi.schemas.mod.Mod;
import io.github.matyrobbrt.curseforgeapi.util.CurseForgeException;
import io.github.paulem.fjc.flow.CurseFileInfo;

import javax.security.auth.login.LoginException;
import java.io.*;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Scanner;

public class FlowJsonCreator {
    private static final Type MODS_TYPE = new TypeToken<ModsJson>() {
    }.getType();

    public static final String CF_API_KEY = "API_KEY"; // Not yet...
    public static CurseForgeAPI cfApi;

    public static final Gson GSON = new Gson();
    public static File modsJson;
    public static JsonReader reader;
    public static ModsJson jsonContent;

    static {
        try {
            modsJson = getActualJar().getParent().resolve("mods.json").toFile();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws IOException, LoginException {
        createJsonFile();
        jsonContent = getJsonContent();

        cfApi = CurseForgeAPI.builder()
                .apiKey(CF_API_KEY)
                .build();

        Scanner scanner = new Scanner(System.in);
        System.out.println("Ajouter un fichier par URL (1) ou par Curseforge (2) ? ");

        int choice = scanner.nextInt();

        switch (choice) {
            case 1:
                // URL
                break;
            case 2:
                // Curseforge
                System.out.println("Nom du projet/Identifiant : ");
                try {
                    int projectId = scanner.nextInt();
                    Response<Mod> modResponse = cfApi.makeRequest(Requests.getMod(projectId));

                    modResponse.ifPresent(mod -> {
                        List<io.github.matyrobbrt.curseforgeapi.schemas.file.File> curseFiles = mod.latestFiles();

                        for (io.github.matyrobbrt.curseforgeapi.schemas.file.File curseFile : curseFiles) {
                            System.out.println(
                                    curseFiles.indexOf(curseFile) + " " + curseFile.displayName() + " pour Minecraft " + collectionToString(curseFile.gameVersions(), ", ")
                            );
                        }

                        System.out.println("Choisissez un fichier par son index : ");
                        int selectedIndex = scanner.nextInt();

                        io.github.matyrobbrt.curseforgeapi.schemas.file.File curseFile = curseFiles.get(selectedIndex);

                        if(curseFile == null) throw new RuntimeException("Impossible de trouver ce fichier !");

                        jsonContent.curseFiles.add(new CurseFileInfo(curseFile.modId(), curseFile.id()));
                    });
                } catch(InputMismatchException e) {
                    String projectName = scanner.nextLine();
                } catch (CurseForgeException e) {
                    throw new RuntimeException(e);
                }
                break;
        }
    }

    public static String collectionToString(Collection<String> collection, String append) {
        StringBuilder result = new StringBuilder();

        for(String item : collection) {
            result.append(item).append(append);
        }

        return result.toString();
    }

    public static void createJsonFile() throws IOException {
        boolean created = modsJson.createNewFile();
        if(created) {
            saveFile(new ModsJson());
            System.out.println("Created json file !");
        }


        reader = new JsonReader(new FileReader(modsJson));
    }

    public static ModsJson getJsonContent() {
        System.out.println("Retrieving json content...");
        return GSON.fromJson(reader, MODS_TYPE);
    }

    public static void saveFile(ModsJson content) throws IOException {
        System.out.println("Saving to json...");

        try (Writer writer = new FileWriter(modsJson)) {
            GSON.toJson(content, writer);
        }
    }

    public static Path getActualJar() throws URISyntaxException {
        return Path.of(FlowJsonCreator.class.getProtectionDomain().getCodeSource().getLocation()
                .toURI());
    }
}