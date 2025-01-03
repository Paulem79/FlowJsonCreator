package io.github.paulem.modrinthapi.types.classes;

import com.google.gson.JsonElement;
import io.github.paulem.modrinthapi.JsonGetter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record GalleryImage(@NotNull String url,
                           @NotNull String created,
                           @NotNull Boolean featured,
                           @Nullable String title,
                           @Nullable String description,
                           @Nullable Integer ordering) {
    public static GalleryImage fromJson(JsonElement json) {
        JsonGetter getter = new JsonGetter(json.getAsJsonObject());

        return new GalleryImage(
                getter.getRequiredString("url"),
                getter.getRequiredString("created"),
                getter.getRequiredBoolean("featured"),

                getter.getString("title"),
                getter.getString("description"),
                getter.getInt("ordering")
        );
    }
}
