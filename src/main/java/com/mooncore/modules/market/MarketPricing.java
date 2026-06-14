package com.mooncore.modules.market;

/**
 * Moteur de prix <b>pur</b> du marché dynamique (aucune dépendance serveur → testable). Modélise une
 * économie offre/demande réaliste :
 * <ul>
 *   <li><b>Rareté → prix</b> : le prix spot d'une unité dépend du stock relatif à l'équilibre.
 *       {@code prix = base · (équilibre / stock)^élasticité}, borné. Stock bas ⇒ prix haut, et inversement.</li>
 *   <li><b>Tarification marginale</b> : acheter retire du stock (renchérit l'unité suivante) ; vendre en
 *       ajoute (fait baisser le prix). Un gros ordre coûte/rapporte progressivement moins — comme un vrai carnet.</li>
 *   <li><b>Spread</b> : le prix de vente = prix d'achat · marge (&lt; 1), comme l'écart bid/ask qui empêche
 *       l'arbitrage instantané.</li>
 *   <li><b>Production / mean-reversion</b> : à chaque tick, le stock se rapproche de l'équilibre de
 *       {@code production} unités — la production réapprovisionne après un achat massif (le prix redescend),
 *       la consommation résorbe l'excédent après un dumping (le prix remonte). Le prix tend vers sa base.</li>
 * </ul>
 */
public final class MarketPricing {

    private MarketPricing() {}

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    /**
     * Prix spot d'<b>une</b> unité pour un stock donné. {@code minFactor}/{@code maxFactor} bornent le prix
     * entre {@code base·minFactor} et {@code base·maxFactor} (évite prix nuls/infinis quand le stock est extrême).
     */
    public static double spotPrice(double basePrice, double equilibrium, double stock,
                                   double elasticity, double minFactor, double maxFactor) {
        if (basePrice <= 0) return 0.0;
        double eq = Math.max(1e-6, equilibrium);
        double st = Math.max(1e-6, stock);                 // plancher : jamais de division par 0
        double raw = basePrice * Math.pow(eq / st, Math.max(0.0, elasticity));
        return clamp(raw, basePrice * minFactor, basePrice * maxFactor);
    }

    /**
     * Coût total d'achat de {@code qty} unités à partir du {@code stock} courant — tarification marginale :
     * chaque unité achetée abaisse le stock d'une unité, donc renchérit la suivante. {@code qty ≤ 0} ⇒ 0.
     */
    public static double buyCost(double basePrice, double equilibrium, double stock,
                                 double elasticity, double minFactor, double maxFactor, int qty) {
        double total = 0.0;
        double st = stock;
        for (int i = 0; i < qty; i++) {
            total += spotPrice(basePrice, equilibrium, st, elasticity, minFactor, maxFactor);
            st = Math.max(1.0, st - 1.0);                  // l'achat retire du stock (plancher à 1)
        }
        return total;
    }

    /**
     * Revenu total de vente de {@code qty} unités à partir du {@code stock} courant — tarification marginale
     * (chaque unité vendue augmente le stock, donc fait baisser la suivante) et application du {@code sellMargin}
     * (écart bid/ask, dans [0,1]). {@code qty ≤ 0} ⇒ 0.
     */
    public static double sellRevenue(double basePrice, double equilibrium, double stock,
                                     double elasticity, double minFactor, double maxFactor,
                                     double sellMargin, int qty) {
        double margin = clamp(sellMargin, 0.0, 1.0);
        double total = 0.0;
        double st = stock;
        for (int i = 0; i < qty; i++) {
            total += spotPrice(basePrice, equilibrium, st, elasticity, minFactor, maxFactor) * margin;
            st += 1.0;                                     // la vente ajoute du stock
        }
        return total;
    }

    /**
     * Stock après un tick de production/consommation : se rapproche de l'équilibre de {@code production} unités
     * (réapprovisionnement sous l'équilibre, résorption au-dessus). {@code production ≤ 0} ⇒ stock inchangé
     * (item à stock figé). Ne dépasse jamais l'équilibre (pas d'oscillation).
     */
    public static double applyProduction(double stock, double equilibrium, double production) {
        if (production <= 0) return stock;
        if (stock < equilibrium) return Math.min(equilibrium, stock + production);
        if (stock > equilibrium) return Math.max(equilibrium, stock - production);
        return stock;
    }

    /**
     * Indice de marché : prix spot courant rapporté au prix de base ({@code 1.0} = au pair, {@code 1.5} = +50%).
     * Pour l'affichage de la tendance.
     */
    public static double marketIndex(double basePrice, double equilibrium, double stock,
                                     double elasticity, double minFactor, double maxFactor) {
        if (basePrice <= 0) return 1.0;
        return spotPrice(basePrice, equilibrium, stock, elasticity, minFactor, maxFactor) / basePrice;
    }
}
