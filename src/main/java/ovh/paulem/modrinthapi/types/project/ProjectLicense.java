package ovh.paulem.modrinthapi.types.project;

import com.google.gson.JsonElement;
import ovh.paulem.modrinthapi.JsonGetter;

public record ProjectLicense(String id, String name, String url) {
    public static ProjectLicense fromJson(JsonElement json) {
        JsonGetter getter = new JsonGetter(json.getAsJsonObject());

        return new ProjectLicense(getter.getString("id"), getter.getString("name"), getter.getString("url"));
    }
}