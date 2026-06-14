package com.mooncore.modules.customitem;

import com.mooncore.api.customitem.CustomItemManagerService.CustomItemView;
import com.mooncore.api.customitem.ItemType;
import com.mooncore.api.customitem.Rarity;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Définition mutable d'un objet custom. Persistée en YAML dans {@code items/<id>.yml}.
 * Éditable en jeu via {@code /moon item ...}. Le rendu visuel (modèle/texture) est
 * optionnel : les stats et capacités vivent dans la PDC et le lore, donc l'objet
 * reste pleinement fonctionnel sur Bedrock même sans resource pack.
 */
public final class CustomItemDef implements CustomItemView {

    /** Référence d'une capacité portée par l'objet. */
    public record AbilityRef(String id, int level) {}

    /** Règle de drop : source = "boss:<id>" | "mob:<ENTITY_TYPE>" | "event:<id>". */
    public record DropRule(String source, double chance, int min, int max) {}

    /** Effet appliqué à la consommation (clic droit) : clé de registre + durée(ticks) + amplificateur(0-based). */
    public record ConsumeEffect(String key, int duration, int amplifier) {}

    /**
     * Règle de minage du composant {@code minecraft:tool}. {@code blocks} = soit un tag
     * ({@code "#minecraft:mineable/pickaxe"}), soit une liste de matériaux séparés par des virgules
     * ({@code "STONE,DEEPSLATE"}). {@code speed} = vitesse de minage ; {@code correctForDrops} =
     * cette règle rend-elle les blocs « correctement minés » (donc droppants).
     */
    public record ToolRule(String blocks, float speed, boolean correctForDrops) {}

    /** Ingredient de recette : material vanilla ou item custom exact. */
    public record RecipeIngredient(Material material, String customItemId) {
        public RecipeIngredient {
            if (customItemId != null) {
                customItemId = customItemId.toLowerCase(Locale.ROOT).trim();
                if (customItemId.isBlank()) customItemId = null;
            }
        }

        public static RecipeIngredient material(Material material) {
            return new RecipeIngredient(material, null);
        }

        public static RecipeIngredient custom(String customItemId) {
            return new RecipeIngredient(null, customItemId);
        }

        public static RecipeIngredient parse(String raw) {
            if (raw == null || raw.isBlank()) return null;
            String value = raw.trim();
            String lower = value.toLowerCase(Locale.ROOT);
            if (lower.startsWith("custom:")) return custom(value.substring("custom:".length()));
            if (lower.startsWith("item:")) return custom(value.substring("item:".length()));
            Material mat = matchMaterial(value);
            return mat == null || !mat.isItem() ? null : material(mat);
        }

        public boolean isCustom() {
            return customItemId != null;
        }

        public String storageKey() {
            return isCustom() ? "custom:" + customItemId : material.name();
        }

        @Override
        public String toString() {
            return storageKey();
        }
    }

    public static final class Recipe {
        public boolean shaped = true;
        public List<String> shape = new ArrayList<>(List.of("   ", "   ", "   "));
        public Map<Character, RecipeIngredient> ingredients = new LinkedHashMap<>();
        public int amount = 1;

        public boolean isEmpty() {
            return ingredients.isEmpty();
        }
    }

    /**
     * Recette de forge (smithing transform, 1.20+) : {@code base} + {@code addition} (+ {@code template})
     * → l'item custom. {@code template} optionnel (null = pas de patron requis). Le résultat est l'item lui-même.
     */
    public static final class SmithingRecipe {
        public RecipeIngredient template;   // patron (smithing template), optionnel
        public RecipeIngredient base;        // item de base à transformer
        public RecipeIngredient addition;    // matériau ajouté

        public SmithingRecipe() {}
        public SmithingRecipe(RecipeIngredient template, RecipeIngredient base, RecipeIngredient addition) {
            this.template = template; this.base = base; this.addition = addition;
        }

        /** Valide si base ET addition sont définis (template facultatif). */
        public boolean isValid() { return base != null && addition != null; }
    }

    /** Appareil de cuisson pour la recette de fonte d'un item custom. */
    public enum SmeltType {
        FURNACE("furnace", "four"),
        BLAST("blast", "haut-fourneau"),
        SMOKER("smoker", "fumoir");

