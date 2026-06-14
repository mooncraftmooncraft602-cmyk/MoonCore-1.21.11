package com.mooncore.modules.loot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Aplatissement pur des tables de loot imbriquées : développe les {@link LootResult#isReference() références}
 * vers d'autres tables en résultats concrets, avec une garde <b>anti-cycle</b> par ensemble de chemin (une
 * table déjà en cours de résolution n'est pas ré-entrée — bloque les cycles tout en autorisant les diamants
 * A→B→D / A→C→D). Le {@code roller} (id de table → résultats d'un tirage) est injecté, donc cette logique est
 * déterministe et testable sans serveur, indépendamment du tirage aléatoire et de la matérialisation.
 */
public final class LootResolver {

    /** Profondeur max de sécurité (garde secondaire au cas où le {@code roller} produirait des chaînes énormes). */
    public static final int DEFAULT_MAX_DEPTH = 16;

    private LootResolver() {}

    /** Résout {@code tableId} en résultats <b>concrets</b> (aucune référence restante). */
    public static List<LootResult> flatten(String tableId, Function<String, List<LootResult>> roller) {
        return flatten(tableId, roller, DEFAULT_MAX_DEPTH);
    }

    public static List<LootResult> flatten(String tableId, Function<String, List<LootResult>> roller, int maxDepth) {
        List<LootResult> out = new ArrayList<>();
        flattenInto(norm(tableId), roller, out, new HashSet<>(), 0, maxDepth);
        return out;
    }

    private static void flattenInto(String id, Function<String, List<LootResult>> roller, List<LootResult> out,
                                    Set<String> path, int depth, int maxDepth) {
        if (id == null || roller == null || depth >= maxDepth) return;
        if (!path.add(id)) return;                 // déjà dans le chemin → cycle, on coupe
        List<LootResult> rolled = roller.apply(id);
        if (rolled != null) {
            for (LootResult r : rolled) {
                if (r == null) continue;
                if (r.isReference()) {
                    int times = Math.max(0, r.count());
                    for (int i = 0; i < times; i++) flattenInto(norm(r.tableRef()), roller, out, path, depth + 1, maxDepth);
                } else if (r.count() > 0) {
                    out.add(r);
                }
            }
        }
        path.remove(id);                           // sortie du chemin : table réutilisable par un autre chemin (diamant)
    }

    private static String norm(String id) {
        return id == null ? null : id.toLowerCase(Locale.ROOT);
    }
}
