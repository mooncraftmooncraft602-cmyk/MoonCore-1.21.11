package com.mooncore.modules.create;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mapping de routage {@link CreateSubCommand#normalizeKind} pour la création unifiée IA : synonymes →
 * type du registre. Une régression enverrait un élément IA vers le mauvais handler (ou aucun). Pur.
 */
class NormalizeKindTest {

    @Test
    void mapsSynonymsToRegistryTypes() {
        assertEquals("block", CreateSubCommand.normalizeKind("ore"));
        assertEquals("block", CreateSubCommand.normalizeKind("Minerai"));
        assertEquals("boss", CreateSubCommand.normalizeKind("mob"));
        assertEquals("crop", CreateSubCommand.normalizeKind("plante"));
        assertEquals("loot", CreateSubCommand.normalizeKind("loot_table"));
        assertEquals("loot", CreateSubCommand.normalizeKind("Butin"));
        assertEquals("mechanic", CreateSubCommand.normalizeKind("trap"));
        assertEquals("mechanic", CreateSubCommand.normalizeKind("MECANIQUE"));
    }

    @Test
    void passesThroughCanonicalTypesLowercased() {
        assertEquals("item", CreateSubCommand.normalizeKind("item"));
        assertEquals("loot", CreateSubCommand.normalizeKind("LOOT"));
        assertEquals("mechanic", CreateSubCommand.normalizeKind("mechanic"));
        assertEquals("boss", CreateSubCommand.normalizeKind("Boss"));
    }
}
