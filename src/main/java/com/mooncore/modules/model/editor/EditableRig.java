package com.mooncore.modules.model.editor;

import com.mooncore.modules.model.Animation;
import com.mooncore.modules.model.RigBone;
import com.mooncore.modules.model.RigModel;
import org.bukkit.block.data.BlockData;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rig <b>mutable</b> de l'éditeur 3D in-game (Étape D1) : hiérarchie d'{@link EditableBone} éditable
 * (ajout/suppression de cube, from/to, pivot, parent, UV par face) plus les animations. Se matérialise
 * via {@link #toRigModel()} (consommé par {@code RigInstance}, D2) et se reconstruit depuis un
 * {@link RigModel} via {@link #fromRigModel(RigModel)} (round-trip avec l'import .bbmodel).
 */
public final class EditableRig {

    public String id;
    public final List<EditableBone> bones = new ArrayList<>();
    public final Map<String, Animation> animations = new LinkedHashMap<>();

    public EditableRig(String id) {
        this.id = id;
    }

    public EditableBone bone(String name) {
        if (name == null) return null;
        for (EditableBone b : bones) if (b.name.equals(name)) return b;
        return null;
    }

    /** Ajoute un cube (nom rendu unique), pivot par défaut au centre de la base. */
    public EditableBone addCube(String name, Vector3f from, Vector3f to, BlockData block) {
        EditableBone b = new EditableBone(uniqueName(name));
        b.from.set(from);
        b.to.set(to);
        b.normalize();
        b.pivot.set((b.from.x + b.to.x) / 2f, b.from.y, (b.from.z + b.to.z) / 2f);
        b.block = block;
        bones.add(b);
        return b;
    }

    public boolean removeCube(String name) {
        return bones.removeIf(b -> b.name.equals(name));
    }

    public boolean setFromTo(String name, Vector3f from, Vector3f to) {
        EditableBone b = bone(name);
        if (b == null) return false;
        b.from.set(from);
        b.to.set(to);
        b.normalize();
        return true;
    }

    public boolean setPivot(String name, Vector3f pivot) {
        EditableBone b = bone(name);
        if (b == null) return false;
        b.pivot.set(pivot);
        return true;
    }

    public boolean setParent(String name, String parent) {
        EditableBone b = bone(name);
        if (b == null) return false;
        b.parent = parent;
        return true;
    }

    public boolean setItemModelKey(String name, String key) {
        EditableBone b = bone(name);
        if (b == null) return false;
        b.itemModelKey = (key == null || key.isBlank()) ? null : key;
        return true;
    }

    public boolean setUv(String name, CubeFace face, float u1, float v1, float u2, float v2) {
        EditableBone b = bone(name);
        if (b == null) return false;
        b.setUv(face, u1, v1, u2, v2);
        return true;
    }

    private String uniqueName(String base) {
        String n = (base == null || base.isBlank()) ? "cube" : base;
        if (bone(n) == null) return n;
        int i = 1;
        while (bone(n + "_" + i) != null) i++;
        return n + "_" + i;
    }

    /** Snapshot immuable (consommable par RigInstance / export). */
    public RigModel toRigModel() {
        List<RigBone> rb = new ArrayList<>(bones.size());
        for (EditableBone b : bones) rb.add(b.toRigBone());
        return new RigModel(id, rb, new LinkedHashMap<>(animations));
    }

    public static EditableRig fromRigModel(RigModel m) {
        EditableRig r = new EditableRig(m.id);
        for (RigBone b : m.bones) r.bones.add(EditableBone.from(b));
        if (m.animations != null) r.animations.putAll(m.animations);
        return r;
    }

    public EditableRig copy() {
        EditableRig r = new EditableRig(id);
        for (EditableBone b : bones) r.bones.add(b.copy());
        r.animations.putAll(animations);
        return r;
    }

    /** Restaure l'état depuis un instantané (pour undo/redo) : remplace bones + animations + id. */
    public void copyFrom(EditableRig other) {
        this.id = other.id;
        bones.clear();
        for (EditableBone b : other.bones) bones.add(b.copy());
        animations.clear();
        animations.putAll(other.animations);
    }
}
