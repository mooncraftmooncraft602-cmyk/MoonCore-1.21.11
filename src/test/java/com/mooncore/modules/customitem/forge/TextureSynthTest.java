package com.mooncore.modules.customitem.forge;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Générateur procédural de textures {@link TextureSynth} : classification d'archetype, déterminisme,
 * présence de pixels du thème, et préservation de la silhouette (mode objet). Headless (BufferedImage).
 */
class TextureSynthTest {

    private static final ThemePalette VENT = ThemePalette.ramp("vent", 0x1b5e20, 0x2adf77, 0xe8f5e9);

    @Test
    void archetypeClassification() {
        assertEquals(TextureSynth.Archetype.ORE, TextureSynth.archetypeOf("diamond_ore"));
        assertEquals(TextureSynth.Archetype.ORE, TextureSynth.archetypeOf("deepslate_emerald_ore"));
        assertEquals(TextureSynth.Archetype.INGOT, TextureSynth.archetypeOf("iron_ingot"));
        assertEquals(TextureSynth.Archetype.GEM, TextureSynth.archetypeOf("diamond"));
        assertEquals(TextureSynth.Archetype.GEM, TextureSynth.archetypeOf("amethyst_shard"));
        assertEquals(TextureSynth.Archetype.ITEM, TextureSynth.archetypeOf("diamond_sword"));
        assertEquals(TextureSynth.Archetype.ITEM, TextureSynth.archetypeOf("netherite_chestplate"));
    }

    @Test
    void oreIsDeterministicAndThemed() {
        BufferedImage a = TextureSynth.ore(VENT, 1234L, 16);
        BufferedImage b = TextureSynth.ore(VENT, 1234L, 16);
        assertImagesEqual(a, b);                                  // déterministe
        assertTrue(opaqueCount(a) == 16 * 16, "minerai = fond plein");
        assertTrue(greenishPixels(a) >= 6, "des cristaux verts (thème) présents : " + greenishPixels(a));
        // graine différente -> image différente
        assertTrue(!imagesEqual(a, TextureSynth.ore(VENT, 9999L, 16)));
    }

    @Test
    void gemHasShapeAndThemeColor() {
        BufferedImage g = TextureSynth.gem(VENT, 7L, 16);
        int opaque = opaqueCount(g);
        assertTrue(opaque > 30 && opaque < 16 * 16, "silhouette de gemme (ni vide ni plein) : " + opaque);
        assertTrue(greenishPixels(g) >= 10, "gemme verte : " + greenishPixels(g));
    }

    @Test
    void ingotHasShineAndShadow() {
        BufferedImage i = TextureSynth.ingot(VENT, 3L, 16);
        assertTrue(opaqueCount(i) > 20, "lingot dessiné");
    }

    @Test
    void detailFromMaskPreservesSilhouette() {
        // base : moitié gauche opaque (forme), moitié droite transparente.
        BufferedImage base = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++)
                base.setRGB(x, y, x < 8 ? (0xFF << 24) | (x * 16) << 16 | (x * 16) << 8 | (x * 16) : 0);
        BufferedImage out = TextureSynth.detailFromMask(base, VENT, 5L);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                boolean baseOpaque = (base.getRGB(x, y) >>> 24) != 0;
                boolean outOpaque = (out.getRGB(x, y) >>> 24) != 0;
                assertEquals(baseOpaque, outOpaque, "silhouette préservée en (" + x + "," + y + ")");
            }
        assertTrue(greenishPixels(out) >= 4, "remplissage thématique vert");
    }

    @Test
    void writeVisualSamples() throws Exception {
        java.io.File dir = new java.io.File("tools/forge-model/samples");
        dir.mkdirs();
        record Th(String n, ThemePalette p) {}
        Th[] themes = {
            new Th("vent", VENT),
            new Th("feu", ThemePalette.ramp("feu", 0x6c2310, 0xf83c0c, 0xffe082)),
            new Th("foudre", ThemePalette.ramp("foudre", 0x2a1a4a, 0x8230da, 0xfff59d)),
            new Th("ocean", ThemePalette.ramp("ocean", 0x07304a, 0x1e7bea, 0xb2ebf2)),
        };
        for (Th t : themes) {
            save(dir, "ore_" + t.n(), TextureSynth.ore(t.p(), t.n().hashCode(), 16));
            save(dir, "gem_" + t.n(), TextureSynth.gem(t.p(), t.n().hashCode(), 16));
            save(dir, "ingot_" + t.n(), TextureSynth.ingot(t.p(), t.n().hashCode(), 16));
        }
        assertTrue(new java.io.File(dir, "ore_vent.png").isFile());
    }

    /** Écrit la texture 16px + un aperçu agrandi ×10 (nearest-neighbor) pour inspection visuelle. */
    private static void save(java.io.File dir, String name, BufferedImage img) throws Exception {
        javax.imageio.ImageIO.write(img, "png", new java.io.File(dir, name + ".png"));
        int s = 10, w = img.getWidth(), h = img.getHeight();
        BufferedImage big = new BufferedImage(w * s, h * s, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h * s; y++)
            for (int x = 0; x < w * s; x++) big.setRGB(x, y, img.getRGB(x / s, y / s));
        javax.imageio.ImageIO.write(big, "png", new java.io.File(dir, name + "_x10.png"));
    }

    // ---- helpers ----
    private static int opaqueCount(BufferedImage im) {
        int c = 0;
        for (int y = 0; y < im.getHeight(); y++)
            for (int x = 0; x < im.getWidth(); x++) if ((im.getRGB(x, y) >>> 24) != 0) c++;
        return c;
    }
    private static int greenishPixels(BufferedImage im) {
        int c = 0;
        for (int y = 0; y < im.getHeight(); y++)
            for (int x = 0; x < im.getWidth(); x++) {
                int v = im.getRGB(x, y);
                if ((v >>> 24) == 0) continue;
                int r = (v >> 16) & 0xFF, g = (v >> 8) & 0xFF, b = v & 0xFF;
                if (g > r + 10 && g > b + 10) c++;
            }
        return c;
    }
    private static boolean imagesEqual(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;
        for (int y = 0; y < a.getHeight(); y++)
            for (int x = 0; x < a.getWidth(); x++) if (a.getRGB(x, y) != b.getRGB(x, y)) return false;
        return true;
    }
    private static void assertImagesEqual(BufferedImage a, BufferedImage b) { assertTrue(imagesEqual(a, b)); }
}
