package com.mooncore.modules.customitem.forge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Filet de sécurité de la source IA ({@link GptProgramSource#isValidFor}) : un programme produit par l'IA
 * n'est accepté que s'il correspond au type d'objet du nom ET dessine quelque chose. Pur (sans modèle).
 */
class GptProgramSourceTest {

    @Test
    void acceptsProgramMatchingTheNamedKind() {
        assertTrue(GptProgramSource.isValidFor(TextureSynth.PROG_SWORD, "Épée du Feu"));
        assertTrue(GptProgramSource.isValidFor(TextureSynth.PROG_PICKAXE, "Pioche de Glace"));
        assertTrue(GptProgramSource.isValidFor(TextureSynth.PROG_HELMET, "Casque du Dragon"));
        assertTrue(GptProgramSource.isValidFor(TextureSynth.PROG_CHESTPLATE, "Armure Royale"));
    }

    @Test
    void rejectsWrongKindOrEmpty() {
        // programme d'épée pour un nom de casque -> rejeté (pas de MELL) -> repli compositeur
        assertFalse(GptProgramSource.isValidFor(TextureSynth.PROG_SWORD, "Casque du Feu"));
        // programme d'épée pour une armure -> rejeté
        assertFalse(GptProgramSource.isValidFor(TextureSynth.PROG_SWORD, "Plastron du Feu"));
        // bruit -> rejeté
        assertFalse(GptProgramSource.isValidFor("blah blah nonsense", "Épée du Feu"));
        assertFalse(GptProgramSource.isValidFor("", "Épée"));
    }
}
