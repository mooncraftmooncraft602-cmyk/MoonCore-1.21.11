package com.mooncore.modules.mechanic;

import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;

import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Gestionnaire des mécaniques custom data-driven (type «&nbsp;mechanic&nbsp;» du backlog vision §2). Charge
 * les définitions ({@link MechanicDef}) depuis {@code mechanics/} via {@link MechanicStore} et, si la base
 * est prête, miroite chaque mécanique dans {@code mooncore_content} (type {@code "mechanic"}) pour requêtage
 * SQL (Étape A).
 * <p>
 * L'exécution effective (listeners qui mappent les événements Bukkit aux {@link TriggerType} et un dispatcher
 * d'actions) est branchée en passes ultérieures (parties LIVE). Ce module expose le registre + les index
 * utilisés par ces étapes ({@link #byTrigger}).
 */
@ModuleInfo(id = "mechanic", name = "MechanicManager", softDepends = {"custom-item", "custom-block"})
public final class MechanicModule extends AbstractModule {

    private static final String CONTENT_TYPE = "mechanic";

    private final Map<String, MechanicDef> defs = new LinkedHashMap<>();
    private MechanicStore store;
    private com.mooncore.data.content.ContentSyncService contentSync; // miroir SQL (null = YAML pur)

    @Override
    protected void onEnable() {
        this.store = new MechanicStore(plugin().getDataFolder(), log());
        reloadDefinitions();

        var dm = data();
        if (dm != null && dm.isReady()) {
            try {
                dm.applyMigrations(com.mooncore.data.content.ContentSyncService.migrations());
                this.contentSync = new com.mooncore.data.content.ContentSyncService(dm.database(),
                        () -> plugin().getConfig().getString("content.storage-mode", "yaml"), log());
            } catch (SQLException e) {
                log().error("Préparation du miroir SQL des mécaniques échouée", e);
                this.contentSync = null;
            }
        }

        log().info("MechanicManager : " + defs.size() + " mécanique(s) (" + runnableCount() + " active(s)).");
    }

    @Override
    protected void onDisable() {
        defs.clear();
    }

    public void reloadDefinitions() {
        defs.clear();
        defs.putAll(store.loadAll());
    }

    // ---- API définitions ----

    public Collection<MechanicDef> definitions() { return defs.values(); }
    public MechanicDef def(String id) { return id == null ? null : defs.get(id.toLowerCase(Locale.ROOT)); }
    public MechanicStore store() { return store; }
    public Set<String> ids() { return Set.copyOf(defs.keySet()); }

    /** Mécaniques exécutables (actives, déclencheur reconnu, ≥1 action valide) liées à ce déclencheur. */
    public List<MechanicDef> byTrigger(TriggerType trigger) {
        List<MechanicDef> out = new java.util.ArrayList<>();
        if (trigger == null || trigger == TriggerType.NONE) return out;
        for (MechanicDef d : defs.values()) {
            if (d.trigger() == trigger && d.isRunnable()) out.add(d);
        }
        return out;
    }

    public int runnableCount() {
        int n = 0;
        for (MechanicDef d : defs.values()) if (d.isRunnable()) n++;
        return n;
    }

    public void put(MechanicDef def) {
        defs.put(def.id(), def);
        store.save(def);
        if (contentSync != null) {
            int version = com.mooncore.data.content.ContentSchemas.currentVersion(CONTENT_TYPE);
            contentSync.mirror(CONTENT_TYPE, def.id(), toJson(def), version, System.currentTimeMillis());
        }
    }

    public boolean removeDef(String id) {
        String norm = id == null ? null : id.toLowerCase(Locale.ROOT);
        if (norm == null) return false;
        boolean mem = defs.remove(norm) != null;
        boolean disk = store.delete(norm);
        if (contentSync != null) contentSync.remove(CONTENT_TYPE, norm);
        return mem || disk;
    }

    /** Sérialise une mécanique en JSON via le pont YAML↔JSON (réutilise {@code def.save}). */
    private static String toJson(MechanicDef def) {
        org.bukkit.configuration.MemoryConfiguration cfg = new org.bukkit.configuration.MemoryConfiguration();
        def.save(cfg);
        return com.mooncore.data.content.ContentJson.toJson(cfg);
    }
}
