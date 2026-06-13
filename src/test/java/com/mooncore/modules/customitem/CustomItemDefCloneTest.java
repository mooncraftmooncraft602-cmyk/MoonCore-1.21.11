package com.mooncore.modules.customitem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CustomItemDef#cloneAs(String)} — copie profonde (y compris food B2 / tool B3) et
 * indépendance source/clone. Pur, sans serveur Bukkit.
 */
class CustomItemDefCloneTest {

    @Test
    void cloneCopiesFoodToolAndStats() {
        CustomItemDef src = new CustomItemDef("orig");
        src.setStat("attack_damage", 9);
        src.setEnchant("sharpness", 3);
        src.setFood(5, 3.0f, true, 1.4f);
        src.setToolComponent(6f, 2);
        src.addToolRule("#minecraft:mineable/pickaxe", 6f, true);

        CustomItemDef c = src.cloneAs("copie");

        assertEquals("copie", c.id());
        assertEquals(9.0, c.stats().get("attack_damage"), 1e-9);
        assertEquals(3, c.enchants().get("sharpness"));
        assertTrue(c.hasFood());
        assertEquals(5, c.foodNutrition());
        assertTrue(c.hasToolComponent());
        assertEquals(1, c.toolRules().size());
        assertEquals("#minecraft:mineable/pickaxe", c.toolRules().get(0).blocks());
    }

    @Test
    void cloneIsIndependent() {
        CustomItemDef src = new CustomItemDef("orig");
        src.setStat("armor", 4);
        CustomItemDef c = src.cloneAs("copie");

        // Muter le clone n'affecte pas la source.
        c.setStat("armor", 99);
        c.setFood(2, 1f, false, 1f);
        assertEquals(4.0, src.stats().get("armor"), 1e-9);
        assertFalse(src.hasFood());
    }
}
