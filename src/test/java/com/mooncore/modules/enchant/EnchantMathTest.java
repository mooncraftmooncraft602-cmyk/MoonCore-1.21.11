package com.mooncore.modules.enchant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnchantMathTest {

    @Test
    void vampScalesWithLevel() {
        assertEquals(1.0, EnchantMath.vampHeal(1, 10), 1e-9);  // 10%
        assertEquals(3.0, EnchantMath.vampHeal(3, 10), 1e-9);  // 30%
    }

    @Test
    void executeOnlyBelowThreshold() {
        assertEquals(0, EnchantMath.executeBonus(3, 0.30, 10), 1e-9);  // au-dessus de 25%
        assertEquals(15, EnchantMath.executeBonus(3, 0.20, 10), 1e-9); // 50%*3*10
    }

    @Test
    void critChanceCapped() {
        assertEquals(0.5, EnchantMath.critChance(5), 1e-9);
        assertTrue(EnchantMath.critChance(20) <= 0.90);
    }

    @Test
    void resilienceCapped() {
        assertEquals(0.30, EnchantMath.resilienceReduction(5), 1e-9);
        assertTrue(EnchantMath.resilienceReduction(20) <= 0.60);
    }

    @Test
    void applyReductionReducesDamage() {
        assertEquals(7.0, EnchantMath.applyReduction(10, 0.30), 1e-9);
        assertEquals(10.0, EnchantMath.applyReduction(10, 0), 1e-9);
    }

    @Test
    void treasureAndFortuneScale() {
        assertEquals(150.0, EnchantMath.treasureMoney(3), 1e-9);
        assertEquals(3, EnchantMath.superFortuneExtra(3));
    }

    @Test
    void berserkOnlyBelowAttackerThreshold() {
        assertEquals(0, EnchantMath.berserkBonus(2, 0.50, 10), 1e-9);   // au-dessus de 40% PV
        assertEquals(3.0, EnchantMath.berserkBonus(2, 0.30, 10), 1e-9); // 15%*2*10
    }

    @Test
    void dropChancesAreCappedAtOne() {
        assertEquals(0.20, EnchantMath.prospectingChance(2), 1e-9);
        assertTrue(EnchantMath.prospectingChance(50) <= 1.0);          // jamais > 100%
        assertEquals(0.10, EnchantMath.treasureChance(5), 1e-9);
        assertTrue(EnchantMath.treasureChance(999) <= 1.0);
    }

    @Test
    void applyReductionClampsFractionOutOfRange() {
        assertEquals(0.0, EnchantMath.applyReduction(10, 1.5), 1e-9);   // >1 clampé à 1 → 0 dégât
        assertEquals(10.0, EnchantMath.applyReduction(10, -0.5), 1e-9); // <0 clampé à 0 → dégâts intacts
    }
}
