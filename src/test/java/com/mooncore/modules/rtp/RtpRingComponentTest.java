package com.mooncore.modules.rtp;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Échantillonnage en anneau de {@link RtpModule#ringComponent} : la valeur absolue reste dans {@code [min,
 * radius]} (jamais dans l'exclusion centrale ni hors rayon). Propriété de correction du RTP. Pur, sans serveur.
 */
class RtpRingComponentTest {

    @Test
    void absoluteValueStaysWithinRing() {
        Random rng = new Random(42);
        int min = 500, radius = 5000;
        for (int i = 0; i < 10_000; i++) {
            int c = RtpModule.ringComponent(rng, min, radius);
            int abs = Math.abs(c);
            assertTrue(abs >= min && abs <= radius, "hors anneau: " + c);
        }
    }

    @Test
    void coversBothSignsAndBounds() {
        Random rng = new Random(7);
        boolean pos = false, neg = false, lowSeen = false, highSeen = false;
        int min = 100, radius = 200;
        for (int i = 0; i < 5_000; i++) {
            int c = RtpModule.ringComponent(rng, min, radius);
            if (c > 0) pos = true;
            if (c < 0) neg = true;
            if (Math.abs(c) == min) lowSeen = true;     // borne basse atteignable
            if (Math.abs(c) == radius) highSeen = true; // borne haute atteignable
        }
        assertTrue(pos && neg, "les deux signes doivent apparaître");
        assertTrue(lowSeen && highSeen, "les deux bornes doivent être atteignables");
    }

    @Test
    void degenerateRangeClampsSafely() {
        Random rng = new Random(1);
        // min > radius (config incohérente) : ne doit pas lever ni produire d'aberration.
        int c = RtpModule.ringComponent(rng, 9999, 100);
        assertTrue(Math.abs(c) <= 100);   // borné au radius
    }
}
