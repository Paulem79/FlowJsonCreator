package io.github.paulem.modrinthapi.types.project;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.paulem.modrinthapi.JsonGetter;

import java.util.ArrayList;
import java.util.List;

public record SearchProject(io.github.paulem.modrinthapi.types.project.ProjectResult[] hits, int offset, int limit, int totalHits) {

    public static SearchProject fromJson(JsonObject json) {
        JsonGetter getter = new JsonGetter(json);

        JsonArray hits = json.getAsJsonArray("hits");
        List<io.github.paulem.modrinthapi.types.project.ProjectResult> hitsList = new ArrayList<>();

        hits.forEach(hit -> {
            JsonObject objectHit = hit.getAsJsonObject();

            hitsList.add(io.github.paulem.modrinthapi.types.project.ProjectResult.fromJson(objectHit));
        });

        return new SearchProject(hitsList.toArray(new ProjectResult[0]), getter.getRequiredInt("offset"), getter.getRequiredInt("limit"), getter.getRequiredInt("total_hits"));
    }
}
