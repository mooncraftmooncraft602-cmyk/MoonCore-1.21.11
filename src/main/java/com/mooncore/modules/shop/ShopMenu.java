package com.mooncore.modules.shop;

import com.mooncore.api.customitem.CustomItemManagerService;
import com.mooncore.api.economy.EconomyService;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ShopMenu implements InventoryHolder {

    private final ShopModule module;
    private final Player player;
    private final Inventory inv;
    private ShopModule.ShopCategory currentCategory = null;

    public ShopMenu(ShopModule module, Player player) {
        this.module = module;
        this.player = player;
        this.inv = Bukkit.createInventory(this, 54, Text.mm("<gradient:#8a2be2:#c77dff>Boutique</gradient>"));
    }

    public void open() {
        render();
        player.openInventory(inv);
    }

    private void render() {
        inv.clear();
        if (currentCategory == null) {
            int slot = 10;
            for (ShopModule.ShopCategory cat : module.getCategories().values()) {
                if (slot > 43) break;
                ItemStack item = new ItemStack(Material.matchMaterial(cat.iconIcon()) != null ? Material.matchMaterial(cat.iconIcon()) : Material.CHEST);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(Text.mm("<yellow>" + cat.name()));
                    item.setItemMeta(meta);
                }
                inv.setItem(slot++, item);
            }
        } else {
            int slot = 0;
            CustomItemManagerService customItems = module.customItems();
            
            for (ShopModule.ShopItem sItem : currentCategory.items()) {
                if (slot > 44) break;
                ItemStack displayItem = null;
                if (sItem.customId() != null && customItems != null) {
                    displayItem = customItems.create(sItem.customId());
                }
                if (displayItem == null) {
                    Material mat = Material.matchMaterial(sItem.material());
                    if (mat != null) {
                        displayItem = new ItemStack(mat);
                    } else {
                        displayItem = new ItemStack(Material.STONE);
                    }
                }

                ItemMeta meta = displayItem.getItemMeta();
                if (meta != null) {
                    List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                    if (meta.hasLore()) {
                        lore.addAll(meta.lore());
                    }
                    lore.add(Text.mm(""));
                    if (sItem.buyPrice() > 0) lore.add(Text.mm("<gray>Achat (Clic Gauche): <green>" + sItem.buyPrice() + "$"));
                    if (sItem.sellPrice() > 0) lore.add(Text.mm("<gray>Vente (Clic Droit): <red>" + sItem.sellPrice() + "$"));
                    
                    meta.lore(lore);
                    displayItem.setItemMeta(meta);
                }
                inv.setItem(slot++, displayItem);
            }

            // Retour button
            ItemStack back = new ItemStack(Material.BARRIER);
            ItemMeta bMeta = back.getItemMeta();
            if (bMeta != null) {
                bMeta.displayName(Text.mm("<red>Retour"));
                back.setItemMeta(bMeta);
            }
            inv.setItem(49, back);
        }
    }

    public void click(Player p, int slot, boolean left, boolean right, boolean shift) {
        if (currentCategory == null) {
            int catIndex = slot - 10;
            if (catIndex >= 0 && catIndex < module.getCategories().size()) {
                currentCategory = (ShopModule.ShopCategory) module.getCategories().values().toArray()[catIndex];
                render();
            }
        } else {
            if (slot == 49) {
                currentCategory = null;
                render();
                return;
            }
            if (slot >= 0 && slot < currentCategory.items().size()) {
                ShopModule.ShopItem item = currentCategory.items().get(slot);
                handleTransaction(p, item, left, right, shift);
            }
        }
    }

    private void handleTransaction(Player p, ShopModule.ShopItem item, boolean left, boolean right, boolean shift) {
        EconomyService eco = module.economy();
        if (eco == null || !eco.isAvailable()) {
            p.sendMessage(Text.mm("<red>Service économique indisponible."));
            return;
        }
        CustomItemManagerService customItems = module.customItems();

        int amount = shift ? 64 : 1;

        if (left && item.buyPrice() > 0) {
            double cost = item.buyPrice() * amount;
            if (eco.withdraw(p.getUniqueId(), cost, "Achat boutique")) {
                ItemStack toGive = null;
                if (item.customId() != null && customItems != null) {
                    toGive = customItems.create(item.customId(), amount);
                } else {
                    Material mat = Material.matchMaterial(item.material());
                    if (mat != null) toGive = new ItemStack(mat, amount);
                }

                if (toGive != null) {
                    p.getInventory().addItem(toGive).values().forEach(excess -> p.getWorld().dropItem(p.getLocation(), excess));
                    p.sendMessage(Text.mm("<green>Vous avez acheté x" + amount + " pour " + cost + "$."));
                } else {
                    eco.deposit(p.getUniqueId(), cost, "Remboursement boutique (erreur item)");
                    p.sendMessage(Text.mm("<red>Erreur lors de la création de l'objet."));
                }
            } else {
                p.sendMessage(Text.mm("<red>Fonds insuffisants."));
            }
        } else if (right && item.sellPrice() > 0) {
            // Count items in inventory matching
            int count = 0;
            for (ItemStack i : p.getInventory().getContents()) {
                if (i == null || i.isEmpty()) continue;
                if (item.customId() != null) {
                    if (customItems != null && item.customId().equals(customItems.idOf(i))) {
                        count += i.getAmount();
                    }
                } else {
                    Material mat = Material.matchMaterial(item.material());
                    if (i.getType() == mat && (customItems == null || !customItems.isCustom(i))) {
                        count += i.getAmount();
                    }
                }
            }

            int toSell = Math.min(count, amount);
            if (toSell <= 0) {
                p.sendMessage(Text.mm("<red>Vous n'avez pas cet objet à vendre."));
                return;
            }

            // Remove items
            int removed = 0;
            for (int i = 0; i < p.getInventory().getSize(); i++) {
                ItemStack invItem = p.getInventory().getItem(i);
                if (invItem == null || invItem.isEmpty()) continue;

                boolean match = false;
                if (item.customId() != null) {
                    if (customItems != null && item.customId().equals(customItems.idOf(invItem))) {
                        match = true;
                    }
                } else {
                    Material mat = Material.matchMaterial(item.material());
                    if (invItem.getType() == mat && (customItems == null || !customItems.isCustom(invItem))) {
                        match = true;
                    }
                }

                if (match) {
                    int taking = Math.min(invItem.getAmount(), toSell - removed);
                    invItem.setAmount(invItem.getAmount() - taking);
                    removed += taking;
                    if (removed >= toSell) break;
                }
            }

            double gain = item.sellPrice() * removed;
            eco.depositWithTax(p.getUniqueId(), gain, "Vente boutique");
            p.sendMessage(Text.mm("<green>Vous avez vendu x" + removed + " pour " + gain + "$."));
        }
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
