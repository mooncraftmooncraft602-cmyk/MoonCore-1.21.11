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

    private final Map<String, CropDef> defs = new LinkedHashMap<>();
    private final Map<String, CropPlacementStore.Placement> placements = new ConcurrentHashMap<>(); // locKey → plant
    private final Map<String, UUID> displays = new ConcurrentHashMap<>();                            // locKey → display

    private CropDefStore defStore;
    private CropPlacementStore placementStore;

    @Override
    protected void onEnable() {
        this.defStore = new CropDefStore(plugin().getDataFolder(), log());
        reloadDefinitions();

        var dm = data();
        if (dm != null && dm.isReady()) {
            try {
                dm.applyMigrations(CropPlacementStore.migrations());
                this.placementStore = new CropPlacementStore(dm.database());
                for (CropPlacementStore.Placement p : placementStore.loadAll()) {
                    if (defs.containsKey(p.cropId())) placements.put(p.locKey(), p);
                }
            } catch (SQLException e) {
                log().error("Chargement des emplantations de cultures échoué", e);
                this.placementStore = null;
            }
        }

        // Affiche les plants des chunks déjà chargés (les autres apparaîtront au ChunkLoad — Étape C4).
        for (World w : Bukkit.getWorlds()) {
            for (Chunk ch : w.getLoadedChunks()) spawnVisualsInChunk(ch);
        }

        log().info("CropManager : " + defs.size() + " culture(s), " + placements.size() + " plant(s).");
    }

    @Override
    protected void onDisable() {
        for (UUID id : displays.values()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        displays.clear();
        placements.clear();
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
