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
    private HttpJson() {
    }

    public static JsonElement get(String url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) URI.create(url).toURL().openConnection();
        con.setRequestMethod("GET");
        con.setConnectTimeout(10_000);
        con.setReadTimeout(20_000);
        con.setRequestProperty("User-Agent", Main.MODRINTH.getUserAgent());
        try (InputStreamReader reader = new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } finally {
            con.disconnect();
        }
    }
}
