package com.mooncore.modules.loot;

import org.bukkit.Material;

import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * Une entrée pondérée d'un {@link LootPool} : un drop possible (item custom MoonCore via {@code itemId},
 * sinon {@link Material} vanilla) avec un <b>poids</b> relatif de sélection et une fourchette de quantité.
 * <p>
 * La logique de tirage est <b>pure</b> (générateur aléatoire injecté) donc déterministe et testable sans
 * serveur. Les fourchettes sont clampées à la construction pour rester cohérentes (min ≤ max).
 */
public final class LootEntry {

    private final String itemId;        // item custom MoonCore (prioritaire), ou null
    private final Material material;    // matériau vanilla de repli (jamais null)
    private final int weight;           // poids relatif de sélection (>= 1)
    private final int countMin;
    private final int countMax;

    public LootEntry(String itemId, Material material, int weight, int countMin, int countMax) {
        this.itemId = (itemId == null || itemId.isBlank()) ? null : itemId.toLowerCase(Locale.ROOT).trim();
        this.material = material == null ? Material.AIR : material;
        this.weight = Math.max(1, weight);
        this.countMin = Math.max(0, Math.min(64, countMin));
        this.countMax = Math.max(this.countMin, Math.min(64, countMax));
    }

    public String itemId() { return itemId; }
    public Material material() { return material; }
    public int weight() { return weight; }
    public int countMin() { return countMin; }
    public int countMax() { return countMax; }
    public boolean isCustom() { return itemId != null; }

    /** Quantité tirée pour cette entrée (uniforme dans [countMin, countMax]). */
    public int rollCount(RandomGenerator rng) {
        if (countMax <= countMin) return countMin;
        return countMin + rng.nextInt(countMax - countMin + 1);
    }
}
