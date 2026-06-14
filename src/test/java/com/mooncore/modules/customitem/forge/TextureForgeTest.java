package com.mooncore.modules.customitem.forge;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Forge de textures intelligente (pure) : rampe de palette, recolorisation par luminance (forme/alpha
 * préservés, teintes remplacées), et résolution thématique du nom (mots-clés FR/EN + repli déterministe).
 * Manipulation de {@link BufferedImage} ARGB en mémoire — sans affichage, headless.
 */
class TextureForgeTest {

    private static int argb(int a, int rgb) { return (a << 24) | (rgb & 0xFFFFFF); }

    // ---- Palette ----

    @Test
    void rampInterpolatesByLuminanceWithClampedEnds() {
        ThemePalette p = ThemePalette.ramp("t", 0x000000, 0x808080, 0xffffff);
        assertEquals(0x000000, p.colorAt(0.0));      // ombres → couleur sombre
        assertEquals(0xffffff, p.colorAt(1.0));      // hautes lumières → claire (paliers bornés)
        int mid = p.colorAt(0.45);                   // au palier moyen
        assertEquals(0x808080, mid);
    }

    @Test
    void lerpAndHslAreSane() {
        assertEquals(0x808080, ThemePalette.lerpRgb(0x000000, 0xffffff, 0.5));
        assertEquals(0xffffff, ThemePalette.lighten(0x000000, 1.0));
        assertEquals(0x000000, ThemePalette.darken(0xffffff, 1.0));
        assertEquals(0xff0000, PaletteResolver.hsl(0, 1.0, 0.5));   // rouge pur
    }

    // ---- Recolorisation ----

    @Test
    void transparentPixelsUntouchedAlphaPreserved() {
        ThemePalette p = ThemePalette.ramp("t", 0x102030, 0x405060, 0x7080a0);
        assertEquals(argb(0, 0x123456), TextureRecolorer.recolorPixel(argb(0, 0x123456), p, 1.0)); // alpha 0 intouché
        int out = TextureRecolorer.recolorPixel(argb(200, 0x808080), p, 1.0);
        assertEquals(200, (out >>> 24) & 0xFF);     // alpha conservé (forme préservée)
    }

    @Test
    void strengthZeroKeepsOriginalColor() {
        ThemePalette p = ThemePalette.ramp("t", 0xff0000, 0x00ff00, 0x0000ff);
        int original = argb(255, 0x336699);
        assertEquals(original, TextureRecolorer.recolorPixel(original, p, 0.0));
    }

    @Test
    void darkPixelMapsToDarkStopLightPixelToLightStop() {
        // Rampe vert→blanc (thème "vent") : un pixel sombre devient verdâtre, un pixel clair devient blanchâtre.
        ThemePalette p = ThemePalette.ramp("vent", 0x1b5e20, 0x66bb6a, 0xe8f5e9);
        int darkOut = TextureRecolorer.recolorPixel(argb(255, 0x202020), p, 1.0) & 0xFFFFFF;
        int lightOut = TextureRecolorer.recolorPixel(argb(255, 0xf0f0f0), p, 1.0) & 0xFFFFFF;
        assertTrue(green(darkOut) > red(darkOut) && green(darkOut) > blue(darkOut)); // dominante verte
        assertTrue(red(lightOut) > 200 && green(lightOut) > 200 && blue(lightOut) > 200); // quasi blanc
        assertTrue(lum(lightOut) > lum(darkOut));    // l'ordre des luminances (ombrage) est préservé
    }

