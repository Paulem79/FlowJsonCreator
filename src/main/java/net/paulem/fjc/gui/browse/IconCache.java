package net.paulem.fjc.gui.browse;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads and caches mod icons. JavaFX cannot decode WebP (what Modrinth serves), so the bytes are decoded through
 * ImageIO (WebP plugin: TwelveMonkeys) and converted, with a plain JavaFX decode as fallback for anything else.
 */
public final class IconCache {
    private static final int MAX_ENTRIES = 400;
    private static final ExecutorService POOL = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "fjc-icon-loader");
        t.setDaemon(true);
        return t;
    });

    private static final Map<String, CompletableFuture<Optional<Image>>> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CompletableFuture<Optional<Image>>> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private IconCache() {
    }

    /** Completes (off the FX thread) with the icon, or empty if it could not be loaded. */
    public static synchronized CompletableFuture<Optional<Image>> get(String url) {
        return CACHE.computeIfAbsent(url, u -> CompletableFuture.supplyAsync(() -> load(u), POOL));
    }

    private static Optional<Image> load(String url) {
        try {
            byte[] bytes;
            try (InputStream in = URI.create(url).toURL().openStream()) {
                bytes = in.readAllBytes();
            }
            BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(bytes));
            if (buffered != null) return Optional.of(SwingFXUtils.toFXImage(buffered, null));

            Image fx = new Image(new ByteArrayInputStream(bytes));
            return fx.isError() ? Optional.empty() : Optional.of(fx);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
