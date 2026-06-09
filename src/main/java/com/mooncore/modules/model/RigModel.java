package com.mooncore.modules.model;

import org.bukkit.Material;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modèle riggé : une hiérarchie d'{@link RigBone} + des {@link Animation}. Données pures.
 * Les os DOIVENT être listés parents avant enfants (résolution de chaîne en une passe).
 */
public final class RigModel {

    public final String id;
    public final List<RigBone> bones;
    public final Map<String, Animation> animations;

    public RigModel(String id, List<RigBone> bones, Map<String, Animation> animations) {
        this.id = id;
        this.bones = bones;
        this.animations = animations;
    }

    public RigBone bone(String name) {
        for (RigBone b : bones) if (b.name.equals(name)) return b;
        return null;
    }

    public Animation animation(String name) { return animations.get(name); }

    // ----------------------------------------------------------------
    //  Modèle de démonstration : un golem articulé (corps/tête/bras/jambes)
    //  pour prouver le pipeline rig + animation en jeu, sans resource pack.
    // ----------------------------------------------------------------

    /** Convertit des unités BlockBench (px, 16 = 1 bloc) en blocs. */
    private static Vector3f bb(float x, float y, float z) { return new Vector3f(x / 16f, y / 16f, z / 16f); }

    public static RigModel golem() {
        List<RigBone> bones = new ArrayList<>();
        // corps = racine
        bones.add(new RigBone("body", null, bb(0, 10, 0), bb(-4, 10, -2), bb(4, 22, 2),
                Material.IRON_BLOCK.createBlockData()));
        bones.add(new RigBone("head", "body", bb(0, 22, 0), bb(-4, 22, -4), bb(4, 30, 4),
                Material.GOLD_BLOCK.createBlockData()));
        bones.add(new RigBone("arm_r", "body", bb(4, 21, 0), bb(4, 11, -2), bb(7, 22, 2),
                Material.COPPER_BLOCK.createBlockData()));
        bones.add(new RigBone("arm_l", "body", bb(-4, 21, 0), bb(-7, 11, -2), bb(-4, 22, 2),
                Material.COPPER_BLOCK.createBlockData()));
        bones.add(new RigBone("leg_r", "body", bb(1.5f, 10, 0), bb(0, 0, -2), bb(3, 10, 2),
                Material.IRON_BLOCK.createBlockData()));
        bones.add(new RigBone("leg_l", "body", bb(-1.5f, 10, 0), bb(-3, 0, -2), bb(0, 10, 2),
                Material.IRON_BLOCK.createBlockData()));

        Map<String, Animation> anims = new LinkedHashMap<>();
        anims.put("idle", idle());
        anims.put("walk", walk());
        anims.put("attack", attack());
        return new RigModel("golem", bones, anims);
    }

    /** Attaque : les bras se lèvent vers l'avant puis retombent (one-shot, 0,6 s). */
    private static Animation attack() {
        Map<String, List<Animation.Keyframe>> tracks = new LinkedHashMap<>();
        tracks.put("arm_r", List.of(rk(0.0, 0f), rk(0.3, -100f), rk(0.6, 0f)));
        tracks.put("arm_l", List.of(rk(0.0, 0f), rk(0.3, -100f), rk(0.6, 0f)));
        return new Animation("attack", 0.6, false, tracks);
    }

    /** Respiration : le corps monte/descend légèrement (boucle 2 s). */
    private static Animation idle() {
        Map<String, List<Animation.Keyframe>> tracks = new LinkedHashMap<>();
        tracks.put("body", List.of(
                tk(0.0, 0f), tk(1.0, 0.04f), tk(2.0, 0f)));
        return new Animation("idle", 2.0, true, tracks);
    }

    /** Marche : jambes et bras balancent en opposition (boucle 1 s). */
    private static Animation walk() {
        Map<String, List<Animation.Keyframe>> tracks = new LinkedHashMap<>();
        tracks.put("leg_r", swingX(1.0, 30f));
        tracks.put("leg_l", swingX(1.0, -30f));
        tracks.put("arm_r", swingX(1.0, -35f));
        tracks.put("arm_l", swingX(1.0, 35f));
        return new Animation("walk", 1.0, true, tracks);
    }

    /** Keyframe translation Y (blocs) seule. */
    private static Animation.Keyframe tk(double t, float y) {
        return new Animation.Keyframe(t, new Vector3f(0, y, 0), new Vector3f(), new Vector3f(1, 1, 1));
    }

    /** Balancement autour de l'axe X : +amp → -amp → +amp sur la durée (boucle). */
    private static List<Animation.Keyframe> swingX(double length, float ampDeg) {
        return List.of(
                rk(0.0, ampDeg), rk(length / 2.0, -ampDeg), rk(length, ampDeg));
    }

    /** Keyframe rotation X (degrés) seule. */
    private static Animation.Keyframe rk(double t, float xDeg) {
        return new Animation.Keyframe(t, new Vector3f(), new Vector3f(xDeg, 0, 0), new Vector3f(1, 1, 1));
    }
}
