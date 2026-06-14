package com.mooncore.modules.mechanic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validation d'id de {@link MechanicStore} : slug sûr (anti path-traversal). Pur, sans serveur ni fichier.
 */
class MechanicStoreTest {

    @Test
    void acceptsSafeSlugs() {
        assertTrue(MechanicStore.isValidId("magic_wand"));
        assertTrue(MechanicStore.isValidId("daily-reward-1"));
        assertTrue(MechanicStore.isValidId("x"));
    }

    @Test
    void rejectsUnsafeOrEmpty() {
        assertFalse(MechanicStore.isValidId(null));
        assertFalse(MechanicStore.isValidId(""));
        assertFalse(MechanicStore.isValidId("UPPER"));
        assertFalse(MechanicStore.isValidId("../etc/passwd"));
        assertFalse(MechanicStore.isValidId("a/b"));
        assertFalse(MechanicStore.isValidId("a".repeat(49)));
    }
}
