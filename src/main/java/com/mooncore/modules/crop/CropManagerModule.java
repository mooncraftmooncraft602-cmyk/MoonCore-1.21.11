package com.mooncore.modules.crop;

import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;

import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire des cultures custom data-driven (Étape C3). Charge les définitions ({@link CropDef})
 * depuis {@code crops/}, persiste les <b>emplantations</b> dans {@code mooncore_crop_placement}
 * ({@link CropPlacementStore}) et matérialise chaque plant via un {@link CropVisual} (ItemDisplay).
 * <p>
 * La croissance (tick batché par chunk) est branchée à l'Étape C4 ; le gameplay (planter/récolter,
 * {@code CropListener}) à l'Étape C5. Ce module expose l'API d'emplantation utilisée par ces étapes.
 */
@ModuleInfo(id = "crop", name = "CropManager", softDepends = {"custom-item", "resource-pack"})
public final class CropManagerModule extends AbstractModule {

    /** Période du tick de croissance (ticks). Chaque contrôle fait avancer une étape avec prob. period/growthTicks. */
    private static final long GROWTH_PERIOD = 40L; // 2 s

    private final Map<String, CropDef> defs = new LinkedHashMap<>();
    private final Map<String, CropPlacementStore.Placement> placements = new ConcurrentHashMap<>(); // locKey → plant
    private final Map<String, UUID> displays = new ConcurrentHashMap<>();                            // locKey → display
    private final Map<String, java.util.Set<String>> byChunk = new ConcurrentHashMap<>();           // chunkKey → locKeys

    /** Type de contenu pour le store universel SQL (Étape A). */
    private static final String CONTENT_TYPE = "crop";

    private CropDefStore defStore;
    private CropPlacementStore placementStore;
    private com.mooncore.data.content.ContentSyncService contentSync; // miroir SQL des défs (null = YAML pur)
    private org.bukkit.scheduler.BukkitTask growthTask;

    @Override
    protected void onEnable() {
        this.defStore = new CropDefStore(plugin().getDataFolder(), log());
        reloadDefinitions();

        var dm = data();
        if (dm != null && dm.isReady()) {
            try {
                dm.applyMigrations(CropPlacementStore.migrations());
                dm.applyMigrations(com.mooncore.data.content.ContentSyncService.migrations());
                this.placementStore = new CropPlacementStore(dm.database());
                this.contentSync = new com.mooncore.data.content.ContentSyncService(dm.database(),
                        () -> plugin().getConfig().getString("content.storage-mode", "yaml"), log());
                for (CropPlacementStore.Placement p : placementStore.loadAll()) {
                    if (defs.containsKey(p.cropId())) { placements.put(p.locKey(), p); indexAdd(p); }
                }
            } catch (SQLException e) {
                log().error("Chargement des emplantations de cultures échoué", e);
                this.placementStore = null;
            }
        }

        registerListener(new CropListener(this));
        plugin().rootCommand().register(new CropSubCommand(this));

        // Affiche les plants des chunks déjà chargés (les autres apparaîtront au ChunkLoad).
        for (World w : Bukkit.getWorlds()) {
            for (Chunk ch : w.getLoadedChunks()) spawnVisualsInChunk(ch);
        }

        // Tick de croissance batché par chunk chargé (jamais de scan global du monde).
        this.growthTask = schedulers().syncTimer(this::tickGrowth, GROWTH_PERIOD, GROWTH_PERIOD);

        log().info("CropManager : " + defs.size() + " culture(s), " + placements.size() + " plant(s).");
    }

