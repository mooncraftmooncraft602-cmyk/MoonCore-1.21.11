package com.mooncore.modules.crop;

import org.bukkit.Material;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Round-trip YAML des chemins Material / id-custom de {@link CropDef} (graine custom, drop material,
 * support non-défaut), empruntés par {@code /moon crop} et l'IA. Pur (Material.matchMaterial).
 */
class CropDefMaterialTest {

    @Test
    void customSeedAndDropMaterialRoundTrip() {
        CropDef d = new CropDef("ble_lunaire");
        d.setSeedCustomId("graine_lunaire");
        d.setPlaceOn(Material.GRASS_BLOCK);
        d.setDropMaterial(Material.WHEAT);
        d.setDropRange(2, 4);

        MemoryConfiguration cfg = new MemoryConfiguration();
        d.save(cfg);
        CropDef back = CropDef.load("ble_lunaire", cfg);

        assertEquals("graine_lunaire", back.seedCustomId());
        assertEquals(Material.GRASS_BLOCK, back.placeOn());
        assertEquals(Material.WHEAT, back.dropMaterial());
        assertNull(back.dropItemId());                 // pas de drop custom → null
        assertEquals(2, back.dropMin());
        assertEquals(4, back.dropMax());
    }

    @Test
    void vanillaSeedDefaultsWhenNoCustom() {
        CropDef d = new CropDef("carotte");
        d.setSeed(Material.CARROT);

        MemoryConfiguration cfg = new MemoryConfiguration();
        d.save(cfg);
        CropDef back = CropDef.load("carotte", cfg);

        assertEquals(Material.CARROT, back.seed());
        assertNull(back.seedCustomId());
    }
}
