package com.mooncore.modules.market;

import com.mooncore.api.customitem.CustomItemManagerService;
import com.mooncore.api.economy.EconomyService;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Marché dynamique « hyper réaliste » : chaque marchandise a un prix piloté par l'offre/la demande
 * ({@link MarketPricing}). Acheter retire du stock (le prix monte), vendre en ajoute (le prix baisse), et la
 * <b>capacité de production</b> de chaque item ramène lentement son stock — donc son prix — vers l'équilibre.
 * L'état (stock) est persisté dans {@code market.yml}. Sert de boutique admin pour amorcer l'économie.
 */
@ModuleInfo(id = "market", name = "Market", softDepends = {"economy", "customitem"})
public final class MarketModule extends AbstractModule implements Listener {

    private final Map<String, MarketItem> items = new LinkedHashMap<>();
    private File file;
    private BukkitTask productionTask;
    private BukkitTask saveTask;

    /** Résultat d'une transaction (pour le message au joueur). */
    public record TxResult(boolean ok, String message) {}

    @Override
    protected void onEnable() {
        file = new File(plugin().getDataFolder(), "market.yml");
        if (!file.isFile()) {
            seedDefaults();      // amorce un marché de départ
            saveAll();
        }
        load();

        plugin().rootCommand().register(new MarketSubCommand(this));
        registerListener(this);

        // Tick de production : période config (défaut 60 s) → mean-reversion des prix vers la base.
        long period = Math.max(20L, plugin().getConfig().getLong("market.production-period-ticks", 1200L));
        productionTask = schedulers().syncTimer(this::tickProduction, period, period);
        // Sauvegarde périodique du stock (défaut 5 min).
        long savePeriod = Math.max(1200L, plugin().getConfig().getLong("market.save-period-ticks", 6000L));
        saveTask = schedulers().asyncTimer(this::saveAll, savePeriod, savePeriod);

        log().info("Market : " + items.size() + " marchandise(s).");
    }

    @Override
    protected void onDisable() {
        if (productionTask != null) { productionTask.cancel(); productionTask = null; }
        if (saveTask != null) { saveTask.cancel(); saveTask = null; }
        saveAll();
        items.clear();
    }

    // ---- Données ----

    public Collection<MarketItem> items() { return items.values(); }
    public MarketItem item(String id) { return id == null ? null : items.get(id.toLowerCase(Locale.ROOT)); }
    public boolean exists(String id) { return item(id) != null; }

    public void put(MarketItem m) { items.put(m.id(), m); }
    public boolean remove(String id) { return id != null && items.remove(id.toLowerCase(Locale.ROOT)) != null; }

    public CustomItemManagerService customItems() { return services().get(CustomItemManagerService.class).orElse(null); }
    public EconomyService economy() { return services().get(EconomyService.class).orElse(null); }

    private void tickProduction() {
        for (MarketItem m : items.values()) m.tickProduction();
    }

