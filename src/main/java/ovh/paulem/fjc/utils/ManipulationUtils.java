package ovh.paulem.fjc.utils;

import com.google.gson.JsonPrimitive;
import io.github.matyrobbrt.curseforgeapi.schemas.mod.ModLoaderType;
import joptsimple.OptionSet;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class ManipulationUtils {
    public static<T> String collectionToString(Iterable<T> collection, String append) {
        StringBuilder result = new StringBuilder();

        for(T item : collection) {
            if(item instanceof JsonPrimitive jsonPrimitive) {
                result.append(jsonPrimitive.getAsString()).append(append);
            } else if(item instanceof String) {
                result.append(item).append(append);
            } else {
                throw new RuntimeException("Incorrect collection type");
            }
        }

        return result.toString();
    }

    @Nullable
    public static String checkOptArg(OptionSet options, String optionName) {
        if (options.has(optionName)) {
            return (String) options.valueOf(optionName);
        }
        return null;
    }

    public static String capitalize(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    public static List<String> getModLoaders() {
        return Arrays.stream(ModLoaderType.values())
                .filter(modLoaderType -> modLoaderType != ModLoaderType.CAULDRON && modLoaderType != ModLoaderType.LITE_LOADER)
                .map(ModLoaderType::toString)
                .toList();
    }
}
