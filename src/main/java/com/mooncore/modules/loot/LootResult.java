package com.mooncore.modules.loot;

import org.bukkit.Material;

/**
 * Résultat concret d'un tirage de loot : soit un item custom MoonCore ({@code itemId} non null), soit un
 * {@link Material} vanilla, en quantité {@code count}. Émis par {@link LootPool#roll} / {@link LootTableDef#roll}.
 * Couche matérialisation (→ {@code ItemStack}) traitée à l'intégration serveur (passe ultérieure).
 */
public record LootResult(String itemId, Material material, int count) {

    public boolean isCustom() { return itemId != null; }

    public static LootResult of(LootEntry entry, int count) {
        return new LootResult(entry.itemId(), entry.material(), count);
    }
}