    @Test
    void recolorPreservesDimensionsAndShape() {
        BufferedImage base = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        // moitié opaque (forme), moitié transparente
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++)
                base.setRGB(x, y, x < 8 ? argb(255, (x * 16) << 16 | (x * 16) << 8 | (x * 16)) : 0);
        BufferedImage out = TextureRecolorer.recolor(base, PaletteResolver.fromName("epee du vent"), 1.0);
        assertEquals(16, out.getWidth());
        assertEquals(16, out.getHeight());
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                boolean opaqueBase = ((base.getRGB(x, y) >>> 24) & 0xFF) != 0;
                boolean opaqueOut = ((out.getRGB(x, y) >>> 24) & 0xFF) != 0;
                assertEquals(opaqueBase, opaqueOut);   // la forme (alpha) est rigoureusement conservée
            }
    }

    // ---- Résolution de palette par nom ----

    @Test
    void nameKeywordsPickTheme() {
        assertEquals("vent", PaletteResolver.fromName("Épée du Vent").name());      // accents ignorés
        assertEquals("feu", PaletteResolver.fromName("Lame de Feu ardente").name());
        assertEquals("glace", PaletteResolver.fromName("Hache de Glace").name());
        assertEquals("ombre", PaletteResolver.fromName("Dague des Ténèbres").name());
        assertEquals("or", PaletteResolver.fromName("Plastron Royal Divin").name()); // marche pour armures
        assertEquals("cristal", PaletteResolver.fromName("Pioche de Diamant").name());// marche pour minerais/outils
    }

    @Test
    void unknownNameFallsBackDeterministically() {
        ThemePalette a = PaletteResolver.fromName("Bidule Quelconque 42");
        ThemePalette b = PaletteResolver.fromName("Bidule Quelconque 42");
        assertEquals(a.name(), b.name());                          // déterministe (même nom → même palette)
        assertTrue(a.name().startsWith("auto:"));
        assertNotEquals(PaletteResolver.fromName("Truc A").name(), PaletteResolver.fromName("Truc Z").name());
    }

    // ---- Résolution des chemins de texture vanilla ----

    @Test
    void vanillaEntryCandidatesTryItemThenBlock() {
        assertEquals(
            java.util.List.of(
                "assets/minecraft/textures/item/diamond_sword.png",
                "assets/minecraft/textures/block/diamond_sword.png"),
            VanillaTextureProvider.entryCandidates("diamond_sword"));
        // préfixe minecraft: et .png tolérés ; chemin explicite respecté
        assertEquals(
            java.util.List.of("assets/minecraft/textures/block/deepslate_diamond_ore.png"),
            VanillaTextureProvider.entryCandidates("block/deepslate_diamond_ore"));
        assertEquals(
            java.util.List.of(
                "assets/minecraft/textures/item/netherite_chestplate.png",
                "assets/minecraft/textures/block/netherite_chestplate.png"),
            VanillaTextureProvider.entryCandidates("minecraft:Netherite_Chestplate.png"));
    }

    // ---- Slug d'item depuis un nom libre ----

    @Test
    void slugNormalizesName() {
        assertEquals("epee_du_vent", ForgeService.slug("Épée du Vent"));
        assertEquals("lame_de_feu", ForgeService.slug("  Lame   de Feu !! "));
        assertEquals("item_forge", ForgeService.slug("???"));          // repli si vide
        assertEquals("dragon", ForgeService.slug("Dragon"));
    }

    // ---- Parsing de palette IA ----

    @Test
    void aiExtractColorsFindsHexAnywhere() {
        assertEquals(java.util.List.of(0x1b5e20, 0x66bb6a, 0xe8f5e9),
                ForgePaletteAI.extractColors("Voici: #1b5e20 puis #66bb6a et enfin #e8f5e9 !"));
        assertTrue(ForgePaletteAI.extractColors("aucune couleur").isEmpty());
        assertTrue(ForgePaletteAI.extractColors(null).isEmpty());
    }

    @Test
    void aiPaletteSortsByLuminanceAndFallsBack() {
        // Couleurs données dans le désordre → triées sombre→clair pour la rampe.
        ThemePalette p = ForgePaletteAI.paletteFromText("test", "#e8f5e9 #1b5e20 #66bb6a");
        assertEquals(0x1b5e20, p.colorAt(0.0));     // la plus sombre en bas
        assertEquals(0xe8f5e9, p.colorAt(0.82));    // la plus claire en haut
        // Moins de 2 couleurs → repli déterministe sur le résolveur par nom.
        assertEquals(PaletteResolver.fromName("Épée du Vent").name(),
                ForgePaletteAI.paletteFromText("Épée du Vent", "pas de hex ici").name());
    }

    // ---- helpers ----
    private static int red(int c) { return (c >> 16) & 0xFF; }
    private static int green(int c) { return (c >> 8) & 0xFF; }
    private static int blue(int c) { return c & 0xFF; }
    private static double lum(int c) { return TextureRecolorer.luminance(c); }
}
