package com.mooncore.modules.model.editor;

import org.joml.Vector3f;

/**
 * Opérations d'édition de géométrie de l'éditeur 3D in-game (Étape D3) — logique pure, sans serveur.
 * Les outils hotbar appellent ces fonctions ; la {@link ModelEditorSession} les enveloppe d'un
 * snapshot (undo) et d'une re-matérialisation. « Sneak = pas fin » se traduit par un {@code step}
 * plus petit passé par l'appelant.
 */
public final class ModelEditorTools {

    private ModelEditorTools() {}

    /** Déplace le cube (from, to et pivot) de {@code (dx,dy,dz)}. */
    public static void translate(EditableBone b, float dx, float dy, float dz) {
        b.from.add(dx, dy, dz);
        b.to.add(dx, dy, dz);
        b.pivot.add(dx, dy, dz);
    }

    /** Déplace uniquement le pivot du cube. */
    public static void movePivot(EditableBone b, float dx, float dy, float dz) {
        b.pivot.add(dx, dy, dz);
    }

    /** Agrandit/réduit la boîte en déplaçant le coin {@code to} (resize). Garde {@code from<=to}. */
    public static void resize(EditableBone b, float dx, float dy, float dz) {
        b.to.add(dx, dy, dz);
        b.normalize();
    }

    /** Met à l'échelle la boîte autour de son pivot par {@code factor} (>0). */
    public static void scaleAroundPivot(EditableBone b, float factor) {
        if (factor <= 0) return;
        b.from.set(scaleAbout(b.from, b.pivot, factor));
        b.to.set(scaleAbout(b.to, b.pivot, factor));
        b.normalize();
    }

    private static Vector3f scaleAbout(Vector3f point, Vector3f pivot, float factor) {
        return new Vector3f(point).sub(pivot).mul(factor).add(pivot);
    }

    /**
     * Duplique un cube (géométrie + UV + bloc), légèrement décalé, sous un nom unique. Retourne le
     * nouvel os ajouté au rig, ou {@code null} si la source est introuvable.
     */
    public static EditableBone duplicate(EditableRig rig, String name, float offsetX, float offsetY, float offsetZ) {
        EditableBone src = rig.bone(name);
        if (src == null) return null;
        EditableBone copy = src.copy();
        copy.name = uniqueName(rig, src.name + "_copy");
        translate(copy, offsetX, offsetY, offsetZ);
        rig.bones.add(copy);
        return copy;
    }

    private static String uniqueName(EditableRig rig, String base) {
        if (rig.bone(base) == null) return base;
        int i = 1;
        while (rig.bone(base + "_" + i) != null) i++;
        return base + "_" + i;
    }
}
