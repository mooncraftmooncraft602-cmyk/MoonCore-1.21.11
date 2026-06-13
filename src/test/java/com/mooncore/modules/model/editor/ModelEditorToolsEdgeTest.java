package com.mooncore.modules.model.editor;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cas dégénérés des outils d'édition (D3) : resize qui inverse un coin doit re-normaliser ;
 * scaleAroundPivot avec facteur ≤ 0 doit être un no-op (pas de boîte nulle/inversée). Pur.
 */
class ModelEditorToolsEdgeTest {

    private EditableBone cube() {
        EditableRig r = new EditableRig("t");
        EditableBone b = r.addCube("c", new Vector3f(0, 0, 0), new Vector3f(2, 2, 2), null);
        b.pivot.set(1, 1, 1);
        return b;
    }

    @Test
    void resizeBelowFromRenormalizes() {
        EditableBone b = cube();
        // Réduit 'to' de 3 sur Y → to.y passe sous from.y ; normalize doit réordonner.
        ModelEditorTools.resize(b, 0, -3, 0);
        assertTrue(b.from.y <= b.to.y, "from <= to après resize");
        assertEquals(-1f, b.from.y, 1e-6); // après réordonnancement : min(0, 2-3) = -1
        assertEquals(0f, b.to.y, 1e-6);    // max(0, -1) = 0
    }

    @Test
    void scaleNonPositiveIsNoOp() {
        EditableBone b = cube();
        Vector3f from0 = new Vector3f(b.from);
        Vector3f to0 = new Vector3f(b.to);
        ModelEditorTools.scaleAroundPivot(b, 0f);
        ModelEditorTools.scaleAroundPivot(b, -2f);
        assertEquals(from0, b.from);
        assertEquals(to0, b.to);
    }

    @Test
    void scalePositiveAroundPivotShrinks() {
        EditableBone b = cube();          // from (0,0,0) to (2,2,2), pivot (1,1,1)
        ModelEditorTools.scaleAroundPivot(b, 0.5f);
        assertEquals(new Vector3f(0.5f, 0.5f, 0.5f), b.from);
        assertEquals(new Vector3f(1.5f, 1.5f, 1.5f), b.to);
    }
}
