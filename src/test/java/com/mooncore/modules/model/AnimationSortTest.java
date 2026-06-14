package com.mooncore.modules.model;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Animation} trie les keyframes à la construction (le moteur d'échantillonnage suppose un
 * ordre temporel croissant) et expose {@code bones()}/{@code keyframes()}. Pur, sans serveur.
 */
class AnimationSortTest {

    @Test
    void keyframesSortedOnConstruction() {
        // Keyframes fournies dans le DÉSORDRE.
        Animation.Keyframe k2 = new Animation.Keyframe(2.0, new Vector3f(), new Vector3f(), new Vector3f(1, 1, 1));
        Animation.Keyframe k0 = new Animation.Keyframe(0.0, new Vector3f(), new Vector3f(), new Vector3f(1, 1, 1));
        Animation.Keyframe k1 = new Animation.Keyframe(1.0, new Vector3f(), new Vector3f(), new Vector3f(1, 1, 1));
        Animation a = new Animation("a", 2.0, false, Map.of("bone", List.of(k2, k0, k1)));

        List<Animation.Keyframe> kfs = a.keyframes("bone");
        assertEquals(0.0, kfs.get(0).time(), 1e-9);
        assertEquals(1.0, kfs.get(1).time(), 1e-9);
        assertEquals(2.0, kfs.get(2).time(), 1e-9);
    }

    @Test
    void bonesAndUnknownKeyframes() {
        Animation.Keyframe k = new Animation.Keyframe(0.0, new Vector3f(), new Vector3f(), new Vector3f(1, 1, 1));
        Animation a = new Animation("a", 1.0, true, Map.of("leg", List.of(k)));
        assertTrue(a.bones().contains("leg"));
        assertTrue(a.keyframes("inconnu").isEmpty());   // os non animé → liste vide
        assertEquals(1, a.keyframes("leg").size());
    }
}
