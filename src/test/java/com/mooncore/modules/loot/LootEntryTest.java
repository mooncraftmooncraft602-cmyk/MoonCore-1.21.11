package com.mooncore.modules.loot;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Bornes de {@link LootEntry} et copie {@link LootEntry#withWeight} (entrée immuable, seul le poids change ;
 * le reste — item/material/comptes/référence — est préservé). Pur, sans serveur.
 */
class LootEntryTest {

    @Test
    void weightClampedToAtLeastOne() {
        assertEquals(1, new LootEntry(null, Material.DIAMOND, 0, 1, 1, null).weight());
        assertEquals(1, new LootEntry(null, Material.DIAMOND, -5, 1, 1, null).weight());
        assertEquals(7, new LootEntry(null, Material.DIAMOND, 7, 1, 1, null).weight());
    }

    @Test
    void withWeightPreservesEverythingElse() {
        LootEntry base = new LootEntry("ruby", null, 3, 2, 5, null);
        LootEntry heavier = base.withWeight(20);
        assertNotSame(base, heavier);
        assertEquals(20, heavier.weight());
        assertEquals(3, base.weight());            // l'original est inchangé (immuable)
        assertEquals("ruby", heavier.itemId());
        assertEquals(2, heavier.countMin());
        assertEquals(5, heavier.countMax());
        assertNull(heavier.tableRef());
    }

    @Test
    void withWeightPreservesTableReference() {
        LootEntry ref = new LootEntry(null, Material.AIR, 1, 1, 1, "tresor");
        LootEntry rebalanced = ref.withWeight(9);
        assertEquals("tresor", rebalanced.tableRef());
        assertEquals(9, rebalanced.weight());
    }
}
