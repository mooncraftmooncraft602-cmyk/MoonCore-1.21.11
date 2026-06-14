package com.mooncore.modules.loot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validation d'id de {@link LootTableStore} : slug sûr (anti path-traversal). Pur, sans serveur ni fichier.
 */
class LootTableStoreTest {

    @Test
    void acceptsSafeSlugs() {
        assertTrue(LootTableStore.isValidId("boss_drops"));
        assertTrue(LootTableStore.isValidId("ore-vein-1"));
        assertTrue(LootTableStore.isValidId("a"));
    }

    @Test
    void rejectsUnsafeOrEmpty() {
        assertFalse(LootTableStore.isValidId(null));
        assertFalse(LootTableStore.isValidId(""));
        assertFalse(LootTableStore.isValidId("UPPER"));            // majuscules hors slug
        assertFalse(LootTableStore.isValidId("../etc/passwd"));    // path traversal
        assertFalse(LootTableStore.isValidId("a/b"));              // séparateur de chemin
        assertFalse(LootTableStore.isValidId("a".repeat(49)));     // trop long (> 48)
    }
}