        private final String id;
        private final String label;
        SmeltType(String id, String label) { this.id = id; this.label = label; }
        public String id() { return id; }
        public String label() { return label; }
        public static SmeltType fromId(String id) {
            if (id == null) return FURNACE;
            for (SmeltType t : values()) if (t.id.equalsIgnoreCase(id.trim())) return t;
            return FURNACE;
        }
    }

    private final String id;
    private String displayName;
    private ItemType type = ItemType.WEAPON;
    private Rarity rarity = Rarity.COMMON;
    private Material material = Material.IRON_SWORD;
    private ToolKind toolKind = ToolKind.NONE;
    private ToolTier toolTier = ToolTier.HAND;
    private int customModelData = 0;             // 0 = aucun (rendu vanilla)
    private String modelKey = null;              // clé de texture (resource pack)
    private String equipmentKey = null;          // clé d'asset d'armure portée (equippable, 1.21.2+) ; null = pas d'armure custom
    private boolean glowing = false;
    private boolean unbreakable = false;
    private final List<String> lore = new ArrayList<>();
    private final Map<String, Double> stats = new LinkedHashMap<>();
    private final List<AbilityRef> abilities = new ArrayList<>();
    private final List<DropRule> drops = new ArrayList<>();
    private Recipe recipe = null;
    private SmithingRecipe smithing = null;      // null = pas de recette de forge
    private Material smeltsInto = null;          // null = ne fond pas (pas de recette de fournaise)
    private String smeltsIntoCustom = null;      // si non null : résultat = item custom (prioritaire sur smeltsInto)
    private SmeltType smeltType = SmeltType.FURNACE; // four / haut-fourneau / fumoir
    private int smeltAmount = 1;
    private Material cutsInto = null;            // tailleur de pierre : null = ne se coupe pas
    private String cutsIntoCustom = null;        // si non null : résultat de coupe = item custom
    private int cutAmount = 1;
    private final Map<String, Integer> enchants = new LinkedHashMap<>(); // clé registre → niveau
    private final List<ConsumeEffect> consumeEffects = new ArrayList<>(); // effets à la consommation
    // Composant minecraft:food (+ consumable) — nourriture NATIVE (mangée par le mécanisme vanilla).
    private boolean food = false;                 // true = compose minecraft:food/consumable
    private int foodNutrition = 4;                // points de faim restaurés
    private float foodSaturation = 2.4f;          // saturation restaurée
    private boolean foodCanAlwaysEat = false;     // mangeable même barre de faim pleine
    private float foodEatSeconds = 1.6f;          // durée de l'animation de consommation
    // Composant minecraft:tool — règles de minage NATIVES (vitesse réelle, durabilité par bloc).
    private boolean toolComp = false;             // true = compose minecraft:tool
    private float toolMiningSpeed = 1f;           // vitesse par défaut (hors règles)
    private int toolDamagePerBlock = 1;           // durabilité perdue par bloc miné
    private final List<ToolRule> toolRules = new ArrayList<>(); // règles explicites ; vide = auto depuis ToolKind
    private int maxDamage = 0;                     // composant minecraft:max_damage (durabilité custom ; 0 = vanilla)

    public CustomItemDef(String id) {
        this.id = id.toLowerCase(Locale.ROOT);
        this.displayName = "<white>" + id + "</white>";
    }

    // ---- CustomItemView ----
    @Override public String id() { return id; }
    @Override public String displayName() { return displayName; }
    @Override public ItemType type() { return type; }
    @Override public Rarity rarity() { return rarity; }

