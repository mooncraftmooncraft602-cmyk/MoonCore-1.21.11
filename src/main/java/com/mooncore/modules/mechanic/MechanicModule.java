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
    private final MechanicCooldowns cooldowns = new MechanicCooldowns();
    private MechanicStore store;
    private MechanicExecutor executor;
    private org.bukkit.scheduler.BukkitTask intervalTask;
    private com.mooncore.data.content.ContentSyncService contentSync; // miroir SQL (null = YAML pur)

    /** Période de contrôle des mécaniques INTERVAL (ticks). Le cooldown par mécanique cale l'espacement réel. */
    private static final long INTERVAL_PERIOD = 20L;

    @Override
    protected void onEnable() {
        this.store = new MechanicStore(plugin().getDataFolder(), log());
        this.executor = new MechanicExecutor(plugin());
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

        registerListener(new MechanicListener(this));
        plugin().rootCommand().register(new MechanicSubCommand(this));
        this.intervalTask = schedulers().syncTimer(this::tickInterval, INTERVAL_PERIOD, INTERVAL_PERIOD);

        log().info("MechanicManager : " + defs.size() + " mécanique(s) (" + runnableCount() + " active(s)).");
    }

    @Override
    protected void onDisable() {
        if (intervalTask != null) { intervalTask.cancel(); intervalTask = null; }
        cooldowns.clearAll();
        defs.clear();
    }

    // ---- Déclenchement (LIVE) ----

    /** Tick courant du serveur (monotone), pour les cooldowns. */
    private static long currentTick() { return org.bukkit.Bukkit.getCurrentTick(); }

    /**
     * Déclenche les mécaniques liées à {@code trigger} pour {@code player}, filtrées par {@code contextKey}
     * (id de l'objet déclencheur : Material en minuscule, {@code custom:<id>}, ou type d'entité) quand le
     * déclencheur utilise un match, puis sous réserve du cooldown par joueur. Exécute leurs actions.
     */
    public void fire(TriggerType trigger, org.bukkit.entity.Player player, String contextKey) {
        if (player == null) return;
        long now = currentTick();
        for (MechanicDef d : byTrigger(trigger)) {
            if (!d.matchesContext(contextKey)) continue;
            if (!d.isPublic() && !player.hasPermission(d.permission())) continue;
            if (!d.passes(java.util.concurrent.ThreadLocalRandom.current().nextDouble())) continue;
            // Solvabilité AVANT le cooldown : un joueur qui ne peut pas payer n'est pas mis en cooldown.
            if (d.hasCost() && !canAfford(player, d.cost())) continue;
            if (!cooldowns.tryAcquire(d.id(), player.getUniqueId(), d.cooldownTicks(), now)) continue;
            if (d.hasCost()) charge(player, d.cost(), d.id());   // débit atomique avant les actions
            executor.run(d, player);
        }
    }

    private boolean canAfford(org.bukkit.entity.Player p, double amount) {
        var eco = services().get(com.mooncore.api.economy.EconomyService.class).orElse(null);
        return eco == null || eco.has(p.getUniqueId(), amount);   // pas d'économie = pas de blocage
    }

    private void charge(org.bukkit.entity.Player p, double amount, String mechanicId) {
        services().get(com.mooncore.api.economy.EconomyService.class)
                .ifPresent(eco -> eco.withdraw(p.getUniqueId(), amount, "mechanic:" + mechanicId));
    }

    /** Contrôle périodique des mécaniques INTERVAL : chaque joueur en ligne est cadencé par intervalTicks. */
    private void tickInterval() {
        List<MechanicDef> intervals = byTrigger(TriggerType.INTERVAL);
        if (intervals.isEmpty()) return;
        long now = currentTick();
        for (MechanicDef d : intervals) {
            for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                if ((d.isPublic() || p.hasPermission(d.permission()))
                        && (!d.hasCost() || canAfford(p, d.cost()))
                        && cooldowns.tryAcquire(d.id(), p.getUniqueId(), d.intervalTicks(), now)
                        && d.passes(java.util.concurrent.ThreadLocalRandom.current().nextDouble())) {
                    if (d.hasCost()) charge(p, d.cost(), d.id());
                    executor.run(d, p);
                }
            }
        }
    }

    public void clearCooldowns(java.util.UUID player) {
        for (MechanicDef d : defs.values()) cooldowns.clear(d.id(), player);
    }

    /** Exécute les actions d'une mécanique pour un joueur en ignorant cooldown/filtre (pour {@code /moon mechanic test}). */
    public void runActions(MechanicDef def, org.bukkit.entity.Player player) {
        if (executor != null) executor.run(def, player);
    }

    /** True si une table de loot de cet id existe (module loot présent). Pour la validation des actions {@code LOOT}. */
    public boolean lootTableExists(String id) {
        var loot = plugin().moduleManager().get(com.mooncore.modules.loot.LootManagerModule.class);
        return loot != null && loot.def(id) != null;
    }

    /** True si un item custom MoonCore de cet id existe (pour la validation des actions {@code GIVE_ITEM}). */
    public boolean customItemExists(String id) {
        if (id == null || id.isBlank()) return false;
        var ci = services().get(com.mooncore.api.customitem.CustomItemManagerService.class).orElse(null);
        return ci != null && ci.create(id, 1) != null;
    }

    /** True si un bloc custom MoonCore de cet id existe (pour valider un matchKey de trigger de bloc). */
    public boolean customBlockExists(String id) {
        if (id == null || id.isBlank()) return false;
        var cb = services().get(com.mooncore.api.customblock.CustomBlockService.class).orElse(null);
        return cb != null && cb.ids().contains(id.toLowerCase(Locale.ROOT));
    }

    /** True si un boss MoonCore de cet id existe (pour valider une action {@code spawn_mob entity=boss:<id>}). */
    public boolean bossExists(String id) {
        if (id == null || id.isBlank()) return false;
        var boss = plugin().moduleManager().get(com.mooncore.modules.boss.BossManagerModule.class);
        return boss != null && boss.exists(id);
    }

    /** Clé de contexte d'un bloc : {@code custom:<id>} si bloc MoonCore, sinon Material en minuscule. */
    public String blockContextKey(org.bukkit.block.Block block) {
        if (block == null) return null;
        String custom = services().get(com.mooncore.api.customblock.CustomBlockService.class)
                .map(cb -> cb.idAt(block)).orElse(null);
        if (custom != null) return "custom:" + custom.toLowerCase(Locale.ROOT);
        return block.getType().name().toLowerCase(Locale.ROOT);
    }

    /** Clé de contexte d'un item : {@code custom:<id>} si item MoonCore, sinon Material en minuscule. */
    public String itemContextKey(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        String custom = services().get(com.mooncore.api.customitem.CustomItemManagerService.class)
                .map(ci -> ci.idOf(item)).orElse(null);
        if (custom != null) return "custom:" + custom.toLowerCase(Locale.ROOT);
        return item.getType().name().toLowerCase(Locale.ROOT);
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
