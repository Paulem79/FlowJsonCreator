package io.github.paulem.modrinthapi;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.paulem.modrinthapi.types.project.Project;
import io.github.paulem.modrinthapi.types.project.SearchProject;
import io.github.paulem.modrinthapi.types.version.ListVersions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class Modrinth {
    public static String MODRINTH_API_LINK = "https://api.modrinth.com/v2";

    private final @Nullable String authorization;
    private final String userAgent;

    public Modrinth(@Nullable String authorization, @NotNull String projectName) {
        this(authorization, null, projectName, null);
    }

    public Modrinth(@Nullable String authorization, @Nullable String githubUsername, @NotNull String projectName, @Nullable String version) {
        this(authorization, githubUsername, projectName, version, null);
    }

    public Modrinth(@Nullable String authorization, @Nullable String githubUsername, @NotNull String projectName, @Nullable String version, @NotNull String mail, @NotNull String mailDomain) {
        this(authorization, githubUsername, projectName, version, mail + "@" + mailDomain);
    }

    public Modrinth(@Nullable String authorization, @Nullable String githubUsername, @NotNull String projectName, @Nullable String version, @Nullable String website) {
        StringBuilder userAgent;
        this.authorization = authorization;

        if(githubUsername != null) {
            userAgent = new StringBuilder(githubUsername + "/" + projectName);
        } else {
            userAgent = new StringBuilder(projectName);
        }

        if(version != null) {
            userAgent.append("/").append(version);
        }

        if(website != null) {
            userAgent.append(" (").append(website).append(")");
        }

        this.userAgent = userAgent.toString();
    }


    public SearchProject searchProject(@Nullable String query, @Nullable String facets, @Nullable String index, @Nullable Integer offset, @Nullable Integer limit) throws IOException, URISyntaxException {
        String parameters = new QueryParametersBuilder()
                .withParameter("query", query, " ", "")
                .withParameter("facets", facets)
                .withParameter("index", index)
                .withParameter("offset", offset)
                .withParameter("limit", limit)
                .build();

        URL url = new URI(MODRINTH_API_LINK + "/search?" + parameters).toURL();

        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");

        JsonObject json = getJSONResponseContent(con, true);

        return SearchProject.fromJson(json);
    }

    public Project getProject(@NotNull String slugOrId) throws URISyntaxException, IOException {
        URL url = new URI(MODRINTH_API_LINK + "/project/" + slugOrId).toURL();

        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");

        JsonObject json = getJSONResponseContent(con, true);

        return Project.fromJson(json);
    }

    public ListVersions listVersions(@NotNull Project project) throws URISyntaxException, IOException {
        return listVersions(project.id());
    }

    public ListVersions listVersions(@NotNull String slugOrId) throws URISyntaxException, IOException {
        URL url = new URI(MODRINTH_API_LINK + "/project/" + slugOrId + "/version").toURL();

        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");

        JsonArray json = getJSONResponseContentArray(con, true);

        return ListVersions.fromJson(json);
    }

    private String getResponseContent(HttpURLConnection con, boolean disconnect) throws IOException {
        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream())
        );

        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();

        if(disconnect) con.disconnect();

        return content.toString();
    }

    private JsonObject getJSONResponseContent(HttpURLConnection con, boolean disconnect) throws IOException {
        return JsonParser.parseString(getResponseContent(con, disconnect)).getAsJsonObject();
    }

    private JsonArray getJSONResponseContentArray(HttpURLConnection con, boolean disconnect) throws IOException {
        return JsonParser.parseString(getResponseContent(con, disconnect)).getAsJsonArray();
    }

    public String getUserAgent() {
        return userAgent;
    }

    public @Nullable String getAuthorization() {
        return authorization;
    }
}
