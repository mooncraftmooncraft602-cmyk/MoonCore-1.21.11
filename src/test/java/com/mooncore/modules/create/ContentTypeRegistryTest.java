package com.mooncore.modules.create;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mécanisme du registre de types de contenu (E4) — dispatch + handler en mémoire. Sans serveur.
 */
class ContentTypeRegistryTest {

    /** Handler factice en mémoire pour tester le registre. */
    private static final class FakeHandler implements ContentTypeHandler {
        private final String type;
        private final Set<String> ids = new LinkedHashSet<>();
        FakeHandler(String type) { this.type = type; }
        @Override public String type() { return type; }
        @Override public boolean create(String id) { return ids.add(id); }
        @Override public boolean exists(String id) { return ids.contains(id); }
        @Override public boolean delete(String id) { return ids.remove(id); }
        @Override public Collection<String> ids() { return ids; }
    }

    @Test
    void registerAndDispatch() {
        ContentTypeRegistry reg = new ContentTypeRegistry();
        FakeHandler crop = new FakeHandler("crop");
        reg.register(crop);
        reg.register(new FakeHandler("item"));

        assertEquals(2, reg.size());
        assertTrue(reg.has("CROP"));               // insensible à la casse
        assertSame(crop, reg.get("crop"));
        assertNull(reg.get("inexistant"));
        assertTrue(reg.types().contains("crop"));
        assertTrue(reg.types().contains("item"));

        ContentTypeHandler h = reg.get("crop");
        assertTrue(h.create("wheat"));
        assertFalse(h.create("wheat"));            // déjà créé
        assertTrue(h.exists("wheat"));
        assertTrue(h.delete("wheat"));
        assertFalse(h.exists("wheat"));
    }

    @Test
    void defaultsForOptionalCapabilities() {
        ContentTypeHandler h = new FakeHandler("x");
        assertNull(h.aiSystemPrompt());
        assertNull(h.createFromAi("{}", "id"));
        assertFalse(h.give(null, "id", 1));
        assertEquals("id", h.describe("id"));
    }
}
