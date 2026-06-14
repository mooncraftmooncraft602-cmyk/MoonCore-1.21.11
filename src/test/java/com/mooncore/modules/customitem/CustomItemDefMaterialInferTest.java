package com.mooncore.modules.customitem;

import com.mooncore.api.customitem.ItemType;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link CustomItemDef#setMaterial} infère la famille/tier d'outil depuis le matériau (inverse de
 * setTool), utilisé par l'éditeur (changer le matériau) et l'IA. Pur (suffixes d'enum Material).
 */
class CustomItemDefMaterialInferTest {

    @Test
    void toolMaterialInfersKindTierAndType() {
        CustomItemDef d = new CustomItemDef("x");
        d.setMaterial(Material.DIAMOND_PICKAXE);
        assertEquals(Material.DIAMOND_PICKAXE, d.material());
        assertEquals(ToolKind.PICKAXE, d.toolKind());
        assertEquals(ToolTier.DIAMOND, d.toolTier());
        assertEquals(ItemType.TOOL, d.type());
    }

    @Test
    void swordMaterialInfersWeaponType() {
        CustomItemDef d = new CustomItemDef("x");
        d.setMaterial(Material.NETHERITE_SWORD);
        assertEquals(ToolKind.SWORD, d.toolKind());
        assertEquals(ToolTier.NETHERITE, d.toolTier());
        assertEquals(ItemType.WEAPON, d.type());
    }

    @Test
    void nonToolMaterialClearsTool() {
        CustomItemDef d = new CustomItemDef("x");
        d.setMaterial(Material.IRON_AXE);          // d'abord un outil
        assertEquals(ToolKind.AXE, d.toolKind());
        d.setMaterial(Material.DIAMOND);           // puis un non-outil
        assertEquals(ToolKind.NONE, d.toolKind());
        assertEquals(ToolTier.HAND, d.toolTier());
    }
}
