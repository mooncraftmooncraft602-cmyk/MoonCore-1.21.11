package com.mooncore.modules.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mooncore.api.customitem.ItemType;
import com.mooncore.api.customitem.Rarity;
import com.mooncore.modules.crop.CropDef;
import com.mooncore.modules.customitem.CustomItemDef;
import com.mooncore.modules.customitem.ToolKind;
import com.mooncore.modules.customitem.ToolTier;
import com.mooncore.modules.customitem.ability.AbilityRegistry;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Couche de sécurité de l'assistant IA. L'IA ne renvoie que des <b>données</b>
 * structurées (JSON). Ce validateur les transforme en {@link CustomItemDef} sûr :
 * <ul>
 *   <li>aucune commande n'est jamais exécutée à partir de la sortie IA ;</li>
 *   <li>les matériaux, types, raretés et capacités sont vérifiés contre les registres ;</li>
 *   <li>les valeurs de stats sont bornées (anti-abus / anti-hallucination) ;</li>
 *   <li>les champs inconnus sont ignorés, les capacités invalides sont retirées avec un warning.</li>
 * </ul>
 */
public final class AiActionValidator {

    public record Result(boolean ok, CustomItemDef def, List<String> warnings, String error) {
        public static Result fail(String error) { return new Result(false, null, List.of(), error); }
    }

    private final Gson gson = new Gson();
    private final AbilityRegistry abilities;
    private final double maxStat;
    private final int maxAbilityLevel;
    private final int maxAbilities;
    private final java.util.Map<String, Double> statCaps;

    /** Bornes par défaut par stat (déléguées au {@link com.mooncore.api.customitem.StatBudget} partagé). */
    public static java.util.Map<String, Double> defaultStatCaps() {
        return com.mooncore.api.customitem.StatBudget.defaults();
    }

    public AiActionValidator(AbilityRegistry abilities, double maxStat, int maxAbilityLevel,
                             int maxAbilities, java.util.Map<String, Double> statCaps) {
        this.abilities = abilities;
        this.maxStat = maxStat;
        this.maxAbilityLevel = maxAbilityLevel;
        this.maxAbilities = maxAbilities;
        this.statCaps = statCaps != null ? statCaps : defaultStatCaps();
    }

    /** Valide une sortie IA décrivant un item. {@code forcedId} impose l'id final. */
    public Result validateItem(String aiText, String forcedId) {
        return validateItem(aiText, forcedId, -1);
    }

    /**
     * @param maxAbilitiesOverride plafond de capacités pour CET appel ({@code <= 0} = défaut config).
     *                             Relevé quand l'admin demande explicitement des pouvoirs.
     */
    public Result validateItem(String aiText, String forcedId, int maxAbilitiesOverride) {
        JsonObject root = parse(aiText);
        if (root == null) return Result.fail("La réponse IA n'est pas un JSON d'objet valide.");

        int abilityCap = maxAbilitiesOverride > 0 ? maxAbilitiesOverride : maxAbilities;
        List<String> warnings = new ArrayList<>();

        String id = forcedId != null ? forcedId
                : str(root, "id", "ai_item_" + Math.abs(aiText.hashCode() % 100000));
        id = sanitizeId(id);

        CustomItemDef def = new CustomItemDef(id);

        if (root.has("display_name")) def.setDisplayName(str(root, "display_name", def.displayName()));

        ItemType type = ItemType.fromId(str(root, "type", "weapon"));
        if (type == null) { type = ItemType.WEAPON; warnings.add("Type inconnu → weapon."); }
        def.setType(type);

        Rarity rarity = Rarity.fromId(str(root, "rarity", "common"));
        if (rarity == null) { rarity = Rarity.COMMON; warnings.add("Rareté inconnue → common."); }
        def.setRarity(rarity);

        Material mat = Material.matchMaterial(str(root, "material", "").toUpperCase(Locale.ROOT));
        if (mat == null || mat.isAir()) {
            mat = defaultMaterial(type);
            warnings.add("Matériau invalide → " + mat.name() + ".");
        }
        def.setMaterial(mat);
        ToolKind toolKind = root.has("tool_kind")
                ? ToolKind.fromId(str(root, "tool_kind", ""))
                : ToolKind.fromMaterial(mat);
        if (toolKind == ToolKind.NONE) {
            toolKind = ToolKind.fromText(id + " " + def.displayName() + " " + str(root, "material", ""));
        }
        if (toolKind != ToolKind.NONE) {
            ToolTier tier = root.has("tool_tier")
                    ? ToolTier.fromId(str(root, "tool_tier", ""))
                    : ToolTier.fromMaterial(mat);
            if (tier == ToolTier.HAND) tier = ToolTier.NETHERITE;
            def.setTool(toolKind, tier);
        }

        def.setGlowing(bool(root, "glowing", false));
        def.setUnbreakable(bool(root, "unbreakable", false));
        int cmd = intOf(root, "custom_model_data", 0);
        if (cmd > 0) def.setCustomModelData(cmd);
        if (root.has("model_key") && !root.get("model_key").isJsonNull()) {
            def.setModelKey(str(root, "model_key", null));
        }

        // Lore
        if (root.has("lore") && root.get("lore").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("lore")) {
                if (el.isJsonPrimitive()) def.lore().add(el.getAsString());
            }
        }