    // ---- GUI ----

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof MarketMenu menu) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;
            if (e.getWhoClicked() instanceof Player p) {
                menu.click(p, e.getRawSlot(), e.isLeftClick(), e.isRightClick(), e.isShiftClick());
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MarketMenu) e.setCancelled(true);
    }

    // ---- Transactions ----

    /** Arrondi monétaire à 2 décimales. */
    public static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    /** Achat de {@code qty} unités : débite le joueur, donne les items, retire du stock (le prix monte). */
    public TxResult buy(Player p, String id, int qty) {
        MarketItem m = item(id);
        if (m == null) return new TxResult(false, "<red>Marchandise inconnue : " + id);
        EconomyService eco = economy();
        if (eco == null || !eco.isAvailable()) return new TxResult(false, "<red>Économie indisponible.");
        int n = Math.max(1, Math.min(2304, qty));            // plafond : 36 piles
        double cost = round2(m.buyCost(n));
        if (cost <= 0) return new TxResult(false, "<red>Cet item n'est pas à vendre.");
        if (!eco.has(p.getUniqueId(), cost)) {
            return new TxResult(false, "<red>Fonds insuffisants : il faut <white>" + cost + "$<red> pour " + n + "×.");
        }
        ItemStack proto = create(m, 1);
        if (proto == null) return new TxResult(false, "<red>Item introuvable (config marché invalide).");
        if (!eco.withdraw(p.getUniqueId(), cost, "market:buy:" + m.id())) {
            return new TxResult(false, "<red>Débit refusé.");
        }
        giveStacks(p, m, n);
        m.addStock(-n);                                       // l'achat raréfie → prix ↑
        return new TxResult(true, "<green>Acheté <white>" + n + "×<green> " + m.displayName()
                + " <gray>pour <white>" + cost + "$<gray> (prix unitaire désormais <white>" + round2(m.unitBuyPrice()) + "$<gray>).");
    }

    /** Vente de {@code qty} unités présentes dans l'inventaire : crédite le joueur, ajoute au stock (prix ↓). */
    public TxResult sell(Player p, String id, int qty) {
        MarketItem m = item(id);
        if (m == null) return new TxResult(false, "<red>Marchandise inconnue : " + id);
        EconomyService eco = economy();
        if (eco == null || !eco.isAvailable()) return new TxResult(false, "<red>Économie indisponible.");
        int want = Math.max(1, Math.min(2304, qty));
        int owned = countOwned(p, m);
        int toSell = Math.min(owned, want);
        if (toSell <= 0) return new TxResult(false, "<red>Tu n'as pas cet objet à vendre.");
        double revenue = round2(m.sellRevenue(toSell));
        int removed = removeItems(p, m, toSell);
        if (removed <= 0) return new TxResult(false, "<red>Rien retiré.");
        if (removed != toSell) revenue = round2(m.sellRevenue(removed));   // sécurité : ce qui a vraiment été retiré
        eco.depositWithTax(p.getUniqueId(), revenue, "market:sell:" + m.id());
        m.addStock(removed);                                  // la vente sature → prix ↓
        return new TxResult(true, "<green>Vendu <white>" + removed + "×<green> " + m.displayName()
                + " <gray>pour <white>" + revenue + "$<gray> (prix unitaire désormais <white>" + round2(m.unitSellPrice()) + "$<gray>).");
    }

    // ---- Helpers items (LIVE) ----

    private ItemStack create(MarketItem m, int amount) {
        if (m.isCustom()) {
            CustomItemManagerService ci = customItems();
            return ci == null ? null : ci.create(m.customId(), amount);
        }
        Material mat = Material.matchMaterial(m.material());
        return (mat == null || mat.isAir()) ? null : new ItemStack(mat, amount);
    }

    /** Donne {@code qty} unités en découpant par pile (le surplus est lâché au sol). */
    private void giveStacks(Player p, MarketItem m, int qty) {
        int remaining = qty;
        while (remaining > 0) {
            ItemStack proto = create(m, 1);
            int max = proto == null ? 64 : Math.max(1, proto.getMaxStackSize());
            int n = Math.min(remaining, max);
            ItemStack stack = create(m, n);
            if (stack == null) return;
            for (ItemStack overflow : p.getInventory().addItem(stack).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), overflow);
            }
            remaining -= n;
        }
    }

    /** Nombre d'unités de cette marchandise dans l'inventaire du joueur. */
    public int countOwned(Player p, MarketItem m) {
        CustomItemManagerService ci = customItems();
        int count = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (matches(it, m, ci)) count += it.getAmount();
        }
        return count;
    }

    private int removeItems(Player p, MarketItem m, int toRemove) {
        CustomItemManagerService ci = customItems();
        int removed = 0;
        for (int i = 0; i < p.getInventory().getSize() && removed < toRemove; i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (!matches(it, m, ci)) continue;
            int taking = Math.min(it.getAmount(), toRemove - removed);
            it.setAmount(it.getAmount() - taking);
            removed += taking;
        }
        return removed;
    }

    private boolean matches(ItemStack it, MarketItem m, CustomItemManagerService ci) {
        if (it == null || it.getType().isAir()) return false;
        if (m.isCustom()) {
            return ci != null && m.customId().equals(ci.idOf(it));
        }
        Material mat = Material.matchMaterial(m.material());
        return it.getType() == mat && (ci == null || !ci.isCustom(it));
    }

    // ---- Persistance ----

    public void load() {
        items.clear();
        if (!file.isFile()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("items");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection is = sec.getConfigurationSection(key);
            if (is != null) put(MarketItem.load(key, is));
        }
    }

    public void saveAll() {
        try {
            YamlConfiguration y = new YamlConfiguration();
            for (MarketItem m : items.values()) {
                m.save(y.createSection("items." + m.id()));
            }
            y.save(file);
        } catch (Exception e) {
            log().warn("[Market] Sauvegarde market.yml échouée : " + e.getMessage());
        }
    }

    /** Crée un marché de départ représentatif (matières premières abondantes bon marché → ressources rares chères). */
    private void seedDefaults() {
        // id, material, base, équilibre, élasticité, production/tick, marge vente
        seed("wheat", "WHEAT", 2.0, 4000, 0.8, 80, 0.6);
        seed("oak_log", "OAK_LOG", 3.0, 3000, 0.9, 60, 0.6);
        seed("cobblestone", "COBBLESTONE", 1.0, 6000, 0.7, 120, 0.5);
        seed("coal", "COAL", 5.0, 2000, 1.0, 30, 0.65);
        seed("iron_ingot", "IRON_INGOT", 15.0, 1200, 1.1, 15, 0.7);
        seed("gold_ingot", "GOLD_INGOT", 40.0, 600, 1.2, 6, 0.72);
        seed("redstone", "REDSTONE", 8.0, 1500, 1.0, 20, 0.65);
        seed("lapis_lazuli", "LAPIS_LAZULI", 12.0, 1000, 1.1, 12, 0.68);
        seed("emerald", "EMERALD", 120.0, 300, 1.4, 2, 0.75);
        seed("diamond", "DIAMOND", 250.0, 200, 1.5, 1, 0.78);
        seed("netherite_scrap", "NETHERITE_SCRAP", 800.0, 60, 1.8, 0.2, 0.8);
        seed("ancient_debris", "ANCIENT_DEBRIS", 1500.0, 40, 2.0, 0.1, 0.82);
    }

    private void seed(String id, String material, double base, double equilibrium,
                      double elasticity, double production, double sellMargin) {
        MarketItem m = new MarketItem(id);
        m.setMaterial(material);
        m.setDisplayName(material.toLowerCase(Locale.ROOT).replace('_', ' '));
        m.setBasePrice(base);
        m.setEquilibrium(equilibrium);
        m.setStock(equilibrium);
        m.setElasticity(elasticity);
        m.setProduction(production);
        m.setSellMargin(sellMargin);
        put(m);
    }
}