    // ---- Accesseurs / mutateurs ----
    public void setDisplayName(String name) { this.displayName = name; }
    public void setType(ItemType type) { this.type = type; }
    public void setRarity(Rarity rarity) { this.rarity = rarity; }
    public Material material() { return material; }
    public void setMaterial(Material material) {
        this.material = material;
        inferToolFromMaterial(material);
    }
    public ToolKind toolKind() { return toolKind; }
    public ToolTier toolTier() { return toolTier; }
    public void setToolKind(ToolKind kind) { setTool(kind, toolTier == ToolTier.HAND ? ToolTier.IRON : toolTier); }
    public void setToolTier(ToolTier tier) { setTool(toolKind, tier); }
    public void setTool(ToolKind kind, ToolTier tier) {
        this.toolKind = kind == null ? ToolKind.NONE : kind;
        this.toolTier = tier == null ? ToolTier.HAND : tier;
        if (this.toolKind == ToolKind.NONE) {
            this.toolTier = ToolTier.HAND;
            return;
        }
        if (this.toolTier == ToolTier.HAND) this.toolTier = ToolTier.IRON;
        Material toolMaterial = this.toolTier.materialFor(this.toolKind);
        if (toolMaterial != null) this.material = toolMaterial;
        this.type = this.toolKind == ToolKind.SWORD
                ? com.mooncore.api.customitem.ItemType.WEAPON
                : com.mooncore.api.customitem.ItemType.TOOL;
    }
    public int customModelData() { return customModelData; }
    public void setCustomModelData(int cmd) { this.customModelData = cmd; }
    public String modelKey() { return modelKey; }
    public void setModelKey(String key) { this.modelKey = key; }
    /** Clé d'asset d'armure portée (equippable). null/blank = pas d'armure custom. */
    public String equipmentKey() { return equipmentKey; }
    public void setEquipmentKey(String key) {
        this.equipmentKey = (key == null || key.isBlank()) ? null : key.toLowerCase(Locale.ROOT).trim();
    }
    public boolean glowing() { return glowing; }
    public void setGlowing(boolean glowing) { this.glowing = glowing; }
    public boolean unbreakable() { return unbreakable; }
    public void setUnbreakable(boolean unbreakable) { this.unbreakable = unbreakable; }
    public List<String> lore() { return lore; }
    public Map<String, Double> stats() { return stats; }
    public List<AbilityRef> abilities() { return abilities; }
    public List<DropRule> drops() { return drops; }
    public Recipe recipe() { return recipe; }
    public void setRecipe(Recipe recipe) { this.recipe = recipe; }

    public SmithingRecipe smithing() { return smithing; }
    public void setSmithing(SmithingRecipe smithing) { this.smithing = smithing; }
    public boolean canSmith() { return smithing != null && smithing.isValid(); }

    // ---- Fonte (l'objet comme entrée de fournaise) ----
    public Material smeltsInto() { return smeltsInto; }
    /** Id de l'item custom produit par la fonte (prioritaire sur {@link #smeltsInto()}), ou null. */
    public String smeltsIntoCustom() { return smeltsIntoCustom; }
    public SmeltType smeltType() { return smeltType; }
    public int smeltAmount() { return smeltAmount; }
    public boolean canSmelt() { return smeltsInto != null || smeltsIntoCustom != null; }
    /** Définit un résultat de fonte VANILLA (Material). Efface un éventuel résultat custom. */
    public void setSmeltsInto(Material result, int amount) {
        this.smeltsInto = result;
        this.smeltsIntoCustom = null;
        this.smeltAmount = Math.max(1, Math.min(64, amount));
    }
    /** Définit un résultat de fonte CUSTOM (id d'item MoonCore). Efface un éventuel résultat vanilla. */
    public void setSmeltsIntoCustom(String customId, int amount) {
        if (customId == null || customId.isBlank()) { this.smeltsIntoCustom = null; return; }
        this.smeltsIntoCustom = customId.toLowerCase(Locale.ROOT).trim();
        this.smeltsInto = null;
        this.smeltAmount = Math.max(1, Math.min(64, amount));
    }
    public void setSmeltType(SmeltType type) { this.smeltType = type == null ? SmeltType.FURNACE : type; }
    public void clearSmelt() {
        this.smeltsInto = null;
        this.smeltsIntoCustom = null;
        this.smeltAmount = 1;
        this.smeltType = SmeltType.FURNACE;
    }

    // ---- Tailleur de pierre (l'objet comme entrée de stonecutter) ----
    public Material cutsInto() { return cutsInto; }
    /** Id de l'item custom produit par le tailleur de pierre (prioritaire sur {@link #cutsInto()}), ou null. */
    public String cutsIntoCustom() { return cutsIntoCustom; }
    public int cutAmount() { return cutAmount; }
    public boolean canCut() { return cutsInto != null || cutsIntoCustom != null; }
    public void setCutsInto(Material result, int amount) {
        this.cutsInto = result; this.cutsIntoCustom = null; this.cutAmount = Math.max(1, Math.min(64, amount));
    }
    public void setCutsIntoCustom(String customId, int amount) {
        if (customId == null || customId.isBlank()) { this.cutsIntoCustom = null; return; }
        this.cutsIntoCustom = customId.toLowerCase(Locale.ROOT).trim(); this.cutsInto = null;
        this.cutAmount = Math.max(1, Math.min(64, amount));
    }
    public void clearCut() { this.cutsInto = null; this.cutsIntoCustom = null; this.cutAmount = 1; }

