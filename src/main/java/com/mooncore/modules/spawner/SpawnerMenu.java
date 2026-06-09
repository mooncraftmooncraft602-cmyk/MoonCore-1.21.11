package com.mooncore.modules.spawner;

import com.mooncore.api.economy.EconomyService;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class SpawnerMenu implements InventoryHolder {

    private final SpawnerGuiModule module;
    private final Player player;
    private final CreatureSpawner spawner;
    private final Inventory inv;

    private static final double SPEED_UPGRADE_COST = 5000.0;
    private static final double COUNT_UPGRADE_COST = 10000.0;
    private static final double PICKUP_COST = 1000.0;

    public SpawnerMenu(SpawnerGuiModule module, Player player, CreatureSpawner spawner) {
        this.module = module;
        this.player = player;
        this.spawner = spawner;
        this.inv = Bukkit.createInventory(this, 27, Text.mm("<gradient:#8a2be2:#c77dff>Spawner : </gradient>" + spawner.getSpawnedType().name()));
    }

    public void open() {
        render();
        player.openInventory(inv);
    }

    private void render() {
        inv.clear();

        // Info
        ItemStack info = new ItemStack(Material.SPAWNER);
        ItemMeta iMeta = info.getItemMeta();
        if (iMeta != null) {
            iMeta.displayName(Text.mm("<yellow>Type : " + spawner.getSpawnedType().name()));
            iMeta.lore(List.of(
                    Text.mm("<gray>Délai max : <white>" + spawner.getMaxSpawnDelay() + " ticks"),
                    Text.mm("<gray>Délai min : <white>" + spawner.getMinSpawnDelay() + " ticks"),
                    Text.mm("<gray>Entités par spawn : <white>" + spawner.getSpawnCount())
            ));
            info.setItemMeta(iMeta);
        }
        inv.setItem(11, info);

        // Upgrade Speed
        ItemStack speed = new ItemStack(Material.SUGAR);
        ItemMeta sMeta = speed.getItemMeta();
        if (sMeta != null) {
            sMeta.displayName(Text.mm("<aqua>Améliorer la vitesse"));
            sMeta.lore(List.of(
                    Text.mm("<gray>Réduit le délai de spawn"),
                    Text.mm("<gray>Coût : <green>" + SPEED_UPGRADE_COST + "$")
            ));
            speed.setItemMeta(sMeta);
        }
        inv.setItem(13, speed);

        // Upgrade Count
        ItemStack count = new ItemStack(Material.EGG);
        ItemMeta cMeta = count.getItemMeta();
        if (cMeta != null) {
            cMeta.displayName(Text.mm("<aqua>Améliorer la quantité"));
            cMeta.lore(List.of(
                    Text.mm("<gray>Augmente le nombre d'entités"),
                    Text.mm("<gray>Coût : <green>" + COUNT_UPGRADE_COST + "$")
            ));
            count.setItemMeta(cMeta);
        }
        inv.setItem(14, count);

        // Pickup
        ItemStack pickup = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta pMeta = pickup.getItemMeta();
        if (pMeta != null) {
            pMeta.displayName(Text.mm("<red>Récupérer le spawner"));
            pMeta.lore(List.of(
                    Text.mm("<gray>Détruit le bloc et donne l'item"),
                    Text.mm("<gray>Coût : <green>" + PICKUP_COST + "$")
            ));
            pickup.setItemMeta(pMeta);
        }
        inv.setItem(15, pickup);

        // Change type info
        ItemStack eggInfo = new ItemStack(Material.PAPER);
        ItemMeta eMeta = eggInfo.getItemMeta();
        if (eMeta != null) {
            eMeta.displayName(Text.mm("<yellow>Changer le type"));
            eMeta.lore(List.of(
                    Text.mm("<gray>Cliquez sur un œuf d'apparition"),
                    Text.mm("<gray>dans votre inventaire en bas")
            ));
            eggInfo.setItemMeta(eMeta);
        }
        inv.setItem(26, eggInfo);
    }

    public void click(Player p, int slot) {
        EconomyService eco = module.economy();

        if (slot == 13) {
            // Speed upgrade
            if (spawner.getMaxSpawnDelay() <= 100) {
                p.sendMessage(Text.mm("<red>Vitesse maximale atteinte."));
                return;
            }
            if (eco != null && eco.isAvailable()) {
                if (eco.withdraw(p.getUniqueId(), SPEED_UPGRADE_COST, "Amélioration spawner")) {
                    spawner.setMaxSpawnDelay(Math.max(100, spawner.getMaxSpawnDelay() - 100));
                    spawner.setMinSpawnDelay(Math.max(50, spawner.getMinSpawnDelay() - 50));
                    spawner.update();
                    p.sendMessage(Text.mm("<green>Vitesse améliorée !"));
                    render();
                } else {
                    p.sendMessage(Text.mm("<red>Fonds insuffisants."));
                }
            }
        } else if (slot == 14) {
            // Count upgrade
            if (spawner.getSpawnCount() >= 10) {
                p.sendMessage(Text.mm("<red>Quantité maximale atteinte."));
                return;
            }
            if (eco != null && eco.isAvailable()) {
                if (eco.withdraw(p.getUniqueId(), COUNT_UPGRADE_COST, "Amélioration spawner")) {
                    spawner.setSpawnCount(spawner.getSpawnCount() + 1);
                    spawner.update();
                    p.sendMessage(Text.mm("<green>Quantité améliorée !"));
                    render();
                } else {
                    p.sendMessage(Text.mm("<red>Fonds insuffisants."));
                }
            }
        } else if (slot == 15) {
            // Pickup
            if (!p.hasPermission("mooncore.spawner.mine")) {
                p.sendMessage(Text.mm("<red>Vous n'avez pas la permission."));
                return;
            }
            if (eco != null && eco.isAvailable()) {
                if (eco.withdraw(p.getUniqueId(), PICKUP_COST, "Récupération spawner")) {
                    ItemStack drop = new ItemStack(Material.SPAWNER);
                    BlockStateMeta meta = (BlockStateMeta) drop.getItemMeta();
                    if (meta != null) {
                        meta.setBlockState(spawner);
                        drop.setItemMeta(meta);
                    }
                    spawner.getBlock().setType(Material.AIR);
                    p.getInventory().addItem(drop).values().forEach(e -> p.getWorld().dropItem(p.getLocation(), e));
                    p.sendMessage(Text.mm("<green>Spawner récupéré !"));
                    p.closeInventory();
                } else {
                    p.sendMessage(Text.mm("<red>Fonds insuffisants."));
                }
            }
        }
    }

    public void playerInventoryClick(Player p, int slot, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        String name = item.getType().name();
        if (name.endsWith("_SPAWN_EGG")) {
            String entityName = name.replace("_SPAWN_EGG", "");
            try {
                EntityType type = EntityType.valueOf(entityName);
                spawner.setSpawnedType(type);
                spawner.update();
                p.sendMessage(Text.mm("<green>Type changé en " + type.name() + "."));
                
                // Consomme l'oeuf
                item.setAmount(item.getAmount() - 1);
                
                // On met à jour l'inventaire
                render(); // Pour rafraichir le nom/info
                
                // Mettre à jour le titre nécessite de rouvrir l'inventaire dans Bukkit
                p.closeInventory();
                new SpawnerMenu(module, player, spawner).open();
                
            } catch (IllegalArgumentException ex) {
                p.sendMessage(Text.mm("<red>Type d'entité invalide."));
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
