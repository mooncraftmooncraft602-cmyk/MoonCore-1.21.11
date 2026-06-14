package com.mooncore.modules.customitem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prédicat de gating {@link ToolTier#meets} (niveau ≥), fondation de l'éligibilité de minage des blocs
 * custom. Vérifie l'ordre des niveaux et l'arête vanilla GOLD == WOOD (l'or mine comme le bois). Pur.
 */
class ToolTierMeetsTest {

    @Test
    void higherOrEqualTierMeetsRequirement() {
        assertTrue(ToolTier.NETHERITE.meets(ToolTier.DIAMOND));
        assertTrue(ToolTier.DIAMOND.meets(ToolTier.IRON));
        assertTrue(ToolTier.IRON.meets(ToolTier.IRON));        // égalité
        assertTrue(ToolTier.STONE.meets(ToolTier.WOOD));
        assertTrue(ToolTier.WOOD.meets(ToolTier.HAND));
        assertTrue(ToolTier.HAND.meets(null));                  // pas d'exigence
    }

    @Test
    void lowerTierFailsRequirement() {
        assertFalse(ToolTier.WOOD.meets(ToolTier.STONE));
        assertFalse(ToolTier.STONE.meets(ToolTier.IRON));
        assertFalse(ToolTier.IRON.meets(ToolTier.DIAMOND));
        assertFalse(ToolTier.DIAMOND.meets(ToolTier.NETHERITE));
        assertFalse(ToolTier.HAND.meets(ToolTier.WOOD));
    }

    @Test
    void goldMinesLikeWoodNotBetter() {
        // GOLD et WOOD au même niveau (1) : l'or satisfait exactement les exigences du bois, rien de plus.
        assertTrue(ToolTier.GOLD.meets(ToolTier.WOOD));
        assertTrue(ToolTier.WOOD.meets(ToolTier.GOLD));
        assertFalse(ToolTier.GOLD.meets(ToolTier.STONE));   // l'or ne mine PAS au niveau pierre+
        assertFalse(ToolTier.GOLD.meets(ToolTier.IRON));
    }
}
