package com.mooncore.modules.mechanic;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * Mappe les événements Bukkit aux {@link TriggerType} des mécaniques et délègue à
 * {@link MechanicModule#fire}. Réactif (ne casse rien) : {@code ignoreCancelled} sur interact/break pour
 * respecter les protections ; priorité {@code MONITOR} pour observer l'état final sans le modifier.
 */
public final class MechanicListener implements Listener {

    private final MechanicModule module;

    public MechanicListener(MechanicModule module) { this.module = module; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;        // évite le double-déclenchement (off-hand)
        Player p = e.getPlayer();
        Action act = e.getAction();
        if (act == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            module.fire(TriggerType.INTERACT_BLOCK, p, module.blockContextKey(e.getClickedBlock()));
        }
        if (act == Action.RIGHT_CLICK_BLOCK || act == Action.RIGHT_CLICK_AIR) {
            ItemStack item = e.getItem();
            if (item != null && !item.getType().isAir()) {
                module.fire(TriggerType.USE_ITEM, p, module.itemContextKey(item));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        module.fire(TriggerType.BREAK_BLOCK, e.getPlayer(), module.blockContextKey(b));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        module.fire(TriggerType.KILL_ENTITY, killer, e.getEntity().getType().name().toLowerCase(Locale.ROOT));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        module.fire(TriggerType.PLAYER_JOIN, e.getPlayer(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        module.fire(TriggerType.PLAYER_QUIT, e.getPlayer(), null);
        module.clearCooldowns(e.getPlayer().getUniqueId());
    }
}
