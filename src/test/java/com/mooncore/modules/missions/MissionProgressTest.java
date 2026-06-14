package com.mooncore.modules.missions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * État de progression des missions {@link MissionProgress} : accumulation plafonnée à la cible, défauts,
 * réclamation, reset (rollover de période), drapeau dirty. Pur, sans serveur.
 */
class MissionProgressTest {

    @Test
    void countDefaultsToZero() {
        assertEquals(0, new MissionProgress().count("x"));
    }

    @Test
    void addAccumulatesAndClampsToTarget() {
        MissionProgress mp = new MissionProgress();
        assertEquals(2, mp.add("kill", 2, 5));      // 0+2
        assertEquals(4, mp.add("kill", 2, 5));      // 2+2
        assertEquals(5, mp.add("kill", 10, 5));     // 4+10 → plafonné à 5
        assertEquals(5, mp.count("kill"));          // valeur stockée plafonnée (jamais > cible)
        assertEquals(5, mp.add("kill", 3, 5));      // reste à la cible
    }

    @Test
    void claimAndReset() {
        MissionProgress mp = new MissionProgress();
        mp.add("daily", 3, 3);
        assertFalse(mp.isClaimed("daily"));
        mp.setClaimed("daily");
        assertTrue(mp.isClaimed("daily"));

        mp.reset("daily");                          // rollover : compteur + réclamation effacés
        assertEquals(0, mp.count("daily"));
        assertFalse(mp.isClaimed("daily"));
    }

    @Test
    void setOverwritesAndDirtyFlag() {
        MissionProgress mp = new MissionProgress();
        assertFalse(mp.isDirty());
        mp.set("m", 7);
        assertEquals(7, mp.count("m"));
        assertTrue(mp.isDirty());
        mp.clearDirty();
        assertFalse(mp.isDirty());
        mp.add("m", 1, 100);
        assertTrue(mp.isDirty());                   // add marque dirty
    }
}
