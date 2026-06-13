package com.mooncore.modules.model.editor;

import com.mooncore.modules.model.Animation;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Animation <b>mutable</b> de l'éditeur 3D in-game (Étape D5) : édition de keyframes par os et par
 * canal (translation / rotation / échelle). Round-trip avec {@link Animation} immuable (consommée par
 * {@code RigInstance} et poussée au mod compagnon via le protocol v2).
 */
public final class EditableAnimation {

    /** Canal éditable d'une keyframe. */
    public enum Channel { TRANSLATION, ROTATION, SCALE }

    private static final double EPS = 1e-4;

    public String name;
    public double length;
    public boolean loop;
    public final Map<String, List<Animation.Keyframe>> tracks = new LinkedHashMap<>();

    public EditableAnimation(String name, double length, boolean loop) {
        this.name = name;
        this.length = Math.max(EPS, length);
        this.loop = loop;
    }

    public List<Animation.Keyframe> track(String bone) {
        return tracks.computeIfAbsent(bone, b -> new ArrayList<>());
    }

    /** Ajoute/remplace la keyframe d'un os à {@code time} (toutes composantes fournies). */
    public void putKey(String bone, double time, Vector3f translation, Vector3f rotationDeg, Vector3f scale) {
        List<Animation.Keyframe> kfs = track(bone);
        kfs.removeIf(k -> Math.abs(k.time() - time) < EPS);
        kfs.add(new Animation.Keyframe(time, new Vector3f(translation), new Vector3f(rotationDeg), new Vector3f(scale)));
        sort(kfs);
        length = Math.max(length, time);
    }

    /**
     * Définit une seule composante (canal) d'une keyframe à {@code time}, en créant la keyframe avec
     * des valeurs de repos pour les autres canaux si elle n'existe pas encore.
     */
    public void setChannel(String bone, double time, Channel channel, Vector3f value) {
        List<Animation.Keyframe> kfs = track(bone);
        Animation.Keyframe existing = null;
        for (Animation.Keyframe k : kfs) if (Math.abs(k.time() - time) < EPS) { existing = k; break; }

        Vector3f t = existing != null ? new Vector3f(existing.translation()) : new Vector3f();
        Vector3f r = existing != null ? new Vector3f(existing.rotationDeg()) : new Vector3f();
        Vector3f s = existing != null ? new Vector3f(existing.scale()) : new Vector3f(1f, 1f, 1f);
        switch (channel) {
            case TRANSLATION -> t.set(value);
            case ROTATION -> r.set(value);
            case SCALE -> s.set(value);
        }
        putKey(bone, time, t, r, s);
    }

    public boolean removeKeyAt(String bone, double time) {
        List<Animation.Keyframe> kfs = tracks.get(bone);
        if (kfs == null) return false;
        boolean removed = kfs.removeIf(k -> Math.abs(k.time() - time) < EPS);
        if (kfs.isEmpty()) tracks.remove(bone);
        return removed;
    }

    public boolean removeKeyIndex(String bone, int index) {
        List<Animation.Keyframe> kfs = tracks.get(bone);
        if (kfs == null || index < 0 || index >= kfs.size()) return false;
        kfs.remove(index);
        if (kfs.isEmpty()) tracks.remove(bone);
        return true;
    }

    /** Déplace la keyframe {@code index} de l'os vers {@code newTime} (ré-trie). */
    public boolean moveKey(String bone, int index, double newTime) {
        List<Animation.Keyframe> kfs = tracks.get(bone);
        if (kfs == null || index < 0 || index >= kfs.size()) return false;
        Animation.Keyframe k = kfs.remove(index);
        kfs.removeIf(o -> Math.abs(o.time() - newTime) < EPS); // écrase une éventuelle keyframe cible
        kfs.add(new Animation.Keyframe(newTime, k.translation(), k.rotationDeg(), k.scale()));
        sort(kfs);
        length = Math.max(length, newTime);
        return true;
    }

    public Animation toAnimation() {
        return new Animation(name, length, loop, tracks);
    }

    public static EditableAnimation from(Animation a) {
        EditableAnimation e = new EditableAnimation(a.name(), a.length(), a.loop());
        for (String bone : a.bones()) e.tracks.put(bone, new ArrayList<>(a.keyframes(bone)));
        return e;
    }

    private static void sort(List<Animation.Keyframe> kfs) {
        kfs.sort(Comparator.comparingDouble(Animation.Keyframe::time));
    }
}
