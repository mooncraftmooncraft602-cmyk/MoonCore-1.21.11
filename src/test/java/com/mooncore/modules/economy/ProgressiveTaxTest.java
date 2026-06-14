package com.mooncore.modules.economy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Taxe progressive par tranches marginales de {@link ProgressiveTax} (argent réel : une régression
 * mistaxerait les joueurs). Vérifie la marginalité, les bornes exactes, le tri d'entrée et le repli (0,0).
 * Pur, sans serveur.
 */
class ProgressiveTaxTest {

    private static ProgressiveTax tax() {
        // 0% sous 1000, 10% de 1000 à 10000, 20% au-delà.
        return new ProgressiveTax(List.of(
                new ProgressiveTax.Bracket(1000, 0.10),
                new ProgressiveTax.Bracket(10000, 0.20)));
    }

    @Test
    void marginalAccumulationAcrossBrackets() {
        ProgressiveTax t = tax();
        assertEquals(0.0, t.computeTax(500), 1e-9);       // sous le premier seuil
        assertEquals(0.0, t.computeTax(1000), 1e-9);      // borne exacte : rien au-dessus de 1000
        assertEquals(400.0, t.computeTax(5000), 1e-9);    // 4000 × 10%
        assertEquals(900.0, t.computeTax(10000), 1e-9);   // 9000 × 10%
        assertEquals(1900.0, t.computeTax(15000), 1e-9);  // 9000×10% + 5000×20%
    }

    @Test
    void zeroOrNegativeWealthIsUntaxed() {
        ProgressiveTax t = tax();
        assertEquals(0.0, t.computeTax(0), 1e-9);
        assertEquals(0.0, t.computeTax(-500), 1e-9);
        assertEquals(0.0, t.effectiveRate(0), 1e-9);
    }

    @Test
    void effectiveRateIsTaxOverWealth() {
        ProgressiveTax t = tax();
        assertEquals(400.0 / 5000.0, t.effectiveRate(5000), 1e-9);  // 8%
        assertEquals(1900.0 / 15000.0, t.effectiveRate(15000), 1e-9);
    }

    @Test
    void prependsZeroBracketAndSortsUnorderedInput() {
        // Entrée désordonnée et sans tranche à 0 : doit être triée et complétée par (0,0).
        ProgressiveTax t = new ProgressiveTax(List.of(
                new ProgressiveTax.Bracket(10000, 0.20),
                new ProgressiveTax.Bracket(1000, 0.10)));
        assertEquals(0.0, t.brackets().get(0).from(), 1e-9);   // tranche (0,0) en tête
        assertEquals(1900.0, t.computeTax(15000), 1e-9);       // même résultat qu'avec entrée ordonnée
    }
}