    // ---- Enchantements vanilla (clé de registre minecraft, ex "sharpness" → niveau) ----
    public List<ConsumeEffect> consumeEffects() { return consumeEffects; }
    /** Ajoute/maj/retire un effet de consommation ({@code duration<=0} = retire). */
    public void setConsumeEffect(String key, int duration, int amplifier) {
        if (key == null || key.isBlank()) return;
        String k = key.toLowerCase(Locale.ROOT);
        consumeEffects.removeIf(c -> c.key().equals(k));
        if (duration > 0) consumeEffects.add(new ConsumeEffect(k, duration, Math.max(0, amplifier)));
    }
    // ---- Composant nourriture NATIVE (minecraft:food + consumable) ----
    public boolean hasFood() { return food; }
    public int foodNutrition() { return foodNutrition; }
    public float foodSaturation() { return foodSaturation; }
    public boolean foodCanAlwaysEat() { return foodCanAlwaysEat; }
    public float foodEatSeconds() { return foodEatSeconds; }
    /** Active la nourriture native avec ses paramètres (bornés). */
    public void setFood(int nutrition, float saturation, boolean canAlwaysEat, float eatSeconds) {
        this.food = true;
        this.foodNutrition = Math.max(0, Math.min(20, nutrition));
        this.foodSaturation = Math.max(0f, Math.min(20f, saturation));
        this.foodCanAlwaysEat = canAlwaysEat;
        this.foodEatSeconds = Math.max(0.1f, Math.min(60f, eatSeconds));
    }
    public void clearFood() { this.food = false; }

    // ---- Composant outil NATIF (minecraft:tool) ----
    public boolean hasToolComponent() { return toolComp; }
    public float toolMiningSpeed() { return toolMiningSpeed; }
    public int toolDamagePerBlock() { return toolDamagePerBlock; }
    public List<ToolRule> toolRules() { return toolRules; }
    /** Active le composant outil (paramètres bornés). N'efface pas les règles existantes. */
    public void setToolComponent(float miningSpeed, int damagePerBlock) {
        this.toolComp = true;
        this.toolMiningSpeed = Math.max(0f, Math.min(1024f, miningSpeed));
        this.toolDamagePerBlock = Math.max(0, Math.min(1000, damagePerBlock));
    }
    public void addToolRule(String blocks, float speed, boolean correctForDrops) {
        if (blocks == null || blocks.isBlank()) return;
        toolRules.add(new ToolRule(blocks.trim(), Math.max(0f, Math.min(1024f, speed)), correctForDrops));
    }
    public void clearToolComponent() { this.toolComp = false; toolRules.clear(); }

    // ---- Durabilité custom (composant minecraft:max_damage) ----
    public int maxDamage() { return maxDamage; }
    /** Définit la durabilité maximale custom ({@code 0} = durabilité vanilla du matériau). */
    public void setMaxDamage(int value) { this.maxDamage = Math.max(0, Math.min(100_000, value)); }

    public Map<String, Integer> enchants() { return enchants; }
    public void setEnchant(String key, int level) {
        if (key == null || key.isBlank()) return;
        if (level <= 0) enchants.remove(key.toLowerCase(Locale.ROOT));
        else enchants.put(key.toLowerCase(Locale.ROOT), level);
    }

    public void setStat(String key, double value) {
        stats.put(key.toLowerCase(Locale.ROOT), value);
    }
    public void removeStat(String key) { stats.remove(key.toLowerCase(Locale.ROOT)); }

    public void addAbility(String abilityId, int level) {
        String norm = abilityId.toLowerCase(Locale.ROOT);
        abilities.removeIf(a -> a.id().equals(norm));
        abilities.add(new AbilityRef(norm, Math.max(1, level)));
    }
    public boolean removeAbility(String abilityId) {
        String norm = abilityId.toLowerCase(Locale.ROOT);
        return abilities.removeIf(a -> a.id().equals(norm));
    }

    private void inferToolFromMaterial(Material material) {
        ToolKind kind = ToolKind.fromMaterial(material);
        if (kind == ToolKind.NONE) {
            this.toolKind = ToolKind.NONE;
            this.toolTier = ToolTier.HAND;
            return;
        }
        this.toolKind = kind;
        this.toolTier = ToolTier.fromMaterial(material);
        this.type = kind == ToolKind.SWORD ? ItemType.WEAPON : ItemType.TOOL;
    }

