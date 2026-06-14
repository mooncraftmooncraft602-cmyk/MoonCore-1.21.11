package com.mooncore.modules.mechanic;

import java.util.Locale;

/**
 * Déclencheur d'une {@link MechanicDef} : l'événement de jeu qui lance les actions. Le filtrage fin
 * (quel bloc / quel item) est porté par {@link MechanicDef#matchKey()}. {@link #fromText} est tolérant
 * (alias FR/EN, séparateurs variés) — la robustesse de ce parsing est testée sans serveur.
 */
public enum TriggerType {
    INTERACT_BLOCK,   // clic droit sur un bloc (custom via matchKey, sinon Material)
    BREAK_BLOCK,      // casse d'un bloc
    PLACE_BLOCK,      // pose d'un bloc (custom via matchKey, sinon Material)
    USE_ITEM,         // clic droit en tenant un item (custom via matchKey, sinon Material)
    KILL_ENTITY,      // mort d'une entité tuée par le joueur
    DAMAGE_TAKEN,     // le joueur subit des dégâts (matchKey = cause, ex FALL/FIRE/ENTITY_ATTACK)
    SNEAK,            // le joueur commence à s'accroupir
    DEATH,            // le joueur meurt (matchKey = cause, ex FALL/ENTITY_ATTACK ; distinct de RESPAWN qui suit)
    RESPAWN,          // réapparition après la mort
    CONSUME_ITEM,     // le joueur mange/boit un item (custom via matchKey, sinon Material)
    FISH,             // le joueur attrape quelque chose à la pêche
    PLAYER_JOIN,      // connexion
    PLAYER_QUIT,      // déconnexion
    INTERVAL,         // tick périodique (matchKey ignoré ; période portée par la mécanique)
    NONE;             // inerte (désactivé / non reconnu)

    /** Parse tolérant : insensible casse, accepte {@code -} {@code _} {@code espace}, alias FR/EN. */
    public static TriggerType fromText(String raw) {
        if (raw == null) return NONE;
        String t = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (t) {
            case "interact_block", "interact", "rightclick_block", "clic_bloc", "interaction_bloc" -> INTERACT_BLOCK;
            case "break_block", "break", "casse", "casse_bloc", "mine_block" -> BREAK_BLOCK;
            case "place_block", "place", "pose", "pose_bloc", "put_block" -> PLACE_BLOCK;
            case "use_item", "use", "rightclick_item", "clic_item", "utilise_item" -> USE_ITEM;
            case "kill_entity", "kill", "kill_mob", "tue", "tue_entite" -> KILL_ENTITY;
            case "damage_taken", "damage", "hurt", "degats", "subit_degats", "on_damage" -> DAMAGE_TAKEN;
            case "sneak", "shift", "accroupi", "sneak_toggle" -> SNEAK;
            case "death", "die", "mort", "meurt", "on_death" -> DEATH;
            case "respawn", "reapparition", "renait" -> RESPAWN;
            case "consume_item", "consume", "eat", "drink", "mange", "consomme" -> CONSUME_ITEM;
            case "fish", "fishing", "peche", "peche_poisson" -> FISH;
            case "player_join", "join", "connexion", "arrivee" -> PLAYER_JOIN;
            case "player_quit", "quit", "leave", "deconnexion", "depart" -> PLAYER_QUIT;
            case "interval", "tick", "timer", "periodique", "periode" -> INTERVAL;
            default -> NONE;
        };
    }

    /** True si ce déclencheur cible un objet identifié (bloc/item/cause) via {@code matchKey}. */
    public boolean usesMatchKey() {
        return this == INTERACT_BLOCK || this == BREAK_BLOCK || this == PLACE_BLOCK
                || this == USE_ITEM || this == KILL_ENTITY || this == DAMAGE_TAKEN
                || this == CONSUME_ITEM || this == DEATH;
    }
}
