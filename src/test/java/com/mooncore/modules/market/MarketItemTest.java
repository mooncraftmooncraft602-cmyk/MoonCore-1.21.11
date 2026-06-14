package com.mooncore.modules.market;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MarketItem} : round-trip YAML (dont le <b>stock vivant</b>, persisté entre redémarrages), bornage
 * des setters et effet du stock sur le prix via le moteur. Pur ({@link MemoryConfiguration}, sans serveur).
 */
class MarketItemTest {

    @Test
    void roundTripPreservesStateIncludingStock() {
        MarketItem m = new MarketItem("diamond");
        m.setMaterial("DIAMOND");
        m.setDisplayName("Diamant");
        m.setBasePrice(250);
        m.setEquilibrium(200);
        m.setStock(137);                 // état vivant après transactions
        m.setElasticity(1.5);
        m.setProduction(1.0);
        m.setSellMargin(0.78);
        m.setPriceBounds(0.2, 8.0);

        MemoryConfiguration cfg = new MemoryConfiguration();
        m.save(cfg);
        MarketItem back = MarketItem.load("diamond", cfg);

        assertEquals("DIAMOND", back.material());
        assertNull(back.customId());
        assertEquals(250, back.basePrice(), 1e-9);
        assertEquals(200, back.equilibrium(), 1e-9);
        assertEquals(137, back.stock(), 1e-9);          // stock restauré
        assertEquals(1.5, back.elasticity(), 1e-9);
        assertEquals(1.0, back.production(), 1e-9);
        assertEquals(0.78, back.sellMargin(), 1e-9);
    }

    @Test
    void customItemRoundTrip() {
        MarketItem m = new MarketItem("ruby");
        m.setCustomId("Ruby_Gem");
        MemoryConfiguration cfg = new MemoryConfiguration();
        m.save(cfg);
        MarketItem back = MarketItem.load("ruby", cfg);
        assertTrue(back.isCustom());
        assertEquals("ruby_gem", back.customId());      // normalisé minuscule
    }

    @Test
    void buyingRaisesPriceSellingLowersIt() {
        MarketItem m = new MarketItem("iron");
        m.setBasePrice(10); m.setEquilibrium(100); m.setStock(100); m.setElasticity(1.0);
        double atPar = m.unitBuyPrice();
        m.addStock(-50);                  // simulate des achats → stock baisse
        assertTrue(m.unitBuyPrice() > atPar);
        m.addStock(+100);                 // simulate des ventes → stock monte au-dessus de l'équilibre
        assertTrue(m.unitBuyPrice() < atPar);
    }

    @Test
    void productionTickMeanRevertsStock() {
        MarketItem m = new MarketItem("wheat");
        m.setEquilibrium(1000); m.setStock(900); m.setProduction(40);
        m.tickProduction();
        assertEquals(940, m.stock(), 1e-9);     // se réapprovisionne vers l'équilibre
    }

    @Test
    void settersAreBoundedAndNanSafe() {
        MarketItem m = new MarketItem("x");
        m.setBasePrice(-5); assertEquals(0, m.basePrice(), 1e-9);
        m.setBasePrice(Double.NaN); assertEquals(0, m.basePrice(), 1e-9);
        m.setSellMargin(5); assertEquals(1.0, m.sellMargin(), 1e-9);     // marge ∈ [0,1]
        m.setElasticity(99); assertEquals(5.0, m.elasticity(), 1e-9);    // élasticité ≤ 5
        m.setEquilibrium(0); assertTrue(m.equilibrium() >= 1.0);
    }
}
