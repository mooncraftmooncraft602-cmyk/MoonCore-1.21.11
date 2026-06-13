package com.mooncore.data.content;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vérifie le pont générique YAML↔JSON (Étape A2). Utilise {@link MemoryConfiguration}
 * (aucune dépendance vers un serveur Bukkit) pour rester un test unitaire pur et rapide.
 */
class ContentJsonTest {

    @Test
    void roundTripPreservesScalarsAndStructure() {
        MemoryConfiguration src = new MemoryConfiguration();
        src.set("display-name", "<white>Épée</white>");
        src.set("custom-model-data", 42);
        src.set("glowing", true);
        src.set("ratio", 0.75);
        src.set("lore", List.of("ligne 1", "ligne 2"));

        ConfigurationSection stats = src.createSection("stats");
        stats.set("attack_damage", 12.5);
        stats.set("armor", 3.0);

        String json = ContentJson.toJson(src);
        ConfigurationSection back = ContentJson.toSection(json);

        assertEquals("<white>Épée</white>", back.getString("display-name"));
        assertEquals(42, back.getInt("custom-model-data"));
        assertTrue(back.getBoolean("glowing"));
        assertEquals(0.75, back.getDouble("ratio"), 1e-9);
        assertEquals(List.of("ligne 1", "ligne 2"), back.getStringList("lore"));
        assertEquals(12.5, back.getConfigurationSection("stats").getDouble("attack_damage"), 1e-9);
        assertEquals(3.0, back.getConfigurationSection("stats").getDouble("armor"), 1e-9);
    }

    @Test
    void roundTripPreservesListOfMaps() {
        MemoryConfiguration src = new MemoryConfiguration();
        src.set("abilities", List.of(
                Map.of("id", "lifesteal", "level", 2),
                Map.of("id", "dash", "level", 1)));

        String json = ContentJson.toJson(src);
        ConfigurationSection back = ContentJson.toSection(json);

        List<Map<?, ?>> abilities = back.getMapList("abilities");
        assertEquals(2, abilities.size());
        assertEquals("lifesteal", abilities.get(0).get("id"));
        assertEquals(2, ((Number) abilities.get(0).get("level")).intValue());
        assertEquals("dash", abilities.get(1).get("id"));
    }

    @Test
    void emptySectionRoundTrips() {
        ConfigurationSection back = ContentJson.toSection(ContentJson.toJson(new MemoryConfiguration()));
        assertFalse(back.getKeys(false).iterator().hasNext());
    }
}
