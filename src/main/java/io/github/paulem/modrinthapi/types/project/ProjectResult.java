package io.github.paulem.modrinthapi.types.project;

import com.google.gson.JsonObject;
import io.github.paulem.modrinthapi.JsonGetter;
import io.github.paulem.modrinthapi.types.enums.MonetizationStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record ProjectResult(@NotNull String slug, @NotNull String title,
                            @NotNull String description, @Nullable List<String> categories,
                            @NotNull io.github.paulem.modrinthapi.types.project.ProjectSide clientSide, @NotNull io.github.paulem.modrinthapi.types.project.ProjectSide serverSide,
                            @NotNull ProjectType projectType, @NotNull Integer downloads,
                            @Nullable String iconUrl, @Nullable Integer color,
                            @Nullable String threadId, @Nullable MonetizationStatus monetizationStatus,
                            @NotNull String projectId, @NotNull String author,
                            @Nullable List<String> displayCategories, @NotNull List<String> versions,
                            @NotNull Integer follows, @NotNull String dateCreated,
                            @NotNull String dateModified, @Nullable String latestVersion,
                            @NotNull String license, @Nullable List<String> gallery,
                            @Nullable String featuredGallery) {

    public static ProjectResult fromJson(JsonObject hit) {
        JsonGetter getter = new JsonGetter(hit);

        return new ProjectResult(
                // NotNull
                getter.getRequiredString("slug"),
                getter.getRequiredString("title"),
                getter.getRequiredString("description"),

                JsonGetter.getOneTypeListFromJsonArray(
                        new String[0],
                        hit.getAsJsonArray("categories")
                ),

                // NotNull
                io.github.paulem.modrinthapi.types.project.ProjectSide.getEnum(getter.getRequiredString("client_side")),
                ProjectSide.getEnum(getter.getRequiredString("server_side")),
                ProjectType.getEnum(getter.getRequiredString("project_type")),
                getter.getRequiredInt("downloads"),

                getter.getString("icon_url"),
                getter.getInt("color"),
                getter.getString("thread_id"),
                MonetizationStatus.getEnum(getter.getString("monetization_status")),

                // NotNull
                getter.getRequiredString("project_id"),
                getter.getRequiredString("author"),

                JsonGetter.getOneTypeListFromJsonArray(
                        new String[0],
                        hit.getAsJsonArray("display_categories")
                ),

                // NotNull
                JsonGetter.getOneTypeListFromJsonArray(
                        new String[0],
                        hit.getAsJsonArray("versions")
                ),
                getter.getRequiredInt("follows"),
                getter.getRequiredString("date_created"),
                getter.getRequiredString("date_modified"),

                getter.getString("latest_version"),

                // NotNull
                getter.getRequiredString("license"),

                JsonGetter.getOneTypeListFromJsonArray(
                        new String[0],
                        hit.getAsJsonArray("gallery")
                ),
                getter.getString("featured_gallery")
        );
    }
}