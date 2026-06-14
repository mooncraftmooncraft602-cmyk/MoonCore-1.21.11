package com.mooncore.modules.customitem.forge;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitaires couleurs de la forge (purs, testables) : reconnaître/parser des {@code #rrggbb}, formater,
 * trier une rampe sombre→clair, et construire une {@link ThemePalette} depuis des couleurs choisies par
 * l'utilisateur. Sert au mode « couleurs explicites » et à la suggestion de palettes.
 */
public final class ForgeColors {

    private ForgeColors() {}

    private static final Pattern HEX = Pattern.compile("^#?([0-9a-fA-F]{6})$");

    /** True si le token est une couleur hex (avec ou sans {@code #}). */
    public static boolean isHex(String token) {
        return token != null && HEX.matcher(token.trim()).matches();
    }

    /** Parse un token hex en RGB (0xRRGGBB), ou -1 si invalide. */
    public static int parseHex(String token) {
        if (token == null) return -1;
        Matcher m = HEX.matcher(token.trim());
        return m.matches() ? Integer.parseInt(m.group(1), 16) : -1;
    }

    public static String toHex(int rgb) {
        return String.format(java.util.Locale.ROOT, "#%06x", rgb & 0xFFFFFF);
    }

    /** Copie triée de la plus sombre à la plus claire (pour une rampe d'ombrage cohérente). */
    public static List<Integer> sortDarkToLight(List<Integer> colors) {
        List<Integer> out = new ArrayList<>(colors);
        out.sort((a, b) -> Double.compare(TextureRecolorer.luminance(a), TextureRecolorer.luminance(b)));
        return out;
    }

    /**
     * Sépare une liste de tokens (le reste de la commande après la base) en {@code name} (mots non-hex,
     * joints) et {@code colors} (tokens hex, dans l'ordre). Permet {@code /forge <base> <nom…> [#hex…]}.
     */
    public static Parsed parseNameAndColors(String[] tokens, int from) {
        StringBuilder name = new StringBuilder();
        List<Integer> colors = new ArrayList<>();
        for (int i = from; i < tokens.length; i++) {
            String tk = tokens[i];
            if (isHex(tk)) {
                colors.add(parseHex(tk));
            } else {
                if (name.length() > 0) name.append(' ');
                name.append(tk);
            }
        }
        return new Parsed(name.toString().trim(), colors);
    }

    /**
     * Palette depuis des couleurs choisies. ≥2 couleurs → rampe directe (triée sombre→clair) ; 1 seule →
     * rampe d'ombrage dérivée (assombrie → couleur → éclaircie). null si aucune.
     */
    public static ThemePalette paletteFromChosen(String name, List<Integer> colors) {
        if (colors == null || colors.isEmpty()) return null;
        if (colors.size() == 1) {
            int c = colors.get(0);
            return ThemePalette.ramp("choix:" + name, ThemePalette.darken(c, 0.55), c, ThemePalette.lighten(c, 0.55));
        }
        return ThemePalette.ofColors("choix:" + name, sortDarkToLight(colors));
    }

    /** Résultat de {@link #parseNameAndColors}. */
    public record Parsed(String name, List<Integer> colors) {}
}