        // Stats (bornées)
        if (root.has("stats") && root.get("stats").isJsonObject()) {
            JsonObject stats = root.getAsJsonObject("stats");
            for (String key : stats.keySet()) {
                try {
                    double v = stats.get(key).getAsDouble();
                    double cap = statCaps.getOrDefault(key.toLowerCase(Locale.ROOT), maxStat);
                    double clamped = Math.max(-cap, Math.min(cap, v));
                    if (clamped != v) warnings.add("Stat " + key + " bornée à " + clamped + " (équilibrage).");
                    def.setStat(key.toLowerCase(Locale.ROOT), clamped);
                } catch (Exception ignored) {
                    warnings.add("Stat ignorée (valeur non numérique) : " + key);
                }
            }
        }

        // Capacités (whitelistées contre le registre)
        if (root.has("abilities") && root.get("abilities").isJsonArray()) {
            JsonArray arr = root.getAsJsonArray("abilities");
            for (JsonElement el : arr) {
                String abId; int level = 1;
                if (el.isJsonObject()) {
                    JsonObject o = el.getAsJsonObject();
                    abId = str(o, "id", null);
                    level = intOf(o, "level", 1);
                } else if (el.isJsonPrimitive()) {
                    abId = el.getAsString();
                } else continue;
                if (abId == null) continue;
                abId = abId.toLowerCase(Locale.ROOT);
                if (!abilities.exists(abId)) {
                    warnings.add("Capacité inconnue ignorée : " + abId);
                    continue;
                }
                if (def.abilities().size() >= abilityCap) {
                    warnings.add("Trop de capacités → " + abId + " ignorée (max " + abilityCap + ").");
                    continue;
                }
                def.addAbility(abId, Math.max(1, Math.min(maxAbilityLevel, level)));
            }
        }

        // Enchantements vanilla : { "sharpness": 5, ... } (clé inconnue ignorée par la factory).
        if (root.has("enchants") && root.get("enchants").isJsonObject()) {
            JsonObject enc = root.getAsJsonObject("enchants");
            for (String k : enc.keySet()) {
                try { def.setEnchant(k, Math.max(1, Math.min(5, enc.get(k).getAsInt()))); }
                catch (Exception ignored) { warnings.add("Enchantement ignoré : " + k); }
            }
        }

