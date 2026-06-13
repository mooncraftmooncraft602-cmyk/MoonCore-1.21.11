package com.mooncore.modules.customitem;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip YAML des composants NATIFS food (B2) et tool (B3) de {@link CustomItemDef}.
 * Utilise {@link MemoryConfiguration} (aucun serveur Bukkit requis).
 */
class CustomItemDefFoodToolTest {

    @Test
    void foodRoundTrips() {
        CustomItemDef d = new CustomItemDef("ration");
        d.setFood(6, 7.2f, true, 2.0f);

        MemoryConfiguration cfg = new MemoryConfiguration();
        d.save(cfg);
        CustomItemDef back = CustomItemDef.load("ration", cfg);

        assertTrue(back.hasFood());
        assertEquals(6, back.foodNutrition());
        assertEquals(7.2f, back.foodSaturation(), 1e-4);
        assertTrue(back.foodCanAlwaysEat());
        assertEquals(2.0f, back.foodEatSeconds(), 1e-4);
    }

    @Test
    void toolComponentAndRulesRoundTrip() {
        CustomItemDef d = new CustomItemDef("drill");
        d.setToolComponent(5.5f, 2);
        d.addToolRule("#minecraft:mineable/pickaxe", 6f, true);
        d.addToolRule("STONE,DEEPSLATE", 3f, false);

        MemoryConfiguration cfg = new MemoryConfiguration();
        d.save(cfg);
        CustomItemDef back = CustomItemDef.load("drill", cfg);

        assertTrue(back.hasToolComponent());
        assertEquals(5.5f, back.toolMiningSpeed(), 1e-4);
        assertEquals(2, back.toolDamagePerBlock());
        assertEquals(2, back.toolRules().size());
        assertEquals("#minecraft:mineable/pickaxe", back.toolRules().get(0).blocks());
        assertEquals(6f, back.toolRules().get(0).speed(), 1e-4);
        assertTrue(back.toolRules().get(0).correctForDrops());
        assertEquals("STONE,DEEPSLATE", back.toolRules().get(1).blocks());
        assertFalse(back.toolRules().get(1).correctForDrops());
    }

    @Test
    void noFoodNoToolByDefault() {
        CustomItemDef d = new CustomItemDef("plain");
        MemoryConfiguration cfg = new MemoryConfiguration();
        d.save(cfg);
        CustomItemDef back = CustomItemDef.load("plain", cfg);
        assertFalse(back.hasFood());
        assertFalse(back.hasToolComponent());
    }
}
