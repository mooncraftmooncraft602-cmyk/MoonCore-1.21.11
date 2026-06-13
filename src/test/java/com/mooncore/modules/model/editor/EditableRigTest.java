package com.mooncore.modules.model.editor;

import com.mooncore.modules.model.RigModel;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Édition de géométrie + round-trip {@link EditableRig} ↔ {@link RigModel} (Étape D1).
 * Géométrie pure (block = null) → aucun serveur Bukkit requis.
 */
class EditableRigTest {

    @Test
    void addRemoveAndUniqueNames() {
        EditableRig rig = new EditableRig("test");
        EditableBone a = rig.addCube("cube", new Vector3f(0, 0, 0), new Vector3f(1, 1, 1), null);
        EditableBone b = rig.addCube("cube", new Vector3f(1, 0, 0), new Vector3f(2, 1, 1), null);
        assertEquals("cube", a.name);
        assertEquals("cube_1", b.name);          // nom rendu unique
        assertEquals(2, rig.bones.size());
        assertTrue(rig.removeCube("cube_1"));
        assertEquals(1, rig.bones.size());
        assertNull(rig.bone("cube_1"));
    }

    @Test
    void normalizesFromTo() {
        EditableRig rig = new EditableRig("t");
        EditableBone b = rig.addCube("c", new Vector3f(2, 3, 4), new Vector3f(0, 1, 1), null);
        assertEquals(new Vector3f(0, 1, 1), b.from);
        assertEquals(new Vector3f(2, 3, 4), b.to);
        assertEquals(new Vector3f(2, 2, 3), b.size());
    }

    @Test
    void editPivotUvAndRoundTrip() {
        EditableRig rig = new EditableRig("golem2");
        rig.addCube("head", new Vector3f(0, 0, 0), new Vector3f(1, 1, 1), null);
        assertTrue(rig.setPivot("head", new Vector3f(0.5f, 0f, 0.5f)));
        assertTrue(rig.setUv("head", CubeFace.UP, 0, 0, 16, 16));
        assertTrue(rig.setItemModelKey("head", "golem_head"));

        RigModel model = rig.toRigModel();
        assertEquals("golem2", model.id);
        assertNotNull(model.bone("head"));
        assertEquals("golem_head", model.bone("head").itemModelKey);
        assertTrue(model.bone("head").hasItemModel());

        EditableRig back = EditableRig.fromRigModel(model);
        assertEquals(1, back.bones.size());
        assertEquals("golem_head", back.bone("head").itemModelKey);
        assertEquals(new Vector3f(0.5f, 0f, 0.5f), back.bone("head").pivot);

        assertFalse(rig.setPivot("inexistant", new Vector3f()));
    }
}
