package ovh.paulem.modrinthapi.types.project;

import com.google.gson.JsonElement;
import ovh.paulem.modrinthapi.JsonGetter;

public record ProjectDonationUrl(String id, String platform, String url) {
    public static ProjectDonationUrl fromJson(JsonElement json) {
        JsonGetter getter = new JsonGetter(json.getAsJsonObject());

        return new ProjectDonationUrl(getter.getString("id"), getter.getString("platform"), getter.getString("url"));
    }
}
