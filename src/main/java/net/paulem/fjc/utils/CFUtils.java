package net.paulem.fjc.utils;

import io.github.matyrobbrt.curseforgeapi.schemas.file.File;
import io.github.matyrobbrt.curseforgeapi.schemas.mod.Mod;
import io.github.matyrobbrt.curseforgeapi.util.CurseForgeException;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static net.paulem.fjc.Main.cfApi;

public class CFUtils {
    // Mémoïse les requêtes CurseForge pour éviter de refaire le même appel réseau
    // plusieurs fois quand plusieurs fichiers proviennent du même mod.
    private static final ConcurrentHashMap<Integer, Optional<Mod>> MOD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Optional<File>> FILE_CACHE = new ConcurrentHashMap<>();

    @Nullable
    public static Mod getModFromId(int modId) {
        if(cfApi == null) return null;

        return MOD_CACHE.computeIfAbsent(modId, id -> {
            try {
                return Optional.ofNullable(cfApi.getHelper().getMod(id).orElse(null));
            } catch (CurseForgeException e) {
                // Ne pas propager, laisser l’appelant gérer l’erreur
                return Optional.empty();
            }
        }).orElse(null);
    }

    @Nullable
    public static File getFileFromId(int modId, int fileId) {
        if(cfApi == null) return null;

        long key = (((long) modId) << 32) | (fileId & 0xffffffffL);
        return FILE_CACHE.computeIfAbsent(key, k -> {
            try {
                return Optional.ofNullable(cfApi.getHelper().getModFile(modId, fileId).orElse(null));
            } catch (CurseForgeException e) {
                // Ne pas propager, laisser l’appelant gérer l’erreur
                return Optional.empty();
            }
        }).orElse(null);
    }
}
