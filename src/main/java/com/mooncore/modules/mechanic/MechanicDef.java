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

    public int cooldownTicks() { return cooldownTicks; }
    public void setCooldownTicks(int t) { this.cooldownTicks = Math.max(0, Math.min(72_000, t)); }

    public int intervalTicks() { return intervalTicks; }
    public void setIntervalTicks(int t) { this.intervalTicks = Math.max(1, Math.min(1_728_000, t)); }

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean b) { this.enabled = b; }

    public List<MechanicAction> actions() { return actions; }
    public MechanicDef addAction(MechanicAction a) { if (a != null) actions.add(a); return this; }

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
