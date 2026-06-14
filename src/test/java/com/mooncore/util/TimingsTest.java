package com.mooncore.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Logique d'agrégation du mini-profiler (pure, sans serveur Bukkit). */
class TimingsTest {

    @AfterEach
    void cleanup() {
        Timings.setEnabled(false);
        Timings.reset();
    }

    @Test
    void disabledByDefault_recordsNothing() {
        Timings.reset();
        // start() doit renvoyer 0 et stop() être un no-op quand désactivé.
        assertFalse(Timings.isEnabled());
        long token = Timings.start();
        assertEquals(0L, token);
        Timings.stop("x", token);
        assertTrue(Timings.snapshot().isEmpty());
    }

    @Test
    void snapshotMath_avgMaxTotal() {
        // Calcul pur via le record, indépendant de l'horloge.
        Timings.Snapshot s = new Timings.Snapshot("foo", 4, 8_000L, 5_000L);
        assertEquals(4, s.count());
        assertEquals(2.0, s.avgMicros(), 1e-9);   // 8000ns / 4 = 2000ns = 2µs
        assertEquals(5.0, s.maxMicros(), 1e-9);   // 5000ns = 5µs
        assertEquals(0.008, s.totalMillis(), 1e-9); // 8000ns = 0.008ms
    }

    @Test
    void emptySnapshot_avgIsZero() {
        Timings.Snapshot s = new Timings.Snapshot("empty", 0, 0L, 0L);
        assertEquals(0.0, s.avgMicros(), 1e-9);
    }

    @Test
    void enabled_accumulatesAndSortsByTotalDesc() {
        Timings.reset();
        Timings.setEnabled(true);
        // Deux échantillons sous le même nom + un sous un autre nom.
        Timings.sample("a", () -> busy(2_000));
        Timings.sample("a", () -> busy(2_000));
        Timings.sample("b", () -> busy(1_000));

        List<Timings.Snapshot> snaps = Timings.snapshot();
        assertEquals(2, snaps.size());
        // Tri par temps total décroissant (vrai par construction, indépendant de l'horloge).
        assertTrue(snaps.get(0).totalNanos() >= snaps.get(1).totalNanos());
        // Le nom "a" a bien été échantillonné deux fois, "b" une fois.
        Timings.Snapshot a = snaps.stream().filter(s -> s.name().equals("a")).findFirst().orElseThrow();
        Timings.Snapshot b = snaps.stream().filter(s -> s.name().equals("b")).findFirst().orElseThrow();
        assertEquals(2, a.count());
        assertEquals(1, b.count());
    }

    /** Petit travail occupé pour générer un delta de temps mesurable et déterministe en signe. */
    private static void busy(int iterations) {
        long acc = 0;
        for (int i = 0; i < iterations; i++) acc += i;
        if (acc == Long.MIN_VALUE) throw new IllegalStateException(); // empêche l'élimination JIT
    }
}
