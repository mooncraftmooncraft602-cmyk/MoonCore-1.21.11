package com.mooncore.modules.loot;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * Définition data-driven d'une <b>table de loot générique</b> (item «&nbsp;Reste à construire&nbsp;» du master
 * brain §2). Une table regroupe plusieurs {@link LootPool} indépendants ; l'évaluer concatène les tirages de
 * chaque pool. Réutilisable par drops de blocs/cultures, butin de boss, conteneurs, pêche, etc.
 * <p>
 * Persistée en YAML ({@code loot/<id>.yml}) et, via le store universel, requêtable en SQL (type de contenu
 * {@code "loot"} — branchement Store/Listener/IA en passes ultérieures). Le tirage est <b>pur</b> (RNG injecté).
 */
public final class LootTableDef {

    private final String id;
    private String displayName;
    private final List<LootPool> pools = new ArrayList<>();

    public LootTableDef(String id) {
        this.id = id.toLowerCase(Locale.ROOT);
        this.displayName = "<white>" + id + "</white>";
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public void setDisplayName(String n) { if (n != null && !n.isBlank()) this.displayName = n; }

    public List<LootPool> pools() { return pools; }
    public LootTableDef add(LootPool p) { if (p != null) pools.add(p); return this; }

    /** Évalue tous les pools et concatène leurs résultats. */
    public List<LootResult> roll(RandomGenerator rng) {
        List<LootResult> out = new ArrayList<>();
        for (LootPool p : pools) out.addAll(p.roll(rng));
        return out;
    }

    /** Ids des tables référencées par les entrées de cette table (imbrication), sans doublon. Pur. */
    public java.util.Set<String> referencedTables() {
        java.util.LinkedHashSet<String> refs = new java.util.LinkedHashSet<>();
        for (LootPool p : pools) {
            for (LootEntry e : p.entries()) {
                if (e.isReference()) refs.add(e.tableRef());
            }
        }
        return refs;
    }

    public void save(ConfigurationSection s) {
        s.set("display-name", displayName);
        for (int pi = 0; pi < pools.size(); pi++) {
            LootPool pool = pools.get(pi);
            ConfigurationSection ps = s.createSection("pools." + pi);
            ps.set("rolls.min", pool.rollsMin());
            ps.set("rolls.max", pool.rollsMax());
            List<LootEntry> es = pool.entries();
            for (int ei = 0; ei < es.size(); ei++) {
                LootEntry e = es.get(ei);
                ConfigurationSection ess = ps.createSection("entries." + ei);
                ess.set("item", e.itemId());
                ess.set("material", e.material().name());
                ess.set("weight", e.weight());
                ess.set("count.min", e.countMin());
                ess.set("count.max", e.countMax());
                ess.set("loot-table", e.tableRef());
            }
        }
    }

    public static LootTableDef load(String id, ConfigurationSection s) {
        LootTableDef d = new LootTableDef(id);
        d.displayName = s.getString("display-name", d.displayName);
        ConfigurationSection poolsSec = s.getConfigurationSection("pools");
        if (poolsSec != null) {
            // Clés numériques triées pour préserver l'ordre des pools.
            List<String> poolKeys = new ArrayList<>(poolsSec.getKeys(false));
            poolKeys.sort(LootTableDef::compareNumericKey);
            for (String pk : poolKeys) {
                ConfigurationSection ps = poolsSec.getConfigurationSection(pk);
                if (ps == null) continue;
                LootPool pool = new LootPool(ps.getInt("rolls.min", 1), ps.getInt("rolls.max", 1));
                ConfigurationSection entriesSec = ps.getConfigurationSection("entries");
                if (entriesSec != null) {
                    List<String> entKeys = new ArrayList<>(entriesSec.getKeys(false));
                    entKeys.sort(LootTableDef::compareNumericKey);
                    for (String ek : entKeys) {
                        ConfigurationSection es = entriesSec.getConfigurationSection(ek);
                        if (es == null) continue;
                        Material mat = matchMaterial(es.getString("material"));
                        pool.add(new LootEntry(
                                es.getString("item", null),
                                mat == null ? Material.AIR : mat,
                                es.getInt("weight", 1),
                                es.getInt("count.min", 1),
                                es.getInt("count.max", 1),
                                es.getString("loot-table", null)));
                    }
                }
                d.pools.add(pool);
            }
        }
        return d;
    }

    private static int compareNumericKey(String a, String b) {
        try { return Integer.compare(Integer.parseInt(a), Integer.parseInt(b)); }
        catch (NumberFormatException ex) { return a.compareTo(b); }
    }

    private static Material matchMaterial(String name) {
        return name == null ? null : Material.matchMaterial(name.toUpperCase(Locale.ROOT));
    }
}
