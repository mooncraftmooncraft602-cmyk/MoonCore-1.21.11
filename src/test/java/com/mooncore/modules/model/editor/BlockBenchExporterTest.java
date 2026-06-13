package com.mooncore.modules.model.editor;

import com.mooncore.modules.model.BlockBenchImporter;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Export .bbmodel (D6) : round-trip via {@link BlockBenchImporter} (géométrie préservée) +
 * agrégation {@link RigToItemModel}. Pur, sans serveur Bukkit.
 */
class BlockBenchExporterTest {

    private EditableRig sampleRig() {
        EditableRig rig = new EditableRig("golem");
        EditableBone body = rig.addCube("body", new Vector3f(0, 0, 0), new Vector3f(1, 2, 1), null);
        body.pivot.set(0.5f, 0f, 0.5f);
        EditableBone head = rig.addCube("head", new Vector3f(0, 2, 0), new Vector3f(1, 3, 1), null);
        head.parent = "body";
        head.pivot.set(0.5f, 2f, 0.5f);
        return rig;
    }

    @Test
    void bbmodelRoundTripPreservesGeometry() {
        String json = BlockBenchExporter.toBbmodel(sampleRig());
        BlockBenchImporter.RawRig raw = BlockBenchImporter.parse(json, "golem");

        BlockBenchImporter.RawBone body = find(raw, "body");
        BlockBenchImporter.RawBone head = find(raw, "head");
        assertNotNull(body);
        assertNotNull(head);

        // from/to en blocs préservés après round-trip (px ×16 → ÷16).
        assertEquals(0f, body.from()[1], 1e-4);
        assertEquals(2f, body.to()[1], 1e-4);
        assertEquals(2f, head.from()[1], 1e-4);
        assertEquals(3f, head.to()[1], 1e-4);
        // pivot préservé
        assertEquals(0.5f, body.pivot()[0], 1e-4);
        assertEquals(2f, head.pivot()[1], 1e-4);
        // hiérarchie : head enfant de body
        assertEquals("body", head.parent());
    }

    @Test
    void rigToItemModelAggregatesElements() {
        String model = RigToItemModel.toItemModel(sampleRig(), "golem_tex");
        assertTrue(model.contains("\"elements\""), model);
        assertTrue(model.contains("mooncore:item/golem_tex"), model);
        assertTrue(model.contains("\"name\": \"body\""), model);
        assertTrue(model.contains("\"name\": \"head\""), model);
        assertTrue(model.contains("\"to\": [16, 48, 16]"), model); // head 0,2,0→1,3,1 en px
    }

    private static BlockBenchImporter.RawBone find(BlockBenchImporter.RawRig raw, String name) {
        for (BlockBenchImporter.RawBone b : raw.bones()) if (b.name().equals(name)) return b;
        return null;
    }
}
