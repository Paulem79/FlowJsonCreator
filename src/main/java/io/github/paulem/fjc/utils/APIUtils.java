package io.github.paulem.fjc.utils;

import io.github.matyrobbrt.curseforgeapi.request.Requests;
import io.github.matyrobbrt.curseforgeapi.request.Response;
import io.github.matyrobbrt.curseforgeapi.schemas.file.File;
import io.github.matyrobbrt.curseforgeapi.schemas.mod.Mod;
import io.github.matyrobbrt.curseforgeapi.util.CurseForgeException;
import io.github.matyrobbrt.curseforgeapi.util.Pair;
import io.github.paulem.fjc.flow.CurseFileInfo;
import io.github.paulem.modrinthapi.types.project.ProjectResult;
import io.github.paulem.modrinthapi.types.version.Version;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.paulem.fjc.FlowJsonCreator.*;
import static io.github.paulem.fjc.utils.ManipulationUtils.collectionToString;

public class APIUtils {

    @Nullable
    public static CurseFileInfo getCFProjectByID(Scanner scanner, int projectId) throws CurseForgeException {
        AtomicReference<@Nullable CurseFileInfo> curseFileInfo = new AtomicReference<>();

        Response<Mod> modResponse = cfApi.makeRequest(Requests.getMod(projectId));

        // If this mod exists
        modResponse.ifPresent(mod -> {

            // Get latest files in project
            List<File> curseFiles = mod
                    .latestFiles()
                    .stream()
                    .filter(curseFile -> {
                        // Get the game infos
                        String gameInfos = collectionToString(curseFile.gameVersions(), ", ").toLowerCase();

                        return ((MC_VERSION == null || gameInfos.contains(MC_VERSION))
                                && (MC_LOADER == null || gameInfos.contains(MC_LOADER)));
                    })
                    .toList();

            for (io.github.matyrobbrt.curseforgeapi.schemas.file.File curseFile : curseFiles) {
                // Show files to user
                String gameInfos = collectionToString(curseFile.gameVersions(), ", ");

                System.out.println(
                        curseFiles.indexOf(curseFile) + " " + curseFile.displayName() + " pour Minecraft " + gameInfos
                );
            }

            System.out.println("Choisissez un fichier par son index : ");
            int selectedIndex = scanner.nextInt();

            // Get the file by his index
            io.github.matyrobbrt.curseforgeapi.schemas.file.File curseFile = curseFiles.get(selectedIndex);

            if(curseFile == null) throw new RuntimeException("Impossible de trouver ce fichier !");

            curseFileInfo.set(new CurseFileInfo(curseFile.modId(), curseFile.id()));
        });

        return curseFileInfo.get();
    }

    public static Pair<Version, String> getModrinthProjectByName(Scanner scanner, String projectName) throws IOException, URISyntaxException {
        // Search the project
        List<ProjectResult> results = Arrays.stream(modrinth
                        .searchProject(projectName, null, null, null, null)
                        .hits())
                .filter(hit ->
                        (MC_VERSION == null || hit.versions().contains(MC_VERSION)) &&
                                (MC_LOADER == null || (hit.categories() != null && hit.categories().contains(MC_LOADER)))
                )
                .toList();

        for(ProjectResult projectResult : results) {
            // Show results
            System.out.println(
                    results.indexOf(projectResult) + " " + projectResult.title()
            );
        }

        System.out.println("Choisissez un projet par son index : ");
        int selectedIndex = scanner.nextInt();

        ProjectResult result = results.get(selectedIndex);

        List<Version> listVersions = modrinth.listVersions(result.slug()).versions();

        for (Version version : listVersions) {
            System.out.println(
                    listVersions.indexOf(version) + " " + version.name() + " pour Minecraft " + collectionToString(version.gameVersions(), ", ")
            );
        }

        System.out.println("Choisissez un fichier par son index : ");
        int selectedVersionIndex = scanner.nextInt();

        Version modrinthFile = listVersions.get(selectedVersionIndex);

        if(modrinthFile == null) {
            throw new RuntimeException("Impossible de trouver ce fichier !");
        }

        return Pair.of(modrinthFile, result.title());
    }
}