        // Effets de consommation : [ {"effect":"speed","duration":10,"amplifier":1}, ... ] (duration en s).
        if (root.has("consume_effects") && root.get("consume_effects").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("consume_effects")) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String key = str(o, "effect", null);
                if (key == null) continue;
                int seconds = Math.max(1, Math.min(600, intOf(o, "duration", 10)));
                int amp = Math.max(0, Math.min(4, intOf(o, "amplifier", 0)));
                def.setConsumeEffect(key.toLowerCase(Locale.ROOT), seconds * 20, amp);
            }
        }

        // Nourriture NATIVE (composant minecraft:food + consumable).
        if (root.has("food") && root.get("food").isJsonObject()) {
            JsonObject f = root.getAsJsonObject("food");
            int nutrition = Math.max(0, Math.min(20, intOf(f, "nutrition", 4)));
            float sat = (float) Math.max(0, Math.min(20, dblOf(f, "saturation", 2.4)));
            boolean always = bool(f, "can_always_eat", false);
            float eat = (float) Math.max(0.1, Math.min(60, dblOf(f, "eat_seconds", 1.6)));
            def.setFood(nutrition, sat, always, eat);
        }

        // Durabilité custom (composant minecraft:max_damage).
        if (root.has("max_damage")) {
            def.setMaxDamage(intOf(root, "max_damage", 0)); // setMaxDamage borne 0..100000
        }

        // Composant outil NATIF (minecraft:tool) + règles de minage.
        if (root.has("tool") && root.get("tool").isJsonObject()) {
            JsonObject t = root.getAsJsonObject("tool");
            float speed = (float) Math.max(0, Math.min(1024, dblOf(t, "mining_speed", 1.0)));
            int dmg = Math.max(0, Math.min(1000, intOf(t, "damage_per_block", 1)));
            def.setToolComponent(speed, dmg);
            if (t.has("rules") && t.get("rules").isJsonArray()) {
                for (JsonElement el : t.getAsJsonArray("rules")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject r = el.getAsJsonObject();
                    String blocks = str(r, "blocks", null);
                    if (blocks == null || blocks.isBlank()) continue;
                    float rspeed = (float) Math.max(0, Math.min(1024, dblOf(r, "speed", speed)));
                    boolean correct = bool(r, "correct_for_drops", true);
                    def.addToolRule(blocks, rspeed, correct);
                }
            }
        }

        return new Result(true, def, warnings, null);
    }

    /** Extrait un objet JSON générique (Map) d'une sortie IA — pour boss/event/etc. */
    @SuppressWarnings("unchecked")
    public java.util.Map<String, Object> extractMap(String aiText) {
        String json = extractJson(aiText);
        if (json == null) return null;
        try {
            return gson.fromJson(json, java.util.Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** Extrait des lignes de lore d'une sortie IA {@code {"lore":[...]}}. */
    public List<String> extractLore(String aiText) {
        List<String> out = new ArrayList<>();
        JsonObject root = parse(aiText);
        if (root != null && root.has("lore") && root.get("lore").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("lore")) {
                if (el.isJsonPrimitive()) out.add(el.getAsString());
            }
        }
        return out;
    }

    /** Extrait + valide une recette d'une sortie IA {@code {"recipe":{...}}}. */
    /**
     * Extrait une recette de forge d'une sortie IA {@code {"smithing":{"template":..,"base":..,"addition":..}}}.
     * Ingrédients = Material ou {@code custom:<id>}. Retourne null si base ou addition manque/invalide.
     */
    public CustomItemDef.SmithingRecipe extractSmithing(String aiText) {
        JsonObject root = parse(aiText);
        if (root == null || !root.has("smithing") || !root.get("smithing").isJsonObject()) return null;
        JsonObject sm = root.getAsJsonObject("smithing");
        CustomItemDef.RecipeIngredient base = CustomItemDef.RecipeIngredient.parse(str(sm, "base", null));
        CustomItemDef.RecipeIngredient addition = CustomItemDef.RecipeIngredient.parse(str(sm, "addition", null));
        if (base == null || addition == null) return null;
        CustomItemDef.RecipeIngredient template = CustomItemDef.RecipeIngredient.parse(str(sm, "template", null));
        return new CustomItemDef.SmithingRecipe(template, base, addition);
    }

    public CustomItemDef.Recipe extractRecipe(String aiText, List<String> warnings) {
        JsonObject root = parse(aiText);
        if (root == null || !root.has("recipe") || !root.get("recipe").isJsonObject()) return null;
        JsonObject r = root.getAsJsonObject("recipe");
        CustomItemDef.Recipe recipe = new CustomItemDef.Recipe();
        recipe.shaped = true;
        recipe.amount = intOf(r, "amount", 1);

        if (r.has("shape") && r.get("shape").isJsonArray()) {
            List<String> shape = new ArrayList<>();
            for (JsonElement el : r.getAsJsonArray("shape")) {
                if (el.isJsonPrimitive()) {
                    String row = el.getAsString();
                    if (row.length() > 3) row = row.substring(0, 3);
                    while (row.length() < 3) row += " ";
                    shape.add(row);
                }
            }
            while (shape.size() < 3) shape.add("   ");
            recipe.shape = shape.subList(0, 3);
        }

        if (r.has("ingredients") && r.get("ingredients").isJsonObject()) {
            JsonObject ing = r.getAsJsonObject("ingredients");
            for (String k : ing.keySet()) {
                if (k.isEmpty()) continue;
                Material m = Material.matchMaterial(ing.get(k).getAsString().toUpperCase(Locale.ROOT));
                if (m == null) { warnings.add("Ingrédient invalide ignoré : " + k); continue; }
                recipe.ingredients.put(k.charAt(0), CustomItemDef.RecipeIngredient.material(m));
            }
        }
        return recipe.isEmpty() ? null : recipe;
    }

    // ---- parsing util ----

    private JsonObject parse(String text) {
        if (text == null) return null;
        String json = extractJson(text);
        if (json == null) return null;
        try {
            JsonElement el = gson.fromJson(json, JsonElement.class);
            return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Extrait le premier objet JSON, même entouré de texte/markdown (```json ... ```). Package-private pour test. */
    static String extractJson(String text) {
        if (text == null) return null;
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        return text.substring(start, end + 1);
    }

    /** Normalise un id IA (slug, max 40, défaut ai_item). Package-private pour test. */
    static String sanitizeId(String id) {
        String s = id.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9_-]", "_");
        if (s.isBlank()) s = "ai_item";
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    private static Material defaultMaterial(ItemType type) {
        return switch (type) {
            case WEAPON, BOSS_ITEM -> Material.NETHERITE_SWORD;
            case TOOL -> Material.NETHERITE_PICKAXE;
            case ARMOR -> Material.NETHERITE_CHESTPLATE;
            case ACCESSORY, RELIC, ARTIFACT -> Material.AMETHYST_SHARD;
            case CONSUMABLE -> Material.POTION;
            case EVENT_ITEM -> Material.NETHER_STAR;
        };
    }

    /**
     * Valide une sortie IA décrivant une culture ({@link AiPrompts#cropSchemaSystem()}) et la
     * transforme en {@link CropDef} sûr (valeurs bornées). Retourne {@code null} si le JSON est invalide.
     */
    public CropDef validateCrop(String aiText, String forcedId) {
        JsonObject root = parse(aiText);
        if (root == null) return null;
        String id = sanitizeId(forcedId != null ? forcedId
                : str(root, "id", "ai_crop_" + Math.abs(aiText.hashCode() % 100000)));
        CropDef d = new CropDef(id);

        if (root.has("display-name")) d.setDisplayName(str(root, "display-name", d.displayName()));

        String seed = str(root, "seed", null);
        if (seed != null) {
            if (seed.toLowerCase(Locale.ROOT).startsWith("custom:")) {
                d.setSeedCustomId(seed.substring("custom:".length()));
            } else {
                Material m = Material.matchMaterial(seed.toUpperCase(Locale.ROOT));
                if (m != null && m.isItem()) d.setSeed(m);
            }
        }
        Material on = Material.matchMaterial(str(root, "place-on", "FARMLAND").toUpperCase(Locale.ROOT));
        if (on != null && on.isBlock()) d.setPlaceOn(on);

        d.setStages(intOf(root, "stages", 4));
        d.setGrowthTicks(intOf(root, "growth-ticks", 600));
        d.setMinLight(intOf(root, "min-light", 9));
        d.setRequiresWater(bool(root, "requires-water", true));
        d.setReplantable(bool(root, "replantable", true));
        d.setBonemealable(bool(root, "bonemealable", true));

        if (root.has("drop") && root.get("drop").isJsonObject()) {
            JsonObject dr = root.getAsJsonObject("drop");
            String item = str(dr, "item", null);
            if (item != null) {
                if (item.toLowerCase(Locale.ROOT).startsWith("custom:")) {
                    d.setDropItemId(item.substring("custom:".length()));
                } else {
                    Material m = Material.matchMaterial(item.toUpperCase(Locale.ROOT));
                    if (m != null && m.isItem()) d.setDropMaterial(m);
                }
            }
            d.setDropRange(intOf(dr, "min", 1), intOf(dr, "max", 2));
        }
        if (root.has("seed-return") && root.get("seed-return").isJsonObject()) {
            JsonObject sr = root.getAsJsonObject("seed-return");
            d.setSeedReturnRange(intOf(sr, "min", 0), intOf(sr, "max", 1));
        }
        d.setLootTableId(str(root, "loot-table", null));
        return d;
    }

    /**
     * Transforme une sortie IA en {@link com.mooncore.modules.loot.LootTableDef} sûre : pools pondérés,
     * matériaux vérifiés contre le registre, comptes/poids bornés par les setters du modèle. Tout champ
     * inconnu est ignoré ; une table sans pools valides reste une table vide cohérente.
     */
    public com.mooncore.modules.loot.LootTableDef validateLoot(String aiText, String forcedId) {
        JsonObject root = parse(aiText);
        if (root == null) return null;
        String id = sanitizeId(forcedId != null ? forcedId
                : str(root, "id", "ai_loot_" + Math.abs(aiText.hashCode() % 100000)));
        com.mooncore.modules.loot.LootTableDef d = new com.mooncore.modules.loot.LootTableDef(id);

        if (root.has("display-name")) d.setDisplayName(str(root, "display-name", d.displayName()));

        if (root.has("pools") && root.get("pools").isJsonArray()) {
            JsonArray pools = root.getAsJsonArray("pools");
            for (JsonElement pe : pools) {
                if (!pe.isJsonObject()) continue;
                JsonObject po = pe.getAsJsonObject();
                int rMin = 1, rMax = 1;
                if (po.has("rolls") && po.get("rolls").isJsonObject()) {
                    JsonObject r = po.getAsJsonObject("rolls");
                    rMin = intOf(r, "min", 1);
                    rMax = intOf(r, "max", rMin);
                }
                com.mooncore.modules.loot.LootPool pool = new com.mooncore.modules.loot.LootPool(rMin, rMax);
                if (po.has("entries") && po.get("entries").isJsonArray()) {
                    for (JsonElement ee : po.getAsJsonArray("entries")) {
                        if (!ee.isJsonObject()) continue;
                        JsonObject eo = ee.getAsJsonObject();
                        String customId = null;
                        Material mat = Material.AIR;
                        String item = str(eo, "item", null);
                        if (item != null) {
                            if (item.toLowerCase(Locale.ROOT).startsWith("custom:")) {
                                customId = item.substring("custom:".length());
                            } else {
                                Material m = Material.matchMaterial(item.toUpperCase(Locale.ROOT));
                                if (m != null && m.isItem()) mat = m;
                            }
                        }
                        int cMin = 1, cMax = 1;
                        if (eo.has("count") && eo.get("count").isJsonObject()) {
                            JsonObject c = eo.getAsJsonObject("count");
                            cMin = intOf(c, "min", 1);
                            cMax = intOf(c, "max", cMin);
                        }
                        // Référence vers une autre table (imbrication) ; ignorée si elle pointe sur elle-même.
                        String ref = str(eo, "loot-table", null);
                        if (ref != null && ref.equalsIgnoreCase(id)) ref = null;
                        pool.add(new com.mooncore.modules.loot.LootEntry(
                                customId, mat, intOf(eo, "weight", 1), cMin, cMax, ref));
                    }
                }
                d.add(pool);
            }
        }
        return d;
    }

    /**
     * Transforme une sortie IA en {@link com.mooncore.modules.mechanic.MechanicDef} sûre : déclencheur et
     * types d'action parsés tolérants (inconnus → ignorés), bornes via les setters du modèle, paramètres
     * d'action conservés tels quels (chaînes). Une mécanique sans action valide reste cohérente (inactive).
     */
    public com.mooncore.modules.mechanic.MechanicDef validateMechanic(String aiText, String forcedId) {
        JsonObject root = parse(aiText);
        if (root == null) return null;
        String id = sanitizeId(forcedId != null ? forcedId
                : str(root, "id", "ai_mechanic_" + Math.abs(aiText.hashCode() % 100000)));
        com.mooncore.modules.mechanic.MechanicDef d = new com.mooncore.modules.mechanic.MechanicDef(id);

        if (root.has("display-name")) d.setDisplayName(str(root, "display-name", d.displayName()));
        d.setTrigger(com.mooncore.modules.mechanic.TriggerType.fromText(str(root, "trigger", "none")));
        d.setMatchKey(str(root, "match", null));
        d.setCooldownTicks(intOf(root, "cooldown-ticks", 0));
        d.setIntervalTicks(intOf(root, "interval-ticks", 100));
        d.setChance(dblOf(root, "chance", 1.0));
        d.setCost(dblOf(root, "cost", 0.0));
        d.setPermission(str(root, "permission", null));
        d.setEnabled(bool(root, "enabled", true));

        if (root.has("actions") && root.get("actions").isJsonArray()) {
            for (JsonElement ae : root.getAsJsonArray("actions")) {
                if (!ae.isJsonObject()) continue;
                JsonObject ao = ae.getAsJsonObject();
                com.mooncore.modules.mechanic.ActionType type =
                        com.mooncore.modules.mechanic.ActionType.fromText(str(ao, "type", "none"));
                java.util.Map<String, String> params = new java.util.LinkedHashMap<>();
                if (ao.has("params") && ao.get("params").isJsonObject()) {
                    for (var entry : ao.getAsJsonObject("params").entrySet()) {
                        if (entry.getValue().isJsonPrimitive()) params.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
                d.addAction(new com.mooncore.modules.mechanic.MechanicAction(type, params));
            }
        }
        return d;
    }

    private static String str(JsonObject o, String key, String def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : def;
    }

    private static boolean bool(JsonObject o, String key, boolean def) {
        try { return o.has(key) ? o.get(key).getAsBoolean() : def; }
        catch (Exception e) { return def; }
    }

    private static int intOf(JsonObject o, String key, int def) {
        try { return o.has(key) ? o.get(key).getAsInt() : def; }
        catch (Exception e) { return def; }
    }

    private static double dblOf(JsonObject o, String key, double def) {
        try { return o.has(key) ? o.get(key).getAsDouble() : def; }
        catch (Exception e) { return def; }
    }
}
