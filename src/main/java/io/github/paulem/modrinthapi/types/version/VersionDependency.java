package io.github.paulem.modrinthapi.types.version;

import com.google.gson.JsonElement;
import io.github.paulem.modrinthapi.JsonGetter;
import io.github.paulem.modrinthapi.types.enums.DependencyType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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