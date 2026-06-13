package com.mooncore.modules.crop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Clés de localisation/chunk des emplantations (Étape C). Pures (record helpers, pas de DB).
 * Le tick de croissance indexe par {@code chunkKey} ; ces clés doivent être cohérentes avec
 * la conversion bloc→chunk ({@code coord >> 4}, y compris en négatif).
 */
class CropPlacementKeyTest {

    private CropPlacementStore.Placement at(int x, int y, int z) {
        return new CropPlacementStore.Placement("world", x, y, z, "wheat", 0, 0L);
    }

    @Test
    void locKeyMatchesStatic() {
        CropPlacementStore.Placement p = at(33, 70, -17);
        assertEquals("world:33:70:-17", p.locKey());
        assertEquals(p.locKey(), CropPlacementStore.Placement.locKey("world", 33, 70, -17));
    }

    @Test
    void chunkKeyUsesBlockToChunkShift() {
        // 33 >> 4 = 2 ; -17 >> 4 = -2 (division entière vers le bas)
        CropPlacementStore.Placement p = at(33, 70, -17);
        assertEquals("world:2:-2", p.chunkKey());
        assertEquals(p.chunkKey(), CropPlacementStore.Placement.chunkKey("world", 2, -2));
    }

    @Test
    void sameChunkVsDifferentChunk() {
        // (16..31) sont dans le chunk 1 ; (0..15) dans le chunk 0.
        assertEquals(at(16, 64, 16).chunkKey(), at(31, 64, 31).chunkKey());
        assertNotEquals(at(15, 64, 15).chunkKey(), at(16, 64, 16).chunkKey());
    }
}
