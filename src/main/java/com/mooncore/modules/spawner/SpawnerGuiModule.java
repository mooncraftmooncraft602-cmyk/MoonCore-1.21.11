package com.mooncore.modules.spawner;

import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

@ModuleInfo(id = "spawner_gui", name = "Spawner GUI", softDepends = {"economy"})
public final class SpawnerGuiModule extends AbstractModule implements Listener {

    @Override
    protected void onEnable() {
        registerListener(this);
    }

    @Override
    protected void onDisable() {
    }

    public com.mooncore.api.economy.EconomyService economy() {
        return services().get(com.mooncore.api.economy.EconomyService.class).orElse(null);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.SPAWNER) return;

        Player p = e.getPlayer();
        if (p.isSneaking()) return; // Allow normal block placement if sneaking? Wait, maybe just open if they right click.

        if (!p.hasPermission("mooncore.spawner.use")) {
            return;
        }

        if (b.getState() instanceof CreatureSpawner spawner) {
            e.setCancelled(true);
            new SpawnerMenu(this, p, spawner).open();
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof SpawnerMenu menu) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null) return;
            if (e.getWhoClicked() instanceof Player p) {
                // We want to allow clicking in the player inventory if it's a spawn egg
                if (!e.getClickedInventory().equals(e.getView().getTopInventory())) {
                    // Clicking in player inventory
                    menu.playerInventoryClick(p, e.getRawSlot(), e.getCurrentItem());
                    return;
                }
                menu.click(p, e.getRawSlot());
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof SpawnerMenu) {
            e.setCancelled(true);
        }
    }
}
