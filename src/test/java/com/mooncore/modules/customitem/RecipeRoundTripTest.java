package com.mooncore.modules.customitem;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip YAML de la recette d'un {@link CustomItemDef}, y compris le flag <b>shapeless</b>
 * (RecipeManager enregistre shaped OU shapeless selon ce flag). Ingrédients custom → headless-safe.
 */
class RecipeRoundTripTest {

    @Test
    void shapelessRecipeRoundTrips() {
        CustomItemDef d = new CustomItemDef("alliage");
        CustomItemDef.Recipe r = new CustomItemDef.Recipe();
        r.shaped = false;
        r.amount = 4;
        r.ingredients.put('A', CustomItemDef.RecipeIngredient.parse("custom:poudre_lunaire"));
        r.ingredients.put('B', CustomItemDef.RecipeIngredient.parse("custom:fer_brut"));
        d.setRecipe(r);

        MemoryConfiguration cfg = new MemoryConfiguration();
        d.save(cfg);
        CustomItemDef back = CustomItemDef.load("alliage", cfg);

        assertNotNull(back.recipe());
        assertFalse(back.recipe().shaped, "flag shapeless préservé");
        assertEquals(4, back.recipe().amount);
        assertEquals(2, back.recipe().ingredients.size());
        assertTrue(back.recipe().ingredients.get('A').isCustom());
        assertEquals("custom:poudre_lunaire", back.recipe().ingredients.get('A').storageKey());
        assertEquals("custom:fer_brut", back.recipe().ingredients.get('B').storageKey());
    }

    @Test
    void shapedRecipeRoundTrips() {
        CustomItemDef d = new CustomItemDef("baton");
        CustomItemDef.Recipe r = new CustomItemDef.Recipe();
        r.shaped = true;
        r.shape = java.util.List.of("A  ", "A  ", "A  ");
        r.ingredients.put('A', CustomItemDef.RecipeIngredient.parse("custom:bois_lunaire"));
        d.setRecipe(r);

        MemoryConfiguration cfg = new MemoryConfiguration();
        d.save(cfg);
        CustomItemDef back = CustomItemDef.load("baton", cfg);

        assertNotNull(back.recipe());
        assertTrue(back.recipe().shaped);
        assertEquals(java.util.List.of("A  ", "A  ", "A  "), back.recipe().shape);
        assertEquals("custom:bois_lunaire", back.recipe().ingredients.get('A').storageKey());
    }
}
