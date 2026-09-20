package net.paulem.fjc.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.paulem.fjc.Main;
import ovh.paulem.modrinthapi.Modrinth;
import ovh.paulem.modrinthapi.types.version.ListVersions;
import ovh.paulem.modrinthapi.types.project.Project;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ModrinthUtils {
    // Mémoïse les requêtes Modrinth pour éviter de refaire le même appel réseau
    // plusieurs fois quand plusieurs versions proviennent du même projet.
    private static final ConcurrentHashMap<String, Optional<Project>> PROJECT_CACHE = new ConcurrentHashMap<>();

    @Nullable
    public static Project getModFromSlug(String slug) {
        return PROJECT_CACHE.computeIfAbsent(slug, s -> {
            try {
                return Optional.ofNullable(Main.MODRINTH.getProject(s));
            } catch (URISyntaxException | IOException e) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    /**
     * Équivalent de {@code Modrinth#listVersions} qui ignore les {@code file_type} inconnus de la
     * librairie (ex: "sources-jar"), sinon son enum FileType fait planter tout le parsing.
     */
    public static ListVersions listVersions(String slugOrId) throws IOException, URISyntaxException {
        String encoded = URLEncoder.encode(slugOrId, StandardCharsets.UTF_8).replace("+", "%20");
        HttpURLConnection con = (HttpURLConnection) new URI(Modrinth.MODRINTH_API_LINK + "/project/" + encoded + "/version").toURL().openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", Main.MODRINTH.getUserAgent());

        JsonArray json;
        try (InputStreamReader reader = new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8)) {
            json = JsonParser.parseReader(reader).getAsJsonArray();
        } finally {
            con.disconnect();
        }

        for (JsonElement version : json) {
            JsonElement files = version.getAsJsonObject().get("files");
            if (files == null || !files.isJsonArray()) continue;
            for (JsonElement file : files.getAsJsonArray()) {
                JsonObject fileObj = file.getAsJsonObject();
                JsonElement type = fileObj.get("file_type");
                if (type != null && !type.isJsonNull() && !type.getAsString().endsWith("resource-pack")) {
                    fileObj.remove("file_type");
                }
            }
        }

        return ListVersions.fromJson(json);
    }
}