    @Override
    protected void onDisable() {
        if (growthTask != null) { growthTask.cancel(); growthTask = null; }
        for (UUID id : displays.values()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        displays.clear();
        placements.clear();
        byChunk.clear();
        defs.clear();
    }

    @Override
    protected void onReload() {
        reloadModuleConfig();
        reloadDefinitions();
    }

    public void reloadDefinitions() {
        defs.clear();
        defs.putAll(defStore.loadAll());
    }

    // ---- API définitions ----

    public Collection<CropDef> definitions() { return defs.values(); }
    public CropDef def(String id) { return id == null ? null : defs.get(id.toLowerCase(Locale.ROOT)); }
    public CropDefStore defStore() { return defStore; }

    public void put(CropDef def) {
        defs.put(def.id(), def);
        defStore.save(def);
        if (contentSync != null) {
            int version = com.mooncore.data.content.ContentSchemas.currentVersion(CONTENT_TYPE);
            contentSync.mirror(CONTENT_TYPE, def.id(), toJson(def), version, System.currentTimeMillis());
        }
    }

    public boolean removeDef(String id) {
        String norm = id == null ? null : id.toLowerCase(Locale.ROOT);
        if (norm == null) return false;
        boolean mem = defs.remove(norm) != null;
        boolean disk = defStore.delete(norm);
        if (contentSync != null) contentSync.remove(CONTENT_TYPE, norm);
        return mem || disk;
    }

    /** Sérialise une culture en JSON via le pont YAML↔JSON (réutilise {@code def.save}). */
    private static String toJson(CropDef def) {
        org.bukkit.configuration.MemoryConfiguration cfg = new org.bukkit.configuration.MemoryConfiguration();
        def.save(cfg);
        return com.mooncore.data.content.ContentJson.toJson(cfg);
    }

    public java.util.Set<String> ids() { return java.util.Set.copyOf(defs.keySet()); }

    /** Vue des définitions (pour le builder de resource pack). */
    public Map<String, CropDef> rawDefs() { return defs; }

    /** Dossier source des PNG de cultures par étape ({@code crops-textures/<modelKey>_stage<n>.png}). */
    public java.io.File texturesFolder() {
        java.io.File f = new java.io.File(plugin().getDataFolder(), "crops-textures");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    // ---- API emplantations ----

    public CropPlacementStore.Placement placementAt(Location loc) {
        return loc == null ? null : placements.get(locKey(loc));
    }

    /** Pose une culture à l'emplacement (étape 0). Retourne false si déjà occupé ou culture inconnue. */
    public boolean place(Location loc, String cropId, long nowMs) {
        CropDef def = def(cropId);
        if (def == null || loc == null || loc.getWorld() == null) return false;
        String key = locKey(loc);
        if (placements.containsKey(key)) return false;

        CropPlacementStore.Placement p = new CropPlacementStore.Placement(
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), def.id(), 0, nowMs);
        placements.put(key, p);
        indexAdd(p);
        spawnVisual(loc.getWorld(), p, def);
        if (placementStore != null) {
            placementStore.save(p).exceptionally(t -> { log().error("Sauvegarde plant échouée", t); return null; });
        }
        return true;
    }

    /** Met à jour l'étape d'un plant (clamp), persiste et rafraîchit son visuel. */
    public void setStage(Location loc, int stage) {
        String key = locKey(loc);
        CropPlacementStore.Placement p = placements.get(key);
        if (p == null) return;
        CropDef def = def(p.cropId());
        if (def == null) return;
        int clamped = Math.max(0, Math.min(def.stages() - 1, stage));
        if (clamped == p.stage()) return;
        CropPlacementStore.Placement upd = new CropPlacementStore.Placement(
                p.world(), p.x(), p.y(), p.z(), p.cropId(), clamped, p.plantedAt());
        placements.put(key, upd);
        UUID displayId = displays.get(key);
        if (displayId != null && Bukkit.getEntity(displayId) instanceof ItemDisplay disp) {
            CropVisual.setStage(disp, def, clamped);
        }
        if (placementStore != null) {
            placementStore.updateStage(key, clamped)
                    .exceptionally(t -> { log().error("MAJ étape plant échouée", t); return null; });
        }
    }

    /** Retire le plant à l'emplacement (visuel + persistance). Retourne false s'il n'y en avait pas. */
    public boolean removeAt(Location loc) {
        String key = locKey(loc);
        CropPlacementStore.Placement p = placements.remove(key);
        if (p == null) return false;
        indexRemove(p);
        removeVisual(key);
        if (placementStore != null) {
            placementStore.delete(key).exceptionally(t -> { log().error("Suppression plant échouée", t); return null; });
        }
        return true;
    }

    /** (Ré)affiche les plants d'un chunk chargé — utilisé au démarrage et au ChunkLoad (Étape C4). */
    public void spawnVisualsInChunk(Chunk chunk) {
        String chunkKey = CropPlacementStore.Placement.chunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        for (CropPlacementStore.Placement p : placements.values()) {
            if (!p.chunkKey().equals(chunkKey)) continue;
            if (displays.containsKey(p.locKey())) continue;
            CropDef def = def(p.cropId());
            if (def != null) spawnVisual(chunk.getWorld(), p, def);
        }
    }

    /** Retire les entités d'affichage d'un chunk déchargé (les données restent en base/mémoire). */
    public void despawnVisualsInChunk(Chunk chunk) {
        String chunkKey = CropPlacementStore.Placement.chunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        for (CropPlacementStore.Placement p : placements.values()) {
            if (p.chunkKey().equals(chunkKey)) removeVisual(p.locKey());
        }
    }

    public Collection<CropPlacementStore.Placement> placements() { return placements.values(); }

    public boolean isMature(CropPlacementStore.Placement p) {
        if (p == null) return false;
        CropDef def = def(p.cropId());
        return def != null && p.stage() >= def.stages() - 1;
    }

    private com.mooncore.api.customitem.CustomItemManagerService customItems() {
        return services().get(com.mooncore.api.customitem.CustomItemManagerService.class).orElse(null);
    }

    /** Trouve la culture dont le bloc support == {@code placeOn} ET dont la graine correspond à l'item en main. */
    public CropDef matchSeed(org.bukkit.inventory.ItemStack item, org.bukkit.Material placeOn) {
        if (item == null || item.getType().isAir()) return null;
        var ci = customItems();
        String customId = ci != null ? ci.idOf(item) : null;
        for (CropDef def : defs.values()) {
            if (def.placeOn() != placeOn) continue;
            if (def.seedCustomId() != null) {
                if (customId != null && customId.equalsIgnoreCase(def.seedCustomId())) return def;
            } else if (customId == null && item.getType() == def.seed()) {
                return def;
            }
        }
        return null;
    }

    /** Item de récolte (item custom prioritaire, sinon Material). {@code null} si quantité nulle. */
    public org.bukkit.inventory.ItemStack harvestDrop(CropDef def, int amount) {
        if (def == null || amount <= 0) return null;
        if (def.dropItemId() != null) {
            var ci = customItems();
            return ci == null ? null : ci.create(def.dropItemId(), amount);
        }
        return new org.bukkit.inventory.ItemStack(def.dropMaterial(), amount);
    }

    /**
     * Tire la table de loot référencée par la culture ({@link CropDef#lootTableId}) et matérialise les
     * résultats en ItemStacks. Liste vide si la culture n'utilise pas de table, si le module loot est
     * absent ou si la table est introuvable (le drop fixe reste utilisé en repli côté listener).
     */
    public java.util.List<org.bukkit.inventory.ItemStack> lootDrops(CropDef def, java.util.random.RandomGenerator rng) {
        java.util.List<org.bukkit.inventory.ItemStack> out = new java.util.ArrayList<>();
        if (def == null || !def.usesLootTable()) return out;
        var loot = plugin().moduleManager().get(com.mooncore.modules.loot.LootManagerModule.class);
        if (loot == null) return out;
        for (com.mooncore.modules.loot.LootResult r : loot.roll(def.lootTableId(), rng)) {
            org.bukkit.inventory.ItemStack stack = toItemStack(r);
            if (stack != null) out.add(stack);
        }
        return out;
    }

    /** Convertit un résultat de loot en ItemStack (item custom prioritaire, sinon Material). */
    private org.bukkit.inventory.ItemStack toItemStack(com.mooncore.modules.loot.LootResult r) {
        if (r == null || r.count() <= 0) return null;
        if (r.isCustom()) {
            var ci = customItems();
            return ci == null ? null : ci.create(r.itemId(), r.count());
        }
        if (r.material() == null || r.material().isAir()) return null;
        return new org.bukkit.inventory.ItemStack(r.material(), r.count());
    }

    /** Item graine (custom prioritaire, sinon Material). {@code null} si quantité nulle. */
    public org.bukkit.inventory.ItemStack seedItem(CropDef def, int amount) {
        if (def == null || amount <= 0) return null;
        if (def.seedCustomId() != null) {
            var ci = customItems();
            return ci == null ? null : ci.create(def.seedCustomId(), amount);
        }
        return new org.bukkit.inventory.ItemStack(def.seed(), amount);
    }

    // ---- Croissance (tick batché par chunk chargé) ----

    /**
     * Avance les étapes des plants situés dans les chunks <b>chargés</b> uniquement (via l'index
     * {@link #byChunk}, jamais de scan global). Chaque contrôle a une probabilité {@code period/growthTicks}
     * de faire pousser d'une étape, sous réserve des conditions (lumière, support, hydratation).
     */
    private void tickGrowth() {
        if (placements.isEmpty()) return;
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        for (World w : Bukkit.getWorlds()) {
            for (Chunk ch : w.getLoadedChunks()) {
                java.util.Set<String> keys = byChunk.get(
                        CropPlacementStore.Placement.chunkKey(w.getName(), ch.getX(), ch.getZ()));
                if (keys == null || keys.isEmpty()) continue;
                for (String key : new java.util.ArrayList<>(keys)) {
                    CropPlacementStore.Placement p = placements.get(key);
                    if (p == null) continue;
                    CropDef def = def(p.cropId());
                    if (def == null || p.stage() >= def.stages() - 1) continue; // inconnue ou déjà mûre
                    Location loc = new Location(w, p.x(), p.y(), p.z());
                    if (!conditionsMet(def, loc)) continue;
                    double chance = (double) GROWTH_PERIOD / def.growthTicks();
                    if (rng.nextDouble() < chance) setStage(loc, p.stage() + 1);
                }
            }
        }
    }

    /** Conditions de croissance : lumière minimale, bloc support présent, hydratation si requise. */
    private boolean conditionsMet(CropDef def, Location loc) {
        org.bukkit.block.Block block = loc.getBlock();
        if (block.getLightLevel() < def.minLight()) return false;
        org.bukkit.block.Block support = block.getRelative(org.bukkit.block.BlockFace.DOWN);
        if (support.getType() != def.placeOn()) return false;
        if (def.requiresWater()
                && support.getBlockData() instanceof org.bukkit.block.data.type.Farmland farm
                && farm.getMoisture() <= 0) {
            return false;
        }
        return true;
    }

    private void indexAdd(CropPlacementStore.Placement p) {
        byChunk.computeIfAbsent(p.chunkKey(), k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(p.locKey());
    }

    private void indexRemove(CropPlacementStore.Placement p) {
        java.util.Set<String> set = byChunk.get(p.chunkKey());
        if (set != null) {
            set.remove(p.locKey());
            if (set.isEmpty()) byChunk.remove(p.chunkKey());
        }
    }

    // ---- Visuels ----

    private void spawnVisual(World world, CropPlacementStore.Placement p, CropDef def) {
        Location loc = new Location(world, p.x(), p.y(), p.z());
        ItemDisplay d = CropVisual.spawn(world, loc, def, p.stage());
        displays.put(p.locKey(), d.getUniqueId());
    }

    private void removeVisual(String locKey) {
        UUID id = displays.remove(locKey);
        if (id == null) return;
        Entity e = Bukkit.getEntity(id);
        if (e != null) e.remove();
    }

    private static String locKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }
}
