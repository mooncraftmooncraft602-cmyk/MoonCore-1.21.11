package com.mooncore.modules.crop;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip YAML de {@link CropDef} (Étape C1). Utilise {@link MemoryConfiguration} (pas de serveur).
 */
class CropDefTest {

    @Test
    void roundTripPreservesScalars() {
        CropDef d = new CropDef("lunar_wheat");
        d.setDisplayName("<aqua>Blé Lunaire</aqua>");
        d.setStages(6);
        d.setGrowthTicks(1200);
        d.setMinLight(7);
        d.setRequiresWater(false);
        d.setDropRange(2, 5);
        d.setSeedReturnRange(1, 3);
        d.setReplantable(false);
        d.setDropItemId("lunar_grain");

        MemoryConfiguration cfg = new MemoryConfiguration();
        d.save(cfg);
        CropDef back = CropDef.load("lunar_wheat", cfg);

        assertEquals("<aqua>Blé Lunaire</aqua>", back.displayName());
        assertEquals(6, back.stages());
        assertEquals(1200, back.growthTicks());
        assertEquals(7, back.minLight());
        assertFalse(back.requiresWater());
        assertEquals(2, back.dropMin());
        assertEquals(5, back.dropMax());
        assertEquals(1, back.seedReturnMin());
        assertEquals(3, back.seedReturnMax());
        assertFalse(back.replantable());
        assertEquals("lunar_grain", back.dropItemId());
    }

    @Test
    void lootTableReferenceRoundTrips() {
        CropDef d = new CropDef("magic_berry");
        assertFalse(d.usesLootTable());                 // aucun par défaut
        d.setLootTableId("  Magic_Harvest  ");          // normalisé (trim + lower)
        assertTrue(d.usesLootTable());
        assertEquals("magic_harvest", d.lootTableId());

        MemoryConfiguration cfg = new MemoryConfiguration();
        d.save(cfg);
        CropDef back = CropDef.load("magic_berry", cfg);
        assertEquals("magic_harvest", back.lootTableId());
        assertTrue(back.usesLootTable());

        d.setLootTableId("");                           // vide → désactive
        assertFalse(d.usesLootTable());
    }

    @Test
    void clampsBoundsAndStageKeys() {
        CropDef d = new CropDef("test");
        d.setStages(99);                 // borné à 16
        assertEquals(16, d.stages());
        d.setMinLight(50);               // borné à 15
        assertEquals(15, d.minLight());
        d.setDropRange(5, 2);            // max < min → max relevé à min
        assertTrue(d.dropMax() >= d.dropMin());
        d.setStages(4);
        assertEquals("test_stage0", d.stageModelKey(0));
        assertEquals("test_stage3", d.stageModelKey(3));
        assertEquals("test_stage3", d.stageModelKey(99)); // clamp haut
    }
}
