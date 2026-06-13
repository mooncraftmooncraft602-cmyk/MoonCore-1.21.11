package com.mooncore.data.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Logique du flag {@code content.storage-mode} ({@link ContentSyncService.Mode}). Pur, sans DB.
 */
class ContentSyncModeTest {

    @Test
    void parseDefaultsToYaml() {
        assertEquals(ContentSyncService.Mode.YAML, ContentSyncService.Mode.parse(null));
        assertEquals(ContentSyncService.Mode.YAML, ContentSyncService.Mode.parse(""));
        assertEquals(ContentSyncService.Mode.YAML, ContentSyncService.Mode.parse("inconnu"));
        assertEquals(ContentSyncService.Mode.YAML, ContentSyncService.Mode.parse(" YAML "));
    }

    @Test
    void parseSqlAndBoth() {
        assertEquals(ContentSyncService.Mode.SQL, ContentSyncService.Mode.parse("sql"));
        assertEquals(ContentSyncService.Mode.SQL, ContentSyncService.Mode.parse("SQL"));
        assertEquals(ContentSyncService.Mode.BOTH, ContentSyncService.Mode.parse("both"));
    }

    @Test
    void writeTargetsPerMode() {
        // yaml : YAML seul
        assertTrue(ContentSyncService.Mode.YAML.writesYaml());
        assertFalse(ContentSyncService.Mode.YAML.writesSql());
        // both : les deux
        assertTrue(ContentSyncService.Mode.BOTH.writesYaml());
        assertTrue(ContentSyncService.Mode.BOTH.writesSql());
        // sql : SQL autoritaire (plus de YAML)
        assertFalse(ContentSyncService.Mode.SQL.writesYaml());
        assertTrue(ContentSyncService.Mode.SQL.writesSql());
    }
}
