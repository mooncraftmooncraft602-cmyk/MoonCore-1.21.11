package com.mooncore.modules.boss;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Contrat anti-triche de {@link BossManagerModule#clampField} : borne une valeur numérique IA dans
 * [min,max], laisse les non-nombres intacts. Pur, sans serveur (uniquement Map + Math).
 */
class BossClampFieldTest {

    @Test
    void clampsNumbersWithinBounds() {
        Map<String, Object> m = new HashMap<>();
        m.put("max-health", 999999);     // tentative de boss surpuissant
        m.put("damage", 8);              // déjà dans les bornes
        m.put("speed", -3.0);            // sous le plancher

        BossManagerModule.clampField(m, "max-health", 10, 5000);
        BossManagerModule.clampField(m, "damage", 1, 40);
        BossManagerModule.clampField(m, "speed", 0.1, 0.6);

        assertEquals(5000.0, m.get("max-health"));   // plafonné
        assertEquals(8.0, m.get("damage"));          // inchangé (déjà valide), mais normalisé en double
        assertEquals(0.1, m.get("speed"));           // relevé au plancher
    }

    @Test
    void leavesNonNumbersAndAbsentKeysUntouched() {
        Map<String, Object> m = new HashMap<>();
        m.put("damage", "beaucoup");     // valeur non numérique (hallucination IA)
        BossManagerModule.clampField(m, "damage", 1, 40);
        assertEquals("beaucoup", m.get("damage"));   // laissée intacte (le parsing en aval la rejettera)

        BossManagerModule.clampField(m, "absente", 0, 10);
        assertFalse(m.containsKey("absente"));        // pas de clé créée
    }
}
