package com.mooncore.modules.model.editor;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Auto-extension de {@code length} dans {@link EditableAnimation} : la durée doit grandir pour
 * contenir les keyframes ajoutées/déplacées (sinon le bouclage du moteur tronquerait l'animation).
 * Pur, sans serveur.
 */
class EditableAnimationLengthTest {

    @Test
    void putKeyBeyondLengthExtendsIt() {
        EditableAnimation a = new EditableAnimation("walk", 1.0, true);
        a.putKey("leg", 2.5, new Vector3f(), new Vector3f(), new Vector3f(1, 1, 1));
        assertTrue(a.length >= 2.5, "length étendue à >= 2.5 ; était " + a.length);
    }

    @Test
    void moveKeyBeyondLengthExtendsIt() {
        EditableAnimation a = new EditableAnimation("a", 1.0, false);
        a.putKey("b", 0.5, new Vector3f(), new Vector3f(), new Vector3f(1, 1, 1));
        a.moveKey("b", 0, 3.0);
        assertTrue(a.length >= 3.0, "length étendue à >= 3.0 ; était " + a.length);
        assertEquals(3.0, a.track("b").get(0).time(), 1e-9);
    }

    @Test
    void shortKeyDoesNotShrinkLength() {
        EditableAnimation a = new EditableAnimation("a", 5.0, true);
        a.putKey("b", 1.0, new Vector3f(), new Vector3f(), new Vector3f(1, 1, 1));
        assertEquals(5.0, a.length, 1e-9); // une keyframe courte ne réduit pas la durée
    }

    @Test
    void keyframesStaySortedByTimeRegardlessOfInsertionOrder() {
        // Insertion dans le désordre : la piste doit rester triée par temps (sinon l'interpolation à la lecture casse).
        EditableAnimation a = new EditableAnimation("walk", 1.0, true);
        double[] times = {3.0, 0.5, 2.0, 1.0, 2.5};
        for (double t : times) a.putKey("leg", t, new Vector3f(), new Vector3f(), new Vector3f(1, 1, 1));
        var kfs = a.track("leg");
        assertEquals(5, kfs.size());
        for (int i = 1; i < kfs.size(); i++) {
            assertTrue(kfs.get(i - 1).time() <= kfs.get(i).time(),
                    "keyframes non triées à l'index " + i + " : " + kfs.get(i - 1).time() + " > " + kfs.get(i).time());
        }
        assertEquals(0.5, kfs.get(0).time(), 1e-9);
        assertEquals(3.0, kfs.get(4).time(), 1e-9);
    }

    @Test
    void putKeyAtSameTimeReplacesNotDuplicates() {
        EditableAnimation a = new EditableAnimation("x", 5.0, false);
        a.putKey("b", 2.0, new Vector3f(1, 0, 0), new Vector3f(), new Vector3f(1, 1, 1));
        a.putKey("b", 2.0, new Vector3f(9, 0, 0), new Vector3f(), new Vector3f(1, 1, 1)); // même temps → écrase
        assertEquals(1, a.track("b").size());
    }
}
