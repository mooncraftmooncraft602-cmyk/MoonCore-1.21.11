package com.mooncore.modules.loot;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Partie pure de la recherche inverse de références ({@link LootManagerModule#addMatches}) : un contenu est
 * listé {@code "<type>:<id>"} ssi sa table de loot égale la cible (insensible à la casse), les valeurs nulles
 * étant ignorées. La partie live (interrogation des modules) n'est pas testable sans serveur.
 */
class LootExternalRefsTest {

    private static Map<String, String> map(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void matchesByTableIgnoringCaseAndNulls() {
        Map<String, String> defs = map("wheat", "common", "carrot", "Common", "potato", "rare", "beet", null);
        List<String> out = new ArrayList<>();
        LootManagerModule.addMatches("crop", defs, "common", out);
        assertEquals(List.of("crop:wheat", "crop:carrot"), out);   // ordre d'insertion, null ignoré, casse ignorée
    }

    @Test
    void noMatchYieldsEmptyAndPreservesExisting() {
        List<String> out = new ArrayList<>(List.of("boss:golem"));
        LootManagerModule.addMatches("crop", map("wheat", "common"), "rare", out);
        assertEquals(List.of("boss:golem"), out);   // rien ajouté, accumulateur préservé
    }

    @Test
    void nullArgsAreNoOp() {
        List<String> out = new ArrayList<>();
        LootManagerModule.addMatches("crop", null, "x", out);
        LootManagerModule.addMatches("crop", map("a", "b"), null, out);
        assertTrue(out.isEmpty());
    }

    @Test
    void accumulatesAcrossTypes() {
        List<String> out = new ArrayList<>();
        LootManagerModule.addMatches("crop", map("wheat", "boss_loot"), "boss_loot", out);
        LootManagerModule.addMatches("block", map("ore", "boss_loot", "log", "other"), "boss_loot", out);
        assertEquals(List.of("crop:wheat", "block:ore"), out);
    }
}
