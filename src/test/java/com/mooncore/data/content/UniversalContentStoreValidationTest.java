package com.mooncore.data.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validateurs statiques de {@link UniversalContentStore} (gardes anti path-traversal des écritures SQL).
 * Purs, sans DB.
 */
class UniversalContentStoreValidationTest {

    @Test
    void validIds() {
        assertTrue(UniversalContentStore.isValidId("lunarium_sword"));
        assertTrue(UniversalContentStore.isValidId("a-b_c9"));
        assertTrue(UniversalContentStore.isValidId("x"));
        assertTrue(UniversalContentStore.isValidId("a".repeat(48)));
    }

    @Test
    void invalidIds() {
        assertFalse(UniversalContentStore.isValidId(null));
        assertFalse(UniversalContentStore.isValidId(""));
        assertFalse(UniversalContentStore.isValidId("Upper"));     // majuscule
        assertFalse(UniversalContentStore.isValidId("a b"));        // espace
        assertFalse(UniversalContentStore.isValidId("../etc"));     // path traversal
        assertFalse(UniversalContentStore.isValidId("a/b"));        // séparateur
        assertFalse(UniversalContentStore.isValidId("a".repeat(49))); // trop long
    }

    @Test
    void validAndInvalidTypes() {
        assertTrue(UniversalContentStore.isValidType("item"));
        assertTrue(UniversalContentStore.isValidType("crop"));
        assertTrue(UniversalContentStore.isValidType("a_b_1"));
        assertFalse(UniversalContentStore.isValidType(null));
        assertFalse(UniversalContentStore.isValidType("Boss"));      // majuscule
        assertFalse(UniversalContentStore.isValidType("a-b"));       // tiret non autorisé pour un type
        assertFalse(UniversalContentStore.isValidType("a".repeat(33))); // > 32
    }
}
