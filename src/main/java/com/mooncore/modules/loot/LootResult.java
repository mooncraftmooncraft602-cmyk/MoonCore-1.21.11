package com.mooncore.modules.loot;

import org.bukkit.Material;

/**
 * Résultat concret d'un tirage de loot : un item custom MoonCore ({@code itemId} non null), un
 * {@link Material} vanilla, ou une <b>référence</b> de table imbriquée ({@code tableRef} non null) à
 * résoudre par le manager. Quantité {@code count}. Émis par {@link LootPool#roll} / {@link LootTableDef#roll}.
 */
public record LootResult(String itemId, Material material, int count, String tableRef) {

    public boolean isCustom() { return itemId != null && tableRef == null; }
    /** True si ce résultat renvoie vers une autre table de loot (à résoudre récursivement). */
    public boolean isReference() { return tableRef != null; }

    public static LootResult of(LootEntry entry, int count) {
        return new LootResult(entry.itemId(), entry.material(), count, entry.tableRef());
    }
}
