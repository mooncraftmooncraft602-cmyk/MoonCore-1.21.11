package com.mooncore.modules.economy;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Détecteur anti-dupe {@link AbnormalGainDetector} : somme glissante des gains sur une fenêtre, signal
 * une seule fois par franchissement de seuil. Horloge injectée → déterministe, sans serveur.
 */
class AbnormalGainDetectorTest {

    @Test
    void flagsOnceWhenThresholdCrossed() {
        AbnormalGainDetector d = new AbnormalGainDetector(10_000, 1000);  // fenêtre 10s, seuil 1000
        UUID p = new UUID(1, 1);
        assertFalse(d.record(p, 400, 0));        // total 400 < 1000
        assertFalse(d.record(p, 400, 1000));     // total 800 < 1000
        assertTrue(d.record(p, 400, 2000));      // total 1200 >= 1000 : franchissement → signale
        assertFalse(d.record(p, 100, 3000));     // déjà au-dessus : ne re-signale pas
        assertEquals(1300, d.windowTotal(p, 3000), 1e-9);
    }

    @Test
    void slidingWindowPrunesOldGains() {
        AbnormalGainDetector d = new AbnormalGainDetector(10_000, 1_000_000);  // seuil haut : pas de flag
        UUID p = new UUID(2, 2);
        d.record(p, 500, 0);
        d.record(p, 500, 5000);
        assertEquals(1000, d.windowTotal(p, 9000), 1e-9);   // les deux dans la fenêtre [−1000, 9000]
        assertEquals(500, d.windowTotal(p, 11000), 1e-9);   // le gain à t=0 sort (cutoff 1000), reste celui à 5000
        assertEquals(0, d.windowTotal(p, 16000), 1e-9);     // les deux sortis
    }

    @Test
    void reSignalsAfterDroppingBelowThreshold() {
        AbnormalGainDetector d = new AbnormalGainDetector(10_000, 1000);
        UUID p = new UUID(3, 3);
        assertTrue(d.record(p, 1500, 0));        // franchissement immédiat
        assertFalse(d.record(p, 100, 1000));     // toujours au-dessus
        assertEquals(0, d.windowTotal(p, 20000), 1e-9);  // tout est sorti de la fenêtre → repasse sous le seuil
        assertTrue(d.record(p, 1500, 20000));    // nouveau franchissement → re-signale
    }

    @Test
    void zeroThresholdNeverFlags() {
        AbnormalGainDetector d = new AbnormalGainDetector(10_000, 0);
        UUID p = new UUID(4, 4);
        assertFalse(d.record(p, 1_000_000, 0));
    }
}
