package com.mooncore.modules.customitem.forge;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformité du forward pass Java ({@link GptInference}) vs PyTorch : un mini-modèle déterministe exporté
 * (src/test/resources/forge/tiny-gpt.bin) et ses logits PyTorch attendus (tiny-expected.txt). Si l'implémentation
 * Java (embeddings, attention causale, MLP GELU, LayerNorm, weight tying, lecture little-endian) est correcte,
 * les logits coïncident. Vérifie aussi le repli quand le fichier est absent.
 */
class GptInferenceTest {

    private static File resource(String name) throws Exception {
        var url = GptInferenceTest.class.getResource("/forge/" + name);
        return url == null ? null : new File(Paths.get(url.toURI()).toString());
    }

    @Test
    void javaForwardMatchesPytorchOnTinyModel() throws Exception {
        File bin = resource("tiny-gpt.bin");
        assertNotNull(bin, "tiny-gpt.bin doit être présent dans les ressources de test");
        GptInference m = GptInference.load(bin);
        assertNotNull(m);
        assertEquals(20, m.vocabSize());
        assertEquals(8, m.blockSize());

        String[] lines = Files.readString(resource("tiny-expected.txt").toPath()).split("\n");
        int[] ids = Arrays.stream(lines[0].trim().split(",")).mapToInt(Integer::parseInt).toArray();
        String[] vals = lines[1].trim().split("\\s+");
        float[] expected = new float[vals.length];
        for (int i = 0; i < vals.length; i++) expected[i] = Float.parseFloat(vals[i]);

        float[] got = m.lastLogits(ids);
        assertEquals(expected.length, got.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], got[i], 2e-3,
                    "logit " + i + " : Java=" + got[i] + " PyTorch=" + expected[i]);
        }
    }

    @Test
    void missingModelLoadsAsNull() {
        assertNull(GptInference.load(new File("n_existe_pas_forge.bin")));
        assertNull(GptInference.load(null));
        assertTrue(new GptPaletteSource(new File("n_existe_pas.bin")).suggestHex("x").isEmpty());
    }
}
