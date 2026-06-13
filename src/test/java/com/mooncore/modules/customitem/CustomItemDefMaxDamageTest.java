package com.mooncore.modules.customitem;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Durabilité custom ({@code minecraft:max_damage}) de {@link CustomItemDef} : round-trip YAML +
 * bornage. Pur (MemoryConfiguration), sans serveur.
 */
class CustomItemDefMaxDamageTest {

    @Test
    void roundTrip() {
        CustomItemDef d = new CustomItemDef("epee_fragile");
        d.setMaxDamage(2000);

        MemoryConfiguration cfg = new MemoryConfiguration();
        d.save(cfg);
        CustomItemDef back = CustomItemDef.load("epee_fragile", cfg);

        assertEquals(2000, back.maxDamage());
    }

    @Test
    void defaultIsZeroAndClamped() {
        CustomItemDef d = new CustomItemDef("x");
        assertEquals(0, d.maxDamage());            // 0 = durabilité vanilla
        d.setMaxDamage(-5);
        assertEquals(0, d.maxDamage());            // borné en bas
        d.setMaxDamage(999_999);
        assertEquals(100_000, d.maxDamage());      // borné en haut
    }

    @Test
    void clonedAndAbsentWhenZero() {
        CustomItemDef d = new CustomItemDef("a");
        d.setMaxDamage(1500);
        assertEquals(1500, d.cloneAs("b").maxDamage());

        // Non écrit en YAML si 0 → re-chargé à 0.
        CustomItemDef plain = new CustomItemDef("p");
        MemoryConfiguration cfg = new MemoryConfiguration();
        plain.save(cfg);
        assertEquals(0, CustomItemDef.load("p", cfg).maxDamage());
    }
}
