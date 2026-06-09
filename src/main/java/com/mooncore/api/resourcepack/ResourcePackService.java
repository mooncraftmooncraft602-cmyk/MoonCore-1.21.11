package com.mooncore.api.resourcepack;

/**
 * Service du resource pack serveur (forcé). Permet aux autres modules (CustomItem, IA)
 * de déclencher une reconstruction du pack après ajout d'une texture, et de connaître
 * l'URL de distribution.
 */
public interface ResourcePackService {

    /** Reconstruit le pack (modèles + textures + sons) et recalcule le SHA-1. */
    void rebuild();

    /**
     * Demande une reconstruction + renvoi du pack en <b>coalesçant</b> les appels rapprochés
     * (une seule reconstruction après une courte fenêtre). À préférer à {@link #rebuild()}
     * suivi de {@link #resendAll()} lors d'ajouts en rafale (génération IA par lot, import
     * de textures par dossier), pour éviter de zipper/hasher le pack N fois d'affilée.
     * <p>Implémentation par défaut : reconstruit immédiatement (pas de coalescence).
     */
    default void requestRebuild() {
        rebuild();
        resendAll();
    }

    /** (Re)pousse le pack forcé à tous les joueurs Java en ligne. */
    void resendAll();

    /** URL de téléchargement du pack, ou {@code null} si le serveur HTTP est arrêté. */
    String url();
}
