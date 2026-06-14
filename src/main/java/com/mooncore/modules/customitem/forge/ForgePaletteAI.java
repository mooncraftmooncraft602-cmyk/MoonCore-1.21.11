package com.mooncore.modules.customitem.forge;

import com.mooncore.modules.ai.AiClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Couche IA <b>optionnelle</b> de la forge : demande au LLM une palette de 2-4 couleurs hex qui incarnent le
 * <i>thème</i> d'un nom d'item (« épée du vent » → vert/blanc), puis la convertit en {@link ThemePalette}. Le
 * parsing est robuste (extrait les {@code #RRGGBB} où qu'ils soient dans la réponse) et tombe toujours sur le
 * résolveur déterministe {@link PaletteResolver} en cas d'échec/IA absente. Le parsing est pur → testable.
 */
public final class ForgePaletteAI {

    private ForgePaletteAI() {}

    private static final Pattern HEX = Pattern.compile("#([0-9a-fA-F]{6})");

    /** Prompt système : impose une sortie hexadécimale exploitable. */
    public static final String SYSTEM =
            "Tu es coloriste pour un jeu. On te donne le NOM d'un objet (arme/armure/minerai). "
          + "Reponds UNIQUEMENT par 3 a 4 couleurs hexadecimales #RRGGBB representant son theme, de la plus "
          + "SOMBRE a la plus CLAIRE, separees par des espaces. Aucune autre parole. Ex pour 'epee du vent': "
          + "#1b5e20 #66bb6a #e8f5e9";

    /** Extrait tous les {@code #RRGGBB} d'un texte, dans l'ordre. Pur. */
    public static List<Integer> extractColors(String text) {
        List<Integer> out = new ArrayList<>();
        if (text == null) return out;
        Matcher m = HEX.matcher(text);
        while (m.find()) out.add(Integer.parseInt(m.group(1), 16));
        return out;
    }

    /**
     * Construit une palette depuis la réponse IA : ≥2 couleurs → rampe triée par luminance (sombre→clair) ;
     * sinon repli déterministe sur {@link PaletteResolver#fromName}. Pur → testable.
     */
    public static ThemePalette paletteFromText(String name, String aiText) {
        List<Integer> colors = extractColors(aiText);
        if (colors.size() < 2) return PaletteResolver.fromName(name);
        colors.sort((a, b) -> Double.compare(TextureRecolorer.luminance(a), TextureRecolorer.luminance(b)));
        int dark = colors.get(0);
        int light = colors.get(colors.size() - 1);
        int mid = colors.get(colors.size() / 2);
        return ThemePalette.ramp("ia:" + name, dark, mid, light);
    }

    /**
     * Palette via IA si disponible, sinon déterministe — toujours résolu (jamais d'exception). Async.
     */
    public static CompletableFuture<ThemePalette> resolve(AiClient ai, String name) {
        if (ai == null || !ai.config().hasApiKey() || !ai.tryAcquireRate(System.currentTimeMillis())) {
            return CompletableFuture.completedFuture(PaletteResolver.fromName(name));
        }
        return ai.ask(SYSTEM, "Nom de l'objet : " + name)
                .handle((text, err) -> (err != null) ? PaletteResolver.fromName(name) : paletteFromText(name, text));
    }
}
