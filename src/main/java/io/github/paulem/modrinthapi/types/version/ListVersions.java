package io.github.paulem.modrinthapi.types.version;

import com.google.gson.JsonArray;
import io.github.paulem.modrinthapi.JsonGetter;

import java.util.List;

public record ListVersions(List<io.github.paulem.modrinthapi.types.version.Version> versions) {

    public static ListVersions fromJson(JsonArray json) {
        return new ListVersions(
                JsonGetter.doOnListElements(
                        json.getAsJsonArray(),
                        Version::fromJson
                )
        );
    }
}