package com.mooncore.modules.mechanic;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Substitution pure des placeholders d'action ({@link MechanicExecutor#fillPlaceholders}) : remplace
 * {@code %clé%} connus, laisse les inconnus, ne re-balaye pas les valeurs et préserve les {@code %} isolés.
 */
class MechanicPlaceholderTest {

    private static final Map<String, String> VARS = Map.of(
            "player", "Steve", "world", "world_nether", "x", "10", "y", "64", "z", "-20", "online", "3");

    @Test
    void substitutesKnownKeys() {
        assertEquals("Salut Steve, monde world_nether (10,64,-20) — 3 en ligne",
                MechanicExecutor.fillPlaceholders("Salut %player%, monde %world% (%x%,%y%,%z%) — %online% en ligne", VARS));
    }

    @Test
    void unknownKeysLeftIntactWhileKnownSubstituted() {
        assertEquals("%money% pour Steve",   // %money% inconnu préservé, %player% connu substitué
                MechanicExecutor.fillPlaceholders("%money% pour %player%", VARS));
    }

    @Test
    void loneAndLiteralPercentPreserved() {
        assertEquals("100% de vie pour Steve",
                MechanicExecutor.fillPlaceholders("100% de vie pour %player%", VARS));
        assertEquals("fin %", MechanicExecutor.fillPlaceholders("fin %", VARS));
    }

    @Test
    void noCascadeSubstitution() {
        // Une valeur contenant un motif %...% ne doit pas être re-substituée.
        Map<String, String> m = Map.of("a", "%b%", "b", "BOOM");
        assertEquals("%b%", MechanicExecutor.fillPlaceholders("%a%", m));
    }

    @Test
    void emptyAndNullHandled() {
        assertEquals("", MechanicExecutor.fillPlaceholders(null, VARS));
        assertEquals("", MechanicExecutor.fillPlaceholders("", VARS));
        assertEquals("texte sans clé", MechanicExecutor.fillPlaceholders("texte sans clé", VARS));
    }
}
