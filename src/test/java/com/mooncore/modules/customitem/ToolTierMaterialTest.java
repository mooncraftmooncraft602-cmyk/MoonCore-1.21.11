package com.mooncore.modules.customitem;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link ToolTier#materialFor(ToolKind)} : chaque combinaison tier×famille doit donner un Material
 * vanilla valide (un préfixe erroné donnerait {@code null} et {@code setTool} ne changerait rien).
 * Pur (Material.matchMaterial sur des noms d'enum), sans serveur.
 */
class ToolTierMaterialTest {

    @Test
    void everyTierKindCombinationResolves() {
        ToolKind[] kinds = {ToolKind.SWORD, ToolKind.PICKAXE, ToolKind.AXE, ToolKind.SHOVEL, ToolKind.HOE};
        for (ToolTier tier : new ToolTier[]{ToolTier.WOOD, ToolTier.GOLD, ToolTier.STONE,
                ToolTier.IRON, ToolTier.DIAMOND, ToolTier.NETHERITE}) {
            for (ToolKind kind : kinds) {
                Material m = tier.materialFor(kind);
                assertNotNull(m, "matériau introuvable pour " + tier + " " + kind);
            }
        }
    }

    @Test
    void knownExamples() {
        assertEquals(Material.WOODEN_SWORD, ToolTier.WOOD.materialFor(ToolKind.SWORD));
        assertEquals(Material.GOLDEN_PICKAXE, ToolTier.GOLD.materialFor(ToolKind.PICKAXE));
        assertEquals(Material.NETHERITE_HOE, ToolTier.NETHERITE.materialFor(ToolKind.HOE));
        assertEquals(Material.DIAMOND_SHOVEL, ToolTier.DIAMOND.materialFor(ToolKind.SHOVEL));
    }

    @Test
    void noneAndHandReturnNull() {
        assertNull(ToolTier.IRON.materialFor(ToolKind.NONE));
        assertNull(ToolTier.IRON.materialFor(null));
        assertNull(ToolTier.HAND.materialFor(ToolKind.SWORD));
    }
}