    /** Copie profonde sous un nouvel id (pour /moon item clone). */
    public CustomItemDef cloneAs(String newId) {
        CustomItemDef c = new CustomItemDef(newId);
        c.displayName = this.displayName;
        c.type = this.type;
        c.rarity = this.rarity;
        c.material = this.material;
        c.toolKind = this.toolKind;
        c.toolTier = this.toolTier;
        c.customModelData = this.customModelData;
        c.modelKey = this.modelKey;
        c.equipmentKey = this.equipmentKey;
        c.glowing = this.glowing;
        c.unbreakable = this.unbreakable;
        c.lore.addAll(this.lore);
        c.stats.putAll(this.stats);
        c.abilities.addAll(this.abilities);
        c.drops.addAll(this.drops);
        if (this.recipe != null) {
            Recipe r = new Recipe();
            r.shaped = this.recipe.shaped;
            r.shape = new ArrayList<>(this.recipe.shape);
            r.ingredients = new LinkedHashMap<>(this.recipe.ingredients);
            r.amount = this.recipe.amount;
            c.recipe = r;
        }
        if (this.smithing != null) {
            c.smithing = new SmithingRecipe(this.smithing.template, this.smithing.base, this.smithing.addition);
        }
        c.smeltsInto = this.smeltsInto;
        c.smeltsIntoCustom = this.smeltsIntoCustom;
        c.smeltType = this.smeltType;
        c.smeltAmount = this.smeltAmount;
        c.cutsInto = this.cutsInto;
        c.cutsIntoCustom = this.cutsIntoCustom;
        c.cutAmount = this.cutAmount;
        c.enchants.putAll(this.enchants);
        c.consumeEffects.addAll(this.consumeEffects);
        c.food = this.food;
        c.foodNutrition = this.foodNutrition;
        c.foodSaturation = this.foodSaturation;
        c.foodCanAlwaysEat = this.foodCanAlwaysEat;
        c.foodEatSeconds = this.foodEatSeconds;
        c.toolComp = this.toolComp;
        c.toolMiningSpeed = this.toolMiningSpeed;
        c.toolDamagePerBlock = this.toolDamagePerBlock;
        c.toolRules.addAll(this.toolRules);
        c.maxDamage = this.maxDamage;
        return c;
    }

    // ---- Sérialisation YAML ----

