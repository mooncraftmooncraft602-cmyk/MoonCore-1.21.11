package com.mooncore.modules.customblock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bijection index ↔ (instrument, note, powered) du cœur pur des blocs custom (sans serveur Bukkit).
 * {@link BlockStateMap} référence l'enum {@code Instrument} (charge le Registry) et n'est donc pas
 * testable headless ; toute l'arithmétique qui pilote l'attribution d'état vit dans {@link BlockStateCoord}.
 */
class BlockStateCoordTest {

    @Test
    void capacityIs800() {
        assertEquals(800, BlockStateCoord.capacity());
    }

    @Test
    void roundTripOverFullRange() {
        // Toute la plage valide doit être une bijection parfaite : fromIndex(i).toIndex() == i.
        for (int i = 0; i < BlockStateCoord.capacity(); i++) {
            BlockStateCoord c = BlockStateCoord.fromIndex(i);
            assertEquals(i, c.toIndex(), "round-trip cassé à i=" + i);
            assertTrue(c.instrumentIndex() >= 0 && c.instrumentIndex() < BlockStateCoord.INSTRUMENT_COUNT);
            assertTrue(c.note() >= 0 && c.note() < BlockStateCoord.NOTES);
        }
    }

    @Test
    void distinctIndicesGiveDistinctStates() {
        // Aucune collision sur la plage valide : 800 index → 800 états distincts.
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < BlockStateCoord.capacity(); i++) {
            BlockStateCoord c = BlockStateCoord.fromIndex(i);
            String key = c.instrumentIndex() + ":" + c.note() + ":" + c.powered();
            assertTrue(seen.add(key), "collision d'état à i=" + i);
        }
        assertEquals(800, seen.size());
    }

    @Test
    void outOfRangeIndicesWrapIntoRange() {
        // fromIndex normalise tout index dans [0, capacity()) — c'est exactement le repli que le
        // garde-fou capacité doit empêcher d'atteindre en production (l'état -1 retomberait sur 799).
        assertEquals(BlockStateCoord.fromIndex(799), BlockStateCoord.fromIndex(-1));
        assertEquals(BlockStateCoord.fromIndex(0), BlockStateCoord.fromIndex(800));
        assertEquals(BlockStateCoord.fromIndex(1), BlockStateCoord.fromIndex(801));
    }

    @Test
    void firstAndLastStates() {
        BlockStateCoord first = BlockStateCoord.fromIndex(0);
        assertEquals(0, first.instrumentIndex());
        assertEquals(0, first.note());
        assertEquals(false, first.powered());

        BlockStateCoord last = BlockStateCoord.fromIndex(799);
        assertEquals(15, last.instrumentIndex());
        assertEquals(24, last.note());
        assertEquals(true, last.powered());
    }
}
