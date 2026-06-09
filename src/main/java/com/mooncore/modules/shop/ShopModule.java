package com.mooncore.modules.shop;

import com.mooncore.MoonCore;
import com.mooncore.api.customitem.CustomItemManagerService;
import com.mooncore.api.economy.EconomyService;
import com.mooncore.command.SubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ModuleInfo(id = "shop", name = "Shop", softDepends = {"economy", "customitem"})
public final class ShopModule extends AbstractModule implements Listener {

    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();
    private File file;

    @Override
    protected void onEnable() {
        file = new File(plugin().getDataFolder(), "shop.yml");
        if (!file.exists()) {
            plugin().saveResource("modules/shop.yml", false);
            file = new File(plugin().getDataFolder(), "modules/shop.yml");
            if (file.exists()) {
                file.renameTo(new File(plugin().getDataFolder(), "shop.yml"));
            }
        }
        loadShop();

        plugin().rootCommand().register(new ShopCmd());
        plugin().rootCommand().register(new AdminShopCmd());
        registerListener(this);
    }

    @Override
    protected void onDisable() {
        categories.clear();
    }

    private void loadShop() {
        categories.clear();
        file = new File(plugin().getDataFolder(), "shop.yml");
        if (!file.isFile()) return;

        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection cats = y.getConfigurationSection("categories");
        if (cats == null) return;

        for (String catKey : cats.getKeys(false)) {
            ConfigurationSection s = cats.getConfigurationSection(catKey);
            if (s == null) continue;
            ShopCategory category = new ShopCategory(s.getString("name", catKey), s.getString("icon", "CHEST"));
            
            ConfigurationSection items = s.getConfigurationSection("items");
            if (items != null) {
                for (String itemKey : items.getKeys(false)) {
                    ConfigurationSection is = items.getConfigurationSection(itemKey);
                    if (is == null) continue;
                    category.items().add(new ShopItem(
                            is.getString("material", "STONE"),
                            is.getString("custom_id", null),
                            is.getDouble("buy_price", -1),
                            is.getDouble("sell_price", -1)
                    ));
                }
            }
            categories.put(catKey, category);
        }
    }

    public Map<String, ShopCategory> getCategories() {
        return categories;
    }

    public CustomItemManagerService customItems() {
        return services().get(CustomItemManagerService.class).orElse(null);
    }

    public EconomyService economy() {
        return services().get(EconomyService.class).orElse(null);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof ShopMenu menu) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;
            if (e.getWhoClicked() instanceof Player p) {
                menu.click(p, e.getRawSlot(), e.isLeftClick(), e.isRightClick(), e.isShiftClick());
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof ShopMenu) {
            e.setCancelled(true);
        }
    }

    private final class ShopCmd implements SubCommand {
        @Override public String name() { return "shop"; }
        @Override public String permission() { return "mooncore.shop.use"; }
        @Override public String description() { return "Ouvre la boutique du serveur"; }
        @Override public String category() { return "player"; }
        @Override public boolean playerOnly() { return true; }

        @Override
        public void execute(MoonCore pl, CommandSender s, String[] a) {
            new ShopMenu(ShopModule.this, (Player) s).open();
        }
    }

    private final class AdminShopCmd implements SubCommand {
        @Override public String name() { return "adminshop"; }
        @Override public String permission() { return "mooncore.admin.shop"; }
        @Override public String description() { return "(admin) Recharge la configuration du shop"; }
        @Override public String category() { return "admin"; }

        @Override
        public void execute(MoonCore pl, CommandSender s, String[] a) {
            loadShop();
            s.sendMessage(Text.mm("<green>Boutique rechargée avec succès."));
        }
    }

    public record ShopCategory(String name, String iconIcon, List<ShopItem> items) {
        public ShopCategory(String name, String iconIcon) {
            this(name, iconIcon, new java.util.ArrayList<>());
        }
    }

    public record ShopItem(String material, String customId, double buyPrice, double sellPrice) {}
}
