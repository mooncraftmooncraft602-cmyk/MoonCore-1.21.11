package com.mooncore.modules.customitem;

import com.mooncore.MoonCore;
import com.mooncore.api.customitem.ItemStats;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;

import java.util.Locale;
import java.util.Map;

/**
 * Couche <b>unique</b> d'application des composants d'objet 1.21 à partir d'une {@link CustomItemDef}
 * (Étape B1). Centralise tous les {@code meta.setX(...)} / {@code item.setData(...)} :
 * item_model, equippable (armure portée), glint, unbreakable, flags, attributs vanilla, enchantements,
 * et le composant {@code GLIDER} (réflexion).
 * <p>
 * Objectif : un seul endroit où brancher les futurs composants (food, consumable, tool…) avec le
 * même pattern <b>réflexion + fallback</b> que GLIDER, au lieu de disperser la logique entre
 * {@code CustomItemFactory} et les modules. Aucune régression : comportement identique à l'existant.
 */
public final class ItemComponentApplier {

    private final MoonCore plugin;

    public ItemComponentApplier(MoonCore plugin) {
        this.plugin = plugin;
    }

    /** Applique tous les composants dérivés de la définition sur le {@code meta} (hors lore/nom/PDC). */
    public void apply(CustomItemDef def, ItemMeta meta) {
        applyItemModel(def, meta);
        applyEquippable(def, meta);
        applyGlint(def, meta);
        applyUnbreakable(def, meta);
        // Masque l'affichage vanilla des attributs : nos stats sont déjà listées dans le lore.
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        applyAttributes(def, meta);
        applyEnchants(def, meta);
    }

    /**
     * Modèle custom MODERNE (composant item_model, 1.21.4+) → assets/mooncore/items/&lt;key&gt;.json.
     * Clé string, zéro collision ; ignoré par les clients sans le pack.
     */
    private void applyItemModel(CustomItemDef def, ItemMeta meta) {
        if (def.modelKey() != null && !def.modelKey().isBlank()) {
            meta.setItemModel(new NamespacedKey(ResourcePackBuilder.NS, def.modelKey().toLowerCase(Locale.ROOT)));
        }
    }

    /**
     * Armure portée custom (composant equippable, 1.21.2+) : la texture s'affiche sur le CORPS du
     * joueur (3e personne). Asset généré par {@code EquipmentPackBuilder}.
     */
    private void applyEquippable(CustomItemDef def, ItemMeta meta) {
        if (def.equipmentKey() == null || def.equipmentKey().isBlank()) return;
        EquipmentSlot slot = armorSlot(def.material());
        if (slot == null) return;
        EquippableComponent eq = meta.getEquippable();
        eq.setSlot(slot);
        eq.setModel(new NamespacedKey(ResourcePackBuilder.NS, def.equipmentKey()));
        meta.setEquippable(eq);
    }

    private void applyGlint(CustomItemDef def, ItemMeta meta) {
        if (def.glowing()) meta.setEnchantmentGlintOverride(true);
    }

    private void applyUnbreakable(CustomItemDef def, ItemMeta meta) {
        if (def.unbreakable()) {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }
    }

    /** Attributs vanilla (s'appliquent serveur-side → identiques Java/Bedrock). */
    private void applyAttributes(CustomItemDef def, ItemMeta meta) {
        EquipmentSlotGroup group;
        if (def.type().appliesWorn()) {
            group = EquipmentSlotGroup.ARMOR;
        } else if (def.type().appliesHeld()) {
            group = EquipmentSlotGroup.MAINHAND;
        } else {
            group = EquipmentSlotGroup.ANY;
        }

        for (Map.Entry<String, Double> e : def.stats().entrySet()) {
            ItemStats.StatMeta m = ItemStats.meta(e.getKey());
            if (m == null || m.vanillaAttribute() == null) continue; // stat gérée par le listener
            Attribute attr = com.mooncore.util.Attrs.byKey(m.vanillaAttribute());
            if (attr == null) continue;

            double value = e.getValue();
            AttributeModifier.Operation op;
            if (m.percent()) {
                op = AttributeModifier.Operation.MULTIPLY_SCALAR_1;
                value = value / 100.0;
            } else {
                op = AttributeModifier.Operation.ADD_NUMBER;
            }
            NamespacedKey modKey = new NamespacedKey(plugin, "ci_" + e.getKey());
            meta.addAttributeModifier(attr, new AttributeModifier(modKey, value, op, group));
        }
    }

    /** Enchantements vanilla (Sharpness, Protection, Efficiency…). */
    private void applyEnchants(CustomItemDef def, ItemMeta meta) {
        for (Map.Entry<String, Integer> e : def.enchants().entrySet()) {
            org.bukkit.enchantments.Enchantment ench = resolveEnchant(e.getKey());
            if (ench != null) meta.addEnchant(ench, Math.max(1, e.getValue()), true);
        }
    }

    /** Slot d'armure déduit du matériau de base (null = matériau non portable comme armure). */
    public static EquipmentSlot armorSlot(Material mat) {
        String n = mat.name();
        if (n.endsWith("_HELMET") || n.endsWith("_HEAD") || n.endsWith("_SKULL") || n.equals("CARVED_PUMPKIN"))
            return EquipmentSlot.HEAD;
        if (n.endsWith("_CHESTPLATE") || n.equals("ELYTRA"))
            return EquipmentSlot.CHEST;
        if (n.endsWith("_LEGGINGS"))
            return EquipmentSlot.LEGS;
        if (n.endsWith("_BOOTS"))
            return EquipmentSlot.FEET;
        return null;
    }

    @SuppressWarnings("deprecation") // Registry.ENCHANTMENT.get : stable en 1.21 (RegistryAccess = plus lourd)
    private static org.bukkit.enchantments.Enchantment resolveEnchant(String key) {
        try { return org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key)); }
        catch (Exception e) { return null; }
    }

    /**
     * Applique le composant {@code GLIDER} (Paper 1.21.4+) par réflexion — l'objet agit comme une
     * élytre dans son slot. <b>Pattern de référence</b> pour les composants modernes risqués :
     * no-op et retourne {@code false} sur un Paper plus ancien (1.21.1).
     *
     * @return {@code true} si le composant a été posé, {@code false} si indisponible
     */
    public static boolean applyGlider(ItemStack item) {
        try {
            Class<?> types = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
            Object glider = types.getField("GLIDER").get(null);
            Class<?> nonValued = Class.forName("io.papermc.paper.datacomponent.DataComponentType$NonValued");
            ItemStack.class.getMethod("setData", nonValued).invoke(item, glider);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
