package com.mooncore.modules.customitem.forge;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Vérifie que l'<b>inférence Java pure</b> ({@link GptInference}) chargée depuis le binaire DSL entraîné
 * produit le bon « gène » {@code KIND b s o} pour un nom — c.-à-d. que l'IA, côté serveur, choisit le bon
 * type d'objet. Ignoré si {@code forge-gpt-dsl.bin} est absent (non versionné).
 */
class GptDslInferenceIT {

    private static final File BIN = new File("tools/forge-model/forge-gpt-dsl.bin");

    @Test
    void javaInferencePicksCorrectKind() {
        assumeTrue(BIN.isFile(), "forge-gpt-dsl.bin absent — test d'intégration ignoré");
        GptInference model = GptInference.load(BIN);
        assertNotNull(model, "modèle DSL chargé");
        String[][] cases = {
            {"Épée du Feu", "SWORD"}, {"Pioche de Glace", "PICKAXE"}, {"Hache de Foudre", "AXE"},
            {"Casque du Dragon", "HELMET"}, {"Plastron du Soleil", "CHESTPLATE"}, {"Armure de Jade", "CHESTPLATE"},
        };
        int ok = 0;
        for (String[] c : cases) {
            String tag = model.generate(c[0] + " => ", 24, '\n');
            int i = tag.indexOf("=>");
            String out = (i >= 0 ? tag.substring(i + 2) : tag).trim();
            if (out.toUpperCase().startsWith(c[1])) ok++;
        }
        assertTrue(ok >= 5, "l'IA Java choisit le bon type (>=5/6), vu " + ok + "/6");
    }
}
