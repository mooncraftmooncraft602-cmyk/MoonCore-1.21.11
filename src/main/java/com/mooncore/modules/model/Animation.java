package com.mooncore.modules.model;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Animation par keyframes d'un {@link RigModel} : pour chaque os (bone), une liste de
 * keyframes (temps en secondes → translation/rotation/échelle). À l'exécution, {@link RigInstance}
 * échantillonne la pose de chaque os à l'instant courant par interpolation linéaire — le client
 * lisse ensuite entre deux envois (interpolation des display-entities).
 *
 * <p>Données pures (aucune dépendance Bukkit) → testables et réutilisables (import BlockBench,
 * envoi au mod compagnon…).
 */
public final class Animation {

    /** Une image-clé : à {@code time} (s), l'os a cette translation (blocs), rotation (degrés XYZ) et échelle. */
    public record Keyframe(double time, Vector3f translation, Vector3f rotationDeg, Vector3f scale) {}

    /** Pose instantanée d'un os (résultat d'un échantillonnage). */
    public record Pose(Vector3f translation, Vector3f rotationDeg, Vector3f scale) {
        public static Pose rest() { return new Pose(new Vector3f(), new Vector3f(), new Vector3f(1f, 1f, 1f)); }
    }

    private final String name;
    private final double length;       // durée en secondes
    private final boolean loop;
    private final Map<String, List<Keyframe>> tracks; // bone → keyframes triées

    public Animation(String name, double length, boolean loop, Map<String, List<Keyframe>> tracks) {
        this.name = name;
        this.length = Math.max(0.0001, length);
        this.loop = loop;
        this.tracks = new LinkedHashMap<>();
        tracks.forEach((bone, kfs) -> {
            List<Keyframe> copy = new ArrayList<>(kfs);
            copy.sort(Comparator.comparingDouble(Keyframe::time));
            this.tracks.put(bone, copy);
        });
    }

    public String name() { return name; }
    public double length() { return length; }
    public boolean loop() { return loop; }

    /** Pose interpolée de l'os {@code bone} à l'instant {@code t} (s). {@link Pose#rest()} si non animé. */
    public Pose sample(String bone, double t) {
        List<Keyframe> kfs = tracks.get(bone);
        if (kfs == null || kfs.isEmpty()) return Pose.rest();

        double tt = loop ? (((t % length) + length) % length) : Math.max(0, Math.min(t, length));

        Keyframe first = kfs.get(0);
        if (tt <= first.time()) return pose(first);
        Keyframe last = kfs.get(kfs.size() - 1);
        if (tt >= last.time()) return pose(last);

        for (int i = 0; i < kfs.size() - 1; i++) {
            Keyframe a = kfs.get(i), b = kfs.get(i + 1);
            if (tt >= a.time() && tt <= b.time()) {
                double span = b.time() - a.time();
                float f = span <= 0 ? 0f : (float) ((tt - a.time()) / span);
                return new Pose(
                        lerp(a.translation(), b.translation(), f),
                        lerp(a.rotationDeg(), b.rotationDeg(), f),
                        lerp(a.scale(), b.scale(), f));
            }
        }
        return pose(last);
    }

    private static Pose pose(Keyframe k) {
        return new Pose(new Vector3f(k.translation()), new Vector3f(k.rotationDeg()), new Vector3f(k.scale()));
    }

    private static Vector3f lerp(Vector3f a, Vector3f b, float f) {
        return new Vector3f(a).lerp(b, f);
    }
}
