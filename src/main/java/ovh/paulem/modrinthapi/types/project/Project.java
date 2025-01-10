package ovh.paulem.modrinthapi.types.project;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ovh.paulem.modrinthapi.JsonGetter;
import ovh.paulem.modrinthapi.types.classes.GalleryImage;
import ovh.paulem.modrinthapi.types.classes.deprecated.ModeratorMessage;

import java.util.List;

public record Project(
        @NotNull String slug,
        @NotNull String title,
        @NotNull String description,
        @NotNull List<String> categories,
        @NotNull ProjectSide clientSide,
        @NotNull ProjectSide serverSide,
        @NotNull String body,
        @NotNull ProjectStatus status,

        @Nullable ProjectRequestedStatus requestedStatus,
        @Nullable List<String> additionalCategories,
        @Nullable String issuesUrl,
        @Nullable String sourceUrl,
        @Nullable String wikiUrl,
        @Nullable String discordUrl,
        @Nullable List<ProjectDonationUrl> donationUrls,

        @NotNull ProjectType projectType,
        @NotNull Integer downloads,

        @Nullable String iconUrl,
        @Nullable Integer color,
        @Nullable String threadId,
        @Nullable String monetizationStatus,

        @NotNull String id,
        @NotNull String team,

        @Deprecated
        @Nullable String bodyUrl,
        @Deprecated
        @Nullable ModeratorMessage moderatorMessage,

        @NotNull String published,
        @NotNull String updated,

        @Nullable String approved,
        @Nullable String queued,

        @NotNull Integer followers,

        @Nullable ProjectLicense license,
        @Nullable List<String> versions,
        @Nullable List<String> gameVersions,
        @Nullable List<String> loaders,
        @Nullable List<GalleryImage> gallery) {

    public static Project fromJson(JsonObject hit) {
        JsonGetter getter = new JsonGetter(hit);

        return new Project(
                // NotNull
                getter.getRequiredString("slug"),
                getter.getRequiredString("title"),
                getter.getRequiredString("description"),
                JsonGetter.getOneTypeListFromJsonArray(
                        new String[0],
                        hit.getAsJsonArray("categories")
                ),
                ProjectSide.getEnum(getter.getRequiredString("client_side")),
                ProjectSide.getEnum(getter.getRequiredString("server_side")),
                getter.getRequiredString("body"),
                ProjectStatus.getEnum(getter.getRequiredString("status")),

                ProjectRequestedStatus.getEnum(getter.getString("requested_status")),
                JsonGetter.getOneTypeListFromJsonArray(
                        new String[0],
                        hit.getAsJsonArray("additional_categories")
                ),
                getter.getString("issues_url"),
                getter.getString("source_url"),
                getter.getString("wiki_url"),
                getter.getString("discord_url"),
                JsonGetter.doOnListElements(
                        hit.getAsJsonArray("donation_urls"),
                        ProjectDonationUrl::fromJson
                ),

                ProjectType.getEnum(getter.getRequiredString("project_type")),
                getter.getRequiredInt("downloads"),

                getter.getString("icon_url"),
                getter.getInt("color"),
                getter.getString("thread_id"),
                getter.getString("monetization_status"),

                getter.getRequiredString("id"),
                getter.getRequiredString("team"),

                getter.getString("body_url"),
                ModeratorMessage.fromJson(getter.get("moderator_message")),

                getter.getRequiredString("published"),
                getter.getRequiredString("updated"),

                getter.getString("approved"),
                getter.getString("queued"),

                getter.getRequiredInt("followers"),

                ProjectLicense.fromJson(getter.get("license")),
                JsonGetter.getOneTypeListFromJsonArray(
                        new String[0],
                        hit.getAsJsonArray("versions")
                ),
                JsonGetter.getOneTypeListFromJsonArray(
                        new String[0],
                        hit.getAsJsonArray("game_versions")
                ),
                JsonGetter.getOneTypeListFromJsonArray(
                        new String[0],
                        hit.getAsJsonArray("loaders")
                ),
                JsonGetter.doOnListElements(
                        hit.getAsJsonArray("gallery"),
                        GalleryImage::fromJson
                )
        );
    }
}
