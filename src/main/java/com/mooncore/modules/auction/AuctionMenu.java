package com.mooncore.modules.auction;

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

public class AuctionMenu implements InventoryHolder {

    private final AuctionModule module;
    private final Player player;
    private final Inventory inv;
    private int page = 0;

    public AuctionMenu(AuctionModule module, Player player) {
        this.module = module;
        this.player = player;
        this.inv = Bukkit.createInventory(this, 54, Text.mm("<gradient:#8a2be2:#c77dff>Hôtel des ventes</gradient>"));
    }

    public void open() {
        render();
        player.openInventory(inv);
    }

    private void render() {
        inv.clear();
        List<AuctionModule.ActiveAuction> all = module.getAuctions();
        int maxPages = Math.max(1, (int) Math.ceil(all.size() / 45.0));
        if (page >= maxPages) page = maxPages - 1;

        int start = page * 45;
        for (int i = 0; i < 45; i++) {
            if (start + i >= all.size()) break;
            AuctionModule.ActiveAuction auc = all.get(start + i);
            ItemStack display = auc.item.clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<net.kyori.adventure.text.Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.add(Text.mm(""));
                lore.add(Text.mm("<gray>Vendeur : <white>" + auc.sellerName));
                lore.add(Text.mm("<gray>Prix : <green>" + auc.price + "$"));
                if (auc.sellerId.equals(player.getUniqueId())) {
                    lore.add(Text.mm("<red>Clic pour annuler"));
                } else {
                    lore.add(Text.mm("<yellow>Clic pour acheter"));
                }
                meta.lore(lore);
                display.setItemMeta(meta);
            }
            inv.setItem(i, display);
        }

        // Pagination
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta m = prev.getItemMeta();
            if (m != null) { m.displayName(Text.mm("<yellow>Page précédente")); prev.setItemMeta(m); }
            inv.setItem(45, prev);
        }
        if (page < maxPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta m = next.getItemMeta();
            if (m != null) { m.displayName(Text.mm("<yellow>Page suivante")); next.setItemMeta(m); }
            inv.setItem(53, next);
        }

        // Info
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta iMeta = info.getItemMeta();
        if (iMeta != null) {
            iMeta.displayName(Text.mm("<aqua>Informations"));
            double bal = module.economy() != null ? module.economy().balance(player.getUniqueId()) : 0.0;
            iMeta.lore(List.of(
                    Text.mm("<gray>Solde : <green>" + bal + "$"),
                    Text.mm("<gray>Pour vendre : <white>/moon ahsell <prix>")
            ));
            info.setItemMeta(iMeta);
        }
        inv.setItem(49, info);
    }

    public void click(Player p, int slot) {
        if (slot == 45 && page > 0) {
            page--;
            render();
            return;
        }
        if (slot == 53) {
            page++;
            render();
            return;
        }
        if (slot >= 0 && slot < 45) {
            int idx = page * 45 + slot;
            if (idx >= module.getAuctions().size()) return;
            AuctionModule.ActiveAuction auc = module.getAuctions().get(idx);

            if (auc.sellerId.equals(p.getUniqueId())) {
                // Annuler
                module.getAuctions().remove(idx);
                module.saveAuctions();
                p.getInventory().addItem(auc.item).values().forEach(e -> p.getWorld().dropItem(p.getLocation(), e));
                p.sendMessage(Text.mm("<yellow>Vente annulée."));
                render();
            } else {
                // Acheter
                EconomyService eco = module.economy();
                if (eco == null || !eco.isAvailable()) {
                    p.sendMessage(Text.mm("<red>Service économique indisponible."));
                    return;
                }
                if (eco.withdraw(p.getUniqueId(), auc.price, "Achat AH")) {
                    eco.depositWithTax(auc.sellerId, auc.price, "Vente AH (" + auc.item.getType().name() + ")");
                    module.getAuctions().remove(idx);
                    module.saveAuctions();
                    p.getInventory().addItem(auc.item).values().forEach(e -> p.getWorld().dropItem(p.getLocation(), e));
                    p.sendMessage(Text.mm("<green>Achat effectué !"));
                    
                    Player seller = Bukkit.getPlayer(auc.sellerId);
                    if (seller != null && seller.isOnline()) {
                        seller.sendMessage(Text.mm("<green>Votre " + auc.item.getType().name() + " a été vendu pour " + auc.price + "$."));
                    }
                    render();
                } else {
                    p.sendMessage(Text.mm("<red>Fonds insuffisants."));
                }
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
