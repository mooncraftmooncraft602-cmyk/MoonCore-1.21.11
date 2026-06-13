package com.mooncore.modules.model.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exporte un {@link EditableRig} en modèle <b>BlockBench</b> ({@code .bbmodel}, JSON) — Étape D6.
 * Round-trip avec {@code BlockBenchImporter} : chaque os devient un groupe d'{@code outliner}
 * (origin = pivot ×16) contenant un cube {@code elements} (from/to ×16, px). Les sous-os (parent)
 * sont imbriqués. Conversion blocs → pixels (×16). Pur (Gson), sans dépendance Bukkit.
 */
public final class BlockBenchExporter {

    private BlockBenchExporter() {}

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public static String toBbmodel(EditableRig rig) {
        JsonObject root = new JsonObject();
        JsonObject meta = new JsonObject();
        meta.addProperty("format_version", "4.5");
        meta.addProperty("model_format", "free");
        meta.addProperty("box_uv", false);
        root.add("meta", meta);
        root.addProperty("name", rig.id);

        JsonObject res = new JsonObject();
        res.addProperty("width", 16);
        res.addProperty("height", 16);
        root.add("resolution", res);

        // 1) Un cube (element) par os, indexé par uuid déterministe.
        JsonArray elements = new JsonArray();
        Map<String, String> cubeUuid = new LinkedHashMap<>(); // bone → cube uuid
        for (EditableBone b : rig.bones) {
            String uuid = "cube_" + b.name;
            cubeUuid.put(b.name, uuid);
            JsonObject el = new JsonObject();
            el.addProperty("name", b.name);
            el.addProperty("uuid", uuid);
            el.add("from", arr(b.from.x * 16, b.from.y * 16, b.from.z * 16));
            el.add("to", arr(b.to.x * 16, b.to.y * 16, b.to.z * 16));
            el.add("faces", faces());
            elements.add(el);
        }
        root.add("elements", elements);

        // 2) Outliner : arbre de groupes (un par os) selon le parent.
        Map<String, List<EditableBone>> byParent = new LinkedHashMap<>();
        for (EditableBone b : rig.bones) {
            String key = b.parent == null ? "" : b.parent;
            byParent.computeIfAbsent(key, k -> new ArrayList<>()).add(b);
        }
        JsonArray outliner = new JsonArray();
        for (EditableBone b : rig.bones) {
            boolean isRoot = b.parent == null || rig.bone(b.parent) == null;
            if (isRoot) outliner.add(group(b, cubeUuid, byParent));
        }
        root.add("outliner", outliner);

        // 3) Animations (réutilise la sérialisation Gson de Animation via EditableAnimation n'est pas
        //    requise pour le round-trip géométrique ; un tableau vide reste un .bbmodel valide).
        root.add("animations", new JsonArray());

        return GSON.toJson(root);
    }

    private static JsonObject group(EditableBone bone, Map<String, String> cubeUuid,
                                    Map<String, List<EditableBone>> byParent) {
        JsonObject g = new JsonObject();
        g.addProperty("name", bone.name);
        g.addProperty("uuid", "group_" + bone.name);
        g.add("origin", arr(bone.pivot.x * 16, bone.pivot.y * 16, bone.pivot.z * 16));
        JsonArray children = new JsonArray();
        children.add(cubeUuid.get(bone.name));                   // le cube de cet os
        for (EditableBone child : byParent.getOrDefault(bone.name, List.of())) {
            children.add(group(child, cubeUuid, byParent));      // sous-os imbriqués
        }
        g.add("children", children);
        return g;
    }

    private static JsonArray arr(float x, float y, float z) {
        JsonArray a = new JsonArray();
        a.add(x); a.add(y); a.add(z);
        return a;
    }

    /** Faces minimales (BlockBench valide) ; l'import ne lit que from/to/uuid. */
    private static JsonObject faces() {
        JsonObject f = new JsonObject();
        for (String face : new String[]{"north", "east", "south", "west", "up", "down"}) {
            JsonObject fo = new JsonObject();
            JsonArray uv = new JsonArray();
            uv.add(0); uv.add(0); uv.add(16); uv.add(16);
            fo.add("uv", uv);
            fo.addProperty("texture", 0);
            f.add(face, fo);
        }
        return f;
    }
}
