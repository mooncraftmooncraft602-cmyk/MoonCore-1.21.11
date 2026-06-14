package com.mooncore.modules.customitem;

import com.mooncore.api.customitem.ItemType;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Logique interdépendante {@link CustomItemDef#setTool}/{@code setToolKind}/{@code setToolTier} :
 * ajuste aussi le matériau et le type. Pur (Material enum + matchMaterial), sans serveur.
 */
class CustomItemDefToolTest {

    @Test
    void setToolSetsMaterialAndType() {
        CustomItemDef d = new CustomItemDef("x");
        d.setTool(ToolKind.PICKAXE, ToolTier.IRON);
        assertEquals(ToolKind.PICKAXE, d.toolKind());
        assertEquals(ToolTier.IRON, d.toolTier());
        assertEquals(Material.IRON_PICKAXE, d.material());
        assertEquals(ItemType.TOOL, d.type());

        d.setTool(ToolKind.SWORD, ToolTier.DIAMOND);
        assertEquals(Material.DIAMOND_SWORD, d.material());
        assertEquals(ItemType.WEAPON, d.type());     // épée → arme, pas outil
    }

    @Test
    void noneToolForcesHandTier() {
        CustomItemDef d = new CustomItemDef("x");
        d.setTool(ToolKind.NONE, ToolTier.DIAMOND);
        assertEquals(ToolKind.NONE, d.toolKind());
        assertEquals(ToolTier.HAND, d.toolTier());   // pas d'outil → tier HAND
    }

    @Test
    void handTierIsUpgradedToIronWhenToolSet() {
        CustomItemDef d = new CustomItemDef("x");
        d.setToolKind(ToolKind.AXE);                 // toolTier était HAND → passe IRON
        assertEquals(ToolKind.AXE, d.toolKind());
        assertEquals(ToolTier.IRON, d.toolTier());
        assertEquals(Material.IRON_AXE, d.material());

        d.setToolTier(ToolTier.NETHERITE);
        assertEquals(Material.NETHERITE_AXE, d.material());
    }
}
