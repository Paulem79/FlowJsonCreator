package net.paulem.fjc.utils;

import com.google.gson.JsonPrimitive;
import io.github.matyrobbrt.curseforgeapi.schemas.mod.ModLoaderType;
import joptsimple.OptionSet;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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

    /** French compact count: 228,12 M, 40,7 k, 950. */
    public static String formatCount(long n) {
        DecimalFormatSymbols fr = DecimalFormatSymbols.getInstance(Locale.FRANCE);
        if (n >= 1_000_000_000L) return new DecimalFormat("0.##", fr).format(n / 1_000_000_000.0) + " Md";
        if (n >= 1_000_000L) return new DecimalFormat("0.##", fr).format(n / 1_000_000.0) + " M";
        if (n >= 1_000L) return new DecimalFormat("0.#", fr).format(n / 1_000.0) + " k";
        return String.valueOf(n);
    }

    /** French relative date: "Il y a 5 jours", "Hier", "Il y a 6 mois". */
    public static String relativeDate(Instant instant) {
        long seconds = Math.max(0, Duration.between(instant, Instant.now()).getSeconds());
        long minutes = seconds / 60, hours = minutes / 60, days = hours / 24;
        if (minutes < 1) return "À l'instant";
        if (hours < 1) return "Il y a " + minutes + (minutes > 1 ? " minutes" : " minute");
        if (days < 1) return "Il y a " + hours + (hours > 1 ? " heures" : " heure");
        if (days == 1) return "Hier";
        if (days == 2) return "Avant-hier";
        if (days < 30) return "Il y a " + days + " jours";
        if (days < 365) return "Il y a " + (days / 30) + " mois";
        long years = days / 365;
        return "Il y a " + years + (years > 1 ? " ans" : " an");
    }
}
