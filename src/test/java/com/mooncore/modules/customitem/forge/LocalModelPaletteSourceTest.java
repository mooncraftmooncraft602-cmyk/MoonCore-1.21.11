package com.mooncore.modules.customitem.forge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parsing de la réponse du sidecar (modèle local) → {@link ThemePalette}, et construction de rampe à N stops
 * ({@link ThemePalette#ofColors}). Pur (pas de réseau : on teste la méthode {@code parse}). Le repli réseau
 * (sidecar éteint) est garanti par construction dans {@code resolve}.
 */
class LocalModelPaletteSourceTest {

    private final LocalModelPaletteSource src = new LocalModelPaletteSource("http://127.0.0.1:8770/palette", 8);

    @Test
    void parsesColorsSortedDarkToLight() {
        // Couleurs dans le désordre → rampe triée sombre→clair.
        ThemePalette p = src.parse("Épée du Vent",
                "{\"colors\":[\"#e8f5e9\",\"#1b5e20\",\"#66bb6a\"],\"source\":\"model\"}");
        assertEquals(0x1b5e20, p.colorAt(0.0));   // plus sombre en bas
        assertEquals(0xe8f5e9, p.colorAt(1.0));   // plus clair en haut
        assertTrue(p.name().startsWith("model:"));
    }

    @Test
    void manyStopsBuildSmoothGradient() {
        ThemePalette p = src.parse("x",
                "{\"colors\":[\"#0a3d12\",\"#1b5e20\",\"#43a047\",\"#a5d6a7\",\"#e8f5e9\"]}");
        // 5 stops répartis : extrémités = plus sombre / plus clair, milieu intermédiaire.
        assertEquals(0x0a3d12, p.colorAt(0.0));
        assertEquals(0xe8f5e9, p.colorAt(1.0));
        assertEquals(0x43a047, p.colorAt(0.5));   // stop central à pos 0.5 (i=2/4)
    }

    @Test
    void tooFewOrBadColorsReturnNull() {
        assertNull(src.parse("x", "{\"colors\":[\"#1b5e20\"]}"));        // 1 couleur insuffisante
        assertNull(src.parse("x", "{\"colors\":[]}"));
        assertNull(src.parse("x", "{\"autre\":1}"));                     // pas de champ colors
        assertNull(src.parse("x", "pas du json"));
        assertNull(src.parse("x", "{\"colors\":[\"rouge\",\"bleu\"]}")); // pas des hex
    }

    @Test
    void ofColorsDistributesStops() {
        ThemePalette p = ThemePalette.ofColors("t", List.of(0x000000, 0x808080, 0xffffff));
        assertEquals(0x000000, p.colorAt(0.0));
        assertEquals(0x808080, p.colorAt(0.5));
        assertEquals(0xffffff, p.colorAt(1.0));
        // une seule couleur → palette constante valide
        assertEquals(0x123456, ThemePalette.ofColors("u", List.of(0x123456)).colorAt(0.3));
    }
}
