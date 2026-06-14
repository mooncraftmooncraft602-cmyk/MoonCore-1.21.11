package com.mooncore.modules.customitem.forge;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compositeur {@link TextureComposer} : déterminisme, variété, rendu valide, et <b>export du corpus
 * d'entraînement</b> {@code (nom → programme DSL)} pour apprendre le langage à une IA.
 */
class TextureComposerTest {

    private static final ThemePalette VENT = ThemePalette.ramp("vent", 0x1b5e20, 0x2adf77, 0xe8f5e9);

    @Test
    void composeIsDeterministicTypedAndRenderable() {
        assertEquals(TextureComposer.compose("Épée du Feu"), TextureComposer.compose("Épée du Feu"));   // déterministe
        // un nom typé donne un programme qui rend une silhouette (fond transparent, pixels du thème)
        BufferedImage img = TextureSynth.renderProgram(TextureComposer.compose("Épée Royale du Dragon"), VENT, 7L);
        assertEquals(0, img.getRGB(0, 0) >>> 24, "coin transparent");
        int op = 0;
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) if ((img.getRGB(x, y) >>> 24) != 0) op++;
        assertTrue(op > 25 && op < 256, "silhouette dessinée : " + op);
        // le type vient du nom : « pioche » contient deux têtes MCAP ; « épée » a un FULLER de lame
        assertTrue(TextureComposer.compose("Pioche de Glace").split("MCAP", -1).length - 1 >= 2, "tête de pioche");
        assertTrue(TextureComposer.compose("Épée du Vent").contains("FULLER"), "rainure de lame");
    }

    @Test
    void variationDiffersAcrossNames() {
        // deux noms différents -> programmes différents (variété), mais chacun reste stable
        assertNotEquals(TextureComposer.compose("Épée du Feu"), TextureComposer.compose("Grande Épée du Feu"));
        assertNotEquals(TextureComposer.compose("Lame Alpha"), TextureComposer.compose("Lame Oméga"));
    }

    /** Écrit le corpus {@code (nom => programme DSL)} dans tools/forge-model/data/dsl_data.txt + 6 aperçus. */
    @Test
    void writeDslCorpusAndSamples() throws Exception {
        String[] kinds = {"Épée", "Lame", "Dague",          // épée
                "Pioche", "Foreuse", "Piochon",             // pioche
                "Hache", "Hachette", "Cognée",              // hache
                "Casque", "Heaume", "Coiffe",               // casque
                "Plastron", "Cuirasse", "Armure"};          // plastron — 3 par type = corpus ÉQUILIBRÉ
        String[] adj = {"", "Grande ", "Petite ", "Royale ", "Runique ", "Sacrée ", "Divine ", "Ancienne ",
                "Légendaire ", "Mythique ", "Brute ", "Céleste ", "Impériale ", "Maudite ", "Brisée ", "Éternelle "};
        String[] themes = {"Feu", "Glace", "Foudre", "Vent", "Ombre", "Poison", "Sang", "Or", "Nature",
                "Océan", "Soleil", "Lune", "Ender", "Nether", "Rose", "Arcane", "Cuivre", "Cristal", "Acier",
                "Sable", "Rubis", "Obsidienne", "Corail", "Menthe", "Lavande", "Citron", "Dragon", "Phénix",
                "Vampire", "Jade", "Saphir", "Turquoise", "Chaos", "Spectre", "Aurore", "Givre"};

        File dir = new File("tools/forge-model/data");
        dir.mkdirs();
        File out = new File(dir, "dsl_data.txt");
        long lines = 0;
        try (java.io.BufferedWriter w = new java.io.BufferedWriter(new java.io.FileWriter(out, java.nio.charset.StandardCharsets.UTF_8))) {
            for (String k : kinds)
                for (String a : adj)
                    for (String th : themes)
                        for (int s = 0; s < 3; s++) {                 // 3 variantes par nom
                            String liaison = th.matches("(?i)(feu|vent|sang|nether|chaos|givre)") ? " du " : " de ";
                            String name = (a + k + liaison + th).trim();
                            String prog = TextureComposer.compose(name, (name + "#" + s).hashCode());
                            w.write(name + " => " + prog + "\n");
                            lines++;
                        }
        }
        assertTrue(lines > 10_000, "corpus conséquent : " + lines + " lignes");

        // aperçus ×10 pour vérifier visuellement la qualité du corpus
        File sdir = new File("tools/forge-model/samples");
        sdir.mkdirs();
        String[] demo = {"Épée du Feu", "Grande Épée du Dragon", "Dague de l'Ombre",
                "Pioche Royale de Glace", "Hache Runique de Foudre", "Plastron Sacré du Soleil"};
        for (String name : demo) {
            BufferedImage img = TextureSynth.renderProgram(TextureComposer.compose(name), VENT, name.hashCode());
            String fn = name.toLowerCase().replaceAll("[^a-z0-9]+", "_");
            int sc = 10;
            BufferedImage big = new BufferedImage(160, 160, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 160; y++) for (int x = 0; x < 160; x++) big.setRGB(x, y, img.getRGB(x / sc, y / sc));
            javax.imageio.ImageIO.write(big, "png", new File(sdir, "compose_" + fn + "_x10.png"));
        }
    }
}
