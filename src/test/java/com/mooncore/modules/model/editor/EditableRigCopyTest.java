package com.mooncore.modules.model.editor;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Indépendance des copies profondes {@link EditableRig#copy()} / {@link EditableBone#copy()}
 * (géométrie + UV par face), socle de l'undo/redo et des snapshots. Pur, sans serveur.
 */
class EditableRigCopyTest {

    @Test
    void deepCopyIsIndependent() {
        EditableRig rig = new EditableRig("r");
        EditableBone b = rig.addCube("c", new Vector3f(0, 0, 0), new Vector3f(1, 1, 1), null);
        b.pivot.set(0.5f, 0f, 0.5f);
        b.itemModelKey = "tex";
        b.setUv(CubeFace.UP, 0, 0, 8, 8);

        EditableRig copy = rig.copy();
        EditableBone cb = copy.bone("c");
        assertNotSame(b, cb);
        assertNotSame(b.from, cb.from);
        assertNotSame(b.uv.get(CubeFace.UP), cb.uv.get(CubeFace.UP));

        // Muter la copie ne doit pas toucher l'original.
        cb.from.set(9, 9, 9);
        cb.pivot.set(1, 1, 1);
        cb.itemModelKey = "autre";
        cb.uv.get(CubeFace.UP)[2] = 16;

        assertEquals(new Vector3f(0, 0, 0), b.from);
        assertEquals(new Vector3f(0.5f, 0f, 0.5f), b.pivot);
        assertEquals("tex", b.itemModelKey);
        assertEquals(8f, b.uv.get(CubeFace.UP)[2], 1e-6);
    }

    @Test
    void copyFromReplacesContentsInPlace() {
        EditableRig a = new EditableRig("a");
        a.addCube("x", new Vector3f(0, 0, 0), new Vector3f(1, 1, 1), null);
        EditableRig b = new EditableRig("b");
        b.addCube("y", new Vector3f(0, 0, 0), new Vector3f(2, 2, 2), null);

        a.copyFrom(b);
        assertEquals("b", a.id);
        assertEquals(1, a.bones.size());
        assertEquals("y", a.bones.get(0).name);
        // Indépendance : muter b après copyFrom n'altère pas a.
        b.bone("y").from.set(5, 5, 5);
        assertEquals(new Vector3f(0, 0, 0), a.bone("y").from);
    }
}
