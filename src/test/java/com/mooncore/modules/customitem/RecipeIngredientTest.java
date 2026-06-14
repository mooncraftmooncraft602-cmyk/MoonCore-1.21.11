package com.mooncore.modules.customitem;

import com.mooncore.modules.customitem.CustomItemDef.RecipeIngredient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parsing d'ingrédient de recette {@link RecipeIngredient} — branches «&nbsp;item custom&nbsp;» et
 * normalisation (cœur du crafting custom). On évite la branche Material qui appelle {@code isItem()}
 * (nécessite un serveur). Pur, sans serveur.
 */
class RecipeIngredientTest {

    @Test
    void parsesCustomPrefixes() {
        RecipeIngredient a = RecipeIngredient.parse("custom:Lame_Du_Dragon");
        assertTrue(a.isCustom());
        assertEquals("lame_du_dragon", a.customItemId());     // normalisé en minuscule

        RecipeIngredient b = RecipeIngredient.parse("item:MagicGem");
        assertTrue(b.isCustom());
        assertEquals("magicgem", b.customItemId());
    }

    @Test
    void nullOrBlankYieldsNull() {
        assertNull(RecipeIngredient.parse(null));
        assertNull(RecipeIngredient.parse("   "));
    }

    @Test
    void constructorNormalizesCustomId() {
        RecipeIngredient i = RecipeIngredient.custom("  Foo  ");
        assertEquals("foo", i.customItemId());                // trim + lower
        RecipeIngredient blank = RecipeIngredient.custom("   ");
        assertFalse(blank.isCustom());                        // blanc → null
        assertNull(blank.customItemId());
    }

    @Test
    void storageKeyRoundTripsCustom() {
        RecipeIngredient i = RecipeIngredient.custom("epic");
        assertEquals("custom:epic", i.storageKey());
        assertEquals("custom:epic", i.toString());
        // storageKey custom est re-parsable en la même valeur.
        assertEquals("epic", RecipeIngredient.parse(i.storageKey()).customItemId());
    }
}