    public void save(ConfigurationSection s) {
        s.set("display-name", displayName);
        s.set("type", type.id());
        s.set("rarity", rarity.id());
        s.set("material", material.name());
        s.set("tool-kind", toolKind.id());
        s.set("tool-tier", toolTier.id());
        s.set("custom-model-data", customModelData);
        s.set("model-key", modelKey);
        s.set("equipment-key", equipmentKey);
        s.set("glowing", glowing);
        s.set("unbreakable", unbreakable);
        s.set("lore", new ArrayList<>(lore));

        ConfigurationSection statsSec = s.createSection("stats");
        for (Map.Entry<String, Double> e : stats.entrySet()) {
            statsSec.set(e.getKey(), e.getValue());
        }

        List<Map<String, Object>> abilityList = new ArrayList<>();
        for (AbilityRef a : abilities) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.id());
            m.put("level", a.level());
            abilityList.add(m);
        }
        s.set("abilities", abilityList);

        List<Map<String, Object>> dropList = new ArrayList<>();
        for (DropRule d : drops) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("source", d.source());
            m.put("chance", d.chance());
            m.put("min", d.min());
            m.put("max", d.max());
            dropList.add(m);
        }
        s.set("drops", dropList);

        if (recipe != null && !recipe.isEmpty()) {
            ConfigurationSection rs = s.createSection("recipe");
            rs.set("shaped", recipe.shaped);
            rs.set("shape", recipe.shape);
            rs.set("amount", recipe.amount);
            ConfigurationSection ing = rs.createSection("ingredients");
            for (Map.Entry<Character, RecipeIngredient> e : recipe.ingredients.entrySet()) {
                ing.set(String.valueOf(e.getKey()), e.getValue().storageKey());
            }
        } else {
            s.set("recipe", null);
        }

        if (canSmelt()) {
            s.set("smelt.amount", smeltAmount);
            s.set("smelt.type", smeltType.id());
            if (smeltsIntoCustom != null) {
                s.set("smelt.result-custom", smeltsIntoCustom);
                s.set("smelt.result", null);
            } else {
                s.set("smelt.result", smeltsInto.name());
                s.set("smelt.result-custom", null);
            }
        } else {
            s.set("smelt", null);
        }

        if (canSmith()) {
            ConfigurationSection sg = s.createSection("smithing");
            if (smithing.template != null) sg.set("template", smithing.template.storageKey());
            sg.set("base", smithing.base.storageKey());
            sg.set("addition", smithing.addition.storageKey());
        } else {
            s.set("smithing", null);
        }

        if (canCut()) {
            s.set("stonecut.amount", cutAmount);
            if (cutsIntoCustom != null) { s.set("stonecut.result-custom", cutsIntoCustom); s.set("stonecut.result", null); }
            else { s.set("stonecut.result", cutsInto.name()); s.set("stonecut.result-custom", null); }
        } else {
            s.set("stonecut", null);
        }

        if (!enchants.isEmpty()) {
            ConfigurationSection encSec = s.createSection("enchants");
            for (Map.Entry<String, Integer> e : enchants.entrySet()) encSec.set(e.getKey(), e.getValue());
        } else {
            s.set("enchants", null);
        }

        if (!consumeEffects.isEmpty()) {
            List<Map<String, Object>> ce = new ArrayList<>();
            for (ConsumeEffect e : consumeEffects) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("key", e.key());
                m.put("duration", e.duration());
                m.put("amplifier", e.amplifier());
                ce.add(m);
            }
            s.set("consume-effects", ce);
        } else {
            s.set("consume-effects", null);
        }

        if (food) {
            s.set("food.nutrition", foodNutrition);
            s.set("food.saturation", foodSaturation);
            s.set("food.can-always-eat", foodCanAlwaysEat);
            s.set("food.eat-seconds", foodEatSeconds);
        } else {
            s.set("food", null);
        }

        if (toolComp) {
            s.set("tool.mining-speed", toolMiningSpeed);
            s.set("tool.damage-per-block", toolDamagePerBlock);
            List<Map<String, Object>> rules = new ArrayList<>();
            for (ToolRule r : toolRules) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("blocks", r.blocks());
                m.put("speed", r.speed());
                m.put("correct-for-drops", r.correctForDrops());
                rules.add(m);
            }
            s.set("tool.rules", rules);
        } else {
            s.set("tool", null);
        }

        s.set("max-damage", maxDamage > 0 ? maxDamage : null);
    }

    public static CustomItemDef load(String id, ConfigurationSection s) {
        CustomItemDef d = new CustomItemDef(id);
        d.displayName = s.getString("display-name", d.displayName);
        ItemType t = ItemType.fromId(s.getString("type", "weapon"));
        if (t != null) d.type = t;
        Rarity r = Rarity.fromId(s.getString("rarity", "common"));
        if (r != null) d.rarity = r;
        Material mat = matchMaterial(s.getString("material", "IRON_SWORD"));
        if (mat != null) d.setMaterial(mat);
        ToolKind savedKind = ToolKind.fromId(s.getString("tool-kind", d.toolKind.id()));
        ToolTier savedTier = ToolTier.fromId(s.getString("tool-tier", d.toolTier.id()));
        if (savedKind != ToolKind.NONE) d.setTool(savedKind, savedTier == ToolTier.HAND ? ToolTier.IRON : savedTier);
        else if (s.contains("tool-kind")) d.setTool(ToolKind.NONE, ToolTier.HAND);
        d.customModelData = s.getInt("custom-model-data", 0);
        d.modelKey = s.getString("model-key", null);
        d.setEquipmentKey(s.getString("equipment-key", null));
        d.glowing = s.getBoolean("glowing", false);
        d.unbreakable = s.getBoolean("unbreakable", false);
        d.lore.addAll(s.getStringList("lore"));

        ConfigurationSection statsSec = s.getConfigurationSection("stats");
        if (statsSec != null) {
            for (String key : statsSec.getKeys(false)) {
                d.stats.put(key.toLowerCase(Locale.ROOT), statsSec.getDouble(key));
            }
        }

        for (Map<?, ?> m : s.getMapList("abilities")) {
            Object aid = m.get("id");
            if (aid == null) continue;
            int lvl = m.get("level") instanceof Number n ? n.intValue() : 1;
            d.abilities.add(new AbilityRef(String.valueOf(aid).toLowerCase(Locale.ROOT), Math.max(1, lvl)));
        }

        for (Map<?, ?> m : s.getMapList("drops")) {
            Object src = m.get("source");
            if (src == null) continue;
            double chance = m.get("chance") instanceof Number n ? n.doubleValue() : 0.0;
            int min = m.get("min") instanceof Number n ? n.intValue() : 1;
            int max = m.get("max") instanceof Number n ? n.intValue() : 1;
            d.drops.add(new DropRule(String.valueOf(src), chance, min, max));
        }

        ConfigurationSection rs = s.getConfigurationSection("recipe");
        if (rs != null) {
            Recipe rec = new Recipe();
            rec.shaped = rs.getBoolean("shaped", true);
            List<String> shape = rs.getStringList("shape");
            if (!shape.isEmpty()) rec.shape = shape;
            rec.amount = rs.getInt("amount", 1);
            ConfigurationSection ing = rs.getConfigurationSection("ingredients");
            if (ing != null) {
                for (String k : ing.getKeys(false)) {
                    RecipeIngredient ingredient = RecipeIngredient.parse(ing.getString(k));
                    if (ingredient != null && !k.isEmpty()) rec.ingredients.put(k.charAt(0), ingredient);
                }
            }
            d.recipe = rec;
        }

        ConfigurationSection sm = s.getConfigurationSection("smelt");
        if (sm != null) {
            int amt = sm.getInt("amount", 1);
            String custom = sm.getString("result-custom");
            if (custom != null && !custom.isBlank()) {
                d.setSmeltsIntoCustom(custom, amt);
            } else {
                Material rm = matchMaterial(sm.getString("result"));
                if (rm != null) d.setSmeltsInto(rm, amt);
            }
            d.setSmeltType(SmeltType.fromId(sm.getString("type", "furnace")));
        }

        ConfigurationSection sg = s.getConfigurationSection("smithing");
        if (sg != null) {
            SmithingRecipe smith = new SmithingRecipe(
                    RecipeIngredient.parse(sg.getString("template")),
                    RecipeIngredient.parse(sg.getString("base")),
                    RecipeIngredient.parse(sg.getString("addition")));
            if (smith.isValid()) d.smithing = smith;
        }

        ConfigurationSection cut = s.getConfigurationSection("stonecut");
        if (cut != null) {
            int camt = cut.getInt("amount", 1);
            String cc = cut.getString("result-custom");
            if (cc != null && !cc.isBlank()) d.setCutsIntoCustom(cc, camt);
            else { Material cm = matchMaterial(cut.getString("result")); if (cm != null) d.setCutsInto(cm, camt); }
        }

        ConfigurationSection encSec = s.getConfigurationSection("enchants");
        if (encSec != null) {
            for (String k : encSec.getKeys(false)) d.setEnchant(k, encSec.getInt(k));
        }

        for (Map<?, ?> m : s.getMapList("consume-effects")) {
            Object k = m.get("key");
            if (k == null) continue;
            int dur = m.get("duration") instanceof Number n ? n.intValue() : 100;
            int amp = m.get("amplifier") instanceof Number n ? n.intValue() : 0;
            d.consumeEffects.add(new ConsumeEffect(String.valueOf(k).toLowerCase(Locale.ROOT), dur, amp));
        }

        ConfigurationSection foodSec = s.getConfigurationSection("food");
        if (foodSec != null) {
            d.setFood(foodSec.getInt("nutrition", 4),
                    (float) foodSec.getDouble("saturation", 2.4),
                    foodSec.getBoolean("can-always-eat", false),
                    (float) foodSec.getDouble("eat-seconds", 1.6));
        }

        ConfigurationSection toolSec = s.getConfigurationSection("tool");
        if (toolSec != null) {
            d.setToolComponent((float) toolSec.getDouble("mining-speed", 1.0),
                    toolSec.getInt("damage-per-block", 1));
            for (Map<?, ?> m : toolSec.getMapList("rules")) {
                Object blocks = m.get("blocks");
                if (blocks == null) continue;
                float speed = m.get("speed") instanceof Number n ? n.floatValue() : 1f;
                boolean correct = !(m.get("correct-for-drops") instanceof Boolean b) || b;
                d.addToolRule(String.valueOf(blocks), speed, correct);
            }
        }

        d.setMaxDamage(s.getInt("max-damage", 0));
        return d;
    }

    private static Material matchMaterial(String name) {
        if (name == null) return null;
        return Material.matchMaterial(name.toUpperCase(Locale.ROOT));
    }
}
