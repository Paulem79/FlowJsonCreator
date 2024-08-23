package io.github.paulem.fjc.utils;

import com.google.gson.JsonPrimitive;
import joptsimple.OptionSet;
import org.jetbrains.annotations.Nullable;

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
}
