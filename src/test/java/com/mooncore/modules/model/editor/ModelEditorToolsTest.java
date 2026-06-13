package com.mooncore.modules.model.editor;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Outils d'édition (D3) + historique undo/redo — géométrie pure, sans serveur Bukkit.
 */
class ModelEditorToolsTest {

    private EditableRig rig() {
        EditableRig r = new EditableRig("t");
        r.addCube("c", new Vector3f(0, 0, 0), new Vector3f(2, 2, 2), null);
        return r;
    }

    @Test
    void translateMovesBoxAndPivot() {
        EditableRig r = rig();
        EditableBone b = r.bone("c");
        b.pivot.set(1, 0, 1);
        ModelEditorTools.translate(b, 5, 0, -3);
        assertEquals(new Vector3f(5, 0, -3), b.from);
        assertEquals(new Vector3f(7, 2, -1), b.to);
        assertEquals(new Vector3f(6, 0, -2), b.pivot);
    }

    @Test
    void scaleAroundPivotDoublesSize() {
        EditableRig r = rig();
        EditableBone b = r.bone("c");
        b.pivot.set(1, 1, 1);                 // centre
        ModelEditorTools.scaleAroundPivot(b, 2f);
        assertEquals(new Vector3f(-1, -1, -1), b.from);
        assertEquals(new Vector3f(3, 3, 3), b.to);
    }

    @Test
    void duplicateMakesUniqueOffsetCopy() {
        EditableRig r = rig();
        EditableBone copy = ModelEditorTools.duplicate(r, "c", 1, 0, 0);
        assertNotNull(copy);
        assertEquals("c_copy", copy.name);
        assertEquals(2, r.bones.size());
        assertEquals(new Vector3f(1, 0, 0), copy.from);
    }

    @Test
    void historyUndoRedoRestoresState() {
        EditableRig r = rig();
        RigHistory h = new RigHistory();

        h.push(r);
        ModelEditorTools.translate(r.bone("c"), 10, 0, 0);
        assertEquals(10f, r.bone("c").from.x, 1e-6);

        assertTrue(h.canUndo());
        assertTrue(h.undo(r));
        assertEquals(0f, r.bone("c").from.x, 1e-6);   // état restauré

        assertTrue(h.canRedo());
        assertTrue(h.redo(r));
        assertEquals(10f, r.bone("c").from.x, 1e-6);  // ré-appliqué

        assertFalse(h.canRedo());
    }
}
