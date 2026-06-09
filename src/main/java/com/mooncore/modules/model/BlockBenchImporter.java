package com.mooncore.modules.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Importe un modèle <b>BlockBench</b> ({@code .bbmodel}, JSON) en {@link RigModel}.
 *
 * <p>La géométrie est parsée en deux temps pour rester <b>testable sans Bukkit</b> :
 * <ol>
 *   <li>{@link #parse(String, String)} → {@link RawRig} (purement numérique, aucune API Bukkit) :
 *       les groupes de l'{@code outliner} deviennent des os, leur boîte = union des cubes enfants
 *       directs, le pivot = {@code origin} du groupe ; unités px → blocs (÷16).</li>
 *   <li>{@link #toRigModel(RawRig, Material)} → {@link RigModel} (runtime, crée les {@link BlockData}).</li>
 * </ol>
 *
 * <p>Limites de cette première version : géométrie uniquement (les animations BlockBench ne sont
 * pas encore importées — ajout prévu), un bloc unique par os (le texturage par-os via item-model
 * viendra avec le ModelPackBuilder). Un os sans cube reste une articulation pure (pas de display).
 */
public final class BlockBenchImporter {

    private BlockBenchImporter() {}

    /** Os brut, sans dépendance Bukkit (coordonnées en BLOCS). */
    public record RawBone(String name, String parent, float[] pivot, float[] from, float[] to, boolean hasBox) {}

    /** Rig brut (résultat du parsing) : géométrie + animations (toutes deux Bukkit-free). */
    public record RawRig(String id, List<RawBone> bones, Map<String, Animation> animations) {}

    // ----------------------------------------------------------------
    //  Parsing (Bukkit-free, testable)
    // ----------------------------------------------------------------

    public static RawRig parse(String json, String id) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        // 1) Cubes : uuid → from/to (en blocs).
        Map<String, float[]> from = new LinkedHashMap<>();
        Map<String, float[]> to = new LinkedHashMap<>();
        if (root.has("elements") && root.get("elements").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("elements")) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                if (!o.has("uuid") || !o.has("from") || !o.has("to")) continue;
                from.put(o.get("uuid").getAsString(), arr3(o, "from"));
                to.put(o.get("uuid").getAsString(), arr3(o, "to"));
            }
        }

        // 2) Hiérarchie depuis l'outliner.
        List<RawBone> bones = new ArrayList<>();
        Set<String> used = new HashSet<>();
        List<String> looseTop = new ArrayList<>();
        if (root.has("outliner") && root.get("outliner").isJsonArray()) {
            for (JsonElement node : root.getAsJsonArray("outliner")) {
                if (node.isJsonPrimitive()) looseTop.add(node.getAsString());
                else if (node.isJsonObject()) walkGroup(node.getAsJsonObject(), null, bones, used, from, to);
            }
        }
        // Cubes en vrac à la racine → os synthétique "root".
        if (!looseTop.isEmpty()) {
            float[][] box = union(looseTop, from, to);
            bones.add(0, new RawBone(uniqueName("root", used), null, new float[]{0, 0, 0}, box[0], box[1], true));
        }
        return new RawRig(id, bones, parseAnimations(root));
    }

    // ----------------------------------------------------------------
    //  Animations (Bukkit-free : Animation est pur)
    // ----------------------------------------------------------------

    /**
     * Parse les animations BlockBench → {@link Animation}. Chaque keyframe BlockBench ne porte
     * qu'UN canal (position/rotation/scale) ; on rééchantillonne sur l'union des temps pour
     * produire des keyframes unifiées (translation+rotation+échelle). Position px → blocs (÷16),
     * rotation en degrés, échelle en multiplicateur.
     */
    private static Map<String, Animation> parseAnimations(JsonObject root) {
        Map<String, Animation> out = new LinkedHashMap<>();
        if (!root.has("animations") || !root.get("animations").isJsonArray()) return out;

        for (JsonElement ae : root.getAsJsonArray("animations")) {
            if (!ae.isJsonObject()) continue;
            JsonObject anim = ae.getAsJsonObject();
            String name = anim.has("name") ? anim.get("name").getAsString().toLowerCase(Locale.ROOT) : "anim";
            double length = anim.has("length") ? anim.get("length").getAsDouble() : 1.0;
            boolean loop = anim.has("loop") && isLoop(anim.get("loop"));

            Map<String, List<Animation.Keyframe>> tracks = new LinkedHashMap<>();
            if (anim.has("animators") && anim.get("animators").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : anim.getAsJsonObject("animators").entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    JsonObject animator = e.getValue().getAsJsonObject();
                    if (!animator.has("name")) continue;
                    String bone = sanitize(animator.get("name").getAsString());

                    List<float[]> pos = new ArrayList<>(); // {time,x,y,z} en blocs
                    List<float[]> rot = new ArrayList<>(); // degrés
                    List<float[]> scl = new ArrayList<>(); // multiplicateur
                    if (animator.has("keyframes") && animator.get("keyframes").isJsonArray()) {
                        for (JsonElement kfe : animator.getAsJsonArray("keyframes")) {
                            if (!kfe.isJsonObject()) continue;
                            JsonObject kf = kfe.getAsJsonObject();
                            String channel = kf.has("channel") ? kf.get("channel").getAsString() : "";
                            float t = kf.has("time") ? kf.get("time").getAsFloat() : 0f;
                            float[] d = dataPoint(kf);
                            switch (channel) {
                                case "position" -> pos.add(new float[]{t, d[0] / 16f, d[1] / 16f, d[2] / 16f});
                                case "rotation" -> rot.add(new float[]{t, d[0], d[1], d[2]});
                                case "scale" -> scl.add(new float[]{t, d[0], d[1], d[2]});
                                default -> { /* canal ignoré */ }
                            }
                        }
                    }
                    List<Animation.Keyframe> unified = unify(pos, rot, scl);
                    if (!unified.isEmpty()) tracks.put(bone, unified);
                }
            }
            if (!tracks.isEmpty()) out.put(name, new Animation(name, length, loop, tracks));
        }
        return out;
    }

    private static boolean isLoop(JsonElement loopEl) {
        try {
            if (loopEl.getAsJsonPrimitive().isBoolean()) return loopEl.getAsBoolean();
            return "loop".equalsIgnoreCase(loopEl.getAsString());
        } catch (Exception e) { return false; }
    }

    /** Premier data_point d'un keyframe → [x,y,z] (gère les valeurs stockées en String). */
    private static float[] dataPoint(JsonObject kf) {
        float[] d = {0, 0, 0};
        if (!kf.has("data_points") || !kf.get("data_points").isJsonArray()) return d;
        JsonArray dps = kf.getAsJsonArray("data_points");
        if (dps.isEmpty() || !dps.get(0).isJsonObject()) return d;
        JsonObject p = dps.get(0).getAsJsonObject();
        d[0] = num(p, "x"); d[1] = num(p, "y"); d[2] = num(p, "z");
        return d;
    }

    private static float num(JsonObject o, String key) {
        if (!o.has(key)) return 0f;
        try { return o.get(key).getAsFloat(); }
        catch (Exception ex) {
            try { return Float.parseFloat(o.get(key).getAsString().trim()); }
            catch (Exception e2) { return 0f; } // expression Molang non numérique → 0
        }
    }

    /** Rééchantillonne les 3 canaux sur l'union de leurs temps → keyframes unifiées. */
    private static List<Animation.Keyframe> unify(List<float[]> pos, List<float[]> rot, List<float[]> scl) {
        java.util.TreeSet<Float> times = new java.util.TreeSet<>();
        for (float[] a : pos) times.add(a[0]);
        for (float[] a : rot) times.add(a[0]);
        for (float[] a : scl) times.add(a[0]);
        List<Animation.Keyframe> out = new ArrayList<>();
        for (float t : times) {
            out.add(new Animation.Keyframe(t,
                    sampleChannel(pos, t, 0f),
                    sampleChannel(rot, t, 0f),
                    sampleChannel(scl, t, 1f)));
        }
        return out;
    }

    /** Valeur interpolée d'un canal (liste {time,x,y,z}) à l'instant t ; {@code def} si vide. */
    private static Vector3f sampleChannel(List<float[]> ch, float t, float def) {
        if (ch.isEmpty()) return new Vector3f(def, def, def);
        ch.sort((a, b) -> Float.compare(a[0], b[0]));
        float[] first = ch.get(0), last = ch.get(ch.size() - 1);
        if (t <= first[0]) return new Vector3f(first[1], first[2], first[3]);
        if (t >= last[0]) return new Vector3f(last[1], last[2], last[3]);
        for (int i = 0; i < ch.size() - 1; i++) {
            float[] a = ch.get(i), b = ch.get(i + 1);
            if (t >= a[0] && t <= b[0]) {
                float span = b[0] - a[0];
                float f = span <= 0 ? 0f : (t - a[0]) / span;
                return new Vector3f(
                        a[1] + (b[1] - a[1]) * f,
                        a[2] + (b[2] - a[2]) * f,
                        a[3] + (b[3] - a[3]) * f);
            }
        }
        return new Vector3f(last[1], last[2], last[3]);
    }

    private static void walkGroup(JsonObject group, String parent, List<RawBone> bones,
                                  Set<String> used, Map<String, float[]> from, Map<String, float[]> to) {
        String name = uniqueName(sanitize(group.has("name") ? group.get("name").getAsString() : "bone"), used);
        float[] pivot = group.has("origin") ? scale(arr3(group, "origin")) : new float[]{0, 0, 0};

        List<String> childElems = new ArrayList<>();
        List<JsonObject> childGroups = new ArrayList<>();
        if (group.has("children") && group.get("children").isJsonArray()) {
            for (JsonElement c : group.getAsJsonArray("children")) {
                if (c.isJsonPrimitive()) childElems.add(c.getAsString());
                else if (c.isJsonObject()) childGroups.add(c.getAsJsonObject());
            }
        }
        boolean hasBox = childElems.stream().anyMatch(from::containsKey);
        float[] bf, bt;
        if (hasBox) { float[][] box = union(childElems, from, to); bf = box[0]; bt = box[1]; }
        else { bf = pivot.clone(); bt = pivot.clone(); }

        bones.add(new RawBone(name, parent, pivot, bf, bt, hasBox));
        for (JsonObject cg : childGroups) walkGroup(cg, name, bones, used, from, to);
    }

    /** Boîte englobante (en blocs) des cubes donnés ; {0,0,0}-{0,0,0} si aucun. */
    private static float[][] union(List<String> uuids, Map<String, float[]> from, Map<String, float[]> to) {
        float[] mn = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] mx = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        boolean any = false;
        for (String u : uuids) {
            float[] f = from.get(u), t = to.get(u);
            if (f == null || t == null) continue;
            any = true;
            for (int i = 0; i < 3; i++) {
                mn[i] = Math.min(mn[i], Math.min(f[i], t[i]) / 16f);
                mx[i] = Math.max(mx[i], Math.max(f[i], t[i]) / 16f);
            }
        }
        if (!any) return new float[][]{{0, 0, 0}, {0, 0, 0}};
        return new float[][]{mn, mx};
    }

    private static float[] arr3(JsonObject o, String key) {
        JsonArray a = o.getAsJsonArray(key);
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    /** Divise par 16 (px BlockBench → blocs). */
    private static float[] scale(float[] px) {
        return new float[]{px[0] / 16f, px[1] / 16f, px[2] / 16f};
    }

    private static String sanitize(String raw) {
        String s = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return s.isBlank() ? "bone" : s;
    }

    private static String uniqueName(String base, Set<String> used) {
        String name = base;
        int i = 2;
        while (!used.add(name)) name = base + "_" + (i++);
        return name;
    }

    // ----------------------------------------------------------------
    //  Adaptation runtime → RigModel (crée les BlockData)
    // ----------------------------------------------------------------

    /** Convertit un {@link RawRig} en {@link RigModel} ; {@code defaultBlock} = bloc affiché par os. */
    public static RigModel toRigModel(RawRig raw, Material defaultBlock) {
        List<RigBone> bones = new ArrayList<>();
        for (RawBone rb : raw.bones()) {
            BlockData block = rb.hasBox() ? defaultBlock.createBlockData() : null;
            bones.add(new RigBone(rb.name(), rb.parent(),
                    vec(rb.pivot()), vec(rb.from()), vec(rb.to()), block));
        }
        return new RigModel(raw.id(), bones, raw.animations());
    }

    private static Vector3f vec(float[] a) { return new Vector3f(a[0], a[1], a[2]); }
}
