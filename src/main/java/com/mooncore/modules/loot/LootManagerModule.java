package com.mooncore.modules.loot;

import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;

import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Gestionnaire des tables de loot custom data-driven (type «&nbsp;loot&nbsp;» du backlog vision). Charge les
 * définitions ({@link LootTableDef}) depuis {@code loot/} via {@link LootTableStore} et, si la base est prête,
 * miroite chaque table dans {@code mooncore_content} (type {@code "loot"}) pour requêtage SQL (Étape A).
 * <p>
 * Pas d'entités ni de listener propre : une table de loot est une <b>ressource référencée</b> par d'autres
 * systèmes (drops de blocs/cultures, butin de boss, conteneurs…) qui appellent {@link #roll}. Le branchement
 * sur ces consommateurs et l'éditeur GUI sont des sous-tâches ultérieures.
 */
@ModuleInfo(id = "loot", name = "LootManager", softDepends = {"custom-item"})
public final class LootManagerModule extends AbstractModule {

    private static final String CONTENT_TYPE = "loot";

    private final Map<String, LootTableDef> defs = new LinkedHashMap<>();
    private LootTableStore store;
    private com.mooncore.data.content.ContentSyncService contentSync; // miroir SQL (null = YAML pur)

    @Override
    protected void onEnable() {
        this.store = new LootTableStore(plugin().getDataFolder(), log());
        reloadDefinitions();

        var dm = data();
        if (dm != null && dm.isReady()) {
            try {
                dm.applyMigrations(com.mooncore.data.content.ContentSyncService.migrations());
                this.contentSync = new com.mooncore.data.content.ContentSyncService(dm.database(),
                        () -> plugin().getConfig().getString("content.storage-mode", "yaml"), log());
            } catch (SQLException e) {
                log().error("Préparation du miroir SQL des tables de loot échouée", e);
                this.contentSync = null;
            }
        }

        plugin().rootCommand().register(new LootSubCommand(this));
        log().info("LootManager : " + defs.size() + " table(s) de loot.");
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

    public Collection<LootTableDef> definitions() { return defs.values(); }
    public LootTableDef def(String id) { return id == null ? null : defs.get(id.toLowerCase(Locale.ROOT)); }
    public LootTableStore store() { return store; }
    public Set<String> ids() { return Set.copyOf(defs.keySet()); }

    public void put(LootTableDef def) {
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

    /** Évalue une table par id (drops concrets), ou liste vide si la table est introuvable. */
    public List<LootResult> roll(String id, RandomGenerator rng) {
        LootTableDef d = def(id);
        return d == null ? List.of() : d.roll(rng);
    }

    /** Profondeur max de résolution des tables imbriquées (garde anti-cycle). */
    private static final int MAX_NEST_DEPTH = 8;

    /** Matérialise un résultat de loot en ItemStack (item custom prioritaire, sinon Material), ou null. */
    public org.bukkit.inventory.ItemStack materialize(LootResult r) {
        if (r == null || r.count() <= 0 || r.isReference()) return null;
        if (r.isCustom()) {
            var ci = services().get(com.mooncore.api.customitem.CustomItemManagerService.class).orElse(null);
            return ci == null ? null : ci.create(r.itemId(), r.count());
        }
        return (r.material() == null || r.material().isAir())
                ? null : new org.bukkit.inventory.ItemStack(r.material(), r.count());
    }

    /**
     * Tire une table et matérialise les résultats en ItemStacks. Résout récursivement les entrées qui
     * <b>référencent</b> une autre table ({@link LootEntry#tableRef}), avec une garde de profondeur contre
     * les cycles. Centralise la conversion utilisée par tous les consommateurs.
     */
    public List<org.bukkit.inventory.ItemStack> rollItems(String id, RandomGenerator rng) {
        List<org.bukkit.inventory.ItemStack> out = new java.util.ArrayList<>();
        rollInto(id, rng, out, 0);
        return out;
    }

    private void rollInto(String id, RandomGenerator rng, List<org.bukkit.inventory.ItemStack> out, int depth) {
        if (depth >= MAX_NEST_DEPTH) {
            log().warn("Table de loot imbriquée trop profonde (>= " + MAX_NEST_DEPTH + ") à '" + id + "' : cycle probable, ignorée.");
            return;
        }
        for (LootResult r : roll(id, rng)) {
            if (r.isReference()) {
                for (int i = 0; i < r.count(); i++) rollInto(r.tableRef(), rng, out, depth + 1);
            } else {
                org.bukkit.inventory.ItemStack stack = materialize(r);
                if (stack != null) out.add(stack);
            }
        }
    }

    /** Donne au joueur le butin tiré de la table ; retourne le nombre de piles données (0 si table inconnue). */
    public int give(org.bukkit.entity.Player player, String id, RandomGenerator rng) {
        if (player == null) return 0;
        List<org.bukkit.inventory.ItemStack> items = rollItems(id, rng);
        for (org.bukkit.inventory.ItemStack it : items) player.getInventory().addItem(it);
        return items.size();
    }

    /** Sérialise une table en JSON via le pont YAML↔JSON (réutilise {@code def.save}). */
    private static String toJson(LootTableDef def) {
        org.bukkit.configuration.MemoryConfiguration cfg = new org.bukkit.configuration.MemoryConfiguration();
        def.save(cfg);
        return com.mooncore.data.content.ContentJson.toJson(cfg);
    }
}
