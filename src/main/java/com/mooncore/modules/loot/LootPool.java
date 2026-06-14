package com.mooncore.modules.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Un <b>pool</b> de loot (concept calqué sur les loot tables vanilla) : à chaque évaluation, on effectue un
 * nombre de tirages ({@code rolls} uniforme dans [rollsMin, rollsMax]), et chaque tirage sélectionne une
 * {@link LootEntry} proportionnellement à son poids. Une table peut combiner plusieurs pools indépendants.
 * <p>
 * La sélection pondérée et le compte de rolls sont <b>purs</b> (RNG injecté) → déterministes et testables
 * sans serveur. La sélection est robuste aux bornes (dernière entrée garantie même en cas d'imprécision).
 */
public final class LootPool {

    private int rollsMin = 1;
    private int rollsMax = 1;
    private final List<LootEntry> entries = new ArrayList<>();

    public LootPool() { }

    public LootPool(int rollsMin, int rollsMax) { setRolls(rollsMin, rollsMax); }

    public int rollsMin() { return rollsMin; }
    public int rollsMax() { return rollsMax; }
    public void setRolls(int min, int max) {
        this.rollsMin = Math.max(0, Math.min(64, min));
        this.rollsMax = Math.max(this.rollsMin, Math.min(64, max));
    }

    public List<LootEntry> entries() { return entries; }
    public LootPool add(LootEntry e) { if (e != null) entries.add(e); return this; }

    /** Somme des poids de toutes les entrées (0 si le pool est vide). */
    public int totalWeight() {
        int w = 0;
        for (LootEntry e : entries) w += e.weight();
        return w;
    }

    /**
     * Sélectionne une entrée proportionnellement à son poids, ou {@code null} si le pool est vide.
     * Robuste aux bornes : retourne la dernière entrée si le tirage dépasse la somme cumulée.
     */
    public LootEntry pickWeighted(RandomGenerator rng) {
        int total = totalWeight();
        if (total <= 0 || entries.isEmpty()) return null;
        int r = rng.nextInt(total);            // [0, total)
        int acc = 0;
        for (LootEntry e : entries) {
            acc += e.weight();
            if (r < acc) return e;
        }
        return entries.get(entries.size() - 1); // garde-fou (ne devrait pas arriver)
    }

    /** Nombre de tirages pour cette évaluation (uniforme dans [rollsMin, rollsMax]). */
    public int rollCount(RandomGenerator rng) {
        if (rollsMax <= rollsMin) return rollsMin;
        return rollsMin + rng.nextInt(rollsMax - rollsMin + 1);
    }

    /** Évalue le pool : {@code rolls} tirages pondérés, chacun produisant un {@link LootResult}. */
    public List<LootResult> roll(RandomGenerator rng) {
        List<LootResult> out = new ArrayList<>();
        int rolls = rollCount(rng);
        for (int i = 0; i < rolls; i++) {
            LootEntry e = pickWeighted(rng);
            if (e == null) continue;
            int count = e.rollCount(rng);
            if (count > 0) out.add(LootResult.of(e, count));
        }
        return out;
    }
}
