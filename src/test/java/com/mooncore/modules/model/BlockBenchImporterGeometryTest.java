package com.mooncore.modules.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Hiérarchie d'import géométrique BlockBench : cubes en vrac → os « root » synthétique, groupes
 * imbriqués (origin → pivot, union des cubes enfants ÷16). Pur, sans serveur.
 */
class BlockBenchImporterGeometryTest {

    private static BlockBenchImporter.RawBone bone(BlockBenchImporter.RawRig raw, String name) {
        for (BlockBenchImporter.RawBone b : raw.bones()) if (b.name().equals(name)) return b;
        return null;
    }

    @Test
    void looseCubesBecomeSyntheticRoot() {
        String json = """
                {
                  "elements": [ { "uuid": "c1", "from": [0,0,0], "to": [16,32,16] } ],
                  "outliner": [ "c1" ]
                }
                """;
        BlockBenchImporter.RawRig raw = BlockBenchImporter.parse(json, "rig");
        BlockBenchImporter.RawBone root = bone(raw, "root");
        assertNotNull(root, "os synthétique 'root' pour les cubes en vrac");
        assertEquals(0f, root.from()[1], 1e-4);
        assertEquals(2f, root.to()[1], 1e-4);   // 32 px ÷ 16 = 2 blocs
    }

    @Test
    void nestedGroupsKeepParentAndPivot() {
        String json = """
                {
                  "elements": [
                    { "uuid": "body", "from": [0,0,0], "to": [16,32,16] },
                    { "uuid": "head", "from": [0,32,0], "to": [16,48,16] }
                  ],
                  "outliner": [
                    {
                      "name": "body", "origin": [8,0,8],
                      "children": [ "body",
                        { "name": "head", "origin": [8,32,8], "children": [ "head" ] }
                      ]
                    }
                  ]
                }
                """;
        BlockBenchImporter.RawRig raw = BlockBenchImporter.parse(json, "rig");
        BlockBenchImporter.RawBone body = bone(raw, "body");
        BlockBenchImporter.RawBone head = bone(raw, "head");
        assertNotNull(body);
        assertNotNull(head);
        assertNull(body.parent());           // racine
        assertEquals("body", head.parent());  // head enfant de body
        // pivot = origin ÷ 16
        assertEquals(0.5f, body.pivot()[0], 1e-4);   // 8 ÷ 16
        assertEquals(2f, head.pivot()[1], 1e-4);      // 32 ÷ 16
        // boîte head = cube head ÷ 16
        assertEquals(2f, head.from()[1], 1e-4);
        assertEquals(3f, head.to()[1], 1e-4);
    }
}
