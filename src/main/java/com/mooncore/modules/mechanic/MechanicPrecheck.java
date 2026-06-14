package com.mooncore.modules.mechanic;

import java.util.List;
import java.util.function.Predicate;

/**
 * Pré-validation <b>atomique</b> d'une séquence d'actions de mécanique (pur → testable sans serveur).
 * Détecte les prérequis qui produiraient sinon un échec silencieux ou un état incohérent à mi-séquence :
 * <ul>
 *   <li>un {@code TELEPORT} vers un monde explicite inexistant (l'action serait un no-op silencieux) ;</li>
 *   <li>un total de {@code TAKE_MONEY} supérieur au solde disponible (le joueur recevrait les
 *       récompenses sans pouvoir payer).</li>
 * </ul>
 * Si la vérification échoue, l'appelant ({@link MechanicExecutor}) n'exécute <b>aucune</b> action.
 */
final class MechanicPrecheck {

    private MechanicPrecheck() {}

    record Result(boolean ok, String reason) {
        static final Result OK = new Result(true, null);
        static Result fail(String reason) { return new Result(false, reason); }
    }

    /**
     * @param actions     actions de la mécanique
     * @param worldExists prédicat : un nom de monde explicite est-il résolvable ?
     * @param available   solde disponible du joueur ({@code Double.MAX_VALUE} si la mécanique ne débite
     *                    rien ; {@code 0} si un débit est demandé mais qu'aucune économie n'est dispo)
     */
    static Result check(List<MechanicAction> actions, Predicate<String> worldExists, double available) {
        if (actions == null) return Result.OK;
        double needed = 0.0;
        for (MechanicAction a : actions) {
            if (a == null || !a.isValid()) continue;
            switch (a.type()) {
                case TELEPORT -> {
                    String world = a.param("world", null);
                    if (world != null && !worldExists.test(world)) {
                        return Result.fail("monde introuvable : " + world);
                    }
                }
                case TAKE_MONEY -> {
                    double amt = a.doubleParam("amount", 0);
                    if (amt > 0) needed += amt;
                }
                default -> { /* pas de prérequis bloquant */ }
            }
        }
        if (needed > 0 && needed > available) {
            return Result.fail("solde insuffisant (besoin " + needed + ", dispo " + available + ")");
        }
        return Result.OK;
    }
}
