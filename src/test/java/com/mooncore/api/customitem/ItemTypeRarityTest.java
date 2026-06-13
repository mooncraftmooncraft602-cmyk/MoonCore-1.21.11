package com.mooncore.api.customitem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parsing des enums {@link ItemType}/{@link Rarity} (alimentent la création IA et l'attribution
 * du slot d'attributs). Pur, sans serveur.
 */
class ItemTypeRarityTest {

    @Test
    void itemTypeFromId() {
        assertEquals(ItemType.WEAPON, ItemType.fromId("weapon"));
        assertEquals(ItemType.BOSS_ITEM, ItemType.fromId("boss-item"));   // tiret → underscore
        assertEquals(ItemType.ARMOR, ItemType.fromId(" ARMOR "));
        assertNull(ItemType.fromId("inexistant"));
        assertNull(ItemType.fromId(null));
    }

    @Test
    void appliesFlags() {
        assertTrue(ItemType.WEAPON.appliesHeld());
        assertFalse(ItemType.WEAPON.appliesWorn());
        assertTrue(ItemType.ARMOR.appliesWorn());
        assertFalse(ItemType.ARMOR.appliesHeld());
        // Accessoire : ni tenu ni porté pour les stats d'attribut.
        assertFalse(ItemType.ACCESSORY.appliesHeld());
        assertFalse(ItemType.ACCESSORY.appliesWorn());
    }

    @Test
    void rarityFromId() {
        assertEquals(Rarity.COMMON, Rarity.fromId("common"));
        assertEquals(Rarity.RARE, Rarity.fromId("RARE"));
        assertEquals(Rarity.UNCOMMON, Rarity.fromId(" uncommon "));
        assertNull(Rarity.fromId("legendaire_inconnu"));
        assertNull(Rarity.fromId(null));
    }

    @Test
    void idRoundTrip() {
        for (ItemType t : ItemType.values()) assertEquals(t, ItemType.fromId(t.id()));
        for (Rarity r : Rarity.values()) assertEquals(r, Rarity.fromId(r.id()));
    }
}
