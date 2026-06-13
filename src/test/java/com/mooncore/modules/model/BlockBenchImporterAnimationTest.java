package com.mooncore.modules.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Import des animations BlockBench (parseAnimations / unify / sampleChannel) — logique pure
 * exercée au chargement d'un {@code .bbmodel}. Sans serveur Bukkit.
 */
class BlockBenchImporterAnimationTest {

    private static final String BBMODEL = """
            {
              "name": "rig",
              "elements": [],
              "outliner": [],
              "animations": [
                {
                  "name": "walk",
                  "length": 1.0,
                  "loop": true,
                  "animators": {
                    "u1": {
                      "name": "leg",
                      "keyframes": [
                        { "channel": "rotation", "time": 0.0, "data_points": [ { "x": 0, "y": 0, "z": 0 } ] },
                        { "channel": "rotation", "time": 1.0, "data_points": [ { "x": 0, "y": 0, "z": 90 } ] },
                        { "channel": "position", "time": 0.0, "data_points": [ { "x": 0, "y": 16, "z": 0 } ] }
                      ]
                    }
                  }
                }
              ]
            }
            """;

    @Test
    void importsAnimationAndSamples() {
        BlockBenchImporter.RawRig raw = BlockBenchImporter.parse(BBMODEL, "rig");
        Animation walk = raw.animations().get("walk");
        assertNotNull(walk, "animation 'walk' importée");
        assertTrue(walk.loop());
        assertEquals(1.0, walk.length(), 1e-6);

        // Rotation : 0° à t=0, 90° à t=1 → 45° à mi-chemin.
        assertEquals(45f, walk.sample("leg", 0.5).rotationDeg().z, 1e-3);
        // Position : 16 px à t=0 → 1 bloc (÷16) ; constante (1 seule keyframe position).
        assertEquals(1f, walk.sample("leg", 0.5).translation().y, 1e-3);
    }
}
