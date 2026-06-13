package com.mooncore.data.content;

import com.google.gson.JsonObject;

/**
 * Schéma versionné d'un type de contenu (Étape A5). Permet la <b>rétro-compatibilité
 * intra-objet</b> : quand une définition stockée en base porte une {@code schema_version}
 * antérieure, son JSON est transformé vers la forme courante avant désérialisation.
 * <p>
 * Implémentation typique : un {@code switch} en cascade qui applique les transformations
 * successives ({@code fromVersion → fromVersion+1 → … → currentVersion}).
 */
public interface ContentSchema {

    /** Version courante de la forme JSON pour ce type (≥ 1). */
    int currentVersion();

    /**
     * Met à niveau {@code data} (forme {@code fromVersion}) vers {@link #currentVersion()}.
     * Doit être idempotent si {@code fromVersion >= currentVersion()} (retourner {@code data} tel quel).
     * Peut muter et retourner le même objet.
     */
    JsonObject upgrade(JsonObject data, int fromVersion);
}
