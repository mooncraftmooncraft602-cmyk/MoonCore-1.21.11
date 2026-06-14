package com.mooncore.modules.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nettoyage des réponses IA brutes : {@code extractJson} (extrait l'objet JSON malgré fences
 * markdown/prose) et {@code sanitizeId}. Logique de robustesse face aux sorties LLM désordonnées.
 */
class AiJsonExtractTest {

    @Test
    void extractsPlainJson() {
        assertEquals("{\"a\":1}", AiActionValidator.extractJson("{\"a\":1}"));
    }

    @Test
    void extractsFromMarkdownFences() {
        String md = "Voici l'objet :\n```json\n{\"id\":\"x\",\"v\":2}\n```\nVoilà.";
        assertEquals("{\"id\":\"x\",\"v\":2}", AiActionValidator.extractJson(md));
    }

    @Test
    void extractsFromSurroundingProse() {
        String s = "Bien sûr ! {\"k\": [1, 2]} — j'espère que ça aide.";
        assertEquals("{\"k\": [1, 2]}", AiActionValidator.extractJson(s));
    }

    @Test
    void noBracesReturnsNull() {
        assertNull(AiActionValidator.extractJson("aucun json ici"));
        assertNull(AiActionValidator.extractJson(""));
        assertNull(AiActionValidator.extractJson(null));
    }

    @Test
    void sanitizeId() {
        assertEquals("lame_du_dragon", AiActionValidator.sanitizeId("Lame du Dragon"));
        assertEquals("a-b_c", AiActionValidator.sanitizeId("  A-B_c  "));
        assertEquals("___", AiActionValidator.sanitizeId("***"));            // chars invalides → underscores (slug valide)
        assertEquals("ai_item", AiActionValidator.sanitizeId("   "));        // vide après trim → défaut
        assertTrue(AiActionValidator.sanitizeId("a".repeat(80)).length() <= 40); // tronqué à 40
    }
}
