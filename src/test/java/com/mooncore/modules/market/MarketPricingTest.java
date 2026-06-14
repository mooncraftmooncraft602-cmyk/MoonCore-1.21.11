package com.mooncore.modules.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Moteur de prix pur du marché : rareté→prix, tarification marginale achat/vente, spread, production
 * (mean-reversion), bornes. Déterministe, sans serveur.
 */
class MarketPricingTest {

    // Réglages de référence : base 10, équilibre 100, élasticité 1, bornes ×0.1..×10.
    private static final double BASE = 10.0, EQ = 100.0, ELA = 1.0, MIN = 0.1, MAX = 10.0;

    private static double spot(double stock) {
        return MarketPricing.spotPrice(BASE, EQ, stock, ELA, MIN, MAX);
    }

    @Test
    void priceEqualsBaseAtEquilibrium() {
        assertEquals(10.0, spot(100), 1e-9);
    }

    @Test
    void scarcityRaisesPriceAbundanceLowersIt() {
        assertEquals(20.0, spot(50), 1e-9);    // stock moitié → prix ×2 (élasticité 1)
        assertEquals(5.0, spot(200), 1e-9);    // stock double → prix ½
        assertTrue(spot(10) > spot(50));       // monotone décroissant en stock
    }

    @Test
    void priceIsBounded() {
        assertEquals(100.0, spot(0.0001), 1e-6);   // plafond base×maxFactor (10×10)
        assertEquals(1.0, spot(1_000_000), 1e-6);  // plancher base×minFactor (10×0.1)
        assertEquals(0.0, MarketPricing.spotPrice(0, EQ, 50, ELA, MIN, MAX), 1e-9); // base 0 → gratuit
    }

    @Test
    void elasticityControlsSensitivity() {
        // À stock moitié : élasticité 0 → prix inchangé ; élasticité 2 → ×4.
        assertEquals(10.0, MarketPricing.spotPrice(BASE, EQ, 50, 0.0, MIN, MAX), 1e-9);
        assertEquals(40.0, MarketPricing.spotPrice(BASE, EQ, 50, 2.0, MIN, MAX), 1e-9);
    }

    @Test
    void buyCostIsMarginalAndRisesWithQuantity() {
        // Acheter retire du stock → chaque unité plus chère que la précédente.
        double oneUnit = MarketPricing.buyCost(BASE, EQ, 100, ELA, MIN, MAX, 1);
        assertEquals(10.0, oneUnit, 1e-9);                      // 1ʳᵉ unité au prix d'équilibre
        double tenUnits = MarketPricing.buyCost(BASE, EQ, 100, ELA, MIN, MAX, 10);
        assertTrue(tenUnits > 10 * oneUnit);                   // marginal > 10× la première
        assertEquals(0.0, MarketPricing.buyCost(BASE, EQ, 100, ELA, MIN, MAX, 0), 1e-9);
    }

    @Test
    void sellRevenueAppliesSpreadAndIsMarginal() {
        // Vente au prix d'équilibre avec marge 0.7 → 7 la première unité ; chaque vente fait baisser la suivante.
        double oneUnit = MarketPricing.sellRevenue(BASE, EQ, 100, ELA, MIN, MAX, 0.7, 1);
        assertEquals(7.0, oneUnit, 1e-9);
        double tenUnits = MarketPricing.sellRevenue(BASE, EQ, 100, ELA, MIN, MAX, 0.7, 10);
        assertTrue(tenUnits < 10 * oneUnit);                   // vendre fait chuter le prix
        // La vente rapporte toujours moins que l'achat ne coûte au même stock (spread anti-arbitrage).
        assertTrue(oneUnit < MarketPricing.buyCost(BASE, EQ, 100, ELA, MIN, MAX, 1));
    }

    @Test
    void productionMeanRevertsTowardEquilibrium() {
        // Sous l'équilibre : se réapprovisionne sans dépasser.
        assertEquals(60.0, MarketPricing.applyProduction(50, 100, 10), 1e-9);
        assertEquals(100.0, MarketPricing.applyProduction(95, 100, 10), 1e-9);  // clamp à l'équilibre
        // Au-dessus : l'excédent se résorbe sans descendre sous l'équilibre.
        assertEquals(140.0, MarketPricing.applyProduction(150, 100, 10), 1e-9);
        assertEquals(100.0, MarketPricing.applyProduction(105, 100, 10), 1e-9);
        // Production nulle → stock figé.
        assertEquals(50.0, MarketPricing.applyProduction(50, 100, 0), 1e-9);
    }

    @Test
    void marketIndexReflectsDeviationFromBase() {
        assertEquals(1.0, MarketPricing.marketIndex(BASE, EQ, 100, ELA, MIN, MAX), 1e-9);
        assertEquals(2.0, MarketPricing.marketIndex(BASE, EQ, 50, ELA, MIN, MAX), 1e-9);
    }
}
