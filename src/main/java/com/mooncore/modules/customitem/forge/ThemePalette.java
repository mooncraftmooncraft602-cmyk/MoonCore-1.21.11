package com.mooncore.modules.customitem.forge;

import java.util.ArrayList;
import java.util.List;

/**
 * Palette thématique d'un item : une <b>rampe de couleurs</b> indexée par la luminance (0 = ombres,
 * 1 = hautes lumières). Le moteur {@link TextureRecolorer} mappe la luminance de chaque pixel existant sur
 * cette rampe pour recolorer une texture <b>sans ajouter ni déplacer de pixel</b> (la forme et l'ombrage,
 * portés par la luminance et l'alpha, sont préservés ; seules les teintes changent).
 *
 * <p>Pur (entiers RGB), sans dépendance serveur → entièrement testable.</p>
 */
public final class ThemePalette {

    /** Un palier de la rampe : couleur RGB (0xRRGGBB) à la position de luminance {@code pos} ∈ [0,1]. */
    public record Stop(double pos, int rgb) {}

    private final String name;
    private final List<Stop> stops;   // triés par pos croissante, bornes 0 et 1 garanties

    public ThemePalette(String name, List<Stop> stops) {
        this.name = name == null ? "theme" : name;
        List<Stop> s = new ArrayList<>(stops);
        s.sort((a, b) -> Double.compare(a.pos(), b.pos()));
        if (s.isEmpty()) s.add(new Stop(0.5, 0x808080));
        // garantit des paliers aux bornes 0 et 1 (extrapolation plate)
        if (s.get(0).pos() > 0.0) s.add(0, new Stop(0.0, s.get(0).rgb()));
        if (s.get(s.size() - 1).pos() < 1.0) s.add(new Stop(1.0, s.get(s.size() - 1).rgb()));
        this.stops = s;
    }

    public String name() { return name; }
    public List<Stop> stops() { return List.copyOf(stops); }

    /** Les couleurs de la rampe en {@code #rrggbb} (de l'ombre à la lumière) — pour afficher/conseiller. */
    public List<String> hexStops() {
        List<String> out = new ArrayList<>(stops.size());
        for (Stop s : stops) out.add(String.format(java.util.Locale.ROOT, "#%06x", s.rgb() & 0xFFFFFF));
        return out;
    }

    /**
     * Construit une rampe à partir d'une liste de couleurs (≥1), réparties uniformément en luminance de la
     * 1re (ombres) à la dernière (hautes lumières). Sert aux palettes du modèle local (dégradés à N stops).
     * Les couleurs doivent être pré-triées sombre→clair pour un ombrage cohérent.
     */
    public static ThemePalette ofColors(String name, List<Integer> colorsDarkToLight) {
        if (colorsDarkToLight == null || colorsDarkToLight.isEmpty()) {
            return new ThemePalette(name, List.of(new Stop(0.5, 0x808080)));
        }
        int n = colorsDarkToLight.size();
        List<Stop> s = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double pos = n == 1 ? 0.5 : (double) i / (n - 1);
            s.add(new Stop(pos, colorsDarkToLight.get(i) & 0xFFFFFF));
        }
        return new ThemePalette(name, s);
    }

    /** Construit une rampe sombre→moyen→clair (3 teintes) : le cas courant d'un thème. */
    public static ThemePalette ramp(String name, int dark, int mid, int light) {
        List<Stop> s = new ArrayList<>();
        s.add(new Stop(0.0, dark));
        s.add(new Stop(0.45, mid));
        s.add(new Stop(0.82, light));
        s.add(new Stop(1.0, lighten(light, 0.35)));   // hautes lumières légèrement éclaircies
        return new ThemePalette(name, s);
    }

    /** Couleur RGB (0xRRGGBB) à une luminance donnée, par interpolation linéaire entre paliers encadrants. */
    public int colorAt(double luminance) {
        double l = luminance < 0 ? 0 : (luminance > 1 ? 1 : luminance);
        Stop lo = stops.get(0);
        Stop hi = stops.get(stops.size() - 1);
        for (int i = 0; i < stops.size() - 1; i++) {
            Stop a = stops.get(i), b = stops.get(i + 1);
            if (l >= a.pos() && l <= b.pos()) { lo = a; hi = b; break; }
        }
        double span = hi.pos() - lo.pos();
        double t = span <= 1e-9 ? 0.0 : (l - lo.pos()) / span;
        return lerpRgb(lo.rgb(), hi.rgb(), t);
    }

    // ---- utilitaires couleur (purs) ----

    public static int rgb(int r, int g, int b) {
        return (clamp8(r) << 16) | (clamp8(g) << 8) | clamp8(b);
    }

    public static int lerpRgb(int c1, int c2, double t) {
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        int r = (int) Math.round(((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) Math.round(((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) Math.round((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return rgb(r, g, b);
    }

    /** Éclaircit une couleur vers le blanc de {@code f} ∈ [0,1]. */
    public static int lighten(int c, double f) {
        return lerpRgb(c, 0xFFFFFF, f);
    }

    /** Assombrit une couleur vers le noir de {@code f} ∈ [0,1]. */
    public static int darken(int c, double f) {
        return lerpRgb(c, 0x000000, f);
    }

    private static int clamp8(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }
}
