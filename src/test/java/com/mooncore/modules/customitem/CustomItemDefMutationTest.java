package com.mooncore.modules.customitem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comportements de mutation à cas limites de {@link CustomItemDef} (dédup capacités, retrait via
 * niveau ≤ 0), empruntés par l'éditeur GUI et l'IA. Pur, sans serveur.
 */
class CustomItemDefMutationTest {

    @Test
    void addAbilityDeduplicatesAndUpdatesLevel() {
        CustomItemDef d = new CustomItemDef("baton");
        d.addAbility("dash", 1);
        d.addAbility("dash", 3);           // même id → met à jour, pas de doublon
        assertEquals(1, d.abilities().size());
        assertEquals(3, d.abilities().get(0).level());
        assertTrue(d.removeAbility("dash"));
        assertFalse(d.removeAbility("dash")); // déjà retiré
        assertEquals(0, d.abilities().size());
    }

    @Test
    void enchantLevelZeroRemoves() {
        CustomItemDef d = new CustomItemDef("epee");
        d.setEnchant("sharpness", 3);
        assertEquals(3, d.enchants().get("sharpness"));
        d.setEnchant("sharpness", 0);      // niveau ≤ 0 → retire
        assertNull(d.enchants().get("sharpness"));
        d.setEnchant("", 5);               // clé vide ignorée
        assertEquals(0, d.enchants().size());
    }

    @Test
    void statRemovalAndConsumeEffectRemoval() {
        CustomItemDef d = new CustomItemDef("x");
        d.setStat("armor", 5);
        d.removeStat("armor");
        assertNull(d.stats().get("armor"));

        d.setConsumeEffect("speed", 200, 0);
        assertEquals(1, d.consumeEffects().size());
        d.setConsumeEffect("speed", 0, 0);  // durée ≤ 0 → retire
        assertEquals(0, d.consumeEffects().size());
    }
}
