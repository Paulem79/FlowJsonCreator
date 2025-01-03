package io.github.paulem.fjc.utils;

import io.github.paulem.fjc.gui.Main;
import io.github.paulem.modrinthapi.types.project.Project;
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
