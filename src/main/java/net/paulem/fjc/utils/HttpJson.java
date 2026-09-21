package net.paulem.fjc.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.paulem.fjc.Main;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/** Minimal JSON-over-HTTP GET helper for the Modrinth endpoints the API wrapper does not cover. */
public final class HttpJson {
    private static final int MAX_ATTEMPTS = 6;

    private HttpJson() {
    }

    /** GET as JSON; a 429 (rate limit) is waited out and retried, honouring {@code Retry-After} when given. */
    public static JsonElement get(String url) throws IOException {
        for (int attempt = 1; ; attempt++) {
            HttpURLConnection con = (HttpURLConnection) URI.create(url).toURL().openConnection();
            try {
                con.setRequestMethod("GET");
                con.setConnectTimeout(10_000);
                con.setReadTimeout(20_000);
                con.setRequestProperty("User-Agent", Main.MODRINTH.getUserAgent());
                if (con.getResponseCode() == 429 && attempt < MAX_ATTEMPTS) {
                    sleepBackoff(attempt, con.getHeaderField("Retry-After"));
                    continue;
                }
                try (InputStreamReader reader = new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8)) {
                    return JsonParser.parseReader(reader);
                }
            } finally {
                con.disconnect();
            }
        }
    }

    /** Sleeps for the server's {@code Retry-After} seconds, or an exponential delay (2s, 4s, 8s...) without it. */
    public static void sleepBackoff(int attempt, String retryAfter) throws IOException {
        long seconds = Math.min(30, 1L << attempt);
        if (retryAfter != null) {
            try {
                seconds = Math.min(60, Math.max(1, Long.parseLong(retryAfter.trim())));
            } catch (NumberFormatException ignored) {
                // Not a number of seconds (an HTTP date): keep the exponential delay
            }
        }
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new java.io.InterruptedIOException("Interrompu pendant l'attente du serveur");
        }
    }
}
