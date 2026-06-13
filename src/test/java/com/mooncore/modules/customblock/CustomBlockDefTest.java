package com.mooncore.modules.customblock;

import com.mooncore.modules.customitem.ToolKind;
import com.mooncore.modules.customitem.ToolTier;
import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip YAML de {@link CustomBlockDef} (faces séparées, worldgen, outil requis). Sans serveur.
 */
class CustomBlockDefTest {

    @Test
    void roundTripFacesWorldgenAndTool() {
        CustomBlockDef d = new CustomBlockDef("mithril_ore");
        d.setDisplayName("<aqua>Minerai de Mithril</aqua>");
        d.setTextureTop("mithril_top");
        d.setTextureSide("mithril_side");
        d.setTextureBottom("mithril_bottom");
        d.setDropItemId("mithril_chunk");
        d.setDropXp(7);
        d.setRequiredTool(ToolKind.PICKAXE);
        d.setMinToolTier(ToolTier.IRON);
        d.setBreakDurability(3);
        d.setBlastResistance(12.0);
        d.setGenerate(true);
        d.setReplace(Material.DEEPSLATE);
        d.setYRange(-48, 16);
        d.setVeinsPerChunk(3);
        d.setVeinSize(5);

        MemoryConfiguration cfg = new MemoryConfiguration();
        d.save(cfg);
        CustomBlockDef back = CustomBlockDef.load("mithril_ore", cfg);

        assertEquals("<aqua>Minerai de Mithril</aqua>", back.displayName());
        assertTrue(back.hasFaces());
        assertEquals("mithril_top", back.textureTop());
        assertEquals("mithril_side", back.textureSide());
        assertEquals("mithril_bottom", back.textureBottom());
        assertEquals("mithril_chunk", back.dropItemId());
        assertEquals(7, back.dropXp());
        assertEquals(ToolKind.PICKAXE, back.requiredTool());
        assertEquals(ToolTier.IRON, back.minToolTier());
        assertEquals(3, back.breakDurability());
        assertEquals(12.0, back.blastResistance(), 1e-6);
        assertTrue(back.generate());
        assertEquals(Material.DEEPSLATE, back.replace());
        assertEquals(-48, back.minY());
        assertEquals(16, back.maxY());
        assertEquals(3, back.veinsPerChunk());
        assertEquals(5, back.veinSize());
    }

    @Test
    void defaultsAreSane() {
        CustomBlockDef d = new CustomBlockDef("plain");
        MemoryConfiguration cfg = new MemoryConfiguration();
        d.save(cfg);
        CustomBlockDef back = CustomBlockDef.load("plain", cfg);
        assertFalse(back.hasFaces());
        assertFalse(back.generate());
        assertTrue(back.requiresPickaxe());     // défaut
    }
}
