package com.mooncore.modules.market;

import com.mooncore.api.customitem.CustomItemManagerService;
import com.mooncore.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI du marché dynamique : une marchandise par case, prix d'achat/vente <b>en direct</b> dans le lore.
 * Clic gauche = acheter 1 (Shift = 64), clic droit = vendre 1 (Shift = tout). Le menu se rafraîchit après
 * chaque transaction pour montrer le prix qui bouge.
 */
public final class MarketMenu implements InventoryHolder {

    private final MarketModule module;
    private final Player player;
    private final Inventory inv;
    private final List<MarketItem> view = new ArrayList<>();

    public MarketMenu(MarketModule module, Player player) {
        this.module = module;
        this.player = player;
        int rows = Math.min(6, Math.max(1, (module.items().size() + 8) / 9));
        this.inv = Bukkit.createInventory(this, rows * 9, Text.mm("<gradient:#2ecc71:#27ae60>Marché dynamique</gradient>"));
    }

    public void open() { render(); player.openInventory(inv); }

    private void render() {
        inv.clear();
        view.clear();
        CustomItemManagerService ci = module.customItems();
        int slot = 0;
        for (MarketItem m : module.items()) {
            if (slot >= inv.getSize()) break;
            ItemStack disp = display(m, ci);
            inv.setItem(slot, disp);
            view.add(m);
            slot++;
        }
    }

    private ItemStack display(MarketItem m, CustomItemManagerService ci) {
        ItemStack disp = null;
        if (m.isCustom() && ci != null) disp = ci.create(m.customId(), 1);
        if (disp == null) {
            Material mat = Material.matchMaterial(m.material());
            disp = new ItemStack(mat == null || mat.isAir() ? Material.PAPER : mat);
        }
        ItemMeta meta = disp.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.mm("<white>" + m.displayName() + " " + trend(m)));
            List<Component> lore = new ArrayList<>();
            lore.add(Text.mm("<gray>Achat <dark_gray>(clic G)<gray> : <green>" + MarketModule.round2(m.unitBuyPrice()) + "$"));
            lore.add(Text.mm("<gray>Vente <dark_gray>(clic D)<gray> : <red>" + MarketModule.round2(m.unitSellPrice()) + "$"));
            lore.add(Text.mm("<dark_gray>Shift = ×64 / tout"));
            lore.add(Text.mm("<dark_gray>stock " + Math.round(m.stock()) + "/" + Math.round(m.equilibrium())));
            meta.lore(lore);
            disp.setItemMeta(meta);
        }
        return disp;
    }

    private static String trend(MarketItem m) {
        int pct = (int) Math.round((m.marketIndex() - 1.0) * 100);
        if (pct > 1) return "<red>▲+" + pct + "%";
        if (pct < -1) return "<green>▼" + pct + "%";
        return "<gray>=";
    }

    /** Appelé par le listener du module. */
    public void click(Player p, int slot, boolean left, boolean right, boolean shift) {
        if (slot < 0 || slot >= view.size()) return;
        MarketItem m = view.get(slot);
        MarketModule.TxResult r;
        if (left) {
            r = module.buy(p, m.id(), shift ? 64 : 1);
        } else if (right) {
            int qty = shift ? module.countOwned(p, m) : 1;
            r = module.sell(p, m.id(), Math.max(1, qty));
        } else {
            return;
        }
        p.sendMessage(Text.mm(r.message()));
        render();   // rafraîchit les prix affichés
    }

    @Override
    public Inventory getInventory() { return inv; }
}
