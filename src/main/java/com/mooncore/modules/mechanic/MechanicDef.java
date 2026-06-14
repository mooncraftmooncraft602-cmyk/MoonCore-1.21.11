package com.mooncore.modules.mechanic;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Définition data-driven d'une <b>mécanique générique</b> trigger→action (item «&nbsp;mécaniques génériques&nbsp;»
 * du master brain §2). Quand son {@link TriggerType} se produit (filtré par {@link #matchKey}), elle exécute
 * sa liste d'{@link MechanicAction} en séquence, sous réserve d'un cooldown par joueur.
 * <p>
 * Persistée en YAML ({@code mechanics/<id>.yml}) et requêtable en SQL via le store universel (type
 * {@code "mechanic"}). L'exécution effective (listeners + dispatch) est branchée en passes ultérieures ;
 * ce modèle et son round-trip sont purs et testés sans serveur.
 */
public final class MechanicDef {

    private final String id;
    private String displayName;
    private TriggerType trigger = TriggerType.NONE;
    private String matchKey = null;          // bloc/item ciblé (Material ou custom:<id>) ; null = tout
    private int cooldownTicks = 0;           // anti-spam par joueur (0 = aucun)
    private int intervalTicks = 100;         // période si trigger == INTERVAL
    private double chance = 1.0;             // probabilité de déclenchement [0..1] (1 = toujours)
    private double cost = 0.0;               // coût en argent ; ne déclenche que si le joueur peut payer (0 = gratuit)
    private String permission = null;        // si non null : ne déclenche que si le joueur a cette permission
    private boolean enabled = true;
    private final List<MechanicAction> actions = new ArrayList<>();

    public MechanicDef(String id) {
        this.id = id.toLowerCase(Locale.ROOT);
        this.displayName = "<white>" + id + "</white>";
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public void setDisplayName(String n) { if (n != null && !n.isBlank()) this.displayName = n; }

    public TriggerType trigger() { return trigger; }
    public void setTrigger(TriggerType t) { this.trigger = t == null ? TriggerType.NONE : t; }

    public String matchKey() { return matchKey; }
    public void setMatchKey(String k) {
        this.matchKey = (k == null || k.isBlank()) ? null : k.toLowerCase(Locale.ROOT).trim();
    }

    /**
     * True si cette mécanique accepte le contexte {@code contextKey} (clé du bloc/item/cause déclencheur).
     * {@code matchKey} nul, ou déclencheur qui n'utilise pas de matchKey, ⇒ accepte tout. Sinon égalité
     * insensible à la casse. Pur → testable sans serveur (extrait de la boucle de tir LIVE).
     */
    public boolean matchesContext(String contextKey) {
        if (matchKey == null || !trigger.usesMatchKey()) return true;
        return matchKey.equalsIgnoreCase(contextKey);
    }

    public int cooldownTicks() { return cooldownTicks; }
    public void setCooldownTicks(int t) { this.cooldownTicks = Math.max(0, Math.min(72_000, t)); }

    public int intervalTicks() { return intervalTicks; }
    public void setIntervalTicks(int t) { this.intervalTicks = Math.max(1, Math.min(1_728_000, t)); }

    public double chance() { return chance; }
    public void setChance(double c) { this.chance = Double.isNaN(c) ? 1.0 : Math.max(0.0, Math.min(1.0, c)); }
    /** True si le tirage {@code rngValue} ∈ [0,1) passe la probabilité {@code chance} (toujours vrai si chance ≥ 1). */
    public boolean passes(double rngValue) { return chance >= 1.0 || rngValue < chance; }

    public double cost() { return cost; }
    public void setCost(double c) { this.cost = Double.isNaN(c) ? 0.0 : Math.max(0.0, c); }
    public boolean hasCost() { return cost > 0.0; }

    public String permission() { return permission; }
    public void setPermission(String p) {
        String t = (p == null) ? null : p.trim();
        this.permission = (t == null || t.isEmpty() || t.equalsIgnoreCase("none")) ? null : t;
    }
    /** True si aucune permission n'est requise ({@code permission} null). */
    public boolean isPublic() { return permission == null; }

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean b) { this.enabled = b; }

    public List<MechanicAction> actions() { return actions; }
    public MechanicDef addAction(MechanicAction a) { if (a != null) actions.add(a); return this; }

    /** Ids des tables de loot utilisées par les actions {@code LOOT} de cette mécanique (param {@code table}). Pur. */
    public java.util.Set<String> lootTablesUsed() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (MechanicAction a : actions) {
            if (a.type() == ActionType.LOOT) {
                String t = a.param("table", "").trim().toLowerCase(Locale.ROOT);
                if (!t.isEmpty()) out.add(t);
            }
        }
        return out;
    }

    /** Tables de loot utilisées (actions {@code LOOT}) que {@code exists} déclare inexistantes. Pur (prédicat injecté). */
    public java.util.Set<String> danglingLootTables(java.util.function.Predicate<String> exists) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String t : lootTablesUsed()) {
            if (exists == null || !exists.test(t)) out.add(t);
        }
        return out;
    }

    /** Ids d'items custom MoonCore référencés par les actions {@code GIVE_ITEM} (param {@code item=custom:<id>}). Pur. */
    public java.util.Set<String> customItemsUsed() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (MechanicAction a : actions) {
            if (a.type() != ActionType.GIVE_ITEM) continue;
            String item = a.param("item", "").trim();
            if (item.toLowerCase(Locale.ROOT).startsWith("custom:")) {
                String id = item.substring("custom:".length()).trim().toLowerCase(Locale.ROOT);
                if (!id.isEmpty()) out.add(id);
            }
        }
        return out;
    }

    /** Items custom référencés (actions {@code GIVE_ITEM}) que {@code exists} déclare inexistants. Pur (prédicat injecté). */
    public java.util.Set<String> danglingCustomItems(java.util.function.Predicate<String> exists) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String id : customItemsUsed()) {
            if (exists == null || !exists.test(id)) out.add(id);
        }
        return out;
    }

    /** Ids de boss MoonCore invoqués par les actions {@code SPAWN_MOB} (param {@code entity=boss:<id>}). Pur. */
    public java.util.Set<String> bossesUsed() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (MechanicAction a : actions) {
            if (a.type() != ActionType.SPAWN_MOB) continue;
            String entity = a.param("entity", "").trim();
            if (entity.toLowerCase(Locale.ROOT).startsWith("boss:")) {
                String id = entity.substring("boss:".length()).trim().toLowerCase(Locale.ROOT);
                if (!id.isEmpty()) out.add(id);
            }
        }
        return out;
    }

    /** Boss invoqués (actions {@code SPAWN_MOB}) que {@code exists} déclare inexistants. Pur (prédicat injecté). */
    public java.util.Set<String> danglingBosses(java.util.function.Predicate<String> exists) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String id : bossesUsed()) {
            if (exists == null || !exists.test(id)) out.add(id);
        }
        return out;
    }

    /** True si la mécanique est exécutable (active, déclencheur reconnu, au moins une action valide). */
    public boolean isRunnable() {
        if (!enabled || trigger == TriggerType.NONE) return false;
        for (MechanicAction a : actions) if (a.isValid()) return true;
        return false;
    }

    public void save(ConfigurationSection s) {
        s.set("display-name", displayName);
        s.set("trigger", trigger.name());
        s.set("match", matchKey);
        s.set("cooldown-ticks", cooldownTicks);
        s.set("interval-ticks", intervalTicks);
        s.set("chance", chance);
        s.set("cost", cost);
        s.set("permission", permission);
        s.set("enabled", enabled);
        for (int i = 0; i < actions.size(); i++) {
            MechanicAction a = actions.get(i);
            ConfigurationSection as = s.createSection("actions." + i);
            as.set("type", a.type().name());
            if (!a.params().isEmpty()) {
                ConfigurationSection ps = as.createSection("params");
                a.params().forEach(ps::set);
            }
        }
    }

    public static MechanicDef load(String id, ConfigurationSection s) {
        MechanicDef d = new MechanicDef(id);
        d.displayName = s.getString("display-name", d.displayName);
        d.setTrigger(TriggerType.fromText(s.getString("trigger", "none")));
        d.setMatchKey(s.getString("match", null));
        d.setCooldownTicks(s.getInt("cooldown-ticks", 0));
        d.setIntervalTicks(s.getInt("interval-ticks", 100));
        d.setChance(s.getDouble("chance", 1.0));
        d.setCost(s.getDouble("cost", 0.0));
        d.setPermission(s.getString("permission", null));
        d.enabled = s.getBoolean("enabled", true);

        ConfigurationSection actionsSec = s.getConfigurationSection("actions");
        if (actionsSec != null) {
            List<String> keys = new ArrayList<>(actionsSec.getKeys(false));
            keys.sort(MechanicDef::compareNumericKey);
            for (String k : keys) {
                ConfigurationSection as = actionsSec.getConfigurationSection(k);
                if (as == null) continue;
                ActionType type = ActionType.fromText(as.getString("type", "none"));
                Map<String, String> params = new LinkedHashMap<>();
                ConfigurationSection ps = as.getConfigurationSection("params");
                if (ps != null) {
                    for (String pk : ps.getKeys(false)) {
                        Object v = ps.get(pk);
                        if (v != null) params.put(pk, String.valueOf(v));
                    }
                }
                d.actions.add(new MechanicAction(type, params));
            }
        }
        return d;
    }

    private static int compareNumericKey(String a, String b) {
        try { return Integer.compare(Integer.parseInt(a), Integer.parseInt(b)); }
        catch (NumberFormatException ex) { return a.compareTo(b); }
    }
}
