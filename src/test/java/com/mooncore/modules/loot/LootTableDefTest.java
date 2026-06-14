package com.mooncore.modules.loot;

import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LootTableDef} : concaténation multi-pools au tirage et round-trip YAML (ordre des pools/entrées
 * préservé via clés numériques). Pur, sans serveur ({@link MemoryConfiguration}, suffixes d'enum Material).
 */
class LootTableDefTest {

    private static LootTableDef sample() {
        LootTableDef t = new LootTableDef("boss_drops");
        t.setDisplayName("<gold>Butin du Boss</gold>");
        t.add(new LootPool(1, 1)
                .add(new LootEntry(null, Material.DIAMOND, 5, 1, 2))
                .add(new LootEntry("legendary_sword", Material.AIR, 1, 1, 1)));
        t.add(new LootPool(2, 2)
                .add(new LootEntry(null, Material.GOLD_INGOT, 1, 3, 5)));
        return t;
    }

    @Test
    void rollConcatenatesAllPools() {
        LootTableDef t = sample();
        // pool0 = 1 roll, pool1 = 2 rolls → 3 résultats par évaluation (toutes entrées produisent count>0).
        List<LootResult> out = t.roll(new Random(7));
        assertEquals(3, out.size());
    }

    @Test
    void yamlRoundTripPreservesStructure() {
        MemoryConfiguration cfg = new MemoryConfiguration();
        sample().save(cfg.createSection("t"));

        LootTableDef back = LootTableDef.load("boss_drops", cfg.getConfigurationSection("t"));
        assertEquals("<gold>Butin du Boss</gold>", back.displayName());
        assertEquals(2, back.pools().size());

        LootPool p0 = back.pools().get(0);
        assertEquals(2, p0.entries().size());
        assertEquals(Material.DIAMOND, p0.entries().get(0).material());
        assertEquals(5, p0.entries().get(0).weight());
        assertEquals(2, p0.entries().get(0).countMax());
        assertEquals("legendary_sword", p0.entries().get(1).itemId());
        assertTrue(p0.entries().get(1).isCustom());

        LootPool p1 = back.pools().get(1);
        assertEquals(2, p1.rollsMin());
        assertEquals(Material.GOLD_INGOT, p1.entries().get(0).material());
        assertEquals(3, p1.entries().get(0).countMin());
        assertEquals(5, p1.entries().get(0).countMax());
    }

    @Test
    void countRangeIsClampedConsistently() {
        // min > max fourni → max relevé au min ; valeurs négatives clampées à 0.
        LootEntry e = new LootEntry(null, Material.STONE, 1, 9, 2);
        assertEquals(9, e.countMin());
        assertEquals(9, e.countMax());
        LootEntry neg = new LootEntry(null, Material.STONE, -3, -5, -1);
        assertEquals(1, neg.weight());     // poids planché à 1
        assertEquals(0, neg.countMin());
        assertEquals(0, neg.countMax());
    }
}
