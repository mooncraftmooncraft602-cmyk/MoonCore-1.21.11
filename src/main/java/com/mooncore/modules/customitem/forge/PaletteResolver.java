package com.mooncore.modules.customitem.forge;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Déduit une {@link ThemePalette} à partir du <b>nom</b> (ou de la description) d'un item — le cœur
 * « intelligent » côté serveur. Reconnaît un large lexique thématique FR/EN (vent, feu, glace, foudre,
 * ténèbres, poison, sang, or, nature, océan, lune, ender, nether, magie…) ; pour tout nom non thématique,
 * un repli déterministe par <b>hash → teinte</b> produit malgré tout une palette stable et cohérente.
 * 100 % pur (aucune dépendance serveur) → testable. Une couche IA optionnelle peut surcharger ce résultat.
 */
public final class PaletteResolver {

    private PaletteResolver() {}

    /** Normalise : minuscule + suppression des accents (é→e) pour matcher FR/EN indifféremment. */
    public static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT);
    }

    /**
     * Vrai si un des mots-clés correspond à un <b>mot</b> du nom (pas une sous-chaîne au milieu d'un autre
     * mot : « air » ne doit PAS matcher « lun<u>air</u>e », ni « or » matcher « z<u>or</u>glub »). Tolère les
     * flexions courtes (pluriels : « polaire » ↔ « polaires », « abysse » ↔ « abysses ») : le token doit
     * commencer par le mot-clé avec au plus 2 caractères en plus.
     */
    private static boolean has(String hay, String... needles) {
        String[] tokens = hay.split("[^a-z0-9]+");
        for (String kw : needles) {
            for (String tk : tokens) {
                if (tk.isEmpty()) continue;
                if (tk.equals(kw)) return true;
                if (tk.startsWith(kw) && tk.length() - kw.length() <= 2) return true;
            }
        }
        return false;
    }

    /**
     * Palette pour un nom donné. Le 1er thème reconnu gagne ; sinon repli par hash de teinte. Garantit
     * toujours une palette non nulle.
     */
    public static ThemePalette fromName(String name) {
        String s = normalize(name);

        if (has(s, "vent", "wind", "aero", "tempete", "storm", "air", "tornade", "cyclone", "zephyr"))
            return ThemePalette.ramp("vent", 0x1b5e20, 0x66bb6a, 0xe8f5e9);            // vert + blanc
        if (has(s, "feu", "fire", "flamme", "flame", "brasier", "infernal", "magma", "lave", "lava", "ardent", "blaze", "ember"))
            return ThemePalette.ramp("feu", 0x7f1d00, 0xef6c00, 0xffe082);            // rouge → orange → jaune
        if (has(s, "glace", "ice", "gel", "givre", "frost", "frozen", "neige", "snow", "blizzard", "polaire", "arctique"))
            return ThemePalette.ramp("glace", 0x0d47a1, 0x4fc3f7, 0xe1f5fe);          // bleu → cyan → blanc
        if (has(s, "foudre", "thunder", "eclair", "lightning", "orage", "electric", "voltaic", "volt", "tonnerre", "storm"))
            return ThemePalette.ramp("foudre", 0x4a148c, 0xffd54f, 0xfffde7);         // violet → jaune → blanc
        if (has(s, "ombre", "tenebre", "shadow", "dark", "nuit", "night", "void", "neant", "abyss", "abysse", "obscur", "noir"))
            return ThemePalette.ramp("ombre", 0x0a0a14, 0x4a148c, 0x9575cd);          // noir → violet
        if (has(s, "poison", "venin", "venom", "toxic", "toxique", "acide", "acid", "corrosif", "venimeux"))
            return ThemePalette.ramp("poison", 0x1b5e20, 0x7cb342, 0xccff90);         // vert toxique
        if (has(s, "sang", "blood", "sanguin", "crimson", "demon", "demoniaque", "carnage", "gore"))
            return ThemePalette.ramp("sang", 0x4a0000, 0xc62828, 0xff8a80);           // rouge sombre
        if (has(s, "or", "gold", "dore", "royal", "divin", "holy", "sacre", "celeste", "lumiere", "light", "radiant", "saint"))
            return ThemePalette.ramp("or", 0x7c5400, 0xffca28, 0xfff8e1);             // or → blanc chaud
        if (has(s, "nature", "foret", "forest", "terre", "earth", "emeraude", "emerald", "sylvestre", "feuille", "leaf", "bois", "wood"))
            return ThemePalette.ramp("nature", 0x1b3a0f, 0x43a047, 0xc5e1a5);         // vert forêt
        if (has(s, "ocean", "mer", "sea", "aqua", "eau", "water", "marine", "abyssal", "tide", "maree", "naval"))
            return ThemePalette.ramp("ocean", 0x012e40, 0x0097a7, 0xb2ebf2);          // bleu profond → cyan
        if (has(s, "soleil", "sun", "solaire", "solar", "aurore", "dawn", "midi"))
            return ThemePalette.ramp("soleil", 0xbf360c, 0xffa726, 0xfff59d);         // orange solaire
        if (has(s, "lune", "moon", "lunaire", "lunar", "argent", "silver", "stellaire", "etoile", "star"))
            return ThemePalette.ramp("lune", 0x263859, 0x90a4ae, 0xeceff1);           // bleu nuit → argent
        if (has(s, "ender", "enderite", "chorus", "endermite", "perle", "warp", "teleport"))
            return ThemePalette.ramp("ender", 0x06231f, 0x1de9b6, 0xd1c4e9);          // teal → mauve
        if (has(s, "nether", "enfer", "hell", "wither", "wraith", "soul", "ame"))
            return ThemePalette.ramp("nether", 0x2a0a0a, 0x8e24aa, 0x4dd0e1);         // sombre → ame
        if (has(s, "rose", "pink", "amour", "love", "fleur", "flower", "sakura", "cerise"))
            return ThemePalette.ramp("rose", 0x880e4f, 0xec407a, 0xfce4ec);           // rose
        if (has(s, "violet", "purple", "amethyste", "amethyst", "magie", "magic", "arcane", "mystique", "mystic", "sorcier", "wizard", "mage"))
            return ThemePalette.ramp("arcane", 0x311b92, 0x7e57c2, 0xede7f6);         // violet arcane
        if (has(s, "cuivre", "copper", "bronze", "rouille", "rust"))
            return ThemePalette.ramp("cuivre", 0x5d2e0a, 0xc97b4a, 0xffccbc);
        if (has(s, "diamant", "diamond", "cristal", "crystal", "saphir", "sapphire"))
            return ThemePalette.ramp("cristal", 0x006064, 0x26c6da, 0xe0f7fa);
        if (has(s, "fer", "iron", "acier", "steel", "metal", "chrome", "gris", "gray", "grey"))
            return ThemePalette.ramp("acier", 0x37474f, 0x90a4ae, 0xeceff1);
        if (has(s, "arc-en-ciel", "rainbow", "prisme", "prism", "spectre", "spectral", "chromatique"))
            return rainbow();

        return fromHash(s.isBlank() ? "item" : s);
    }

    /** Repli : teinte déterministe issue du hash du nom → rampe sombre/vif/clair de cette teinte. */
    public static ThemePalette fromHash(String s) {
        int h = Math.floorMod(s.hashCode(), 360);
        int dark = hsl(h, 0.65, 0.20);
        int mid = hsl(h, 0.70, 0.50);
        int light = hsl(h, 0.55, 0.85);
        return ThemePalette.ramp("auto:" + h, dark, mid, light);
    }

    private static ThemePalette rainbow() {
        java.util.List<ThemePalette.Stop> s = new java.util.ArrayList<>();
        s.add(new ThemePalette.Stop(0.00, 0x6a1b9a));
        s.add(new ThemePalette.Stop(0.20, 0x1565c0));
        s.add(new ThemePalette.Stop(0.40, 0x2e7d32));
        s.add(new ThemePalette.Stop(0.60, 0xf9a825));
        s.add(new ThemePalette.Stop(0.80, 0xe65100));
        s.add(new ThemePalette.Stop(1.00, 0xc62828));
        return new ThemePalette("arc-en-ciel", s);
    }

    /** HSL → RGB (0xRRGGBB). h en degrés [0,360), s/l ∈ [0,1]. Pur. */
    public static int hsl(double h, double s, double l) {
        h = ((h % 360) + 360) % 360 / 360.0;
        double r, g, b;
        if (s <= 0) { r = g = b = l; }
        else {
            double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
            double p = 2 * l - q;
            r = hue2rgb(p, q, h + 1.0 / 3.0);
            g = hue2rgb(p, q, h);
            b = hue2rgb(p, q, h - 1.0 / 3.0);
        }
        return ThemePalette.rgb((int) Math.round(r * 255), (int) Math.round(g * 255), (int) Math.round(b * 255));
    }

    private static double hue2rgb(double p, double q, double t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1.0 / 6.0) return p + (q - p) * 6 * t;
        if (t < 1.0 / 2.0) return q;
        if (t < 2.0 / 3.0) return p + (q - p) * (2.0 / 3.0 - t) * 6;
        return p;
    }
}
