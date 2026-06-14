package com.mooncore.modules.loot;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aplatissement pur des tables imbriquées par {@link LootResolver} : développe les références, bloque les
 * cycles (path-set) et autorise les diamants. {@code roller} injecté (déterministe), sans serveur.
 */
class LootResolverTest {

    private static LootResult item(Material m) { return new LootResult(null, m, 1, null); }
    private static LootResult ref(String table, int count) { return new LootResult(null, Material.AIR, count, table); }

    @Test
    void expandsNestedReferences() {
        // dungeon -> [DIRT, ref(rare ×2)] ; rare -> [DIAMOND]
        Map<String, List<LootResult>> tables = Map.of(
                "dungeon", List.of(item(Material.DIRT), ref("rare", 2)),
                "rare", List.of(item(Material.DIAMOND)));
        List<LootResult> out = LootResolver.flatten("dungeon", tables::get);
        // DIRT + 2× DIAMOND (rare tirée 2 fois), aucune référence restante.
        assertEquals(3, out.size());
        long diamonds = out.stream().filter(r -> r.material() == Material.DIAMOND).count();
        assertEquals(2, diamonds);
        assertTrue(out.stream().noneMatch(LootResult::isReference));
    }

    @Test
    void breaksCyclesWithoutInfiniteLoop() {
        // a -> ref(b) ; b -> ref(a) : cycle. Doit terminer et ne produire aucun item concret.
        Map<String, List<LootResult>> tables = Map.of(
                "a", List.of(ref("b", 1)),
                "b", List.of(ref("a", 1)));
        List<LootResult> out = LootResolver.flatten("a", tables::get);
        assertTrue(out.isEmpty());
    }

    @Test
    void selfReferenceTerminates() {
        Map<String, List<LootResult>> tables = Map.of(
                "loop", List.of(item(Material.STONE), ref("loop", 5)));
        List<LootResult> out = LootResolver.flatten("loop", tables::get);
        assertEquals(1, out.size());                    // le STONE une fois, la self-ref coupée
        assertEquals(Material.STONE, out.get(0).material());
    }

    @Test
    void diamondAllowsSameTableViaDifferentPaths() {
        // top -> [ref(b), ref(c)] ; b -> ref(shared) ; c -> ref(shared) ; shared -> [GOLD_INGOT]
        Map<String, List<LootResult>> tables = Map.of(
                "top", List.of(ref("b", 1), ref("c", 1)),
                "b", List.of(ref("shared", 1)),
                "c", List.of(ref("shared", 1)),
                "shared", List.of(item(Material.GOLD_INGOT)));
        List<LootResult> out = LootResolver.flatten("top", tables::get);
        assertEquals(2, out.size());                    // shared résolue via b ET via c (diamant autorisé)
        assertTrue(out.stream().allMatch(r -> r.material() == Material.GOLD_INGOT));
    }

    @Test
    void unknownTableYieldsNothing() {
        assertTrue(LootResolver.flatten("absente", t -> null).isEmpty());
        assertTrue(LootResolver.flatten(null, t -> List.of(item(Material.DIRT))).isEmpty());
    }
}
