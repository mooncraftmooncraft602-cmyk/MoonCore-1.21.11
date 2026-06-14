package com.mooncore.modules.customitem.forge;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Source de palette par le modèle GPT <b>exécuté dans la JVM du serveur</b> ({@link GptInference}) — aucune
 * dépendance externe (ni Python, ni sidecar). Charge {@code forge-gpt.bin} une fois ; chaque requête fait un
 * forward pass en Java (sur un thread de travail, ~dizaines de ms) et convertit la sortie en {@link ThemePalette}.
 * <b>Repli</b> systématique sur {@link PaletteResolver#fromName} si le modèle est absent ou répond mal.
 */
public final class GptPaletteSource {

    private final GptInference model;   // null si binaire absent

    public GptPaletteSource(File binFile) {
        this.model = GptInference.load(binFile);
    }

    /** True si le modèle est chargé (sinon, tout retombe sur le moteur déterministe). */
    public boolean available() { return model != null; }

    /** Palette par le modèle (async), repli déterministe garanti. */
    public CompletableFuture<ThemePalette> resolve(String name) {
        if (model == null) return CompletableFuture.completedFuture(PaletteResolver.fromName(name));
        final String nm = name == null ? "" : name;
        return CompletableFuture.supplyAsync(() -> {
            try {
                String gen = model.generate(nm + " => ", 64, '\n');
                List<Integer> colors = ForgePaletteAI.extractColors(gen);
                if (colors.size() < 2) return PaletteResolver.fromName(nm);
                return ThemePalette.ofColors("gpt:" + nm, ForgeColors.sortDarkToLight(colors));
            } catch (Exception e) {
                return PaletteResolver.fromName(nm);
            }
        });
    }

    /** Couleurs proposées par le modèle (pour la suggestion), ou liste vide si indispo/échec. Synchrone. */
    public List<String> suggestHex(String name) {
        if (model == null) return List.of();
        try {
            List<Integer> colors = ForgeColors.sortDarkToLight(
                    ForgePaletteAI.extractColors(model.generate((name == null ? "" : name) + " => ", 64, '\n')));
            return colors.stream().map(ForgeColors::toHex).toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
