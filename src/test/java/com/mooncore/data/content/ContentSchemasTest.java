package com.mooncore.data.content;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vérifie le mécanisme de versionnage de schéma + upgrade (Étape A5).
 */
class ContentSchemasTest {

    /** Schéma de test : v2 renomme le champ {@code dmg} en {@code damage}. */
    private static final ContentSchema TEST_SCHEMA = new ContentSchema() {
        @Override public int currentVersion() { return 2; }
        @Override public JsonObject upgrade(JsonObject data, int fromVersion) {
            if (fromVersion < 2 && data.has("dmg")) {
                data.add("damage", data.get("dmg"));
                data.remove("dmg");
            }
            return data;
        }
    };

    @Test
    void unregisteredTypeUsesIdentity() {
        assertSame(ContentSchemas.IDENTITY, ContentSchemas.get("type-inexistant"));
        assertEquals(1, ContentSchemas.currentVersion("type-inexistant"));
        String json = "{\"a\":1}";
        assertEquals(json, ContentSchemas.upgradeToCurrent("type-inexistant", json, 1));
    }

    @Test
    void upgradesOldVersionToCurrent() {
        ContentSchemas.register("test-upgrade", TEST_SCHEMA);
        String v1 = "{\"dmg\":7,\"name\":\"épée\"}";

        String upgraded = ContentSchemas.upgradeToCurrent("test-upgrade", v1, 1);

        assertTrue(upgraded.contains("\"damage\":7"), upgraded);
        assertTrue(upgraded.contains("\"name\":\"épée\""), upgraded);
        assertTrue(!upgraded.contains("\"dmg\""), upgraded);
    }

    @Test
    void alreadyCurrentIsUntouched() {
        ContentSchemas.register("test-upgrade", TEST_SCHEMA);
        String current = "{\"damage\":7}";
        assertEquals(current, ContentSchemas.upgradeToCurrent("test-upgrade", current, 2));
    }

    @Test
    void malformedJsonReturnedUnchanged() {
        ContentSchemas.register("test-upgrade", TEST_SCHEMA);
        String bad = "ce n'est pas du json";
        assertEquals(bad, ContentSchemas.upgradeToCurrent("test-upgrade", bad, 1));
    }
}
