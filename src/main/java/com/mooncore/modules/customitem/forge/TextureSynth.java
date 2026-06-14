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

    // ------------------- ARMES / OUTILS / ARMURES DESSINÉS (pixel-art from scratch) -------------------

    /** Sous-type d'objet déduit du nom de base, pour choisir le dessin approprié. */
    public enum Kind { SWORD, PICKAXE, AXE, HELMET, CHESTPLATE, GENERIC }

    public static Kind itemKind(String baseName) {
        String n = baseName == null ? "" : baseName.toLowerCase(Locale.ROOT);
        if (n.contains("pickaxe") || n.contains("pioche")) return Kind.PICKAXE;       // avant "axe"
        if (n.contains("axe") || n.contains("hache") || n.contains("hatchet")) return Kind.AXE;
        if (n.contains("sword") || n.contains("blade") || n.contains("dagger") || n.contains("katana")
                || n.contains("sabre") || n.contains("epee") || n.contains("lame")) return Kind.SWORD;
        if (n.contains("helmet") || n.contains("cap") || n.contains("hood") || n.contains("crown")
                || n.contains("casque") || n.contains("heaume")) return Kind.HELMET;
        if (n.contains("chestplate") || n.contains("tunic") || n.contains("plastron")
                || n.contains("chest") || n.contains("plate")) return Kind.CHESTPLATE;
        return Kind.GENERIC;
    }

    /** Dessine un objet (arme/outil/armure) depuis zéro selon son sous-type ; repli recolorage sinon. */
    public static BufferedImage drawTool(String baseName, BufferedImage base, ThemePalette p, long seed) {
        return switch (itemKind(baseName)) {
            case SWORD -> drawSword(p, seed);
            case PICKAXE -> drawPickaxe(p, seed);
            case AXE -> drawAxe(p, seed);
            case HELMET -> drawHelmet(p, seed);
            case CHESTPLATE -> drawChestplate(p, seed);
            case GENERIC -> base != null ? detailFromMask(base, p, seed) : gem(p, seed, 16);
        };
    }

    private static final ThemePalette WOOD = ThemePalette.ramp("wood", 0x2c1d10, 0x5a4026, 0x8a6a3e);
    private static final ThemePalette STEEL = ThemePalette.ramp("steel", 0x3a3d44, 0x80858f, 0xccd0d8);
    private static final ThemePalette GOLD = ThemePalette.ramp("gold", 0x5a3c0e, 0xc9971f, 0xffe8a8);
    private static final int MAT_METAL = 1, MAT_WOOD = 2, MAT_STEEL = 3, MAT_GOLD = 4;

    public static BufferedImage drawSword(ThemePalette p, long seed) {
        int[][] m = new int[16][16];
        capsule(m, MAT_WOOD, 4.2, 11.0, 2.6, 13.8, 1.0, false);   // poignée
        capsule(m, MAT_GOLD, 3.5, 11.4, 4.3, 12.6, 0.9, false);   // virole dorée
        disc(m, MAT_GOLD, 2.2, 13.8, 1.5);                        // pommeau doré
        capsule(m, MAT_GOLD, 2.7, 7.7, 7.3, 12.3, 1.1, false);    // garde dorée (s'étend)
        capsule(m, MAT_METAL, 5.0, 10.2, 13.7, 1.6, 1.7, true);   // lame (s'affine vers la pointe)
        BufferedImage img = shade(m, p);
        fuller(img, 6.0, 9.4, 12.8, 2.6);                         // rainure centrale de la lame
        jewel(img, 5.0, 10.0, 1.6, p);                            // joyau serti dans la garde
        glint(img, 12, 3, p); glint(img, 10, 5, p);              // éclats sur la lame
        return img;
    }

    public static BufferedImage drawPickaxe(ThemePalette p, long seed) {
        int[][] m = new int[16][16];
        capsule(m, MAT_WOOD, 10.8, 14.4, 8.2, 6.4, 1.0, false);   // manche
        capsule(m, MAT_GOLD, 8.9, 7.4, 9.7, 8.8, 0.9, false);     // virole dorée
        capsule(m, MAT_METAL, 8.0, 4.6, 2.2, 6.6, 1.7, true);     // tête : moitié gauche (pointe)
        capsule(m, MAT_METAL, 8.0, 4.6, 13.8, 3.0, 1.7, true);    // tête : moitié droite (pointe)
        disc(m, MAT_GOLD, 8.0, 5.6, 1.8);                         // socle doré (jonction)
        BufferedImage img = shade(m, p);
        jewel(img, 8.0, 5.4, 1.6, p);                             // joyau central
        glint(img, 4, 5, p); glint(img, 12, 4, p);
        return img;
    }

    public static BufferedImage drawAxe(ThemePalette p, long seed) {
        int[][] m = new int[16][16];
        capsule(m, MAT_WOOD, 12.6, 14.6, 5.6, 3.0, 1.0, false);   // manche
        disc(m, MAT_METAL, 4.7, 5.9, 3.1);                        // corps de tête
        capsule(m, MAT_METAL, 6.4, 2.6, 2.4, 5.0, 1.7, false);    // dos haut
        capsule(m, MAT_METAL, 2.4, 5.0, 5.0, 9.6, 1.9, false);    // tranchant (gauche)
        capsule(m, MAT_GOLD, 6.6, 3.2, 6.0, 8.6, 0.7, false);     // liseré doré (dos)
        disc(m, MAT_GOLD, 6.6, 5.4, 1.4);                         // socle doré (jonction manche)
        BufferedImage img = shade(m, p);
        jewel(img, 4.6, 5.8, 1.6, p);                             // joyau sur la tête
        glint(img, 2, 6, p); glint(img, 3, 4, p);
        return img;
    }

    public static BufferedImage drawHelmet(ThemePalette p, long seed) {
        int[][] m = new int[16][16];
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double dx = (x - 8.0) / 6.2, dy = (y - 7.5) / 6.0;
            boolean dome = dx * dx + dy * dy <= 1.0 && y >= 2 && y <= 13;
            boolean slit = y >= 7 && y <= 8 && x >= 4 && x <= 11;     // fente des yeux (vide)
            if (dome && !slit) m[x][y] = MAT_METAL;
        }
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {   // liserés dorés
            if (m[x][y] != MAT_METAL) continue;
            if (y >= 11) m[x][y] = MAT_GOLD;                          // bandeau du bas
            else if ((y == 6 || y == 9) && x >= 4 && x <= 11) m[x][y] = MAT_GOLD;  // cadre de la fente
        }
        BufferedImage img = shade(m, p);
        jewel(img, 8.0, 4.6, 1.5, p);                                // joyau frontal
        glint(img, 5, 3, p); glint(img, 11, 4, p);
        return img;
    }

    public static BufferedImage drawChestplate(ThemePalette p, long seed) {
        int[][] m = new int[16][16];
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            boolean torso = x >= 3 && x <= 12 && y >= 3 && y <= 14
                    && !(y <= 4 && x >= 6 && x <= 9);                 // encoche du col
            boolean pauldron = y >= 2 && y <= 5 && ((x >= 2 && x <= 4) || (x >= 11 && x <= 13));
            if (torso || pauldron) m[x][y] = MAT_METAL;
        }
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {   // liserés dorés (col + épaules)
            if (m[x][y] != MAT_METAL) continue;
            if (y == 5 && (x <= 5 || x >= 10)) m[x][y] = MAT_GOLD;    // bandeau des épaules
            else if ((x == 5 || x == 10) && y >= 5 && y <= 6) m[x][y] = MAT_GOLD;
        }
        BufferedImage img = shade(m, p);
        jewel(img, 7.5, 8.8, 1.7, p);                                // joyau au centre du torse
        glint(img, 4, 4, p); glint(img, 11, 4, p);
        return img;
    }

    // -- moteur de dessin partagé : masque matière -> couleur (lumière haut-gauche) + contour 1px --

    private static void capsule(int[][] m, int val, double ax, double ay, double bx, double by, double hw, boolean taper) {
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double[] c = segCoord(x + 0.5, y + 0.5, ax, ay, bx, by);
            double t = c[0], sd = Math.abs(c[1]);
            if (t < -0.05 || t > 1.05) continue;
            double w = taper ? hw * (1.0 - 0.75 * Math.max(0, t)) : hw;   // effilage vers b
            if (sd <= w) m[x][y] = val;
        }
    }

    private static void disc(int[][] m, int val, double cx, double cy, double r) {
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
            if (Math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r) m[x][y] = val;
    }

    /** Joyau facetté serti (post-traitement) : disque dégradé haut-gauche, rebord sombre, éclat blanc central. */
    private static void jewel(BufferedImage img, double cx, double cy, double r, ThemePalette p) {
        int dark = p.colorAt(0.55), lite = p.colorAt(0.98), rim = ThemePalette.darken(p.colorAt(0.28), 0.30);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
            if (d > r) continue;
            double f = clampf(0.5 + 0.55 * (((cx - x - 0.5) + (cy - y - 0.5)) / (2 * r)));  // facette haut-gauche
            int col = d > r - 0.7 ? rim : ThemePalette.lerpRgb(dark, lite, f);
            set(img, x, y, argb(255, col));
        }
        set(img, (int) Math.round(cx - 0.4 * r), (int) Math.round(cy - 0.4 * r),
                argb(255, ThemePalette.lighten(p.colorAt(0.95), 0.6)));                     // éclat
    }

    /** Éclat spéculaire (1px quasi-blanc) si le pixel cible est déjà opaque. */
    private static void glint(BufferedImage img, int x, int y, ThemePalette p) {
        if (x >= 0 && y >= 0 && x < 16 && y < 16 && alpha(img.getRGB(x, y)) != 0)
            set(img, x, y, argb(255, ThemePalette.lighten(p.colorAt(0.95), 0.5)));
    }

    /** Rainure centrale (fuller) : assombrit légèrement une bande le long d'un segment, sans toucher les bords. */
    private static void fuller(BufferedImage img, double ax, double ay, double bx, double by) {
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            if (alpha(img.getRGB(x, y)) == 0) continue;
            double[] c = segCoord(x + 0.5, y + 0.5, ax, ay, bx, by);
            if (c[0] < 0 || c[0] > 1 || Math.abs(c[1]) > 0.45) continue;
            if (opaqueNeighbor4(img, x, y)) set(img, x, y, argb(255, ThemePalette.darken(img.getRGB(x, y) & 0xFFFFFF, 0.20)));
        }
    }

    /** Vrai si les 4 voisins orthogonaux sont opaques (pixel intérieur, pas un bord). */
    private static boolean opaqueNeighbor4(BufferedImage img, int x, int y) {
        for (int[] d : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            int nx = x + d[0], ny = y + d[1];
            if (nx < 0 || ny < 0 || nx >= 16 || ny >= 16 || alpha(img.getRGB(nx, ny)) == 0) return false;
        }
        return true;
    }

    /** Masque matière → image : aplat thématique ombré (lumière haut-gauche) + arêtes + contour sombre. */
    private static BufferedImage shade(int[][] m, ThemePalette p) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int mat = m[x][y];
            if (mat == 0) continue;
            ThemePalette ramp = mat == MAT_WOOD ? WOOD : mat == MAT_STEEL ? STEEL : mat == MAT_GOLD ? GOLD : p;
            double g = clampf(0.52 + 0.30 * (((7.5 - x) + (7.5 - y)) / 15.0));   // dégradé haut-gauche
            int col;
            if (empty(m, x - 1, y) || empty(m, x, y - 1)) col = ramp.colorAt(clampf(g + 0.34));   // arête éclairée
            else if (empty(m, x + 1, y) || empty(m, x, y + 1)) col = ramp.colorAt(clampf(g - 0.30)); // ombre
            else col = ramp.colorAt(g);
            set(img, x, y, argb(255, col));
        }
        int outline = ThemePalette.darken(p.colorAt(0.16), 0.45);
        BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            if (alpha(img.getRGB(x, y)) != 0) { out.setRGB(x, y, img.getRGB(x, y)); continue; }
            if (opaqueNeighbor(img, x, y)) out.setRGB(x, y, argb(255, outline));   // contour extérieur 1px
        }
        return out;
    }

    private static boolean empty(int[][] m, int x, int y) {
        return x < 0 || y < 0 || x >= 16 || y >= 16 || m[x][y] == 0;
    }
    private static boolean opaqueNeighbor(BufferedImage img, int x, int y) {
        int w = img.getWidth(), h = img.getHeight();
        for (int[] d : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            int nx = x + d[0], ny = y + d[1];
            if (nx >= 0 && ny >= 0 && nx < w && ny < h && alpha(img.getRGB(nx, ny)) != 0) return true;
        }
        return false;
    }
    private static double[] segCoord(double px, double py, double ax, double ay, double bx, double by) {
        double vx = bx - ax, vy = by - ay, L = Math.hypot(vx, vy);
        if (L < 1e-9) return new double[]{0, Math.hypot(px - ax, py - ay)};
        double t = ((px - ax) * vx + (py - ay) * vy) / (L * L);
        double sd = ((px - ax) * vy - (py - ay) * vx) / L;
        return new double[]{t, sd};
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
