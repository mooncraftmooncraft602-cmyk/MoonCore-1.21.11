package com.mooncore.modules.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valide le parsing géométrique BlockBench (sans Bukkit) : hiérarchie outliner → os,
 * union des cubes enfants, conversion px → blocs (÷16).
 */
class BlockBenchImporterTest {

    private static final String SAMPLE = """
            {
              "elements": [
                {"uuid":"e1","name":"c1","from":[0,0,0],"to":[16,16,16]},
                {"uuid":"e2","name":"c2","from":[4,16,4],"to":[12,24,12]}
              ],
              "outliner": [
                {"name":"Body","uuid":"g1","origin":[8,0,8],"children":[
                  "e1",
                  {"name":"Head","uuid":"g2","origin":[8,16,8],"children":["e2"]}
                ]}
              ]
            }
            """;

    @Test
    void parsesHierarchyBoxesAndUnits() {
        BlockBenchImporter.RawRig rig = BlockBenchImporter.parse(SAMPLE, "test");
        assertEquals(2, rig.bones().size(), "deux os (body + head)");

        BlockBenchImporter.RawBone body = rig.bones().get(0);
        assertEquals("body", body.name(), "nom sanitizé en minuscules");
        assertNull(body.parent(), "body = racine");
        assertTrue(body.hasBox());
        assertArrayEquals(new float[]{0, 0, 0}, body.from(), 1e-6f);
        assertArrayEquals(new float[]{1, 1, 1}, body.to(), 1e-6f);   // 16px → 1 bloc
        assertArrayEquals(new float[]{0.5f, 0, 0.5f}, body.pivot(), 1e-6f); // origin 8px → 0.5

        BlockBenchImporter.RawBone head = rig.bones().get(1);
        assertEquals("head", head.name());
        assertEquals("body", head.parent(), "head enfant de body");
        assertArrayEquals(new float[]{0.25f, 1f, 0.25f}, head.from(), 1e-6f);
        assertArrayEquals(new float[]{0.75f, 1.5f, 0.75f}, head.to(), 1e-6f);
    }

    @Test
    void parsesAnimationsAndResamples() {
        String json = """
                {
                  "elements":[{"uuid":"e1","from":[0,0,0],"to":[16,16,16]}],
                  "outliner":[{"name":"Leg","uuid":"g1","origin":[8,8,8],"children":["e1"]}],
                  "animations":[
                    {"name":"Walk","loop":"loop","length":0.5,
                     "animators":{"g1":{"name":"Leg","keyframes":[
                        {"channel":"rotation","time":0,"data_points":[{"x":"0","y":"0","z":"0"}]},
                        {"channel":"rotation","time":0.5,"data_points":[{"x":"45","y":"0","z":"0"}]}
                     ]}}}
                  ]
                }
                """;
        BlockBenchImporter.RawRig rig = BlockBenchImporter.parse(json, "t");
        Animation walk = rig.animations().get("walk");
        assertNotNull(walk, "animation 'walk' (nom minusculé)");
        assertTrue(walk.loop());
        assertEquals(0.5, walk.length(), 1e-6);

        // interpolation linéaire à mi-chemin (0.25 s) → 22.5°, translation 0, échelle 1
        Animation.Pose mid = walk.sample("leg", 0.25);
        assertEquals(22.5f, mid.rotationDeg().x, 1e-3f);
        assertEquals(0f, mid.translation().x, 1e-4f);
        assertEquals(1f, mid.scale().x, 1e-4f);

        // début à 0° ; et comme l'anim boucle, échantillonner à la longueur exacte revient au début
        assertEquals(0f, walk.sample("leg", 0.0).rotationDeg().x, 1e-4f);
        assertEquals(0f, walk.sample("leg", 0.5).rotationDeg().x, 1e-4f);
    }

    @Test
    void jointlessGroupHasNoBox() {
        String json = """
                {
                  "elements": [],
                  "outliner": [ {"name":"empty","uuid":"g","origin":[8,8,8],"children":[]} ]
                }
                """;
        BlockBenchImporter.RawRig rig = BlockBenchImporter.parse(json, "t");
        assertEquals(1, rig.bones().size());
        assertFalse(rig.bones().get(0).hasBox(), "groupe sans cube = articulation pure");
    }
}
