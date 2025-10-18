package net.paulem.fjc.utils;

import net.paulem.fjc.Main;
import ovh.paulem.modrinthapi.types.project.Project;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URISyntaxException;

public class ModrinthUtils {
    @Nullable
    public static Project getModFromSlug(String slug) {
        try {
            return Main.MODRINTH.getProject(slug);
        } catch (URISyntaxException | IOException e) {
            return null;
        }
    }
}
