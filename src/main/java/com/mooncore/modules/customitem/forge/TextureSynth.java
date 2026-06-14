package com.mooncore.modules.customitem.forge;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Génère de <b>vraies textures d'item détaillées</b> (pixel-art façon modpack) côté serveur, sans IA ni
 * dépendance : à partir d'un type d'item (minerai/gemme/lingot/outil…) et d'une {@link ThemePalette}, dessine
 * un PNG par un pipeline déterministe (masque → structure par bruit → ombrage par luminance → contour). Bien
 * plus riche qu'un simple recolorage. Déterministe (graine = hash du nom) → reproductible.
 */
public final class TextureSynth {

    private TextureSynth() {}

    public enum Archetype { ORE, GEM, INGOT, ITEM }

    private static final String[] ITEM_WORDS = {
        "sword", "axe", "pickaxe", "shovel", "hoe", "blade", "dagger", "hammer", "mace", "trident",
        "bow", "crossbow", "shield", "sceptre", "scepter", "staff", "wand", "spear", "scythe", "katana",
        "helmet", "chestplate", "leggings", "boots", "cap", "tunic", "hilt", "stick", "rod", "horse_armor"
    };

    /** Devine l'archetype depuis le nom de la texture de base (diamond_ore→minerai, emerald→gemme, _sword→objet…). */
    public static Archetype archetypeOf(String baseName) {
        String n = baseName == null ? "" : baseName.toLowerCase(Locale.ROOT);
        if (n.contains("ore") || n.contains("minerai")) return Archetype.ORE;
        for (String w : ITEM_WORDS) if (n.contains(w)) return Archetype.ITEM;   // outils/armes/armures d'abord
        if (n.contains("ingot") || n.contains("nugget") || n.contains("lingot")) return Archetype.INGOT;
        if (n.contains("gem") || n.contains("crystal") || n.contains("shard") || n.contains("diamond")
                || n.contains("emerald") || n.contains("amethyst") || n.contains("quartz")
                || n.contains("lapis") || n.contains("ruby") || n.contains("sapphire") || n.contains("pearl"))
            return Archetype.GEM;
        return Archetype.ITEM;
    }

