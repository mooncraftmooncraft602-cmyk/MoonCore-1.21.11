package com.mooncore.modules.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Comparaison de versions {@link UpdateModule#compare} de l'auto-update (>0 si v1 plus récent). Logique
 * critique : un bug provoquerait un downgrade ou une MAJ ratée. Couvre le piège numérique (10 > 9), le
 * préfixe {@code v}, les suffixes pré-release et les longueurs inégales. Pur, sans serveur.
 */
class UpdateVersionCompareTest {

    @Test
    void numericNotLexicalOrdering() {
        assertTrue(UpdateModule.compare("2.0.10", "2.0.9") > 0);   // 10 > 9 (et non "10" < "9")
        assertTrue(UpdateModule.compare("2.0.9", "2.0.10") < 0);
        assertTrue(UpdateModule.compare("2.10.0", "2.9.0") > 0);
    }

    @Test
    void higherComponentWins() {
        assertTrue(UpdateModule.compare("2.1.0", "2.0.9") > 0);
        assertTrue(UpdateModule.compare("3.0.0", "2.9.9") > 0);
        assertTrue(UpdateModule.compare("2.0.1", "2.0.0") > 0);
    }

    @Test
    void equalVersions() {
        assertEquals(0, UpdateModule.compare("2.0.0", "2.0.0"));
        assertEquals(0, UpdateModule.compare("2.0", "2.0.0"));     // longueurs inégales, composants manquants = 0
        assertEquals(0, UpdateModule.compare("v2.0.0", "2.0.0"));  // préfixe v ignoré
    }

    @Test
    void stripsPrefixAndPreReleaseSuffix() {
        assertTrue(UpdateModule.compare("v2.1.0", "v2.0.0") > 0);
        assertEquals(0, UpdateModule.compare("2.0.0-SNAPSHOT", "2.0.0"));  // suffixe pré-release ignoré (contrat)
        assertTrue(UpdateModule.compare("2.1.0-beta", "2.0.0") > 0);
    }
}
