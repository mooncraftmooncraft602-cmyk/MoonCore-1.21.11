package com.mooncore.modules.customitem;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Inférence de famille d'outil {@link ToolKind} (fromId / fromText / fromMaterial), utilisée par
 * la création IA et l'auto-détection d'outil. Pur (chaînes + suffixes d'enum Material).
 */
class ToolKindTest {

    @Test
    void fromIdAcceptsFrenchAndEnglishSynonyms() {
        assertEquals(ToolKind.PICKAXE, ToolKind.fromId("pioche"));
        assertEquals(ToolKind.PICKAXE, ToolKind.fromId("PICKAXE"));
        assertEquals(ToolKind.AXE, ToolKind.fromId("hache"));
        assertEquals(ToolKind.SHOVEL, ToolKind.fromId("pelle"));
        assertEquals(ToolKind.HOE, ToolKind.fromId("houe"));
        assertEquals(ToolKind.SWORD, ToolKind.fromId("epee"));
        assertEquals(ToolKind.SWORD, ToolKind.fromId("weapon"));
        assertEquals(ToolKind.NONE, ToolKind.fromId("inconnu"));
        assertEquals(ToolKind.NONE, ToolKind.fromId(null));
    }

    @Test
    void fromTextDetectsKeywords() {
        assertEquals(ToolKind.AXE, ToolKind.fromText("une grande hache de guerre"));
        assertEquals(ToolKind.PICKAXE, ToolKind.fromText("the lunar pickaxe"));
        assertEquals(ToolKind.SWORD, ToolKind.fromText("lame du dragon"));
        assertEquals(ToolKind.SHOVEL, ToolKind.fromText("a shovel"));
        assertEquals(ToolKind.NONE, ToolKind.fromText("un casque"));
    }

    @Test
    void fromMaterialUsesSuffix() {
        assertEquals(ToolKind.PICKAXE, ToolKind.fromMaterial(Material.NETHERITE_PICKAXE));
        assertEquals(ToolKind.SWORD, ToolKind.fromMaterial(Material.DIAMOND_SWORD));
        assertEquals(ToolKind.AXE, ToolKind.fromMaterial(Material.IRON_AXE));
        assertEquals(ToolKind.HOE, ToolKind.fromMaterial(Material.WOODEN_HOE));
        assertEquals(ToolKind.SHOVEL, ToolKind.fromMaterial(Material.GOLDEN_SHOVEL));
        assertEquals(ToolKind.NONE, ToolKind.fromMaterial(Material.STICK));
        assertEquals(ToolKind.NONE, ToolKind.fromMaterial(null));
    }
}
