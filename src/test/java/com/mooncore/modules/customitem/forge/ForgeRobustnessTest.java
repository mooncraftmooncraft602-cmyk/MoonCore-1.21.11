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
        // thèmes étendus (≥30 au total)
        assertEquals("soleil", PaletteResolver.fromName("Couronne Solaire").name());
        assertEquals("dragon", PaletteResolver.fromName("Écailles de Dragon").name());
        assertEquals("phenix", PaletteResolver.fromName("Plumes du Phénix").name());
        assertEquals("jade", PaletteResolver.fromName("Statuette de Jade").name());
        assertEquals("saphir", PaletteResolver.fromName("Pendentif Saphir").name());
        assertEquals("turquoise", PaletteResolver.fromName("Lame Turquoise").name());
        assertEquals("ender", PaletteResolver.fromName("Gantelets de l'Ender").name());
        assertEquals("rose", PaletteResolver.fromName("Bouclier de Sakura").name());
        assertEquals("corail", PaletteResolver.fromName("Trident de Corail").name());
        assertEquals("foudre", PaletteResolver.fromName("Marteau de la Foudre").name());
        // marche pour outils / armures / minerais (matériaux), pas que les armes
        assertEquals("feu", PaletteResolver.fromName("Pioche Ardente").name());
        assertEquals("glace", PaletteResolver.fromName("Plastron Glacial").name());
        assertEquals("poison", PaletteResolver.fromName("Casque Venimeux").name());
        assertEquals("or", PaletteResolver.fromName("Minerai Sacré").name());
    }

    @Test
    void at_least_30_distinct_themes() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String n : new String[]{"vent","feu","glace","foudre","ombre","poison","sang","or","nature",
                "ocean","soleil","lune","ender","nether","rose","arcane","cuivre","cristal","acier","sable",
                "rubis","obsidienne","corail","menthe","lavande","citron","chocolat","cendre","chaos","dragon",
                "phenix","vampire","spectral","jade","saphir","turquoise"}) {
            seen.add(PaletteResolver.fromName("Lame de " + n).name());
        }
        assertTrue(seen.size() >= 30, "au moins 30 thèmes distincts reconnus, vu : " + seen.size());
    }

    @Test
    void multipleThemesBlendIntoRichGradient() {
        // « lune + ténèbres + lumière » -> dégradé MÉLANGÉ (pas un seul violet plat).
        ThemePalette p = PaletteResolver.fromName("Plastron de Lune, Ténèbres et Lumière");
        assertTrue(p.name().startsWith("mix:"), "nom: " + p.name());
        assertTrue(p.name().contains("ombre") && p.name().contains("or") && p.name().contains("lune"));
        assertTrue(p.hexStops().size() >= 4);                 // vrai dégradé multi-couleurs
        // un seul thème -> pas de mélange (rampe simple).
        assertFalse(PaletteResolver.fromName("Épée du Vent").name().startsWith("mix"));
        assertEquals("feu", PaletteResolver.fromName("Lame de Feu").name());
    }

    @Test
    void longThemedSentenceStillResolvesToTheme() {
        // BUG corrigé : « Épée du dieu du feu avec des particules de feu » donnait du VIOLET (modèle GPT
        // hors-distribution sur une longue phrase). La priorité mot-clé garantit désormais FEU.
        assertTrue(PaletteResolver.hasKnownTheme("Épée du dieu du feu avec des particules de feu"));
        assertEquals("feu", PaletteResolver.fromName("Épée du dieu du feu avec des particules de feu").name());
        assertFalse(PaletteResolver.hasKnownTheme("Zorglub Machin 77"));   // nom sans thème -> modèle/repli
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
