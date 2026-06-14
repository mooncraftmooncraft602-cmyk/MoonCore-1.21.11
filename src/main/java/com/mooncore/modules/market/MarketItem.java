package com.mooncore.modules.market;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/**
 * Une marchandise du marché dynamique : un {@link org.bukkit.Material} ou un item custom MoonCore
 * ({@code custom:<id>}), avec ses paramètres économiques et son <b>stock courant</b> (état mutable, persisté).
 * Le calcul de prix vit dans {@link MarketPricing} (pur) ; cette classe porte les données + le stock vivant.
 */
public final class MarketItem {

    private final String id;                 // clé unique (a-z0-9_-)
    private String material;                 // Material vanilla (si customId == null)
    private String customId;                 // id d'item custom MoonCore, ou null
    private String displayName;

    private double basePrice;                // prix de référence (au stock d'équilibre)
    private double equilibrium;              // stock auquel prix == basePrice
    private double stock;                    // stock courant (vivant, persisté)
    private double elasticity = 1.0;         // sensibilité du prix à la rareté
    private double production = 0.0;         // unités/tick de retour vers l'équilibre (capacité de production)
    private double sellMargin = 0.7;         // prix de vente = prix d'achat × marge (spread bid/ask)
    private double minFactor = 0.1;          // prix plancher = base × minFactor
    private double maxFactor = 12.0;         // prix plafond = base × maxFactor

    public MarketItem(String id) {
        this.id = id == null ? "item" : id.toLowerCase(Locale.ROOT);
        this.material = "STONE";
        this.displayName = this.id;
        this.basePrice = 10.0;
        this.equilibrium = 1000.0;
        this.stock = 1000.0;
    }

    public String id() { return id; }

    public String material() { return material; }
    public void setMaterial(String m) { this.material = (m == null || m.isBlank()) ? "STONE" : m.toUpperCase(Locale.ROOT); }

    public String customId() { return customId; }
    public void setCustomId(String c) { this.customId = (c == null || c.isBlank()) ? null : c.toLowerCase(Locale.ROOT); }
    public boolean isCustom() { return customId != null; }

    public String displayName() { return displayName; }
    public void setDisplayName(String n) { if (n != null && !n.isBlank()) this.displayName = n; }

    public double basePrice() { return basePrice; }
    public void setBasePrice(double p) { this.basePrice = Double.isNaN(p) ? 0.0 : Math.max(0.0, p); }

    public double equilibrium() { return equilibrium; }
    public void setEquilibrium(double e) { this.equilibrium = Double.isNaN(e) ? 1.0 : Math.max(1.0, e); }

    public double stock() { return stock; }
    public void setStock(double s) { this.stock = Double.isNaN(s) ? 0.0 : Math.max(0.0, s); }
    public void addStock(double delta) { setStock(stock + delta); }

    public double elasticity() { return elasticity; }
    public void setElasticity(double e) { this.elasticity = Double.isNaN(e) ? 1.0 : Math.max(0.0, Math.min(5.0, e)); }

    public double production() { return production; }
    public void setProduction(double p) { this.production = Double.isNaN(p) ? 0.0 : Math.max(0.0, p); }

    public double sellMargin() { return sellMargin; }
    public void setSellMargin(double m) { this.sellMargin = Double.isNaN(m) ? 0.0 : Math.max(0.0, Math.min(1.0, m)); }

    public double minFactor() { return minFactor; }
    public double maxFactor() { return maxFactor; }
    public void setPriceBounds(double minF, double maxF) {
        this.minFactor = Double.isNaN(minF) ? 0.1 : Math.max(0.0, minF);
        this.maxFactor = Double.isNaN(maxF) ? 12.0 : Math.max(this.minFactor, maxF);
    }

    // ---- Prix dérivés (délèguent au moteur pur) ----

    /** Prix d'achat unitaire courant. */
    public double unitBuyPrice() {
        return MarketPricing.spotPrice(basePrice, equilibrium, stock, elasticity, minFactor, maxFactor);
    }

    /** Prix de vente unitaire courant (achat × marge). */
    public double unitSellPrice() {
        return unitBuyPrice() * Math.max(0.0, Math.min(1.0, sellMargin));
    }

    /** Coût d'achat marginal de {@code qty} unités. */
    public double buyCost(int qty) {
        return MarketPricing.buyCost(basePrice, equilibrium, stock, elasticity, minFactor, maxFactor, qty);
    }

    /** Revenu marginal de vente de {@code qty} unités. */
    public double sellRevenue(int qty) {
        return MarketPricing.sellRevenue(basePrice, equilibrium, stock, elasticity, minFactor, maxFactor, sellMargin, qty);
    }

    /** Indice de marché courant (1.0 = au pair). */
    public double marketIndex() {
        return MarketPricing.marketIndex(basePrice, equilibrium, stock, elasticity, minFactor, maxFactor);
    }

    /** Avance d'un tick de production (mean-reversion du stock vers l'équilibre). */
    public void tickProduction() {
        setStock(MarketPricing.applyProduction(stock, equilibrium, production));
    }

    // ---- Persistance YAML ----

    public void save(ConfigurationSection s) {
        s.set("material", material);
        s.set("custom-id", customId);
        s.set("display-name", displayName);
        s.set("base-price", basePrice);
        s.set("equilibrium", equilibrium);
        s.set("stock", stock);
        s.set("elasticity", elasticity);
        s.set("production", production);
        s.set("sell-margin", sellMargin);
        s.set("min-factor", minFactor);
        s.set("max-factor", maxFactor);
    }

    public static MarketItem load(String id, ConfigurationSection s) {
        MarketItem m = new MarketItem(id);
        m.setMaterial(s.getString("material", "STONE"));
        m.setCustomId(s.getString("custom-id", null));
        m.setDisplayName(s.getString("display-name", id));
        m.setBasePrice(s.getDouble("base-price", 10.0));
        m.setEquilibrium(s.getDouble("equilibrium", 1000.0));
        m.setStock(s.getDouble("stock", s.getDouble("equilibrium", 1000.0)));
        m.setElasticity(s.getDouble("elasticity", 1.0));
        m.setProduction(s.getDouble("production", 0.0));
        m.setSellMargin(s.getDouble("sell-margin", 0.7));
        m.setPriceBounds(s.getDouble("min-factor", 0.1), s.getDouble("max-factor", 12.0));
        return m;
    }
}
