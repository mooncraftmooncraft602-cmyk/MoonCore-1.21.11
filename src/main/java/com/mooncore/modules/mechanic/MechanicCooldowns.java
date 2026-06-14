package com.mooncore.modules.mechanic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Suivi des cooldowns par couple (mécanique, joueur) pour les {@link MechanicDef}. Pur : le temps courant
 * (en ticks) est <b>injecté</b> à chaque appel ({@code nowTick}), donc déterministe et testable sans serveur.
 * Un cooldown ≤ 0 n'impose aucune limite. Réutilisable par l'exécuteur LIVE et le tick d'INTERVAL.
 */
public final class MechanicCooldowns {

    private final Map<String, Long> nextAllowedTick = new HashMap<>();

    private static String key(String mechanicId, UUID player) {
        return mechanicId + "|" + (player == null ? "*" : player);
    }

    /**
     * Tente de consommer le cooldown : si la mécanique est prête pour ce joueur au tick {@code nowTick},
     * enregistre le prochain tick autorisé ({@code nowTick + cooldownTicks}) et retourne {@code true}.
     * Sinon retourne {@code false} sans rien changer. Un {@code cooldownTicks <= 0} passe toujours.
     */
    public boolean tryAcquire(String mechanicId, UUID player, int cooldownTicks, long nowTick) {
        if (mechanicId == null) return false;
        if (cooldownTicks <= 0) return true;
        String k = key(mechanicId, player);
        Long next = nextAllowedTick.get(k);
        if (next != null && nowTick < next) return false;
        nextAllowedTick.put(k, nowTick + cooldownTicks);
        return true;
    }

    /** Ticks restants avant la prochaine activation autorisée (0 si prête). */
    public long remaining(String mechanicId, UUID player, long nowTick) {
        Long next = nextAllowedTick.get(key(mechanicId, player));
        return (next == null || nowTick >= next) ? 0L : next - nowTick;
    }

    /** Oublie le cooldown d'un joueur pour une mécanique (ex. à la déconnexion ou au rechargement). */
    public void clear(String mechanicId, UUID player) {
        nextAllowedTick.remove(key(mechanicId, player));
    }

    public void clearAll() { nextAllowedTick.clear(); }
}
