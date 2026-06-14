package com.mooncore.modules.crop;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/**
 * Définition data-driven d'une culture/plante custom à cycle de ticks (Étape C). Une culture pousse
 * par <b>étapes</b> ({@code stages}), chaque étape durant {@code growthTicks}, sous réserve de
 * conditions (lumière minimale, bloc support, hydratation). À maturité, la récolte produit un drop
 * (item custom ou Material) et, si {@code replantable}, repart à l'étape 0.
 * <p>
 * Persistée en YAML ({@code crops/<id>.yml}) et, via le store universel, requêtable en SQL
 * (type de contenu {@code "crop"}). Le rendu des étapes est traité à l'Étape C2.
 */
public final class CropDef {

    private final String id;
    private String displayName;

    // Semis.
    private Material seed = Material.WHEAT_SEEDS;   // item planté (clic droit pour semer)
    private String seedCustomId = null;            // si non null : graine = item custom MoonCore

    // Croissance.
    private int stages = 4;                         // nombre d'étapes de croissance (>= 1)
    private int growthTicks = 600;                  // ticks par étape (20 t/s → 30 s)
    private int minLight = 9;                       // niveau de lumière minimal pour pousser
    private Material placeOn = Material.FARMLAND;   // bloc support requis sous la culture
    private boolean requiresWater = true;          // exige une terre labourée hydratée

    // Rendu (base ; clés par étape dérivées : <modelKey>_stage<N>).
    private String modelKey;

    // Récolte.
    private String dropItemId = null;              // récolte = item custom (prioritaire)
    private Material dropMaterial = Material.WHEAT; // sinon Material vanilla
    private int dropMin = 1;
    private int dropMax = 2;
    private int seedReturnMin = 0;                  // graines rendues à la récolte
    private int seedReturnMax = 1;
    private boolean replantable = true;            // remet l'étape 0 au lieu de casser
    private String lootTableId = null;             // si non null : récolte = tirage de cette table de loot (prioritaire sur drop fixe)

    public CropDef(String id) {
        this.id = id.toLowerCase(Locale.ROOT);
        this.displayName = "<white>" + id + "</white>";
        this.modelKey = this.id;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public void setDisplayName(String n) { this.displayName = n; }

    public Material seed() { return seed; }
    public void setSeed(Material m) { if (m != null) this.seed = m; }
    public String seedCustomId() { return seedCustomId; }
    public void setSeedCustomId(String id) {
        this.seedCustomId = (id == null || id.isBlank()) ? null : id.toLowerCase(Locale.ROOT).trim();
    }

    public int stages() { return stages; }
    public void setStages(int s) { this.stages = Math.max(1, Math.min(16, s)); }
    public int growthTicks() { return growthTicks; }
    public void setGrowthTicks(int t) { this.growthTicks = Math.max(1, Math.min(72_000, t)); }
    public int minLight() { return minLight; }
    public void setMinLight(int l) { this.minLight = Math.max(0, Math.min(15, l)); }
    public Material placeOn() { return placeOn; }
    public void setPlaceOn(Material m) { if (m != null) this.placeOn = m; }
    public boolean requiresWater() { return requiresWater; }
    public void setRequiresWater(boolean b) { this.requiresWater = b; }

    public String modelKey() { return modelKey; }
    public void setModelKey(String k) { this.modelKey = (k == null || k.isBlank()) ? id : k.toLowerCase(Locale.ROOT); }
    /** Clé de texture/modèle de l'étape donnée (0-based). */
    public String stageModelKey(int stage) {
        int s = Math.max(0, Math.min(stages - 1, stage));
        return modelKey + "_stage" + s;
    }

    public String dropItemId() { return dropItemId; }
    public void setDropItemId(String id) {
        this.dropItemId = (id == null || id.isBlank()) ? null : id.toLowerCase(Locale.ROOT).trim();
    }
    public Material dropMaterial() { return dropMaterial; }
    public void setDropMaterial(Material m) { if (m != null) this.dropMaterial = m; }
    public int dropMin() { return dropMin; }
    public int dropMax() { return dropMax; }
    public void setDropRange(int min, int max) {
        this.dropMin = Math.max(0, Math.min(64, min));
        this.dropMax = Math.max(this.dropMin, Math.min(64, max));
    }
    public int seedReturnMin() { return seedReturnMin; }
    public int seedReturnMax() { return seedReturnMax; }
    public void setSeedReturnRange(int min, int max) {
        this.seedReturnMin = Math.max(0, Math.min(16, min));
        this.seedReturnMax = Math.max(this.seedReturnMin, Math.min(16, max));
    }
    public boolean replantable() { return replantable; }
    public void setReplantable(boolean b) { this.replantable = b; }

    public String lootTableId() { return lootTableId; }
    public void setLootTableId(String id) {
        this.lootTableId = (id == null || id.isBlank()) ? null : id.toLowerCase(Locale.ROOT).trim();
    }
    /** True si la récolte doit tirer une table de loot plutôt que le drop fixe. */
    public boolean usesLootTable() { return lootTableId != null; }

    public void save(ConfigurationSection s) {
        s.set("display-name", displayName);
        s.set("seed", seed.name());
        s.set("seed-custom", seedCustomId);
        s.set("stages", stages);
        s.set("growth-ticks", growthTicks);
        s.set("min-light", minLight);
        s.set("place-on", placeOn.name());
        s.set("requires-water", requiresWater);
        s.set("model-key", modelKey);
        s.set("drop.item", dropItemId);
        s.set("drop.material", dropMaterial.name());
        s.set("drop.min", dropMin);
        s.set("drop.max", dropMax);
        s.set("seed-return.min", seedReturnMin);
        s.set("seed-return.max", seedReturnMax);
        s.set("replantable", replantable);
        s.set("loot-table", lootTableId);
    }

    public static CropDef load(String id, ConfigurationSection s) {
        CropDef d = new CropDef(id);
        d.displayName = s.getString("display-name", d.displayName);
        Material seedMat = matchMaterial(s.getString("seed"));
        if (seedMat != null) d.seed = seedMat;
        d.setSeedCustomId(s.getString("seed-custom", null));
        d.setStages(s.getInt("stages", 4));
        d.setGrowthTicks(s.getInt("growth-ticks", 600));
        d.setMinLight(s.getInt("min-light", 9));
        Material on = matchMaterial(s.getString("place-on"));
        if (on != null) d.placeOn = on;
        d.requiresWater = s.getBoolean("requires-water", true);
        d.setModelKey(s.getString("model-key", d.id));
        d.setDropItemId(s.getString("drop.item", null));
        Material drop = matchMaterial(s.getString("drop.material"));
        if (drop != null) d.dropMaterial = drop;
        d.setDropRange(s.getInt("drop.min", 1), s.getInt("drop.max", 2));
        d.setSeedReturnRange(s.getInt("seed-return.min", 0), s.getInt("seed-return.max", 1));
        d.replantable = s.getBoolean("replantable", true);
        d.setLootTableId(s.getString("loot-table", null));
        return d;
    }

    private static Material matchMaterial(String name) {
        return name == null ? null : Material.matchMaterial(name.toUpperCase(Locale.ROOT));
    }
}
