package ovh.paulem.modrinthapi.types.version;

import com.google.gson.JsonArray;
import ovh.paulem.modrinthapi.JsonGetter;

import java.util.List;

public record ListVersions(List<Version> versions) {

    public static ListVersions fromJson(JsonArray json) {
        return new ListVersions(
                JsonGetter.doOnListElements(
                        json.getAsJsonArray(),
                        Version::fromJson
                )
        );
    }
}