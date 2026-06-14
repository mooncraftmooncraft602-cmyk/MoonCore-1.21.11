package com.mooncore.modules.mechanic;

import java.util.Locale;

/**
 * Type d'action exécutée par une {@link MechanicDef} quand son {@link TriggerType} se déclenche. Chaque
 * action lit ses propres paramètres dans {@link MechanicAction#params()} (interprétés à l'exécution, côté
 * serveur). {@link #fromText} est tolérant (alias FR/EN) ; la robustesse est testée sans serveur.
 */
public enum ActionType {
    MESSAGE,    // params: text (MiniMessage, placeholders %player%)
    COMMAND,    // params: command (exécutée console ; %player% remplacé)
    SOUND,      // params: sound, volume, pitch
    POTION,     // params: effect, duration (ticks), amplifier
    GIVE_ITEM,  // params: item (Material ou custom:<id>), amount
    MONEY,      // params: amount (économie)
    DAMAGE,     // params: amount
    HEAL,       // params: amount
    XP,         // params: amount (progression)
    TELEPORT,   // params: x, y, z [, world] OU target=spawn
    LIGHTNING,  // params: damage (true/false) — éclair à la position du joueur
    SPAWN_MOB,  // params: entity (EntityType), count
    TITLE,      // params: title, subtitle (MiniMessage, %player%)
    CLEAR_EFFECTS, // aucun paramètre : retire tous les effets de potion
    FEED,       // params: amount (points de faim) — recharge la nourriture
    NONE;       // inerte (non reconnu)

    /** Parse tolérant : insensible casse, accepte {@code -} {@code _} {@code espace}, alias FR/EN. */
    public static ActionType fromText(String raw) {
        if (raw == null) return NONE;
        String t = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (t) {
            case "message", "msg", "say", "tell", "dis" -> MESSAGE;
            case "command", "cmd", "commande", "run", "execute" -> COMMAND;
            case "sound", "son", "play_sound", "joue_son" -> SOUND;
            case "potion", "effect", "effet", "potion_effect" -> POTION;
            case "give_item", "give", "item", "donne", "donne_item" -> GIVE_ITEM;
            case "money", "argent", "eco", "economy", "cash" -> MONEY;
            case "damage", "degats", "hurt", "blesse" -> DAMAGE;
            case "heal", "soin", "soigne", "regen" -> HEAL;
            case "xp", "exp", "experience" -> XP;
            case "teleport", "tp", "teleporte" -> TELEPORT;
            case "lightning", "eclair", "foudre", "strike" -> LIGHTNING;
            case "spawn_mob", "spawn", "summon", "invoque", "spawn_entity" -> SPAWN_MOB;
            case "title", "titre" -> TITLE;
            case "clear_effects", "clear", "cure", "retire_effets", "milk" -> CLEAR_EFFECTS;
            case "feed", "nourris", "faim", "food" -> FEED;
            default -> NONE;
        };
    }
}
