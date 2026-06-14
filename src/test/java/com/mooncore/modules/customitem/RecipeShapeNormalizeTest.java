package com.mooncore.modules.customitem;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Normalisation de grille de craft de {@link RecipeManager#normalizeShape} : toujours 3 lignes de 3
 * caractères, tolérante aux entrées partielles/null. Pur, sans serveur.
 */
class RecipeShapeNormalizeTest {

    @Test
    void padsToThreeByThree() {
        List<String> out = RecipeManager.normalizeShape(List.of("X"));
        assertEquals(3, out.size());
        for (String row : out) assertEquals(3, row.length());
        assertEquals("X  ", out.get(0));
        assertEquals("   ", out.get(1));
        assertEquals("   ", out.get(2));
    }

    @Test
    void truncatesOverlongRowsAndExtraRows() {
        List<String> out = RecipeManager.normalizeShape(List.of("ABCDE", "FG", "H", "IGNORED"));
        assertEquals(3, out.size());
        assertEquals("ABC", out.get(0));   // tronquée à 3
        assertEquals("FG ", out.get(1));   // complétée
        assertEquals("H  ", out.get(2));   // 4e ligne ignorée
    }

    @Test
    void toleratesNullShapeAndNullRow() {
        List<String> allEmpty = RecipeManager.normalizeShape(null);
        assertEquals(List.of("   ", "   ", "   "), allEmpty);

        List<String> withNullRow = RecipeManager.normalizeShape(Arrays.asList("X", null, "Y"));
        assertEquals("X  ", withNullRow.get(0));
        assertEquals("   ", withNullRow.get(1));   // ligne null → vide, pas de NPE
        assertEquals("Y  ", withNullRow.get(2));
    }
}
