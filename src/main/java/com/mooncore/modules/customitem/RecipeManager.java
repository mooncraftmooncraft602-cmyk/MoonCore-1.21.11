package com.mooncore.modules.customitem;

import com.mooncore.MoonCore;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Enregistre/retire les recettes d'artisanat des objets custom auprès du serveur.
 * Une recette utilise une clé {@code ci_recipe_<id>} pour pouvoir être retirée
 * proprement au reload/disable.
 */
public final class RecipeManager {

    private final MoonCore plugin;
    private final CustomItemManagerModule module;
    private final List<NamespacedKey> registered = new ArrayList<>();

    public RecipeManager(MoonCore plugin, CustomItemManagerModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    public void registerAll() {
        for (CustomItemDef def : module.rawDefs().values()) {
            register(def);
            registerSmelt(def);
            registerStonecut(def);
            registerSmithing(def);
        }
    }

    /** Recette de FORGE (smithing transform 1.20+) : base + addition (+ template) → l'objet custom. */
    public boolean registerSmithing(CustomItemDef def) {
        if (!def.canSmith()) return false;
        NamespacedKey key = new NamespacedKey(plugin, "ci_smith_" + def.id());
        try {
            ItemStack result = module.buildItem(def, 1);
            CustomItemDef.SmithingRecipe sm = def.smithing();
            RecipeChoice base = choiceFor(sm.base);
            RecipeChoice addition = choiceFor(sm.addition);
            if (base == null || addition == null) {
                plugin.logger().warn("Recette de forge ignorée pour " + def.id() + " : base/addition introuvable.");
                return false;
            }
            // Template requis par l'API ; à défaut, accepte un smithing template vide (n'importe quel patron).
            RecipeChoice template = sm.template != null ? choiceFor(sm.template)
                    : new RecipeChoice.MaterialChoice(org.bukkit.Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
            if (template == null) template = new RecipeChoice.MaterialChoice(org.bukkit.Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
            org.bukkit.inventory.SmithingTransformRecipe recipe =
                    new org.bukkit.inventory.SmithingTransformRecipe(key, result, template, base, addition);
            plugin.getServer().addRecipe(recipe);
            registered.add(key);
            return true;
        } catch (Exception e) {
            plugin.logger().warn("Recette de forge invalide pour " + def.id() + " : " + e.getMessage());
            return false;
        }
    }

    /** Recette de TAILLEUR DE PIERRE : l'objet custom (exact) → résultat (Material ou item custom). */
    public boolean registerStonecut(CustomItemDef def) {
        if (!def.canCut()) return false;
        NamespacedKey key = new NamespacedKey(plugin, "ci_cut_" + def.id());
        try {
            ItemStack result = module.cutOutput(def);
            if (result == null) {
                plugin.logger().warn("Recette de tailleur ignorée pour " + def.id() + " : résultat introuvable.");
                return false;
            }
            RecipeChoice input = new RecipeChoice.ExactChoice(module.buildItem(def, 1));
            org.bukkit.inventory.StonecuttingRecipe recipe =
                    new org.bukkit.inventory.StonecuttingRecipe(key, result, input);
            plugin.getServer().addRecipe(recipe);
            registered.add(key);
            return true;
        } catch (Exception e) {
            plugin.logger().warn("Recette de tailleur invalide pour " + def.id() + " : " + e.getMessage());
            return false;
        }
    }

    /**
     * Recette de cuisson : l'objet custom (exact) → résultat configuré, qui peut être un
     * Material vanilla <b>ou un autre item custom</b> ({@code smeltsIntoCustom}). Le type
     * d'appareil (four / haut-fourneau / fumoir) suit {@code def.smeltType()}. Le résultat
     * exact est en plus garanti par {@code CustomItemListener.onSmelt} (FurnaceSmeltEvent).
     */
    public boolean registerSmelt(CustomItemDef def) {
        if (!def.canSmelt()) return false;
        NamespacedKey key = new NamespacedKey(plugin, "ci_smelt_" + def.id());
        try {
            ItemStack result = module.smeltOutput(def);
            if (result == null) {
                plugin.logger().warn("Recette de fonte ignorée pour " + def.id()
                        + " : résultat introuvable (item custom « " + def.smeltsIntoCustom() + " » ?).");
                return false;
            }
            RecipeChoice input = new RecipeChoice.ExactChoice(module.buildItem(def, 1));
            org.bukkit.inventory.Recipe recipe = switch (def.smeltType()) {
                case BLAST -> new org.bukkit.inventory.BlastingRecipe(key, result, input, 0.2f, 100);
                case SMOKER -> new org.bukkit.inventory.SmokingRecipe(key, result, input, 0.2f, 100);
                case FURNACE -> new org.bukkit.inventory.FurnaceRecipe(key, result, input, 0.2f, 200);
            };
            plugin.getServer().addRecipe(recipe);
            registered.add(key);
            return true;
        } catch (Exception e) {
            plugin.logger().warn("Recette de fonte invalide pour " + def.id() + " : " + e.getMessage());
            return false;
        }
    }

    public boolean register(CustomItemDef def) {
        CustomItemDef.Recipe r = def.recipe();
        if (r == null || r.isEmpty()) return false;
        ItemStack result = module.buildItem(def, Math.max(1, r.amount));
        NamespacedKey key = new NamespacedKey(plugin, "ci_recipe_" + def.id());
        try {
            if (r.shaped) {
                ShapedRecipe recipe = new ShapedRecipe(key, result);
                List<String> shape = normalizeShape(r.shape);
                recipe.shape(shape.toArray(new String[0]));
                for (Map.Entry<Character, CustomItemDef.RecipeIngredient> e : r.ingredients.entrySet()) {
                    RecipeChoice choice = choiceFor(e.getValue());
                    if (choice == null) throw new IllegalArgumentException("ingredient introuvable: " + e.getValue());
                    recipe.setIngredient(e.getKey(), choice);
                }
                plugin.getServer().addRecipe(recipe);
            } else {
                ShapelessRecipe recipe = new ShapelessRecipe(key, result);
                for (CustomItemDef.RecipeIngredient ingredient : r.ingredients.values()) {
                    RecipeChoice choice = choiceFor(ingredient);
                    if (choice == null) throw new IllegalArgumentException("ingredient introuvable: " + ingredient);
                    recipe.addIngredient(choice);
                }
                plugin.getServer().addRecipe(recipe);
            }
            registered.add(key);
            return true;
        } catch (Exception e) {
            plugin.logger().warn("Recette invalide pour l'objet custom " + def.id() + " : " + e.getMessage());
            return false;
        }
    }

    private RecipeChoice choiceFor(CustomItemDef.RecipeIngredient ingredient) {
        if (ingredient == null) return null;
        if (ingredient.isCustom()) {
            CustomItemDef def = module.rawDef(ingredient.customItemId());
            if (def == null) return null;
            return new RecipeChoice.ExactChoice(module.buildItem(def, 1));
        }
        org.bukkit.Material material = ingredient.material();
        return material == null || !material.isItem() ? null : new RecipeChoice.MaterialChoice(material);
    }

    /**
     * Garantit exactement 3 lignes de 3 caractères (les espaces = slots vides). Tolérant : une shape
     * {@code null}, plus courte/longue, ou une ligne {@code null} sont normalisées sans erreur.
     * Package-private pour test.
     */
    static List<String> normalizeShape(List<String> shape) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String row = (shape != null && i < shape.size()) ? shape.get(i) : "   ";
            if (row == null) row = "   ";                 // ligne null → vide (anti-NPE)
            if (row.length() > 3) row = row.substring(0, 3);
            while (row.length() < 3) row += " ";
            out.add(row);
        }
        return out;
    }

    public void unregisterAll() {
        for (NamespacedKey key : registered) {
            plugin.getServer().removeRecipe(key);
        }
        registered.clear();
    }
}
