package com.mooncore.modules.model;

import org.bukkit.block.data.BlockData;
import org.joml.Vector3f;

/**
 * Un os (bone) d'un {@link RigModel} : une boîte visuelle ({@code from}→{@code to}, en BLOCS)
 * affichée par un BlockDisplay, articulée autour d'un {@code pivot}, éventuellement enfant d'un
 * autre os ({@code parent}). Les coordonnées sont en blocs (1 bloc = 16 px BlockBench → diviser
 * par 16 à la construction).
 *
 * <p>Première implémentation : chaque os = une boîte de bloc (modèle « blocky » type golem).
 * L'import BlockBench (phase ultérieure) remplacera le bloc par un item-model texturé par os,
 * sans changer la mécanique d'articulation/animation.
 */
public final class RigBone {

    public final String name;
    public final String parent;   // null = enfant de la racine du rig
    public final Vector3f pivot;  // centre de rotation (blocs)
    public final Vector3f from;   // coin min de la boîte (blocs)
    public final Vector3f to;     // coin max de la boîte (blocs)
    public final BlockData block; // bloc affiché

    public RigBone(String name, String parent, Vector3f pivot, Vector3f from, Vector3f to, BlockData block) {
        this.name = name;
        this.parent = parent;
        this.pivot = pivot;
        this.from = from;
        this.to = to;
        this.block = block;
    }

    /** Taille de la boîte (to - from), en blocs. */
    public Vector3f size() {
        return new Vector3f(to.x - from.x, to.y - from.y, to.z - from.z);
    }
}
