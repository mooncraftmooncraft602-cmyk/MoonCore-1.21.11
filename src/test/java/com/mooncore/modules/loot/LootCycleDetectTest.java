package com.mooncore.modules.loot;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Détection pure de cycles de références imbriquées ({@link LootManagerModule#detectReferenceCycle}). Le
 * graphe (id → ids référencés) est injecté → testable sans serveur. Les diamants (A→B→D, A→C→D) ne sont
 * pas des cycles ; l'auto-référence et les boucles le sont.
 */
class LootCycleDetectTest {

    private static Function<String, Set<String>> graph(Map<String, Set<String>> g) {
        return id -> g.getOrDefault(id, Set.of());
    }

    @Test
    void noCycleInAcyclicGraph() {
        // a → b → d ; a → c → d (diamant, pas de cycle).
        var g = graph(Map.of("a", Set.of("b", "c"), "b", Set.of("d"), "c", Set.of("d"), "d", Set.of()));
        assertTrue(LootManagerModule.detectReferenceCycle("a", g).isEmpty());
    }

    @Test
    void detectsSelfReference() {
        var g = graph(Map.of("a", Set.of("a")));
        assertEquals(List.of("a", "a"), LootManagerModule.detectReferenceCycle("a", g));
    }

    @Test
    void detectsTwoNodeCycle() {
        // a → b → a
        var g = graph(Map.of("a", Set.of("b"), "b", Set.of("a")));
        assertEquals(List.of("a", "b", "a"), LootManagerModule.detectReferenceCycle("a", g));
    }

    @Test
    void detectsDeeperCycleReachableFromStart() {
        // a → b → c → b (cycle b→c→b atteint depuis a)
        var g = graph(Map.of("a", Set.of("b"), "b", Set.of("c"), "c", Set.of("b")));
        assertEquals(List.of("b", "c", "b"), LootManagerModule.detectReferenceCycle("a", g));
    }

    @Test
    void caseInsensitiveAndNullSafe() {
        var g = graph(Map.of("a", Set.of("B"), "b", Set.of("A")));
        assertEquals(List.of("a", "b", "a"), LootManagerModule.detectReferenceCycle("A", g));
        assertTrue(LootManagerModule.detectReferenceCycle(null, g).isEmpty());
        assertTrue(LootManagerModule.detectReferenceCycle("a", null).isEmpty());
    }
}
