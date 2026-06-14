package com.mooncore.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mini-profiler interne léger pour les chemins chauds (tick/listeners). Désactivé par défaut :
 * tant que {@link #isEnabled()} est faux, {@link #sample}/{@link #start}/{@link #stop} n'appellent
 * <b>aucun</b> {@code System.nanoTime()} et n'allouent rien → coût nul en production. Un admin l'active
 * le temps d'une mesure via {@code /moon admin timings on}, compare l'avant/après, puis le coupe.
 * <p>
 * Toute la logique d'agrégation est pure (testable sans serveur Bukkit).
 */
public final class Timings {

    private static volatile boolean enabled = false;
    private static final Map<String, Stat> STATS = new ConcurrentHashMap<>();

    private Timings() {}

    public static void setEnabled(boolean on) { enabled = on; }
    public static boolean isEnabled() { return enabled; }
    public static void reset() { STATS.clear(); }

    /** Mesure l'exécution de {@code body} sous l'étiquette {@code name} (no-op si désactivé). */
    public static void sample(String name, Runnable body) {
        if (!enabled) { body.run(); return; }
        long t0 = System.nanoTime();
        try {
            body.run();
        } finally {
            record(name, System.nanoTime() - t0);
        }
    }

    /** Début d'une mesure manuelle ; renvoie un jeton à passer à {@link #stop}. Renvoie 0 si désactivé. */
    public static long start() {
        return enabled ? System.nanoTime() : 0L;
    }

    /** Fin d'une mesure manuelle ouverte par {@link #start} (no-op si désactivé ou jeton nul). */
    public static void stop(String name, long startToken) {
        if (!enabled || startToken == 0L) return;
        record(name, System.nanoTime() - startToken);
    }

    private static void record(String name, long nanos) {
        STATS.computeIfAbsent(name, k -> new Stat()).add(nanos);
    }

    /** Instantané trié par temps total décroissant. */
    public static List<Snapshot> snapshot() {
        List<Snapshot> out = new ArrayList<>(STATS.size());
        for (Map.Entry<String, Stat> e : STATS.entrySet()) {
            out.add(e.getValue().snap(e.getKey()));
        }
        out.sort((a, b) -> Long.compare(b.totalNanos(), a.totalNanos()));
        return out;
    }

    /** Compteur thread-safe d'un point de mesure (count + total + max). */
    private static final class Stat {
        private long count;
        private long totalNanos;
        private long maxNanos;

        synchronized void add(long nanos) {
            count++;
            totalNanos += nanos;
            if (nanos > maxNanos) maxNanos = nanos;
        }

        synchronized Snapshot snap(String name) {
            return new Snapshot(name, count, totalNanos, maxNanos);
        }
    }

    /** Vue immuable d'un point de mesure ; les conversions sont pures. */
    public record Snapshot(String name, long count, long totalNanos, long maxNanos) {
        public double avgMicros() { return count == 0 ? 0.0 : (totalNanos / (double) count) / 1000.0; }
        public double maxMicros() { return maxNanos / 1000.0; }
        public double totalMillis() { return totalNanos / 1_000_000.0; }
    }
}
