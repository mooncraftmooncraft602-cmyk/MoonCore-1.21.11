package com.mooncore.modules.customblock;

import com.mooncore.modules.customitem.ToolKind;
import com.mooncore.modules.customitem.ToolTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cohérence des setters d'outil interdépendants de {@link CustomBlockDef}
 * (requiresPickaxe ↔ requiredTool ↔ minToolTier). Pur, sans serveur.
 */
class CustomBlockDefToolTest {

    @Test
    void requiresPickaxeFalseClearsTool() {
        CustomBlockDef d = new CustomBlockDef("x");
        d.setRequiresPickaxe(false);
        assertEquals(ToolKind.NONE, d.requiredTool());
        assertEquals(ToolTier.HAND, d.minToolTier());
        assertFalse(d.requiresPickaxe());
    }

    @Test
    void requiresPickaxeTrueSetsPickaxeAndMinTier() {
        CustomBlockDef d = new CustomBlockDef("x");
        d.setRequiredTool(ToolKind.NONE);   // repart de zéro
        d.setRequiresPickaxe(true);
        assertEquals(ToolKind.PICKAXE, d.requiredTool());
        assertTrue(d.requiresPickaxe());
        // minToolTier ne reste pas HAND quand un outil est requis.
        assertEquals(ToolTier.WOOD, d.minToolTier());
    }

    @Test
    void setRequiredToolSyncsRequiresPickaxe() {
        CustomBlockDef d = new CustomBlockDef("x");
        d.setRequiredTool(ToolKind.AXE);
        assertFalse(d.requiresPickaxe());           // une hache n'est pas une pioche
        d.setMinToolTier(ToolTier.DIAMOND);
        assertEquals(ToolTier.DIAMOND, d.minToolTier());

        d.setRequiredTool(ToolKind.PICKAXE);
        assertTrue(d.requiresPickaxe());

        d.setRequiredTool(ToolKind.NONE);
        assertEquals(ToolTier.HAND, d.minToolTier()); // plus d'outil → tier HAND
        assertFalse(d.requiresPickaxe());
    }
}
