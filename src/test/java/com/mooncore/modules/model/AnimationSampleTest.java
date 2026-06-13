package com.mooncore.modules.model;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cas limites du moteur d'échantillonnage {@link Animation#sample} (utilisé par RigInstance).
 * Données pures (JOML), aucun serveur Bukkit.
 */
class AnimationSampleTest {

    private Animation anim(boolean loop) {
        Animation.Keyframe k0 = new Animation.Keyframe(0.0,
                new Vector3f(0, 0, 0), new Vector3f(0, 0, 0), new Vector3f(1, 1, 1));
        Animation.Keyframe k1 = new Animation.Keyframe(1.0,
                new Vector3f(0, 10, 0), new Vector3f(0, 0, 90), new Vector3f(1, 1, 1));
        return new Animation("a", 1.0, loop, Map.of("bone", List.of(k0, k1)));
    }

    @Test
    void interpolatesLinearlyAtMidpoint() {
        Animation.Pose p = anim(false).sample("bone", 0.5);
        assertEquals(5f, p.translation().y, 1e-4);
        assertEquals(45f, p.rotationDeg().z, 1e-4);
    }

    @Test
    void clampsBeforeFirstAndAfterLastWhenNoLoop() {
        Animation a = anim(false);
        assertEquals(0f, a.sample("bone", -2.0).translation().y, 1e-4);   // avant la 1re → 1re
        assertEquals(10f, a.sample("bone", 5.0).translation().y, 1e-4);    // après la dernière → dernière
    }

    @Test
    void wrapsWhenLooping() {
        Animation a = anim(true);
        // t=1.5 en bouclant sur length=1.0 → 0.5 → mi-chemin.
        assertEquals(5f, a.sample("bone", 1.5).translation().y, 1e-4);
        // t négatif en bouclant reste dans [0,length).
        assertEquals(5f, a.sample("bone", -0.5).translation().y, 1e-4);
    }

    @Test
    void restPoseForUnknownBone() {
        Animation.Pose p = anim(false).sample("inconnu", 0.5);
        assertEquals(0f, p.translation().y, 1e-9);
        assertEquals(1f, p.scale().x, 1e-9);   // échelle de repos = 1
    }
}
