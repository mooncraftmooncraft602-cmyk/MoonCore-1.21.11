package com.mooncore.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Conversion secondes→ticks (20 t/s) utilisée par tous les timers. Pur. */
class SchedulersTicksTest {

    @Test
    void secondsToTicks() {
        assertEquals(0L, Schedulers.secondsToTicks(0));
        assertEquals(20L, Schedulers.secondsToTicks(1));
        assertEquals(1200L, Schedulers.secondsToTicks(60));   // 1 min
        assertEquals(72_000L, Schedulers.secondsToTicks(3600)); // 1 h
    }
}
