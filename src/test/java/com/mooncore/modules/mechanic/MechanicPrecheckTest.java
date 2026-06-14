package com.mooncore.modules.mechanic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pré-validation atomique des mécaniques (logique pure, sans serveur Bukkit). */
class MechanicPrecheckTest {

    private static final Predicate<String> WORLDS = Set.of("world", "world_nether")::contains;

    private static MechanicAction action(ActionType type, String... kv) {
        Map<String, String> p = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) p.put(kv[i], kv[i + 1]);
        return new MechanicAction(type, p);
    }

    @Test
    void emptyOrNull_isOk() {
        assertTrue(MechanicPrecheck.check(List.of(), WORLDS, 0).ok());
        assertTrue(MechanicPrecheck.check(null, WORLDS, 0).ok());
    }

    @Test
    void teleportToExistingWorld_isOk() {
        var r = MechanicPrecheck.check(
                List.of(action(ActionType.TELEPORT, "world", "world_nether", "x", "0", "y", "64", "z", "0")),
                WORLDS, Double.MAX_VALUE);
        assertTrue(r.ok());
        assertNull(r.reason());
    }

    @Test
    void teleportToMissingWorld_fails() {
        var r = MechanicPrecheck.check(
                List.of(action(ActionType.TELEPORT, "world", "deleted_world", "x", "0", "y", "64", "z", "0")),
                WORLDS, Double.MAX_VALUE);
        assertFalse(r.ok());
        assertTrue(r.reason().contains("deleted_world"));
    }

    @Test
    void teleportWithoutExplicitWorld_isOk() {
        // Pas de paramètre "world" → monde courant du joueur, toujours valide → pas de blocage.
        var r = MechanicPrecheck.check(
                List.of(action(ActionType.TELEPORT, "target", "spawn")), WORLDS, Double.MAX_VALUE);
        assertTrue(r.ok());
    }

    @Test
    void takeMoneyWithinBalance_isOk() {
        var r = MechanicPrecheck.check(
                List.of(action(ActionType.TAKE_MONEY, "amount", "100")), WORLDS, 150.0);
        assertTrue(r.ok());
    }

    @Test
    void takeMoneySumExceedsBalance_fails() {
        // Deux débits cumulés (60 + 60 = 120) > solde (100) → toute la séquence est annulée.
        var r = MechanicPrecheck.check(List.of(
                action(ActionType.TAKE_MONEY, "amount", "60"),
                action(ActionType.TAKE_MONEY, "amount", "60")), WORLDS, 100.0);
        assertFalse(r.ok());
        assertTrue(r.reason().contains("solde"));
    }

    @Test
    void takeMoneyWithNoEconomy_fails() {
        // available = 0 (aucune économie) et un débit demandé → annulation (pas de cadeau gratuit).
        var r = MechanicPrecheck.check(
                List.of(action(ActionType.TAKE_MONEY, "amount", "5")), WORLDS, 0.0);
        assertFalse(r.ok());
    }

    @Test
    void otherActions_haveNoPrerequisite() {
        var r = MechanicPrecheck.check(List.of(
                action(ActionType.MESSAGE, "text", "salut"),
                action(ActionType.GIVE_ITEM, "item", "DIAMOND", "amount", "1"),
                action(ActionType.MONEY, "amount", "999999")), WORLDS, 0.0);
        assertTrue(r.ok());
    }

    @Test
    void invalidActionsAreSkipped() {
        // ActionType.NONE n'est pas validé → ignoré par le précheck.
        var r = MechanicPrecheck.check(
                List.of(action(ActionType.NONE, "world", "deleted_world")), WORLDS, Double.MAX_VALUE);
        assertTrue(r.ok());
    }

    @Test
    void multipleConstraints_firstFailureReported() {
        // Monde manquant ET solde insuffisant : le monde manquant est détecté en premier (court-circuit).
        var r = MechanicPrecheck.check(List.of(
                action(ActionType.TAKE_MONEY, "amount", "9999"),
                action(ActionType.TELEPORT, "world", "ghost", "x", "0", "y", "0", "z", "0")), WORLDS, 1.0);
        assertFalse(r.ok());
        // L'ordre d'itération suit la liste : TAKE_MONEY accumule, TELEPORT échoue → raison "monde".
        assertEquals("monde introuvable : ghost", r.reason());
    }
}
