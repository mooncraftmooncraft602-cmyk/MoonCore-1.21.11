package com.mooncore.modules.auction;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

@ModuleInfo(id = "auction", name = "Auction House", softDepends = {"economy", "customitem"})
public final class AuctionModule extends AbstractModule implements Listener {

    private final List<ActiveAuction> auctions = new ArrayList<>();
    private File file;
    private int cleanupTaskId = -1;

    @Override
    protected void onEnable() {
        file = new File(plugin().getDataFolder(), "auction.yml");
        loadAuctions();

        plugin().rootCommand().register(new AhCmd());
        plugin().rootCommand().register(new SellCmd());
        registerListener(this);

        cleanupTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin(), this::cleanupExpired, 1200L, 1200L);
    }

    @Override
    protected void onDisable() {
        if (cleanupTaskId != -1) {
            Bukkit.getScheduler().cancelTask(cleanupTaskId);
        }
        cleanupExpired();
        saveAuctions();
        auctions.clear();
    }

    private void loadAuctions() {
        auctions.clear();
        if (!file.isFile()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("auctions");
        if (s == null) return;

        for (String key : s.getKeys(false)) {
            ConfigurationSection as = s.getConfigurationSection(key);
            if (as == null) continue;
            try {
                UUID id = UUID.fromString(key);
                UUID seller = UUID.fromString(as.getString("seller"));
                String sellerName = as.getString("sellerName");
                double price = as.getDouble("price");
                long expireAt = as.getLong("expireAt");
                ItemStack item = as.getItemStack("item");
                
                if (item != null && !item.isEmpty()) {
                    auctions.add(new ActiveAuction(id, seller, sellerName, item, price, expireAt));
                }
            } catch (Exception e) {
                log().warn("Erreur de chargement d'une enchère : " + e.getMessage());
            }
        }
    }

    public void saveAuctions() {
        YamlConfiguration y = new YamlConfiguration();
        ConfigurationSection s = y.createSection("auctions");
        for (ActiveAuction a : auctions) {
            ConfigurationSection as = s.createSection(a.id.toString());
            as.set("seller", a.sellerId.toString());
            as.set("sellerName", a.sellerName);
            as.set("price", a.price);
            as.set("expireAt", a.expireAt);
            as.set("item", a.item);
        }
        try {
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            y.save(file);
        } catch (Exception e) {
            log().warn("Sauvegarde auction.yml échouée : " + e.getMessage());
        }
    }

    private void cleanupExpired() {
        boolean changed = false;
        long now = System.currentTimeMillis();
        Iterator<ActiveAuction> it = auctions.iterator();
        while (it.hasNext()) {
            ActiveAuction a = it.next();
            if (now > a.expireAt) {
                it.remove();
                changed = true;
                refundItem(a.sellerId, a.item);
            }
        }
        if (changed) saveAuctions();
    }

    public void refundItem(UUID playerId, ItemStack item) {
        Player p = Bukkit.getPlayer(playerId);
        if (p != null && p.isOnline()) {
            p.getInventory().addItem(item).values().forEach(excess -> p.getWorld().dropItem(p.getLocation(), excess));
            p.sendMessage(Text.mm("<red>Une de vos ventes a expiré ou a été annulée. L'objet a été retourné."));
        } else {
            // Need to save offline
            // But since DataManager offline storage isn't specified, we use a simple approach: return to auction house as 'expired' claimable?
            // Actually, for simplicity and lack of offline inventory API in Bukkit, we just add it to a claimable stash or drop it.
            // Let's add it to an "expired.yml" to be safe.
            saveOfflineRefund(playerId, item);
        }
    }

    private void saveOfflineRefund(UUID playerId, ItemStack item) {
        File expFile = new File(plugin().getDataFolder(), "auction_refunds.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(expFile);
        List<ItemStack> items = (List<ItemStack>) y.getList(playerId.toString(), new ArrayList<>());
        items.add(item);
        y.set(playerId.toString(), items);
        try { y.save(expFile); } catch (Exception ignored) {}
    }

    public void checkRefunds(Player p) {
        File expFile = new File(plugin().getDataFolder(), "auction_refunds.yml");
        if (!expFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(expFile);
        List<?> items = y.getList(p.getUniqueId().toString());
        if (items != null && !items.isEmpty()) {
            for (Object obj : items) {
                if (obj instanceof ItemStack item) {
                    p.getInventory().addItem(item).values().forEach(excess -> p.getWorld().dropItem(p.getLocation(), excess));
                }
            }
            y.set(p.getUniqueId().toString(), null);
            try { y.save(expFile); } catch (Exception ignored) {}
            p.sendMessage(Text.mm("<green>Vos objets expirés de l'AH ont été restitués dans votre inventaire."));
        }
    }

    public List<ActiveAuction> getAuctions() {
        return auctions;
    }

    public com.mooncore.api.economy.EconomyService economy() {
        return services().get(com.mooncore.api.economy.EconomyService.class).orElse(null);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof AuctionMenu menu) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;
            if (e.getWhoClicked() instanceof Player p) {
                menu.click(p, e.getRawSlot());
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof AuctionMenu) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        checkRefunds(e.getPlayer());
    }

    private final class AhCmd implements SubCommand {
        @Override public String name() { return "ah"; }
        @Override public String permission() { return "mooncore.ah.use"; }
        @Override public String description() { return "Ouvre l'hôtel des ventes"; }
        @Override public String category() { return "player"; }
        @Override public boolean playerOnly() { return true; }

        @Override
        public void execute(MoonCore pl, CommandSender s, String[] a) {
            new AuctionMenu(AuctionModule.this, (Player) s).open();
        }
    }

    private final class SellCmd implements SubCommand {
        @Override public String name() { return "ahsell"; }
        @Override public List<String> aliases() { return List.of("sell"); } // Actually this will be /moon ahsell, we can do /ah sell via arguments in AhCmd, but SubCommand architecture usually mounts it flat. We will mount it as `ahsell`.
        @Override public String permission() { return "mooncore.ah.use"; }
        @Override public String description() { return "Met en vente l'objet en main"; }
        @Override public String category() { return "player"; }
        @Override public boolean playerOnly() { return true; }

        @Override
        public void execute(MoonCore pl, CommandSender s, String[] a) {
            Player p = (Player) s;
            if (a.length < 1) {
                p.sendMessage(Text.mm("<red>Usage : /moon ahsell <prix>"));
                return;
            }
            double price;
            try { price = Double.parseDouble(a[0]); } catch (NumberFormatException e) {
                p.sendMessage(Text.mm("<red>Prix invalide.")); return;
            }
            if (price <= 0) { p.sendMessage(Text.mm("<red>Le prix doit être supérieur à 0.")); return; }
            
            ItemStack item = p.getInventory().getItemInMainHand();
            if (item.getType() == Material.AIR) {
                p.sendMessage(Text.mm("<red>Vous devez tenir un objet en main."));
                return;
            }

            ActiveAuction auc = new ActiveAuction(UUID.randomUUID(), p.getUniqueId(), p.getName(), item.clone(), price, System.currentTimeMillis() + (86400L * 1000L * 2)); // 48h
            auctions.add(auc);
            saveAuctions();
            p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            p.sendMessage(Text.mm("<green>Objet mis en vente pour " + price + "$."));
        }
    }

    public static class ActiveAuction {
        public UUID id;
        public UUID sellerId;
        public String sellerName;
        public ItemStack item;
        public double price;
        public long expireAt;

        public ActiveAuction(UUID id, UUID sellerId, String sellerName, ItemStack item, double price, long expireAt) {
            this.id = id;
            this.sellerId = sellerId;
            this.sellerName = sellerName;
            this.item = item;
            this.price = price;
            this.expireAt = expireAt;
        }
    }
}
