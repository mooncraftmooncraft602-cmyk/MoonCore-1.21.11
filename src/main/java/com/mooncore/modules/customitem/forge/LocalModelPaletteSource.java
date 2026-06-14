package com.mooncore.modules.customitem.forge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Source de palette par <b>modèle local auto-hébergé</b> : interroge le sidecar Python (HTTP localhost)
 * qui fait tourner le petit GPT couleurs, et convertit sa réponse en {@link ThemePalette} (rampe N stops =
 * dégradé). Même style que {@code AiClient} (java.net.http + Gson, async). <b>Repli systématique</b> sur
 * {@link PaletteResolver#fromName} si le sidecar est éteint, lent ou répond mal — donc aucune régression.
 */
public final class LocalModelPaletteSource {

    private static final Pattern HEX = Pattern.compile("#?([0-9a-fA-F]{6})");

    private final HttpClient http;
    private final String endpoint;
    private final int timeoutSeconds;
    private final Gson gson = new Gson();

    public LocalModelPaletteSource(String endpoint, int timeoutSeconds) {
        this.endpoint = (endpoint == null || endpoint.isBlank()) ? "http://127.0.0.1:8770/palette" : endpoint;
        this.timeoutSeconds = Math.max(2, timeoutSeconds);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.min(5, this.timeoutSeconds))).build();
    }

    /** Palette via le modèle local (async). Ne lève jamais : repli déterministe garanti. */
    public CompletableFuture<ThemePalette> resolve(String name) {
        final ThemePalette fallback = PaletteResolver.fromName(name);
        try {
            JsonObject body = new JsonObject();
            body.addProperty("name", name == null ? "" : name);
            HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();
            return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .handle((resp, err) -> {
                        if (err != null || resp == null || resp.statusCode() >= 400) return fallback;
                        ThemePalette p = parse(name, resp.body());
                        return p != null ? p : fallback;
                    });
        } catch (Exception e) {
            return CompletableFuture.completedFuture(fallback);
        }
    }

    /** Parse la réponse {@code {"colors":["#..",...]}} en rampe triée sombre→clair. null si exploitable < 2 couleurs. */
    ThemePalette parse(String name, String json) {
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null || !root.has("colors") || !root.get("colors").isJsonArray()) return null;
            JsonArray arr = root.getAsJsonArray("colors");
            List<Integer> rgb = new ArrayList<>();
            for (JsonElement e : arr) {
                if (!e.isJsonPrimitive()) continue;
                Matcher m = HEX.matcher(e.getAsString());
                if (m.find()) rgb.add(Integer.parseInt(m.group(1), 16));
            }
            if (rgb.size() < 2) return null;
            rgb.sort((a, b) -> Double.compare(TextureRecolorer.luminance(a), TextureRecolorer.luminance(b)));
            return ThemePalette.ofColors("model:" + name, rgb);
        } catch (Exception e) {
            return null;
        }
    }
}
