package io.github.paulem.fjc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import io.github.matyrobbrt.curseforgeapi.CurseForgeAPI;
import io.github.matyrobbrt.curseforgeapi.request.Requests;
import io.github.matyrobbrt.curseforgeapi.request.Response;
import io.github.matyrobbrt.curseforgeapi.request.query.ModSearchQuery;
import io.github.matyrobbrt.curseforgeapi.schemas.mod.Mod;
import io.github.matyrobbrt.curseforgeapi.util.Constants;
import io.github.matyrobbrt.curseforgeapi.util.CurseForgeException;
import io.github.paulem.fjc.flow.CurseFileInfo;
import io.github.paulem.fjc.flow.UrlMod;
import io.github.paulem.modrinthapi.Modrinth;
import io.github.paulem.modrinthapi.types.project.ProjectResult;
import io.github.paulem.modrinthapi.types.version.Version;

import javax.security.auth.login.LoginException;
import javax.xml.bind.annotation.adapters.HexBinaryAdapter;
import java.io.*;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class FlowJsonCreator {
    private static final Type MODS_TYPE = new TypeToken<ModsJson>() {
    }.getType();

    public static final String CF_API_KEY = Config.CF_API_KEY;
    public static CurseForgeAPI cfApi;

    public static Modrinth modrinth = new Modrinth(null, "paulem", "FlowJsonCreator", "1.1");

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static File modsJson = getActualJar().getParent().resolve("mods.json").toFile();
    public static JsonReader reader;
    public static ModsJson jsonContent;

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, URISyntaxException, LoginException {
        createJsonFile();
        jsonContent = getJsonContent();

        cfApi = CurseForgeAPI.builder()
                .apiKey(CF_API_KEY)
                .build();

        Scanner scanner = new Scanner(System.in);

        int choice = 0;
        while(choice != 4) {

            System.out.println("Ajouter un fichier par URL (1), par Curseforge (2), par Modrinth (3) ou fermer (4) ? ");

            choice = scanner.nextInt();

            switch (choice) {
                case 1:
                    // URL
                    System.out.println("URL du fichier jar : ");

                    String jarUrl = scanner.next();
                    String[] split = jarUrl.split("/");
                    String fileName = split[split.length-1];

                    jsonContent.mods.add(getDownloadMod(jarUrl, fileName));
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
                        // TODO : Not working

                        String projectName = scanner.next();
                        try {
                            Response<List<Mod>> modResponse = cfApi.makeRequest(Requests.searchMods(ModSearchQuery.of(Constants.GameIDs.MINECRAFT)));

                            modResponse.ifPresent(mods -> {
                                Optional<Mod> mod = mods.stream().filter(m -> m.name().equalsIgnoreCase(projectName)).findFirst();

                                if(mod.isEmpty()) return;

                                List<io.github.matyrobbrt.curseforgeapi.schemas.file.File> curseFiles = mod.get().latestFiles();

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
                        } catch (CurseForgeException ex) {
                            throw new RuntimeException(ex);
                        }
                    } catch (CurseForgeException e) {
                        throw new RuntimeException(e);
                    }
                    break;
                case 3:
                    System.out.println("Nom du projet : ");
                    String projectName = scanner.nextLine();
                    projectName = scanner.nextLine(); // IDK why

                    ProjectResult[] results = modrinth.searchProject(projectName, null, null, null, null).hits();

                    for(ProjectResult projectResult : results) {
                        System.out.println(
                                Arrays.asList(results).indexOf(projectResult) + " " + projectResult.title()
                        );
                    }

                    System.out.println("Choisissez un projet par son index : ");
                    int selectedIndex = scanner.nextInt();

                    ProjectResult result = results[selectedIndex];

                    List<Version> listVersions = modrinth.listVersions(result.slug()).versions();

                    for (Version version : listVersions) {
                        System.out.println(
                                listVersions.indexOf(version) + " " + version.name() + " pour Minecraft " + collectionToString(version.gameVersions(), ", ")
                        );
                    }

                    System.out.println("Choisissez un fichier par son index : ");
                    int selectedVersionIndex = scanner.nextInt();

                    Version modrinthFile = listVersions.get(selectedVersionIndex);

                    if(modrinthFile == null) throw new RuntimeException("Impossible de trouver ce fichier !");

                    jsonContent.mods.add(getDownloadMod(
                            modrinthFile.files().get(0).url(),
                            modrinthFile.name().contains(result.title()) ? modrinthFile.name() : result.title() + " " + modrinthFile.name())
                    );
                    break;
            }
            saveFile(jsonContent);
        }
        saveFile(jsonContent);
    }

    public static String collectionToString(List<JsonPrimitive> collection, String append) {
        StringBuilder result = new StringBuilder();

        for(JsonPrimitive item : collection) {
            result.append(item.getAsString()).append(append);
        }

        return result.toString();
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

    public static Path getActualJar() {
        try {
            return Path.of(FlowJsonCreator.class.getProtectionDomain().getCodeSource().getLocation()
                    .toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Read the file and calculate the SHA-1 checksum
     *
     * @param file
     *            the file to read
     * @return the hex representation of the SHA-1 using uppercase chars
     * @throws FileNotFoundException
     *             if the file does not exist, is a directory rather than a
     *             regular file, or for some other reason cannot be opened for
     *             reading
     * @throws IOException
     *             if an I/O error occurs
     * @throws NoSuchAlgorithmException
     *             should never happen
     */
    public static String calcSHA1(File file) throws FileNotFoundException,
            IOException, NoSuchAlgorithmException {

        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        try (InputStream input = new FileInputStream(file)) {

            byte[] buffer = new byte[8192];
            int len = input.read(buffer);

            while (len != -1) {
                sha1.update(buffer, 0, len);
                len = input.read(buffer);
            }

            return new HexBinaryAdapter().marshal(sha1.digest());
        }
    }

    public static void downloadFile(String url, File file) {
        try (BufferedInputStream in = new BufferedInputStream(new URL(url).openStream());
             FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static UrlMod getDownloadMod(String jarUrl, String fileName) throws IOException, NoSuchAlgorithmException {
        File file = getActualJar().getParent().resolve(UUID.randomUUID() + ".jar").toFile();

        downloadFile(jarUrl, file);

        String sha1 = calcSHA1(file);
        long size = file.length();

        file.delete();

        return new UrlMod(fileName, jarUrl, sha1, size);
    }
}