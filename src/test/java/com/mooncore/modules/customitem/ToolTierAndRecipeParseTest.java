package com.mooncore.modules.customitem;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Inférence de tier d'outil {@link ToolTier} et parsing d'ingrédient de recette
 * {@link CustomItemDef.RecipeIngredient}. Pur (chaînes/enum), sans serveur.
 */
class ToolTierAndRecipeParseTest {

    @Test
    void tierFromId() {
        assertEquals(ToolTier.WOOD, ToolTier.fromId("bois"));
        assertEquals(ToolTier.GOLD, ToolTier.fromId("or"));
        assertEquals(ToolTier.STONE, ToolTier.fromId("pierre"));
        assertEquals(ToolTier.IRON, ToolTier.fromId("fer"));
        assertEquals(ToolTier.DIAMOND, ToolTier.fromId("diamant"));
        assertEquals(ToolTier.NETHERITE, ToolTier.fromId("NETHERITE"));
        assertEquals(ToolTier.HAND, ToolTier.fromId("inconnu"));
        assertEquals(ToolTier.HAND, ToolTier.fromId(null));
    }

    @Test
    void tierFromMaterialPrefix() {
        assertEquals(ToolTier.WOOD, ToolTier.fromMaterial(Material.WOODEN_PICKAXE));
        assertEquals(ToolTier.GOLD, ToolTier.fromMaterial(Material.GOLDEN_SWORD));
        assertEquals(ToolTier.STONE, ToolTier.fromMaterial(Material.STONE_AXE));
        assertEquals(ToolTier.IRON, ToolTier.fromMaterial(Material.IRON_SHOVEL));
        assertEquals(ToolTier.DIAMOND, ToolTier.fromMaterial(Material.DIAMOND_HOE));
        assertEquals(ToolTier.NETHERITE, ToolTier.fromMaterial(Material.NETHERITE_PICKAXE));
        assertEquals(ToolTier.HAND, ToolTier.fromMaterial(Material.STICK));
    }

    @Test
    void recipeIngredientPrefixes() {
        var custom = CustomItemDef.RecipeIngredient.parse("custom:lunarium_ingot");
        assertTrue(custom.isCustom());
        assertEquals("custom:lunarium_ingot", custom.storageKey());

        var item = CustomItemDef.RecipeIngredient.parse("item:lunarium_ingot");
        assertTrue(item.isCustom());

        assertNull(CustomItemDef.RecipeIngredient.parse(null));
        assertNull(CustomItemDef.RecipeIngredient.parse("   "));
    }

    @Test
    void recipeIngredientCustomFactoryAndStorageKey() {
        var c = CustomItemDef.RecipeIngredient.custom("Mon_Item");
        assertTrue(c.isCustom());
        assertEquals("custom:mon_item", c.storageKey());   // id normalisé en minuscules
    }
}
