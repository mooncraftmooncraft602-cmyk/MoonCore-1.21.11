package com.mooncore.modules.loot;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Agrégation <b>pure</b> de simulations de loot (outil de balance pour les designers). À partir d'un
 * échantillon de tirages développés ({@code List<List<LootResult>>}, un sous-liste par itération), calcule
 * pour chaque item distinct : la fréquence d'apparition (part des itérations qui l'ont produit au moins une
 * fois) et la quantité moyenne tirée par itération. Aucune dépendance serveur → testable sans Bukkit.
 */
public final class LootStats {

    private LootStats() { }

    /** Statistique agrégée d'un item sur l'ensemble des itérations simulées. */
    public record Entry(String key, boolean custom, int rollsWith, long totalCount, int iterations) {
        /** Part des itérations où l'item est apparu au moins une fois, dans [0,1]. */
        public double frequency() { return iterations <= 0 ? 0.0 : (double) rollsWith / iterations; }
        /** Quantité moyenne tirée par itération (peut dépasser 1 si plusieurs piles/itération). */
        public double avgPerIteration() { return iterations <= 0 ? 0.0 : (double) totalCount / iterations; }
    }

    /** Clé d'agrégation stable d'un résultat : {@code ✦<id>} pour un item custom, le nom du Material sinon. */
    public static String keyOf(LootResult r) {
        if (r == null || r.isReference()) return null;
        if (r.isCustom()) return "✦" + r.itemId();
        Material m = r.material();
        return m == null ? null : m.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Agrège un échantillon de tirages : une entrée par item distinct, triée par fréquence décroissante puis
     * quantité totale décroissante (les drops les plus fréquents en tête). Les références non développées et
     * les résultats nuls sont ignorés.
     */
    public static List<Entry> aggregate(List<List<LootResult>> samples) {
        if (samples == null || samples.isEmpty()) return List.of();
        int iterations = samples.size();
        Map<String, int[]> rollsWith = new LinkedHashMap<>();   // key -> {nbItérationsAvec}
        Map<String, long[]> totals = new LinkedHashMap<>();     // key -> {sommeQuantités}
        Map<String, Boolean> custom = new LinkedHashMap<>();

        for (List<LootResult> sample : samples) {
            if (sample == null) continue;
            java.util.Set<String> seenThisIter = new java.util.HashSet<>();
            for (LootResult r : sample) {
                String key = keyOf(r);
                if (key == null || r.count() <= 0) continue;
                custom.putIfAbsent(key, r.isCustom());
                totals.computeIfAbsent(key, k -> new long[1])[0] += r.count();
                if (seenThisIter.add(key)) {
                    rollsWith.computeIfAbsent(key, k -> new int[1])[0]++;
                }
            }
        }

        List<Entry> out = new ArrayList<>(totals.size());
        for (String key : totals.keySet()) {
            out.add(new Entry(key, custom.getOrDefault(key, false),
                    rollsWith.getOrDefault(key, new int[1])[0],
                    totals.get(key)[0], iterations));
        }
        out.sort((x, y) -> {
            int byFreq = Integer.compare(y.rollsWith(), x.rollsWith());
            if (byFreq != 0) return byFreq;
            int byTotal = Long.compare(y.totalCount(), x.totalCount());
            if (byTotal != 0) return byTotal;
            return x.key().compareTo(y.key());      // stable/déterministe à égalité
        });
        return out;
    }
}
