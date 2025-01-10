package ovh.paulem.modrinthapi;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class JsonGetter {
    public JsonObject json;

    public JsonGetter(JsonObject json) {
        this.json = json;
    }

    @Nullable
    public String getString(String memberName) {
        JsonElement element = get(memberName);

        if(element == null || element instanceof JsonNull) return null;

        return element.getAsString();
    }

    @NotNull
    public String getRequiredString(String memberName) {
        return getRequired(memberName).getAsString();
    }

    @Nullable
    public Integer getInt(String memberName) {
        JsonElement element = get(memberName);

        if(element == null || element instanceof JsonNull) return null;

        return element.getAsInt();
    }

    @NotNull
    public Integer getRequiredInt(String memberName) {
        return getRequired(memberName).getAsInt();
    }

    @Nullable
    public Boolean getBoolean(String memberName) {
        JsonElement element = get(memberName);

        if(element == null || element instanceof JsonNull) return null;

        return element.getAsBoolean();
    }

    @NotNull
    public Boolean getRequiredBoolean(String memberName) {
        return getRequired(memberName).getAsBoolean();
    }

    public JsonElement get(String memberName) {
        return json.get(memberName);
    }

    @NotNull
    public JsonElement getRequired(String memberName) {
        JsonElement got = json.get(memberName);

        if(got == null) throw new NullPointerException("Unrecognized with member " + memberName);

        return got;
    }

    @Nullable
    public static List<@Nullable Object> getListFromJsonArray(JsonArray array) {
        if(array == null) return null;

        List<@Nullable Object> finalList = new ArrayList<>();

        for (JsonElement jsonElement : array) {
            finalList.add(jsonElement);
        }

        return finalList;
    }

    public static<T> List<T> getOneTypeListFromJsonArray(T[] type, JsonArray array) {
        if(Arrays.equals(type, new String[0])) return (List<T>) JsonGetter.doOnListElements(array, JsonElement::getAsString);

        if(array == null) return null;

        List<T> finalList = new ArrayList<>();

        for (JsonElement jsonElement : array) {
            if (jsonElement == null) continue;
            finalList.add((T) jsonElement);
        }

        return finalList;
    }

    public static<R> List<R> doOnListElements(JsonArray array, Function<JsonElement, ? extends R> toDo) {
        if(array == null) return null;

        List<R> finalList = new ArrayList<>();

        for (JsonElement jsonElement : array) {
            if (jsonElement == null) continue;
            finalList.add(toDo.apply(jsonElement));
        }

        return finalList;
    }
}
