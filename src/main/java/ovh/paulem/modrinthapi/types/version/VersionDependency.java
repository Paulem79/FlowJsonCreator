package ovh.paulem.modrinthapi.types.version;

import com.google.gson.JsonElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ovh.paulem.modrinthapi.JsonGetter;
import ovh.paulem.modrinthapi.types.enums.DependencyType;

public record VersionDependency(
        @Nullable String versionId, @Nullable String projectId,
        @Nullable String fileName, @NotNull DependencyType dependencyType
) {

    public static VersionDependency fromJson(JsonElement json) {
        JsonGetter getter = new JsonGetter(json.getAsJsonObject());

        return new VersionDependency(
                getter.getString("versionId"),
                getter.getString("projectId"),
                getter.getString("fileName"),
                DependencyType.getEnum(getter.getRequiredString("dependency_type"))
        );
    }
}