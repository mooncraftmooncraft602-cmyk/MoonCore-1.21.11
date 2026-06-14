package com.mooncore.modules.crop;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Reprise des visuels de cultures au chargement/déchargement des chunks (Étape C4) et gameplay
 * planter/récolter/replanter (Étape C5). {@code ignoreCancelled = true} sur l'interaction : un plugin
 * de protection (ou le {@code ZoneModule}) qui annule l'interaction empêche aussi planter/récolter.
 * Les données vivent en base/mémoire ; les entités d'affichage (transitoires) sont (re)construites
 * par chunk — jamais de scan global du monde.
 */
public final class CropListener implements Listener {

    private final CropManagerModule module;

    public CropListener(CropManagerModule module) {
        this.module = module;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        module.spawnVisualsInChunk(e.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        module.despawnVisualsInChunk(e.getChunk());
    }

    /** Planter (graine sur bloc support) ou récolter (clic sur le support d'un plant mûr). */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;
        World w = clicked.getWorld();
        Block above = clicked.getRelative(BlockFace.UP);
        Location aboveLoc = above.getLocation();
        Player p = e.getPlayer();

        // 1) Récolte : un plant existe au-dessus du bloc cliqué.
        CropPlacementStore.Placement existing = module.placementAt(aboveLoc);
        if (existing != null) {
            CropDef def = module.def(existing.cropId());
            if (def == null) return;
            e.setCancelled(true);                       // évite l'usage parasite du bloc support
            if (existing.stage() >= def.stages() - 1) harvest(p, w, aboveLoc, def);
            return;
        }

        // 2) Plantation : item en main = graine d'une culture dont le support == bloc cliqué.
        ItemStack hand = p.getInventory().getItemInMainHand();
        CropDef def = module.matchSeed(hand, clicked.getType());
        if (def == null || !above.getType().isAir()) return;
        if (module.place(aboveLoc, def.id(), System.currentTimeMillis())) {
            if (p.getGameMode() != GameMode.CREATIVE && !hand.getType().isAir()) {
                hand.setAmount(hand.getAmount() - 1);
            }
            w.playSound(aboveLoc, "minecraft:block.grass.place", 1f, 1f);
            e.setCancelled(true);
        }
    }

    private void harvest(Player p, World w, Location loc, CropDef def) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (def.usesLootTable()) {
            // Récolte = tirage de la table de loot référencée (repli sur le drop fixe si elle ne produit rien).
            java.util.List<ItemStack> loot = module.lootDrops(def, rng);
            if (loot.isEmpty()) {
                ItemStack drop = module.harvestDrop(def, randomBetween(rng, def.dropMin(), def.dropMax()));
                if (drop != null) w.dropItemNaturally(loc, drop);
            } else {
                for (ItemStack stack : loot) w.dropItemNaturally(loc, stack);
            }
        } else {
            int dropAmt = randomBetween(rng, def.dropMin(), def.dropMax());
            ItemStack drop = module.harvestDrop(def, dropAmt);
            if (drop != null) w.dropItemNaturally(loc, drop);
        }

        int seedAmt = randomBetween(rng, def.seedReturnMin(), def.seedReturnMax());
        ItemStack seeds = module.seedItem(def, seedAmt);
        if (seeds != null) w.dropItemNaturally(loc, seeds);

        w.playSound(loc, "minecraft:block.grass.break", 1f, 1f);
        if (def.replantable()) module.setStage(loc, 0);   // repart à l'étape 0
        else module.removeAt(loc);                        // plante consommée
    }

    private static int randomBetween(ThreadLocalRandom rng, int min, int max) {
        if (max <= min) return Math.max(0, min);
        return min + rng.nextInt(max - min + 1);
    }
}
