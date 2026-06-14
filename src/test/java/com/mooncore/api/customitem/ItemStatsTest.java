package com.mooncore.api.customitem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Registre de métadonnées des stats {@link ItemStats} : pilote l'application des attributs
 * (vanillaAttribute non null) vs les stats gérées par le listener (null). Pur.
 */
class ItemStatsTest {

    @Test
    void attributeBackedStats() {
        assertEquals("attack_damage", ItemStats.meta(ItemStats.DAMAGE).vanillaAttribute());
        assertEquals("max_health", ItemStats.meta(ItemStats.HEALTH).vanillaAttribute());
        assertEquals("armor", ItemStats.meta(ItemStats.ARMOR).vanillaAttribute());
        assertEquals("movement_speed", ItemStats.meta(ItemStats.MOVEMENT_SPEED).vanillaAttribute());
        assertFalse(ItemStats.meta(ItemStats.DAMAGE).percent());
        assertTrue(ItemStats.meta(ItemStats.MOVEMENT_SPEED).percent());
    }

    @Test
    void listenerHandledStatsHaveNoVanillaAttribute() {
        // Ces stats sont appliquées par le listener (pas via attribute modifier) → vanillaAttribute null.
        assertNull(ItemStats.meta(ItemStats.CRIT_CHANCE).vanillaAttribute());
        assertNull(ItemStats.meta(ItemStats.LIFE_STEAL).vanillaAttribute());
        assertNull(ItemStats.meta(ItemStats.MANA).vanillaAttribute());
        assertNull(ItemStats.meta(ItemStats.BOSS_DAMAGE).vanillaAttribute());
        assertTrue(ItemStats.meta(ItemStats.CRIT_CHANCE).percent());
    }

    @Test
    void helpersAndUnknown() {
        assertEquals("Dégâts", ItemStats.label(ItemStats.DAMAGE));
        assertTrue(ItemStats.isPercent(ItemStats.COOLDOWN_REDUCTION));
        assertFalse(ItemStats.isPercent(ItemStats.MANA));
        // Stat inconnue : meta null, label = clé, isPercent false.
        assertNull(ItemStats.meta("stat_custom_inconnue"));
        assertEquals("stat_custom_inconnue", ItemStats.label("stat_custom_inconnue"));
        assertFalse(ItemStats.isPercent("stat_custom_inconnue"));
        assertNotNull(ItemStats.known());
    }
}
