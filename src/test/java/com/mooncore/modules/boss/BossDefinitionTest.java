package com.mooncore.modules.boss;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Règles de défaut/validation du constructeur compact de {@link BossDefinition} : garde l'intégrité
 * des données boss (phases, maxHealth, textureKey, equipment). Pur (record + enum), sans serveur.
 */
class BossDefinitionTest {

    private BossDefinition def(double maxHealth, List<BossPhase> phases, String textureKey,
                              Map<String, String> equipment) {
        return new BossDefinition("b", "Boss", EntityType.ZOMBIE, maxHealth, 8, 0.25, 0,
                phases, null, 0, "PURPLE", textureKey, 0, equipment, null);
    }

    @Test
    void lootTableNormalizationAndFlag() {
        BossDefinition none = new BossDefinition("b", "Boss", EntityType.ZOMBIE, 100, 8, 0.25, 0,
                List.of(), null, 0, "PURPLE", null, 0, Map.of(), "   ");  // blanc → null
        assertNull(none.lootTableId());
        assertTrue(!none.usesLootTable());

        BossDefinition with = new BossDefinition("b", "Boss", EntityType.ZOMBIE, 100, 8, 0.25, 0,
                List.of(), null, 0, "PURPLE", null, 0, Map.of(), "boss_drops");
        assertEquals("boss_drops", with.lootTableId());
        assertTrue(with.usesLootTable());
    }

    @Test
    void emptyPhasesGetsDefaultPhase() {
        BossDefinition d = def(200, List.of(), "tex", Map.of());
        assertEquals(1, d.phases().size());
        assertEquals("default", d.phases().get(0).name());
        assertEquals(100, d.phases().get(0).fromPercent(), 1e-9);
    }

    @Test
    void nonPositiveHealthDefaultsTo100() {
        assertEquals(100, def(0, null, null, null).maxHealth(), 1e-9);
        assertEquals(100, def(-5, null, null, null).maxHealth(), 1e-9);
        assertEquals(250, def(250, null, null, null).maxHealth(), 1e-9);
    }

    @Test
    void blankTextureKeyBecomesNullAndNullEquipmentBecomesEmpty() {
        BossDefinition d = def(200, null, "   ", null);
        assertNull(d.textureKey());
        assertTrue(d.equipment().isEmpty());

        BossDefinition keep = def(200, null, "gardien", Map.of("head", "casque"));
        assertEquals("gardien", keep.textureKey());
        assertEquals("casque", keep.equipment().get("head"));
    }
}
