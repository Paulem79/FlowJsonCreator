package io.github.paulem.fjc;

import io.github.matyrobbrt.curseforgeapi.CurseForgeAPI;
import io.github.matyrobbrt.curseforgeapi.util.CurseForgeException;
import io.github.matyrobbrt.curseforgeapi.util.Pair;
import io.github.paulem.fjc.flow.CurseFileInfo;
import io.github.paulem.fjc.flow.ModsJson;
import io.github.paulem.fjc.utils.APIUtils;
import io.github.paulem.modrinthapi.Modrinth;
import io.github.paulem.modrinthapi.types.version.Version;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static io.github.paulem.fjc.utils.FileUtils.getDownloadedMod;
import static io.github.paulem.fjc.utils.JsonUtils.*;
import static io.github.paulem.fjc.utils.ManipulationUtils.checkOptArg;

public class FlowJsonCreator {

    @Nullable
    public static String CF_API_KEY = null;
    public static CurseForgeAPI cfApi;

    @Nullable
    public static String MC_VERSION = null;
    @Nullable
    public static String MC_LOADER = null;

    public static Modrinth modrinth = new Modrinth(null, "paulem", "FlowJsonCreator", "1.2");

    public static ModsJson jsonContent;

    public static void main(String[] args) throws Exception {
        createJsonFile();
        jsonContent = getJsonContent();

        OptionParser parser = new OptionParser();

        // Define the --cf option
        parser.accepts("cfKey").withOptionalArg().ofType(String.class);
        // Define the --version option
        parser.accepts("version").withOptionalArg().ofType(String.class);
        // Define the --loader option
        parser.accepts("loader").withOptionalArg().ofType(String.class);

        // Parse the command line arguments
        OptionSet options = parser.parse(args);

        CF_API_KEY = checkOptArg(options, "cfKey");
        MC_VERSION = checkOptArg(options, "version");
        MC_LOADER = checkOptArg(options, "loader");

        if(MC_VERSION != null || MC_LOADER != null) {
            System.out.println("--version et --loader ne sont pas très fiables ! Prenez garde :)");
        }

        // Define CF API
        if(CF_API_KEY != null) {
            cfApi = CurseForgeAPI.builder()
                    .apiKey(CF_API_KEY)
                    .build();
        }

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

                    jsonContent.mods.add(getDownloadedMod(jarUrl, fileName));
                    break;

                case 2:
                    // Curseforge
                    if(CF_API_KEY == null) {
                        System.out.println("La clé d'API Curseforge n'est pas définie. Passage en \"mode sans recherche\"");
                        break;
                    }

                    System.out.println("Identifiant du projet : ");
                    try {
                        // Get the project ID
                        int projectId = scanner.nextInt();

                        if(CF_API_KEY == null) {

                            System.out.println("Identifiant du fichier : ");

                            // Get the file ID
                            int fileId = scanner.nextInt();

                            jsonContent.curseFiles.add(new CurseFileInfo(projectId, fileId));

                        } else {

                            @Nullable
                            CurseFileInfo projectInfos = APIUtils.getCFProjectByID(scanner, projectId);

                            if(projectInfos != null) {
                                jsonContent.curseFiles.add(projectInfos);
                            } else {
                                System.out.println("Aucun projet trouvé avec cet identifiant.");
                            }

                        }
                    } catch(InputMismatchException | CurseForgeException e) {
                        throw new RuntimeException(e);
                    }
                    break;

                case 3:
                    System.out.println("Nom du projet : ");
                    String projectName = scanner.nextLine();
                    projectName = scanner.nextLine(); // IDK why

                    // Get the modrinthFile and the project title
                    Pair<Version, String> project = APIUtils.getModrinthProjectByName(scanner, projectName);

                    // Add mod to json
                    jsonContent.mods.add(getDownloadedMod(
                            project.first().files().get(0).url(),
                            project.first().name().contains(project.second()) ? project.first().name() : project.second() + " " + project.first().name())
                    );
                    break;

            }

            saveFile(jsonContent);
        }

        saveFile(jsonContent);
    }
}