package com.mooncore.modules.mechanic;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Logique de cooldown de {@link MechanicCooldowns} avec temps injecté (déterministe, sans serveur).
 * Couvre les frontières exactes (tick d'expiration), l'isolation par joueur et le cas «&nbsp;sans limite&nbsp;».
 */
class MechanicCooldownsTest {

    @Test
    void blocksUntilExactExpiryTick() {
        MechanicCooldowns cd = new MechanicCooldowns();
        UUID p = new UUID(1, 1);
        assertTrue(cd.tryAcquire("m", p, 20, 100));    // 1re activation OK → prochaine à 120
        assertFalse(cd.tryAcquire("m", p, 20, 105));   // encore en cooldown
        assertFalse(cd.tryAcquire("m", p, 20, 119));   // juste avant l'expiration
        assertTrue(cd.tryAcquire("m", p, 20, 120));    // pile à l'expiration → OK
        assertFalse(cd.tryAcquire("m", p, 20, 130));   // ré-armé à 140
    }

    @Test
    void remainingCountsDown() {
        MechanicCooldowns cd = new MechanicCooldowns();
        UUID p = new UUID(2, 2);
        cd.tryAcquire("m", p, 40, 1000);
        assertEquals(40, cd.remaining("m", p, 1000));
        assertEquals(15, cd.remaining("m", p, 1025));
        assertEquals(0, cd.remaining("m", p, 1040));
        assertEquals(0, cd.remaining("m", p, 2000));   // jamais négatif
    }

    @Test
    void isolatedPerPlayerAndMechanic() {
        MechanicCooldowns cd = new MechanicCooldowns();
        UUID a = new UUID(3, 3), b = new UUID(4, 4);
        assertTrue(cd.tryAcquire("m1", a, 50, 0));
        assertTrue(cd.tryAcquire("m1", b, 50, 0));     // autre joueur : indépendant
        assertTrue(cd.tryAcquire("m2", a, 50, 0));     // autre mécanique : indépendante
        assertFalse(cd.tryAcquire("m1", a, 50, 10));
    }

    @Test
    void zeroCooldownAlwaysPasses() {
        MechanicCooldowns cd = new MechanicCooldowns();
        UUID p = new UUID(5, 5);
        assertTrue(cd.tryAcquire("m", p, 0, 0));
        assertTrue(cd.tryAcquire("m", p, 0, 0));
        assertTrue(cd.tryAcquire("m", p, -10, 0));
    }

    @Test
    void clearResetsCooldown() {
        MechanicCooldowns cd = new MechanicCooldowns();
        UUID p = new UUID(6, 6);
        cd.tryAcquire("m", p, 100, 0);
        assertFalse(cd.tryAcquire("m", p, 100, 10));
        cd.clear("m", p);
        assertTrue(cd.tryAcquire("m", p, 100, 10));    // oublié → ré-autorisé
    }
}
