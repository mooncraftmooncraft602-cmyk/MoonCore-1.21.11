package com.mooncore.modules.model.editor;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Historique undo/redo de l'éditeur 3D (Étape D3) — pile d'instantanés d'{@link EditableRig}, bornée.
 * Même principe que l'undo/redo de {@code PixelCanvas} (paint editor). Logique pure, sans serveur.
 * <p>Usage : {@link #push(EditableRig)} <b>avant</b> chaque mutation ; {@link #undo(EditableRig)} /
 * {@link #redo(EditableRig)} restaurent l'état dans le rig fourni (en place).
 */
public final class RigHistory {

    private final Deque<EditableRig> undo = new ArrayDeque<>();
    private final Deque<EditableRig> redo = new ArrayDeque<>();
    private final int capacity;

    public RigHistory() { this(64); }

    public RigHistory(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /** Enregistre l'état courant avant une mutation (vide la pile redo). */
    public void push(EditableRig current) {
        undo.push(current.copy());
        if (undo.size() > capacity) undo.removeLast();
        redo.clear();
    }

    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }

    /** Restaure l'état précédent dans {@code rig}. Retourne false s'il n'y a rien à annuler. */
    public boolean undo(EditableRig rig) {
        if (undo.isEmpty()) return false;
        redo.push(rig.copy());
        rig.copyFrom(undo.pop());
        return true;
    }

    /** Réapplique l'état annulé dans {@code rig}. Retourne false s'il n'y a rien à refaire. */
    public boolean redo(EditableRig rig) {
        if (redo.isEmpty()) return false;
        undo.push(rig.copy());
        rig.copyFrom(redo.pop());
        return true;
    }

    public void clear() {
        undo.clear();
        redo.clear();
    }
}
