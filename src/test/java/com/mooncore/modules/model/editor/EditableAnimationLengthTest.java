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
}
