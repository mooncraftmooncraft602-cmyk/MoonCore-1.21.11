package com.mooncore.modules.customitem;

import com.mooncore.api.customitem.CustomItemManagerService;
import com.mooncore.api.customitem.Rarity;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.data.content.ContentJson;
import com.mooncore.data.content.ContentSyncService;
import com.mooncore.data.content.UniversalContentStore;
import com.mooncore.modules.customitem.ability.AbilityRegistry;
import com.mooncore.modules.customitem.command.CustomItemSubCommand;
import com.mooncore.util.Cooldowns;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Gestionnaire d'objets custom data-driven (armes, armures, outils, artefacts,
 * reliques, objets de boss/event/endgame, récompenses saisonnières).
 * <p>
 * Définitions stockées en YAML ({@code items/<id>.yml}), éditables en jeu via
 * {@code /moon item ...}. Stats appliquées via attributs vanilla + lore ; capacités
 * actives au clic droit, passives en continu. Conçu pour fonctionner identiquement
 * sur Java et Bedrock (Geyser/Floodgate) — voir {@code docs/REVIEW-customitem.md}.
 */
@ModuleInfo(
        id = "custom-item",
        name = "CustomItemManager",
        softDepends = {"boss", "event", "reward", "progression", "custom-enchant"}
)
public final class CustomItemManagerModule extends AbstractModule
        implements CustomItemManagerService, CustomItemFactory.RarityResolver {

    private final Map<String, CustomItemDef> defs = new LinkedHashMap<>();
    private final Map<Rarity, String> rarityColors = new LinkedHashMap<>();
    private final Map<Rarity, String> rarityLabels = new LinkedHashMap<>();
    private final Cooldowns<String> abilityCooldowns = new Cooldowns<>();

    /** Type de contenu pour le store universel SQL (Étape A). */
    private static final String CONTENT_TYPE = "item";

    private NamespacedKey idKey;
    private NamespacedKey bossKey;
    private CustomItemDefStore store;
    private ContentSyncService contentSync;     // null = miroir SQL indisponible → YAML pur
    private CustomItemFactory factory;
    private AbilityRegistry abilities;
    private RecipeManager recipeManager;
    private CustomItemListener listener;
    private com.mooncore.modules.customitem.paint.PaintManager paintManager;
    private com.mooncore.util.ChatInput chatInput;
    private org.bukkit.scheduler.BukkitTask passiveTask;

    @Override
    protected void onEnable() throws Exception {
        this.idKey = new NamespacedKey(plugin(), "ci_id");
        this.bossKey = new NamespacedKey(plugin(), "boss");

        loadRarities();

        this.abilities = new AbilityRegistry(plugin());
        this.factory = new CustomItemFactory(plugin(), idKey, abilities, this);
        this.store = new CustomItemDefStore(plugin().getDataFolder(), log());
        setupContentSync();

        reloadDefinitions();

        services().register(CustomItemManagerService.class, this);

        this.listener = new CustomItemListener(this);
        registerListener(listener);

        this.paintManager = new com.mooncore.modules.customitem.paint.PaintManager(plugin());
        registerListener(new com.mooncore.modules.customitem.paint.PaintListener(paintManager));

        this.chatInput = new com.mooncore.util.ChatInput(plugin());
        registerListener(chatInput);
        registerListener(new com.mooncore.modules.customitem.editor.ItemEditorListener());

        this.recipeManager = new RecipeManager(plugin(), this);
        recipeManager.registerAll();

        plugin().rootCommand().register(new CustomItemSubCommand(this));
        plugin().rootCommand().register(new com.mooncore.modules.customitem.forge.ForgeSubCommand(this));

        // Tick des effets passifs continus (régén/célérité) toutes les 2 s.
        this.passiveTask = schedulers().syncTimer(listener::tickPassives, 40L, 40L);

        log().info("CustomItemManager : " + defs.size() + " objet(s), "
                + abilities.all().size() + " capacité(s).");
    }

    @Override
    protected void onDisable() {
        if (passiveTask != null) passiveTask.cancel();
        if (paintManager != null) paintManager.closeAll();
        if (recipeManager != null) recipeManager.unregisterAll();
        abilityCooldowns.clearAll();
        defs.clear();
        services().unregister(CustomItemManagerService.class);
    }

    @Override
    protected void onReload() {
        reloadModuleConfig();
        loadRarities();
        reloadDefinitions();
        if (recipeManager != null) {
            recipeManager.unregisterAll();
            recipeManager.registerAll();
        }
    }

    private void loadRarities() {
        rarityColors.clear();
        rarityLabels.clear();
        var sec = moduleConfig().getConfigurationSection("rarities");
        for (Rarity r : Rarity.values()) {
            String color = r.defaultColor();
            String label = r.defaultLabel();
            if (sec != null) {
                var rs = sec.getConfigurationSection(r.id());
                if (rs != null) {
                    color = rs.getString("color", color);
                    label = rs.getString("label", label);
                }
            }
            rarityColors.put(r, color);
            rarityLabels.put(r, label);
        }
    }

    // ============================================================
    //  CustomItemManagerService
    // ============================================================

    @Override public Set<String> ids() { return Set.copyOf(defs.keySet()); }

    @Override public Collection<? extends CustomItemView> all() { return defs.values(); }

    @Override public CustomItemView definition(String id) {
        return id == null ? null : defs.get(id.toLowerCase(java.util.Locale.ROOT));
    }

    @Override public ItemStack create(String id) { return create(id, 1); }

    @Override public ItemStack create(String id, int amount) {
        CustomItemDef def = rawDef(id);
        return def == null ? null : factory.build(def, amount);
    }

    @Override public boolean give(Player player, String id, int amount) {
        ItemStack item = create(id, amount);
        if (item == null) return false;
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        return true;
    }

    @Override public String idOf(ItemStack item) { return factory.idOf(item); }

    @Override public boolean isCustom(ItemStack item) { return idOf(item) != null; }

    /**
     * Initialise le miroir SQL requêtable (Étape A3) si la base est prête. En cas d'échec,
     * {@link #contentSync} reste {@code null} et le module se comporte en YAML pur (aucune régression).
     */
    private void setupContentSync() {
        try {
            var dm = data();
            if (dm != null && dm.isReady()) {
                dm.applyMigrations(ContentSyncService.migrations());
                this.contentSync = new ContentSyncService(dm.database(),
                        () -> plugin().getConfig().getString("content.storage-mode", "yaml"), log());
            }
        } catch (Exception e) {
            log().error("Init du miroir SQL de contenu échouée (repli YAML pur)", e);
            this.contentSync = null;
        }
    }

    @Override
    public void reloadDefinitions() {
        defs.clear();
        Map<String, CustomItemDef> loaded = new LinkedHashMap<>();
        // Base YAML (sauf en mode SQL pur où le YAML n'est plus autoritaire).
        if (contentSync == null || contentSync.writesYaml()) {
            loaded.putAll(store.loadAll());
        }
        // Recouvrement SQL (mode sql|both). Repli silencieux sur YAML si la lecture échoue.
        if (contentSync != null && contentSync.writesSql()) {
            try {
                Map<String, UniversalContentStore.Row> rows = contentSync.store().loadAll(CONTENT_TYPE).join();
                for (UniversalContentStore.Row row : rows.values()) {
                    try {
                        // Rétro-compat intra-objet : met à niveau la forme JSON si stockée dans une version antérieure.
                        String json = com.mooncore.data.content.ContentSchemas.upgradeToCurrent(
                                CONTENT_TYPE, row.dataJson(), row.schemaVersion());
                        loaded.put(row.id(), CustomItemDef.load(row.id(), ContentJson.toSection(json)));
                    } catch (Exception ex) {
                        log().error("Item SQL invalide ignoré : " + row.id(), ex);
                    }
                }
            } catch (Exception e) {
                log().error("Chargement SQL des items échoué, repli YAML.", e);
            }
        }
        defs.putAll(loaded);
    }

    // ============================================================
    //  RarityResolver
    // ============================================================

    @Override public String color(Rarity rarity) { return rarityColors.getOrDefault(rarity, rarity.defaultColor()); }
    @Override public String label(Rarity rarity) { return rarityLabels.getOrDefault(rarity, rarity.defaultLabel()); }

    // ============================================================
    //  Accès internes (listener / commande)
    // ============================================================

    public CustomItemDef rawDef(String id) {
        return id == null ? null : defs.get(id.toLowerCase(java.util.Locale.ROOT));
    }

    public Map<String, CustomItemDef> rawDefs() { return defs; }

    public void put(CustomItemDef def) {
        defs.put(def.id(), def);
        if (contentSync == null || contentSync.writesYaml()) {
            store.save(def);
        }
        if (contentSync != null) {
            int version = com.mooncore.data.content.ContentSchemas.currentVersion(CONTENT_TYPE);
            contentSync.mirror(CONTENT_TYPE, def.id(), toJson(def), version, System.currentTimeMillis());
        }
    }

    public boolean removeDef(String id) {
        String norm = id.toLowerCase(java.util.Locale.ROOT);
        boolean disk = store.delete(norm);
        boolean mem = defs.remove(norm) != null;
        if (contentSync != null) {
            contentSync.remove(CONTENT_TYPE, norm);
        }
        if (mem && !disk && (contentSync == null || contentSync.writesYaml())) {
            log().warn("Objet custom « " + norm + " » retiré de la mémoire mais son fichier n'a pas pu "
                    + "être supprimé : il réapparaîtra au prochain reload.");
        }
        return disk || mem;
    }

    /** Sérialise une définition d'item en JSON via le pont YAML↔JSON (réutilise {@code def.save}). */
    private static String toJson(CustomItemDef def) {
        org.bukkit.configuration.MemoryConfiguration cfg = new org.bukkit.configuration.MemoryConfiguration();
        def.save(cfg);
        return ContentJson.toJson(cfg);
    }

    public CustomItemDefStore store() { return store; }
    public CustomItemFactory factory() { return factory; }
    public AbilityRegistry abilities() { return abilities; }
    public Cooldowns<String> abilityCooldowns() { return abilityCooldowns; }
    public NamespacedKey idKey() { return idKey; }
    public NamespacedKey bossKey() { return bossKey; }
    public RecipeManager recipeManager() { return recipeManager; }
    public com.mooncore.modules.customitem.paint.PaintManager paintManager() { return paintManager; }
    public com.mooncore.util.ChatInput chatInput() { return chatInput; }

    /** Sensibilité par défaut du curseur de l'éditeur de texture (réglable aussi en jeu).
     *  Bas = curseur lent et précis (recommandé) ; haut = rapide. */
    public double paintCursorSensitivity() {
        return moduleConfig().getDouble("paint.cursor-sensitivity", 0.8);
    }

    public ItemStack buildItem(CustomItemDef def, int amount) { return factory.build(def, amount); }

    /**
     * Résultat de la recette de fonte d'un item custom : un autre item <b>custom</b>
     * ({@code smeltsIntoCustom}, prioritaire) ou un {@link org.bukkit.Material} vanilla
     * ({@code smeltsInto}). {@code null} si l'item ne fond pas ou si l'item custom
     * résultat est introuvable. Utilisé par {@link RecipeManager} (recette) et par
     * {@link CustomItemListener} (filet de sécurité {@code FurnaceSmeltEvent}).
     */
    public ItemStack smeltOutput(CustomItemDef def) {
        if (def == null || !def.canSmelt()) return null;
        int amt = Math.max(1, def.smeltAmount());
        if (def.smeltsIntoCustom() != null) {
            CustomItemDef out = rawDef(def.smeltsIntoCustom());
            return out == null ? null : factory.build(out, amt);
        }
        if (def.smeltsInto() != null) return new ItemStack(def.smeltsInto(), amt);
        return null;
    }

    /** Résultat de la recette de tailleur de pierre (item custom prioritaire, sinon Material). null si rien/introuvable. */
    public ItemStack cutOutput(CustomItemDef def) {
        if (def == null || !def.canCut()) return null;
        int amt = Math.max(1, def.cutAmount());
        if (def.cutsIntoCustom() != null) {
            CustomItemDef out = rawDef(def.cutsIntoCustom());
            return out == null ? null : factory.build(out, amt);
        }
        if (def.cutsInto() != null) return new ItemStack(def.cutsInto(), amt);
        return null;
    }

    public static boolean isAir(ItemStack item) {
        return item == null || item.getType() == org.bukkit.Material.AIR;
    }

    public Player asPlayer(Object sender) {
        return sender instanceof Player p ? p : null;
    }

    public boolean give(Player p, String id) { return give(p, id, 1); }

    public org.bukkit.Server server() { return Bukkit.getServer(); }

    /** Accès public à l'instance plugin (pour listener/commande du module). */
    public com.mooncore.MoonCore mc() { return plugin; }

    /** Prochain custom-model-data libre (≥ 1000) pour une nouvelle texture. */
    public int nextCustomModelData() {
        int max = 1000;
        for (CustomItemDef d : defs.values()) max = Math.max(max, d.customModelData());
        return max + 1;
    }

    /** Dossier des PNG sources des textures custom (créé si absent). */
    public java.io.File texturesFolder() {
        java.io.File f = new java.io.File(plugin.getDataFolder(), "items-textures");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    /**
     * Dossier des PNG sources des textures d'<b>armure portée</b> (créé si absent). Convention :
     * {@code <key>_body.png} (couche humanoïde : casque/plastron/bottes) et {@code <key>_legs.png}
     * (couche jambières) ; un simple {@code <key>.png} sert de couche corps par défaut.
     */
    public java.io.File armorTexturesFolder() {
        java.io.File f = new java.io.File(plugin.getDataFolder(), "armor-textures");
        if (!f.exists()) f.mkdirs();
        return f;
    }
}
