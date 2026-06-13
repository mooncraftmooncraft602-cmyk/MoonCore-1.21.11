package com.mooncore.data.content;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Migration de schéma en CASCADE (Étape A5) : un schéma à 3 versions doit amener un JSON depuis
 * n'importe quelle version antérieure jusqu'à la version courante. Pur, sans DB.
 */
class ContentSchemaCascadeTest {

    /** v2 : renomme {@code dmg}→{@code damage}. v3 : ajoute {@code tier:1} par défaut. */
    private static final ContentSchema SCHEMA = new ContentSchema() {
        @Override public int currentVersion() { return 3; }
        @Override public JsonObject upgrade(JsonObject data, int fromVersion) {
            if (fromVersion < 2 && data.has("dmg")) {
                data.add("damage", data.get("dmg"));
                data.remove("dmg");
            }
            if (fromVersion < 3 && !data.has("tier")) {
                data.addProperty("tier", 1);
            }
            return data;
        }
    };

    @Test
    void upgradesFromV1ThroughAllSteps() {
        ContentSchemas.register("cascade", SCHEMA);
        String upgraded = ContentSchemas.upgradeToCurrent("cascade", "{\"dmg\":7}", 1);
        assertTrue(upgraded.contains("\"damage\":7"), upgraded);   // v1→v2
        assertTrue(upgraded.contains("\"tier\":1"), upgraded);     // v2→v3
        assertFalse(upgraded.contains("\"dmg\""), upgraded);
    }

    @Test
    void upgradesFromV2OnlyAddsTier() {
        ContentSchemas.register("cascade", SCHEMA);
        String upgraded = ContentSchemas.upgradeToCurrent("cascade", "{\"damage\":5}", 2);
        assertTrue(upgraded.contains("\"damage\":5"), upgraded);
        assertTrue(upgraded.contains("\"tier\":1"), upgraded);
    }

    @Test
    void alreadyCurrentUntouched() {
        ContentSchemas.register("cascade", SCHEMA);
        String current = "{\"damage\":5,\"tier\":2}";
        assertEquals(current, ContentSchemas.upgradeToCurrent("cascade", current, 3));
    }
}
