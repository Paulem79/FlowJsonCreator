package io.github.paulem.modrinthapi.types.classes.deprecated;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import io.github.paulem.modrinthapi.JsonGetter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Deprecated
public record ModeratorMessage(@NotNull String message, @Nullable String body) {

    @Nullable
    public static ModeratorMessage fromJson(@Nullable JsonElement json) {
        if(json == null || json instanceof JsonNull) return null;

        JsonGetter getter = new JsonGetter(json.getAsJsonObject());

        return new ModeratorMessage(getter.getRequiredString("message"), getter.getString("body"));
    }
}
