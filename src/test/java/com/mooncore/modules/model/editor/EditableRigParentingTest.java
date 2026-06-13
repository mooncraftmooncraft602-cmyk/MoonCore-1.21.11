package com.mooncore.modules.model.editor;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Garde anti-cycle de {@link EditableRig#setParent} (Étape D) : empêche les hiérarchies corrompues
 * (auto-parent, parent = descendant). Pur, sans serveur.
 */
class EditableRigParentingTest {

    private EditableRig rig() {
        EditableRig r = new EditableRig("t");
        r.addCube("a", new Vector3f(0, 0, 0), new Vector3f(1, 1, 1), null);
        r.addCube("b", new Vector3f(0, 0, 0), new Vector3f(1, 1, 1), null);
        r.addCube("c", new Vector3f(0, 0, 0), new Vector3f(1, 1, 1), null);
        return r;
    }

    @Test
    void validParentingAllowed() {
        EditableRig r = rig();
        assertTrue(r.setParent("b", "a"));     // b enfant de a
        assertTrue(r.setParent("c", "b"));     // c enfant de b
        assertEquals("a", r.bone("b").parent);
        assertEquals("b", r.bone("c").parent);
        assertTrue(r.setParent("b", null));    // détacher b (racine)
        assertNull(r.bone("b").parent);
    }

    @Test
    void rejectsSelfParent() {
        EditableRig r = rig();
        assertFalse(r.setParent("a", "a"));
        assertNull(r.bone("a").parent);
    }

    @Test
    void rejectsCycle() {
        EditableRig r = rig();
        r.setParent("b", "a");                 // b ← a
        r.setParent("c", "b");                 // c ← b ← a
        // Donner 'c' comme parent de 'a' fermerait la boucle a→...→c→a.
        assertFalse(r.setParent("a", "c"));
        assertNull(r.bone("a").parent);        // a reste racine
    }
}
