package com.mooncore.modules.customitem;

import com.mooncore.modules.customitem.CustomItemDef.RecipeIngredient;
import com.mooncore.modules.customitem.CustomItemDef.SmithingRecipe;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip YAML de la recette de forge {@link SmithingRecipe} sur {@link CustomItemDef}. Utilise des
 * ingrédients {@code custom:} (branche headless de {@link RecipeIngredient}, sans {@code Material.isItem()}).
 */
class SmithingRecipeRoundTripTest {

    @Test
    void validityRequiresBaseAndAddition() {
        assertFalse(new SmithingRecipe().isValid());
        assertFalse(new SmithingRecipe(null, RecipeIngredient.custom("a"), null).isValid()); // addition manquante
        assertTrue(new SmithingRecipe(null, RecipeIngredient.custom("a"), RecipeIngredient.custom("b")).isValid());
    }

    @Test
    void roundTripWithTemplate() {
        CustomItemDef d = new CustomItemDef("forged_blade");
        d.setSmithing(new SmithingRecipe(
                RecipeIngredient.custom("dragon_template"),
                RecipeIngredient.custom("base_sword"),
                RecipeIngredient.custom("dragon_scale")));
        assertTrue(d.canSmith());

        MemoryConfiguration cfg = new MemoryConfiguration();
        d.save(cfg);
        CustomItemDef back = CustomItemDef.load("forged_blade", cfg);
        assertTrue(back.canSmith());
        assertEquals("dragon_template", back.smithing().template.customItemId());
        assertEquals("base_sword", back.smithing().base.customItemId());
        assertEquals("dragon_scale", back.smithing().addition.customItemId());
    }

    @Test
    void roundTripWithoutTemplate() {
        CustomItemDef d = new CustomItemDef("plated_helm");
        d.setSmithing(new SmithingRecipe(null,
                RecipeIngredient.custom("base_helm"),
                RecipeIngredient.custom("plating")));
        MemoryConfiguration cfg = new MemoryConfiguration();
        d.save(cfg);
        CustomItemDef back = CustomItemDef.load("plated_helm", cfg);
        assertTrue(back.canSmith());
        assertNull(back.smithing().template);                 // patron optionnel
        assertEquals("base_helm", back.smithing().base.customItemId());
    }

    @Test
    void cloneCopiesSmithing() {
        CustomItemDef d = new CustomItemDef("x");
        d.setSmithing(new SmithingRecipe(null, RecipeIngredient.custom("a"), RecipeIngredient.custom("b")));
        CustomItemDef c = d.cloneAs("y");
        assertTrue(c.canSmith());
        assertEquals("a", c.smithing().base.customItemId());
    }
}
