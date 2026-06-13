package com.mooncore.modules.model.editor;

import com.mooncore.modules.model.RigBone;
import org.bukkit.block.data.BlockData;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Map;

/**
 * Os <b>mutable</b> de l'éditeur 3D in-game (Étape D1) : une boîte ({@code from}→{@code to}, en blocs)
 * articulée autour d'un {@code pivot}, rendue soit par un BlockDisplay ({@code block}) soit, à terme,
 * par un ItemDisplay texturé ({@code itemModelKey}, D4). Stocke un mapping UV par face (D4).
 * <p>Version mutable de {@link RigBone} : conversions {@link #toRigBone()} / {@link #from(RigBone)}.
 */
public final class EditableBone {

    public String name;
    public String parent;                    // null = enfant racine
    public final Vector3f pivot = new Vector3f();
    public final Vector3f from = new Vector3f();
    public final Vector3f to = new Vector3f();
    public BlockData block;                   // rendu « blocky » (fallback)
    public String itemModelKey;               // null = rendu via block ; sinon ItemDisplay texturé
    /** UV par face : {@code [u1, v1, u2, v2]} en pixels (0–16). Absent = UV par défaut. */
    public final Map<CubeFace, float[]> uv = new EnumMap<>(CubeFace.class);

    public EditableBone(String name) {
        this.name = name;
    }

    public Vector3f size() {
        return new Vector3f(to).sub(from);
    }

    /** Réordonne {@code from}/{@code to} pour que {@code from <= to} composante par composante. */
    public void normalize() {
        float minx = Math.min(from.x, to.x), miny = Math.min(from.y, to.y), minz = Math.min(from.z, to.z);
        float maxx = Math.max(from.x, to.x), maxy = Math.max(from.y, to.y), maxz = Math.max(from.z, to.z);
        from.set(minx, miny, minz);
        to.set(maxx, maxy, maxz);
    }

    public void setUv(CubeFace face, float u1, float v1, float u2, float v2) {
        uv.put(face, new float[]{u1, v1, u2, v2});
    }

    public EditableBone copy() {
        EditableBone b = new EditableBone(name);
        b.parent = parent;
        b.pivot.set(pivot);
        b.from.set(from);
        b.to.set(to);
        b.block = block;
        b.itemModelKey = itemModelKey;
        for (Map.Entry<CubeFace, float[]> e : uv.entrySet()) b.uv.put(e.getKey(), e.getValue().clone());
        return b;
    }

    public RigBone toRigBone() {
        return new RigBone(name, parent, new Vector3f(pivot), new Vector3f(from), new Vector3f(to),
                block, itemModelKey);
    }

    public static EditableBone from(RigBone rb) {
        EditableBone b = new EditableBone(rb.name);
        b.parent = rb.parent;
        b.pivot.set(rb.pivot);
        b.from.set(rb.from);
        b.to.set(rb.to);
        b.block = rb.block;
        b.itemModelKey = rb.itemModelKey;
        return b;
    }
}