    private static int argb(int a, int rgb) { return (a << 24) | (rgb & 0xFFFFFF); }
    private static void set(BufferedImage img, int x, int y, int v) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) img.setRGB(x, y, v);
    }
    private static int alpha(int argb) { return (argb >>> 24) & 0xFF; }

    /** Texture finale pour un archetype donné. {@code base} = texture vanilla (silhouette), peut être null. */
    public static BufferedImage synthesize(Archetype type, BufferedImage base, ThemePalette p, long seed, int size) {
        return switch (type) {
            case ORE -> ore(p, seed, size);
            case GEM -> gem(p, seed, size);
            case INGOT -> ingot(p, seed, size);
            case ITEM -> base != null ? detailFromMask(base, p, seed) : gem(p, seed, size);
        };
    }

    // ----------------------------- MINERAI -----------------------------

    public static BufferedImage ore(ThemePalette p, long seed, int size) {
        Random rng = new Random(seed);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        // 1) fond pierre par bruit de valeur, quantifié sur 4 gris (deepslate)
        float[][] n = valueNoise(size, 4, rng);
        int[] stone = {0x26262b, 0x33333a, 0x42424a, 0x515159};
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                set(img, x, y, argb(255, stone[clamp((int) (n[x][y] * 4), 0, 3)]));

        // 2) amas de cristaux (graines Poisson) du thème, avec reflet/ombre
        int mid = p.colorAt(0.55), lite = p.colorAt(0.85), dark = p.colorAt(0.22);
        boolean[][] cr = new boolean[size][size];
        int seeds = Math.max(4, size / 3) + rng.nextInt(3);
        for (int[] s : poisson(size, seeds, Math.max(3, size / 5), rng)) {
            int len = 3 + rng.nextInt(size / 4 + 2);
            int cx = s[0], cy = s[1];
            for (int i = 0; i < len; i++) {
                if (cx >= 0 && cy >= 0 && cx < size && cy < size) {
                    cr[cx][cy] = true;
                    set(img, cx, cy, argb(255, mid));
                }
                cx += rng.nextInt(3) - 1; cy += rng.nextInt(3) - 1;     // marche aléatoire = amas contigu
            }
        }
        // 3) reflet (haut-gauche), ombre (bas-droite), contour sombre autour des amas
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                if (!cr[x][y]) {
                    if (neigh(cr, x, y)) set(img, x, y, argb(255, ThemePalette.darken(stone(img, x, y), 0.45)));
                    continue;
                }
                if (!get(cr, x - 1, y) || !get(cr, x, y - 1)) set(img, x, y, argb(255, lite));
                else if (!get(cr, x + 1, y) || !get(cr, x, y + 1)) set(img, x, y, argb(255, dark));
            }
        return img;
    }

    // ----------------------------- GEMME -----------------------------

    public static BufferedImage gem(ThemePalette p, long seed, int size) {
        Random rng = new Random(seed);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        double cx = (size - 1) / 2.0, cy = (size - 1) / 2.0, r = size * 0.46;
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                double dx = Math.abs(x - cx), dy = Math.abs(y - cy);
                if (dx + dy > r) continue;                                  // masque losange
                double t = 1.0 - (y / (double) (size - 1));                 // gradient vertical (clair en haut)
                // facettes : léger décalage selon le côté + petit bruit
                double facet = ((x < cx) ? 0.08 : -0.08) + (rng.nextDouble() - 0.5) * 0.10;
                int col = p.colorAt(clampf(0.25 + 0.6 * t + facet));
                set(img, x, y, argb(255, col));
            }
        // arêtes de facettes (diagonales) + éclat + contour
        int lite = p.colorAt(0.95), dark = p.colorAt(0.18);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                if (alpha(img.getRGB(x, y)) == 0) continue;
                double dx = Math.abs(x - cx), dy = Math.abs(y - cy);
                if (dx + dy > r - 1.0) set(img, x, y, argb(255, dark));      // contour sombre
                else if (Math.abs((x - cx) - (y - cy)) < 0.6 || Math.abs((x - cx) + (y - cy)) < 0.6)
                    set(img, x, y, argb(255, ThemePalette.lighten(img.getRGB(x, y) & 0xFFFFFF, 0.25)));
            }
        set(img, (int) (cx - size * 0.18), (int) (cy - size * 0.18), argb(255, lite));   // éclat
        return img;
    }

    // ----------------------------- LINGOT -----------------------------

    public static BufferedImage ingot(ThemePalette p, long seed, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        int top = (int) (size * 0.30), bot = (int) (size * 0.78);
        int mid = p.colorAt(0.5), lite = p.colorAt(0.85), dark = p.colorAt(0.2), edge = p.colorAt(0.12);
        for (int y = top; y <= bot; y++) {
            double k = (y - top) / (double) (bot - top);
            int inset = (int) Math.round((1 - k) * size * 0.10) + (int) (size * 0.14);  // trapèze incliné
            int x0 = inset, x1 = size - 1 - inset + (int) (size * 0.06);
            for (int x = x0; x <= x1 && x < size; x++) {
                int col = mid;
                if (y <= top + 1 || x == x0) col = lite;                    // brillance haut/gauche
                else if (y >= bot - 1 || x == x1) col = dark;               // ombre bas/droite
                set(img, x, y, argb(255, col));
            }
            set(img, x0 - 1, y, argb(255, edge));                           // contour
            if (x1 + 1 < size) set(img, x1 + 1, y, argb(255, edge));
        }
        for (int x = 0; x < size; x++) {                                   // contours haut/bas
            if (alpha(img.getRGB(x, top)) != 0) set(img, x, top - 1, argb(255, edge));
            if (bot + 1 < size && alpha(img.getRGB(x, bot)) != 0) set(img, x, bot + 1, argb(255, edge));
        }
        return img;
    }

    // ----------------------------- OUTIL/ARMURE (silhouette vanilla + détails) -----------------------------

    /**
     * Recolore la silhouette vanilla par luminance (dégradé du thème) PUIS ajoute un <b>contour sombre</b> sur
     * le bord et un <b>reflet</b> diagonal — rendu nettement plus détaillé qu'un simple aplat recoloré.
     */
    public static BufferedImage detailFromMask(BufferedImage base, ThemePalette p, long seed) {
        int w = base.getWidth(), h = base.getHeight();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int dark = p.colorAt(0.12);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int src = base.getRGB(x, y);
                if (alpha(src) == 0) { img.setRGB(x, y, 0); continue; }
                double l = TextureRecolorer.luminance(src & 0xFFFFFF);
                img.setRGB(x, y, argb(alpha(src), p.colorAt(l)));
            }
        // contour : pixel opaque ayant un voisin transparent → assombri
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int v = img.getRGB(x, y);
                if (alpha(v) == 0) { out.setRGB(x, y, 0); continue; }
                if (transparentNeighbor(base, x, y)) out.setRGB(x, y, argb(alpha(v), dark));
                else out.setRGB(x, y, v);
            }
        // reflet diagonal léger (sheen) sur ~1 pixel sur deux d'une bande
        Random rng = new Random(seed);
        int band = rng.nextInt(Math.max(1, w / 4)) + w / 4;
        int liteC = p.colorAt(0.92);
        for (int y = 0; y < h; y++) {
            int x = band - y;
            if (x >= 0 && x < w && alpha(out.getRGB(x, y)) != 0 && !transparentNeighbor(base, x, y))
                out.setRGB(x, y, argb(alpha(out.getRGB(x, y)), ThemePalette.lerpRgb(out.getRGB(x, y) & 0xFFFFFF, liteC, 0.5)));
        }
        return out;
    }

    // ----------------------------- helpers -----------------------------

    private static float[][] valueNoise(int size, int cell, Random rng) {
        int g = size / cell + 2;
        float[][] lat = new float[g][g];
        for (int i = 0; i < g; i++) for (int j = 0; j < g; j++) lat[i][j] = rng.nextFloat();
        float[][] out = new float[size][size];
        for (int x = 0; x < size; x++)
            for (int y = 0; y < size; y++) {
                float gx = x / (float) cell, gy = y / (float) cell;
                int ix = (int) gx, iy = (int) gy;
                float fx = gx - ix, fy = gy - iy;
                float a = lerp(lat[ix][iy], lat[ix + 1][iy], fx);
                float b = lerp(lat[ix][iy + 1], lat[ix + 1][iy + 1], fx);
                out[x][y] = lerp(a, b, fy);
            }
        return out;
    }

    /** Graines réparties (rejet Poisson-disk : distance min {@code d}). */
    private static List<int[]> poisson(int size, int count, int d, Random rng) {
        List<int[]> pts = new ArrayList<>();
        int tries = 0;
        while (pts.size() < count && tries++ < count * 30) {
            int x = 1 + rng.nextInt(size - 2), y = 1 + rng.nextInt(size - 2);
            boolean ok = true;
            for (int[] q : pts) if (Math.abs(q[0] - x) + Math.abs(q[1] - y) < d) { ok = false; break; }
            if (ok) pts.add(new int[]{x, y});
        }
        return pts;
    }

    private static boolean get(boolean[][] b, int x, int y) {
        return x >= 0 && y >= 0 && x < b.length && y < b[0].length && b[x][y];
    }
    private static boolean neigh(boolean[][] b, int x, int y) {
        return get(b, x - 1, y) || get(b, x + 1, y) || get(b, x, y - 1) || get(b, x, y + 1);
    }
    private static int stone(BufferedImage img, int x, int y) { return img.getRGB(x, y) & 0xFFFFFF; }

    private static boolean transparentNeighbor(BufferedImage img, int x, int y) {
        int w = img.getWidth(), h = img.getHeight();
        return x == 0 || y == 0 || x == w - 1 || y == h - 1
                || alpha(img.getRGB(x - 1, y)) == 0 || alpha(img.getRGB(x + 1, y)) == 0
                || alpha(img.getRGB(x, y - 1)) == 0 || alpha(img.getRGB(x, y + 1)) == 0;
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }
    private static double clampf(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
}
