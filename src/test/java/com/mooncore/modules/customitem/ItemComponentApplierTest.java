package com.mooncore.modules.customitem;

import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Mapping matériau → slot d'armure ({@link ItemComponentApplier#armorSlot}), cœur de l'armure
 * portée equippable (B4). Pur (enum {@link Material}), sans serveur Bukkit.
 */
class ItemComponentApplierTest {

    @Test
    void mapsArmorPiecesToSlots() {
        assertEquals(EquipmentSlot.HEAD, ItemComponentApplier.armorSlot(Material.DIAMOND_HELMET));
        assertEquals(EquipmentSlot.CHEST, ItemComponentApplier.armorSlot(Material.NETHERITE_CHESTPLATE));
        assertEquals(EquipmentSlot.LEGS, ItemComponentApplier.armorSlot(Material.IRON_LEGGINGS));
        assertEquals(EquipmentSlot.FEET, ItemComponentApplier.armorSlot(Material.GOLDEN_BOOTS));
    }

    @Test
    void specialHeadAndChestCases() {
        assertEquals(EquipmentSlot.HEAD, ItemComponentApplier.armorSlot(Material.CARVED_PUMPKIN));
        assertEquals(EquipmentSlot.HEAD, ItemComponentApplier.armorSlot(Material.PLAYER_HEAD));
        assertEquals(EquipmentSlot.CHEST, ItemComponentApplier.armorSlot(Material.ELYTRA));
    }

    @Test
    void nonArmorMaterialsReturnNull() {
        assertNull(ItemComponentApplier.armorSlot(Material.DIAMOND_SWORD));
        assertNull(ItemComponentApplier.armorSlot(Material.STICK));
        assertNull(ItemComponentApplier.armorSlot(Material.STONE));
    }
}
