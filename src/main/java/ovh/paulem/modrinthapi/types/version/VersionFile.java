package ovh.paulem.modrinthapi.types.version;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ovh.paulem.modrinthapi.JsonGetter;

import java.util.Map;

public record VersionFile(@NotNull VersionFileHashes hashes, @NotNull String url,
                          @NotNull String filename, @NotNull Boolean primary,
                          @NotNull Integer size, @Nullable FileType fileType) {

    public static VersionFile fromJson(JsonElement j) {
        JsonObject json = j.getAsJsonObject();
        JsonGetter getter = new JsonGetter(json);

        Gson gson = new Gson();
        Map hashes = gson.fromJson(json.get("hashes"), Map.class);

        return new VersionFile(
                new VersionFileHashes(hashes),
                getter.getRequiredString("url"),
                getter.getRequiredString("filename"),
                getter.getRequiredBoolean("primary"),
                getter.getRequiredInt("size"),
                FileType.getEnum(getter.getString("file_type"))
        );
    }
}