package com.mooncore.modules.loot;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agrégation pure de {@link LootStats} : fréquence d'apparition et quantité moyenne par item, tri
 * déterministe (fréquence puis quantité totale puis clé). Sans serveur (records purs).
 */
class LootStatsTest {

    private static LootResult mat(Material m, int c) { return new LootResult(null, m, c, null); }
    private static LootResult custom(String id, int c) { return new LootResult(id, null, c, null); }
    private static LootResult ref(String t) { return new LootResult(null, Material.AIR, 1, t); }

    @Test
    void emptySampleYieldsNoEntries() {
        assertTrue(LootStats.aggregate(List.of()).isEmpty());
        assertTrue(LootStats.aggregate(null).isEmpty());
    }

    @Test
    void frequencyIsShareOfIterationsContainingItem() {
        // 4 itérations : diamant dans 3, émeraude dans 1.
        List<List<LootResult>> samples = List.of(
                List.of(mat(Material.DIAMOND, 1)),
                List.of(mat(Material.DIAMOND, 1)),
                List.of(mat(Material.DIAMOND, 1), mat(Material.EMERALD, 2)),
                List.of()
        );
        List<LootStats.Entry> agg = LootStats.aggregate(samples);
        assertEquals(2, agg.size());
        LootStats.Entry diamond = agg.get(0);   // le plus fréquent en tête
        assertEquals("diamond", diamond.key());
        assertEquals(3, diamond.rollsWith());
        assertEquals(0.75, diamond.frequency(), 1e-9);
        assertEquals(3L, diamond.totalCount());
        assertEquals(0.75, diamond.avgPerIteration(), 1e-9);   // 3 diamants / 4 itérations
    }

    @Test
    void multipleStacksSameIterationCountOnceForFrequencyButSumForTotal() {
        // Une seule itération avec deux piles du même item : fréquence 100%, total = somme.
        List<List<LootResult>> samples = List.of(
                List.of(mat(Material.IRON_INGOT, 5), mat(Material.IRON_INGOT, 3))
        );
        List<LootStats.Entry> agg = LootStats.aggregate(samples);
        assertEquals(1, agg.size());
        LootStats.Entry iron = agg.get(0);
        assertEquals(1, iron.rollsWith());          // une seule itération, comptée une fois
        assertEquals(8L, iron.totalCount());        // 5 + 3
        assertEquals(1.0, iron.frequency(), 1e-9);
        assertEquals(8.0, iron.avgPerIteration(), 1e-9);
    }

    @Test
    void customItemKeyedWithMarkerAndFlagged() {
        List<LootStats.Entry> agg = LootStats.aggregate(List.of(List.of(custom("ruby", 1))));
        assertEquals(1, agg.size());
        assertEquals("✦ruby", agg.get(0).key());
        assertTrue(agg.get(0).custom());
    }

    @Test
    void referencesAndZeroCountIgnored() {
        List<List<LootResult>> samples = List.of(
                List.of(ref("nested"), mat(Material.STONE, 0), mat(Material.GOLD_INGOT, 2))
        );
        List<LootStats.Entry> agg = LootStats.aggregate(samples);
        assertEquals(1, agg.size());                 // ref non développée + count 0 ignorés
        assertEquals("gold_ingot", agg.get(0).key());
    }

    @Test
    void sortedByFrequencyThenTotalThenKey() {
        // a et b même fréquence (2/2) mais b a plus de total → b avant a ; c moins fréquent → dernier.
        List<List<LootResult>> samples = List.of(
                List.of(mat(Material.APPLE, 1), mat(Material.BREAD, 5), mat(Material.CARROT, 1)),
                List.of(mat(Material.APPLE, 1), mat(Material.BREAD, 5))
        );
        List<LootStats.Entry> agg = LootStats.aggregate(samples);
        assertEquals("bread", agg.get(0).key());     // freq 2, total 10
        assertEquals("apple", agg.get(1).key());     // freq 2, total 2
        assertEquals("carrot", agg.get(2).key());    // freq 1
        assertFalse(agg.get(0).custom());
    }
}
