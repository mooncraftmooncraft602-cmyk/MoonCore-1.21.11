package com.mooncore.modules.customitem.forge;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CompletableFuture;

/**
 * Source de <b>programme DSL</b> par le modèle GPT exécuté <b>dans la JVM</b> ({@link GptInference}, zéro
 * dépendance externe). L'IA <b>écrit le langage de texture</b> (la suite de mots) à partir du nom ; le serveur
 * la dessine via {@link TextureSynth#renderProgram}. C'est la concrétisation : « le texte devient une image ».
 *
 * <p><b>Filet de sécurité</b> : si le modèle est absent, ou si le programme produit ne dessine rien ou ne
 * correspond pas au type d'objet attendu (déduit du nom), on retombe sur {@link TextureComposer} — donc la
 * forge reste TOUJOURS correcte, et utilise l'IA dès qu'elle produit un programme valide.</p>
 */
public final class GptProgramSource {

    private final GptInference model;   // null si binaire absent

    public GptProgramSource(File binFile) {
        this.model = GptInference.load(binFile);
    }

    /** True si le modèle DSL est chargé. */
    public boolean available() { return model != null; }

    /** Programme DSL pour {@code name} (async), filet déterministe garanti sur {@link TextureComposer}. */
    public CompletableFuture<String> resolve(String name) {
        final String nm = name == null ? "" : name;
        // Type inconnu -> le modèle n'a pas appris ce cas : compositeur direct.
        if (model == null || TextureSynth.itemKind(nm) == TextureSynth.Kind.GENERIC)
            return CompletableFuture.completedFuture(TextureComposer.compose(nm));
        return CompletableFuture.supplyAsync(() -> {
            try {
                String tag = afterArrow(model.generate(nm + " => ", 24, '\n'));   // l'IA émet « KIND b s o »
                long seed = nm.toLowerCase(java.util.Locale.ROOT).hashCode();
                String prog = TextureComposer.fromTag(tag, seed);                  // le serveur l'étend en DSL
                if (isValidFor(prog, nm)) return prog;            // type cohérent avec le nom
            } catch (Exception ignored) { }
            return TextureComposer.compose(nm);                    // filet
        });
    }

    /** Extrait le programme après « => » et coupe à la fin de ligne. */
    private static String afterArrow(String gen) {
        if (gen == null) return "";
        int i = gen.indexOf("=>");
        String p = i >= 0 ? gen.substring(i + 2) : gen;
        int nl = p.indexOf('\n');
        if (nl >= 0) p = p.substring(0, nl);
        return p.trim();
    }

    /** Le programme doit (a) contenir la signature du bon type d'objet, (b) dessiner une silhouette non vide. */
    static boolean isValidFor(String prog, String name) {
        if (prog == null || prog.length() < 12) return false;
        String sig = switch (TextureSynth.itemKind(name)) {
            case SWORD -> "FULLER";
            case PICKAXE -> "GDISC 8";
            case AXE -> "MDISC 4";
            case HELMET -> "MELL";
            case CHESTPLATE -> "MRECT 3";
            case GENERIC -> "";
        };
        if (!sig.isEmpty() && !prog.contains(sig)) return false;
        BufferedImage img = TextureSynth.renderProgram(prog, ThemePalette.ramp("x", 0x202020, 0x808080, 0xf0f0f0), 1L);
        int opaque = 0;
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) if ((img.getRGB(x, y) >>> 24) != 0) opaque++;
        return opaque > 20;
    }
}
