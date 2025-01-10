package ovh.paulem.modrinthapi.types.project;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import ovh.paulem.modrinthapi.JsonGetter;

import java.util.ArrayList;
import java.util.List;

public record SearchProject(ProjectResult[] hits, int offset, int limit, int totalHits) {

    public static SearchProject fromJson(JsonObject json) {
        JsonGetter getter = new JsonGetter(json);

        JsonArray hits = json.getAsJsonArray("hits");
        List<ProjectResult> hitsList = new ArrayList<>();

        hits.forEach(hit -> {
            JsonObject objectHit = hit.getAsJsonObject();

            hitsList.add(ProjectResult.fromJson(objectHit));
        });

        return new SearchProject(hitsList.toArray(new ProjectResult[0]), getter.getRequiredInt("offset"), getter.getRequiredInt("limit"), getter.getRequiredInt("total_hits"));
    }
}
