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

    /** Un thème : nom canonique, teinte/saturation de base, teinte de reflet (-1 = pas de reflet), mots-clés. */
    private record Theme(String name, double hue, double sat, double hl, String[] keys) {}

    private static Theme t(String name, double hue, double sat, double hl, String keys) {
        return new Theme(name, hue, sat, hl, keys.split(" "));
    }

    /** 36 thèmes distincts (teintes réparties), miroir du lexique d'entraînement du modèle. */
    private static final java.util.List<Theme> THEMES = java.util.List.of(
        t("vent", 145, .75, 150, "vent wind air aero tempete storm zephyr tornade cyclone bourrasque gale aerien"),
        t("feu", 12, .95, 44, "feu fire flamme flame brasier infernal magma lave lava ardent blaze ember braise pyro ignis embrase"),
        t("glace", 200, .80, 188, "glace ice gel givre frost frozen neige snow blizzard polaire arctique cryo gele glacial hiver"),
        t("foudre", 268, .72, 52, "foudre thunder eclair lightning orage electrique voltaique tonnerre volt spark fulgur galvanique"),
        t("ombre", 272, .50, 286, "ombre tenebre tenebres shadow nuit night void neant obscur umbra occulte sombre noirceur"),
        t("poison", 88, .85, 72, "poison venin venom toxique toxic acide acid corrosif venimeux putride peste miasme noxious"),
        t("sang", 358, .85, 8, "sang blood sanguin crimson carnage gore ecarlate hemo saignant boucher"),
        t("or", 46, .92, 52, "or gold dore royal divin holy sacre celeste lumiere light radiant saint aureum glorieux imperial"),
        t("nature", 110, .62, 95, "nature foret forest terre earth emeraude emerald sylvestre feuille leaf bois wood verdant druide"),
        t("ocean", 212, .85, 195, "ocean mer sea aqua eau water marine abyssal tide maree naval ondine aquatique neptune"),
        t("soleil", 32, .95, 48, "soleil sun solaire solar aurore dawn midi helios ensoleille estival"),
        t("lune", 226, .32, 220, "lune moon lunaire lunar argent silver stellaire etoile star selene nocturne astral"),
        t("ender", 168, .60, 286, "ender enderite chorus warp teleport endermite perle warped"),
        t("nether", 348, .55, 30, "nether enfer hell wither ame soul wraith damne maudit"),
        t("rose", 332, .70, 342, "rose pink amour love fleur flower sakura cerise blossom petale romantique"),
        t("arcane", 286, .65, 292, "arcane amethyste amethyst magie magic mystique mystic sorcier wizard mage enchante rune runique ensorcele"),
        t("cuivre", 24, .70, 36, "cuivre copper bronze rouille rust laiton oxyde"),
        t("cristal", 186, .70, 190, "cristal crystal diamant diamond prisme prism gemme scintillant etincelant"),
        t("acier", 210, .08, -1, "acier iron fer steel metal chrome gris gray grey titane titan blinde forge"),
        t("sable", 42, .72, 52, "sable sand desert dune ambre amber topaze topaz aride"),
        t("rubis", 348, .88, 4, "rubis ruby grenat garnet vermillon"),
        t("obsidienne", 280, .35, 250, "obsidienne obsidian onyx basalte basalt tenebreux"),
        t("corail", 10, .78, 24, "corail coral saumon salmon peche"),
        t("menthe", 158, .60, 150, "menthe mint fraicheur menthole"),
        t("lavande", 278, .45, 295, "lavande lavender lilas lilac prune mauve glycine"),
        t("citron", 62, .90, 56, "citron lemon lime citrus agrume soufre sulfur souffre"),
        t("chocolat", 24, .50, 36, "chocolat chocolate cafe coffee brun brown boue mud terreux cacao"),
        t("cendre", 220, .05, -1, "cendre ash fumee smoke brume mist fog nuage cloud poussiere"),
        t("chaos", 308, .60, 120, "chaos corrompu corrupt corruption eldritch folie madness demence aberrant"),
        t("dragon", 16, .82, 44, "dragon draconique drake wyrm draconien"),
        t("phenix", 22, .95, 52, "phenix phoenix renaissance immortel"),
        t("vampire", 352, .60, 358, "vampire vampirique sombrelame demoniaque demon"),
        t("spectral", 182, .25, 195, "spectral fantome ghost ethere pale revenant hante"),
        t("jade", 162, .62, 168, "jade olivine peridot malachite"),
        t("saphir", 228, .80, 215, "saphir sapphire azur azure cobalt indigo"),
        t("turquoise", 176, .72, 180, "turquoise teal sarcelle lagon cyan")
    );

    /**
     * Palette pour un nom donné. Si le nom évoque <b>plusieurs</b> thèmes (ex. « lune ténèbres et lumière »),
     * ils sont <b>mélangés</b> en un dégradé multi-couleurs riche ; un seul thème → sa rampe ; aucun → repli
     * par hash de teinte. Garantit toujours une palette non nulle, pour TOUT nom.
     */
    public static ThemePalette fromName(String name) {
        String s = normalize(name);
        if (has(s, "arc-en-ciel", "rainbow", "prisme", "spectre", "chromatique")) return rainbow();
        java.util.List<Theme> found = themesIn(s, 4);
        if (found.isEmpty()) return fromHash(s.isBlank() ? "item" : s);
        if (found.size() == 1) return rampOf(found.get(0));
        return blend(found);
    }

    /** Vrai si le nom évoque un thème connu (mot-clé) → la palette par lexique est fiable et doit primer. */
    public static boolean hasKnownTheme(String name) {
        String s = normalize(name);
        if (has(s, "arc-en-ciel", "rainbow", "prisme", "spectre", "chromatique")) return true;
        return !themesIn(s, 1).isEmpty();
    }

    /** Tous les thèmes évoqués par le nom (mot entier), dans l'ordre, plafonnés à {@code cap}. */
    private static java.util.List<Theme> themesIn(String s, int cap) {
        java.util.List<Theme> out = new java.util.ArrayList<>();
        for (Theme th : THEMES) {
            if (has(s, th.keys())) {
                out.add(th);
                if (out.size() >= cap) break;
            }
        }
        return out;
    }

    /** Dégradé qui MÉLANGE plusieurs thèmes : sombre → teinte de chaque thème (vif) → clair. */
    private static ThemePalette blend(java.util.List<Theme> ts) {
        int n = ts.size();
        java.util.List<ThemePalette.Stop> stops = new java.util.ArrayList<>();
        Theme first = ts.get(0);
        stops.add(new ThemePalette.Stop(0.0, hsl(first.hue(), Math.min(1.0, first.sat() + 0.05), 0.13)));
        for (int i = 0; i < n; i++) {
            Theme th = ts.get(i);
            double pos = 0.18 + 0.64 * (n == 1 ? 0.5 : (double) i / (n - 1));
            stops.add(new ThemePalette.Stop(pos, hsl(th.hue(), Math.min(1.0, th.sat() + 0.05), 0.52)));
        }
        Theme last = ts.get(n - 1);
        double hl = last.hl() >= 0 ? last.hl() : last.hue();
        stops.add(new ThemePalette.Stop(1.0, hsl(hl, last.sat() * 0.4, 0.90)));
        StringBuilder nm = new StringBuilder("mix");
        for (Theme th : ts) nm.append(':').append(th.name());
        return new ThemePalette(nm.toString(), stops);
    }

    /** Rampe sombre→clair d'un thème (HSL), reflet désaturé/éclairci — cohérent avec le dataset du modèle. */
    private static ThemePalette rampOf(Theme th) {
        int dark = hsl(th.hue(), Math.min(1.0, th.sat() + 0.05), 0.17);
        int mid = hsl(th.hue(), th.sat(), 0.50);
        double hlHue = th.hl() >= 0 ? th.hl() : th.hue();
        int light = hsl(hlHue, th.sat() * 0.45, 0.86);
        return ThemePalette.ramp(th.name(), dark, mid, light);
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
