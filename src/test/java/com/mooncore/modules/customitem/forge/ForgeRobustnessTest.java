package com.mooncore.modules.customitem.forge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Robustesse de la forge sur des noms variés et le choix de couleurs par l'utilisateur :
 * <ul>
 *   <li>n'importe quel nom donne une palette (thème reconnu, ou repli déterministe) — ex. « épée lunaire » ;</li>
 *   <li>parsing des couleurs explicites {@code #rrggbb} (mode « je choisis ») ;</li>
 *   <li>suggestion (hexStops) pour conseiller des couleurs.</li>
 * </ul>
 * Pur, sans serveur.
 */
class ForgeRobustnessTest {

    // ---- Tout nom marche (thème reconnu) ----

    @Test
    void variedNamesResolveToExpectedThemes() {
        assertEquals("lune", PaletteResolver.fromName("Épée Lunaire").name());        // « épée lunaire »
        assertEquals("lune", PaletteResolver.fromName("Hache de la Lune").name());
        assertEquals("vent", PaletteResolver.fromName("Arc de la Tempête").name());
        assertEquals("feu", PaletteResolver.fromName("Marteau du Brasier").name());
        assertEquals("glace", PaletteResolver.fromName("Bottes Polaires").name());
        assertEquals("nature", PaletteResolver.fromName("Bouclier de la Forêt").name());
        assertEquals("ocean", PaletteResolver.fromName("Trident des Océans").name());
        assertEquals("arcane", PaletteResolver.fromName("Bâton du Sorcier").name());
        assertEquals("acier", PaletteResolver.fromName("Lame d'Acier").name());
        assertEquals("sang", PaletteResolver.fromName("Dague Sanguine").name());
    }

    @Test
    void anyUnknownNameStillGivesDeterministicPalette() {
        // Pas de thème -> repli par hash, mais TOUJOURS une palette (déterministe).
        ThemePalette a = PaletteResolver.fromName("Zorglub Machin 77");
        ThemePalette b = PaletteResolver.fromName("Zorglub Machin 77");
        assertTrue(a.name().startsWith("auto:"));
        assertEquals(a.name(), b.name());
        assertTrue(a.hexStops().size() >= 3);   // une vraie rampe (dégradé) est produite
    }

    // ---- L'utilisateur choisit / on conseille ----

    @Test
    void hexDetectionAndParsing() {
        assertTrue(ForgeColors.isHex("#1b5e20"));
        assertTrue(ForgeColors.isHex("1b5e20"));         // sans #
        assertFalse(ForgeColors.isHex("vert"));
        assertFalse(ForgeColors.isHex("#12345"));        // trop court
        assertEquals(0x1b5e20, ForgeColors.parseHex("#1B5E20"));
        assertEquals(-1, ForgeColors.parseHex("nope"));
        assertEquals("#1b5e20", ForgeColors.toHex(0x1b5e20));
    }

    @Test
    void parseSeparatesNameFromTrailingColors() {
        String[] a = {"diamond_sword", "Épée", "du", "Vent", "#1b5e20", "#66bb6a", "#e8f5e9"};
        ForgeColors.Parsed p = ForgeColors.parseNameAndColors(a, 1);
        assertEquals("Épée du Vent", p.name());
        assertEquals(List.of(0x1b5e20, 0x66bb6a, 0xe8f5e9), p.colors());
        // aucun hex -> pas de couleurs, nom complet
        ForgeColors.Parsed q = ForgeColors.parseNameAndColors(new String[]{"x", "Lame", "Maudite"}, 1);
        assertTrue(q.colors().isEmpty());
        assertEquals("Lame Maudite", q.name());
    }

    @Test
    void chosenColorsBuildPalette() {
        // 1 couleur -> rampe d'ombrage (sombre < couleur < clair).
        ThemePalette one = ForgeColors.paletteFromChosen("x", List.of(0x2e7d32));
        assertTrue(TextureRecolorer.luminance(one.colorAt(0.0)) < TextureRecolorer.luminance(one.colorAt(1.0)));
        // >=2 couleurs -> triées sombre->clair.
        ThemePalette many = ForgeColors.paletteFromChosen("x", List.of(0xe8f5e9, 0x1b5e20, 0x66bb6a));
        assertEquals(0x1b5e20, many.colorAt(0.0));
        assertEquals(0xe8f5e9, many.colorAt(1.0));
        assertNull(ForgeColors.paletteFromChosen("x", List.of()));
    }

    @Test
    void hexStopsExposeRampForSuggestion() {
        List<String> hex = ThemePalette.ramp("t", 0x1b5e20, 0x66bb6a, 0xe8f5e9).hexStops();
        assertFalse(hex.isEmpty());
        for (String h : hex) assertTrue(ForgeColors.isHex(h), "stop hex valide: " + h);
        assertEquals("#1b5e20", hex.get(0));   // 1er stop = couleur sombre fournie
    }
}
