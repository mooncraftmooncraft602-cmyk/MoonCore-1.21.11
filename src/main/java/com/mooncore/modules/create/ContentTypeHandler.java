package com.mooncore.modules.create;

import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * Adaptateur d'un type de contenu pour la commande de création unifiée (Étape E). Chaque type
 * (item, block, crop, boss…) implémente cette interface en déléguant à son module/store : c'est le
 * « {@code {def, store, factory, editor, ai schema, validator}} » du master brain regroupé derrière
 * une façade commune, enregistrée dans le {@link ContentTypeRegistry}.
 */
public interface ContentTypeHandler {

    /** Identifiant du type (ex. {@code "item"}, {@code "block"}, {@code "crop"}). */
    String type();

    /** Crée et persiste une nouvelle définition vide sous cet id. False si invalide ou déjà existant. */
    boolean create(String id);

    boolean exists(String id);

    boolean delete(String id);

    /** Ids existants de ce type. */
    Collection<String> ids();

    /** Donne l'objet/la graine correspondant (items, blocs, graines de culture…). Défaut : non supporté. */
    default boolean give(Player player, String id, int amount) { return false; }

    /** Ouvre l'éditeur GUI du type pour cet id. Défaut : pas d'éditeur GUI. */
    default boolean openEditor(Player player, String id) { return false; }

    /** Prompt système du schéma IA de ce type, ou {@code null} si la génération IA n'est pas branchée. */
    default String aiSystemPrompt() { return null; }

    /**
     * Crée/met à jour une définition depuis une sortie IA validée. Retourne l'id créé, ou {@code null}
     * si la génération IA n'est pas supportée ou si la sortie est invalide.
     */
    default String createFromAi(String aiText, String forcedId) { return null; }

    /** Description courte d'une entrée (pour {@code info}). Défaut : l'id. */
    default String describe(String id) { return id; }

    /**
     * Clone une entrée (copie profonde) {@code sourceId} → {@code newId}. Retourne false si la source
     * est introuvable, la cible existe déjà ou l'id est invalide. Défaut : non supporté.
     */
    default boolean cloneEntry(String sourceId, String newId) { return false; }

    /**
     * Valide une sortie IA <b>sans persister</b> (dry-run) : retourne une description lisible du
     * résultat si la sortie est valide, ou {@code null}. Défaut : non supporté.
     */
    default String validateAi(String aiText, String forcedId) { return null; }
}
