package com.mooncore.modules.create;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validation/normalisation des ids de contenu (Étape E). Pur. */
class ContentIdsTest {

    @Test
    void normalizes() {
        assertEquals("abc", ContentIds.norm("  ABC "));
        assertEquals("lunar_wheat", ContentIds.norm("Lunar_Wheat"));
        assertNull(ContentIds.norm(null));
    }

    @Test
    void validatesSlug() {
        assertTrue(ContentIds.valid("abc"));
        assertTrue(ContentIds.valid("a-b_c9"));
        assertFalse(ContentIds.valid("Abc"));       // majuscule
        assertFalse(ContentIds.valid("a b"));       // espace
        assertFalse(ContentIds.valid(""));
        assertFalse(ContentIds.valid("../evil"));   // path traversal
        assertFalse(ContentIds.valid("a".repeat(49))); // > 48
        assertTrue(ContentIds.valid("a".repeat(48)));
        assertFalse(ContentIds.valid(null));
    }
}
