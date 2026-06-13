package com.mooncore.modules.model.editor;

import com.mooncore.modules.model.Animation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Édition de keyframes (D5) + round-trip {@link EditableAnimation} ↔ {@link Animation}. Pur, sans serveur.
 */
class EditableAnimationTest {

    @Test
    void putAndSampleInterpolates() {
        EditableAnimation a = new EditableAnimation("walk", 1.0, true);
        a.putKey("leg", 0.0, new Vector3f(), new Vector3f(0, 0, 0), new Vector3f(1, 1, 1));
        a.putKey("leg", 1.0, new Vector3f(), new Vector3f(0, 0, 90), new Vector3f(1, 1, 1));

        Animation anim = a.toAnimation();
        Animation.Pose mid = anim.sample("leg", 0.5);
        assertEquals(45f, mid.rotationDeg().z, 1e-3);   // interpolation linéaire à mi-chemin
    }

    @Test
    void setChannelKeepsOtherChannels() {
        EditableAnimation a = new EditableAnimation("a", 1.0, false);
        a.setChannel("b", 0.5, EditableAnimation.Channel.ROTATION, new Vector3f(0, 30, 0));
        a.setChannel("b", 0.5, EditableAnimation.Channel.TRANSLATION, new Vector3f(2, 0, 0));

        assertEquals(1, a.track("b").size());           // même temps → une seule keyframe
        Animation.Keyframe k = a.track("b").get(0);
        assertEquals(30f, k.rotationDeg().y, 1e-6);     // rotation conservée
        assertEquals(2f, k.translation().x, 1e-6);      // translation ajoutée
        assertEquals(1f, k.scale().x, 1e-6);            // scale par défaut (repos)
    }

    @Test
    void moveAndRemoveKeys() {
        EditableAnimation a = new EditableAnimation("a", 2.0, false);
        a.putKey("b", 0.0, new Vector3f(), new Vector3f(), new Vector3f(1, 1, 1));
        a.putKey("b", 1.0, new Vector3f(), new Vector3f(), new Vector3f(1, 1, 1));
        assertTrue(a.moveKey("b", 0, 1.5));             // déplace la 1re vers 1.5
        assertEquals(1.0, a.track("b").get(0).time(), 1e-6); // tri : 1.0 puis 1.5
        assertEquals(1.5, a.track("b").get(1).time(), 1e-6);
        assertTrue(a.removeKeyAt("b", 1.0));
        assertEquals(1, a.track("b").size());
        assertFalse(a.removeKeyAt("b", 9.9));
    }

    @Test
    void roundTrip() {
        EditableAnimation a = new EditableAnimation("idle", 1.0, true);
        a.putKey("head", 0.25, new Vector3f(1, 0, 0), new Vector3f(0, 10, 0), new Vector3f(1, 1, 1));
        EditableAnimation back = EditableAnimation.from(a.toAnimation());
        assertEquals(1, back.track("head").size());
        assertEquals(0.25, back.track("head").get(0).time(), 1e-6);
        assertTrue(back.loop);
    }
}
