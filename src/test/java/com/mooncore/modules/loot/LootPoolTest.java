package com.mooncore.modules.loot;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Logique de tirage pondéré de {@link LootPool} — sélection cumulée, bornes, comptes. Pur, sans serveur :
 * un RNG scripté ({@link ScriptedRandom}) impose des valeurs exactes pour valider les frontières de poids.
 */
class LootPoolTest {

    /** Random à valeurs scriptées : {@code nextInt(bound)} dépile une suite imposée (clampée au bound). */
    private static final class ScriptedRandom extends Random {
        private final Queue<Integer> q = new ArrayDeque<>();
        ScriptedRandom(int... values) { for (int v : values) q.add(v); }
        @Override public int nextInt(int bound) {
            Integer v = q.poll();
            int x = (v == null) ? 0 : v;
            return Math.max(0, Math.min(bound - 1, x));
        }
    }

    private static LootPool threeWeighted() {
        // poids 1 / 3 / 6  → total 10 ; bornes cumulées : A=[0,1) B=[1,4) C=[4,10)
        return new LootPool(1, 1)
                .add(new LootEntry(null, Material.DIRT, 1, 1, 1))
                .add(new LootEntry(null, Material.STONE, 3, 1, 1))
                .add(new LootEntry(null, Material.DIAMOND, 6, 1, 1));
    }

    @Test
    void totalWeightSums() {
        assertEquals(10, threeWeighted().totalWeight());
    }

    @Test
    void chanceOfIsWeightOverTotal() {
        LootPool p = threeWeighted();   // poids 1 / 3 / 6 sur total 10
        assertEquals(0.1, p.chanceOf(p.entries().get(0)), 1e-9);
        assertEquals(0.3, p.chanceOf(p.entries().get(1)), 1e-9);
        assertEquals(0.6, p.chanceOf(p.entries().get(2)), 1e-9);
        // les probabilités somment à 1.
        double sum = 0;
        for (LootEntry e : p.entries()) sum += p.chanceOf(e);
        assertEquals(1.0, sum, 1e-9);
        assertEquals(0.0, p.chanceOf(null), 1e-9);
        assertEquals(0.0, new LootPool().chanceOf(new LootEntry(null, Material.DIRT, 1, 1, 1)), 1e-9); // pool vide
    }

    @Test
    void pickWeightedRespectsCumulativeBoundaries() {
        LootPool p = threeWeighted();
        assertEquals(Material.DIRT,    p.pickWeighted(new ScriptedRandom(0)).material()); // [0,1) → A
        assertEquals(Material.STONE,   p.pickWeighted(new ScriptedRandom(1)).material()); // début de B
        assertEquals(Material.STONE,   p.pickWeighted(new ScriptedRandom(3)).material()); // fin de B
        assertEquals(Material.DIAMOND, p.pickWeighted(new ScriptedRandom(4)).material()); // début de C
        assertEquals(Material.DIAMOND, p.pickWeighted(new ScriptedRandom(9)).material()); // dernière unité
    }

    @Test
    void emptyPoolPicksNull() {
        assertNull(new LootPool().pickWeighted(new ScriptedRandom(0)));
        assertTrue(new LootPool().roll(new ScriptedRandom(0)).isEmpty());
    }

    @Test
    void rollCountStaysInRange() {
        LootPool p = new LootPool(2, 5);
        Random rng = new Random(123);
        for (int i = 0; i < 200; i++) {
            int n = p.rollCount(rng);
            assertTrue(n >= 2 && n <= 5, "rolls hors bornes: " + n);
        }
    }

    @Test
    void weightedDistributionFavorsHeavier() {
        LootPool p = threeWeighted();
        Random rng = new Random(42);
        int dirt = 0, diamond = 0;
        for (int i = 0; i < 10_000; i++) {
            Material m = p.pickWeighted(rng).material();
            if (m == Material.DIRT) dirt++;
            else if (m == Material.DIAMOND) diamond++;
        }
        // poids 6 vs 1 → diamant largement majoritaire face à dirt (déterministe, seed fixe).
        assertTrue(diamond > dirt * 3, "diamond=" + diamond + " dirt=" + dirt);
    }

    @Test
    void singleRollProducesOneResultWithCount() {
        LootPool p = new LootPool(1, 1)
                .add(new LootEntry("epic_gem", Material.AIR, 1, 3, 3));
        List<LootResult> out = p.roll(new ScriptedRandom(0 /*pick*/, 0 /*count base*/));
        assertEquals(1, out.size());
        assertEquals("epic_gem", out.get(0).itemId());
        assertTrue(out.get(0).isCustom());
        assertEquals(3, out.get(0).count());
    }
}
