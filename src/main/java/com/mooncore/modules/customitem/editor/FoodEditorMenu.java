package com.mooncore.modules.customitem.editor;

import com.mooncore.modules.customitem.CustomItemDef;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.util.ChatInput;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Sous-menu d'édition de la NOURRITURE native (composants {@code minecraft:food} + {@code consumable}) :
 * active/désactive, règle nutrition, saturation, « mangeable rassasié », durée. Clic = +, clic droit = −.
 */
public final class FoodEditorMenu implements InventoryHolder {

    private final CustomItemManagerModule module;
    private final ChatInput chat;
    private final String id;
    private Inventory inv;

    private FoodEditorMenu(CustomItemManagerModule module, ChatInput chat, String id) {
        this.module = module;
        this.chat = chat;
        this.id = id;
    }

    public static void open(CustomItemManagerModule module, ChatInput chat, Player p, String id) {
        FoodEditorMenu m = new FoodEditorMenu(module, chat, id);
        m.inv = Bukkit.createInventory(m, 27,
                Text.mm("<gradient:#8a2be2:#c77dff>Nourriture native</gradient> <dark_gray>» <white>" + id));
        m.build();
        p.openInventory(m.inv);
    }

    private void build() {
        CustomItemDef d = module.rawDef(id);
        if (d == null) return;
        for (int i = 0; i < 27; i++) inv.setItem(i, ItemEditorMenu.btn(Material.GRAY_STAINED_GLASS_PANE, " "));

        boolean on = d.hasFood();
        inv.setItem(10, ItemEditorMenu.btn(on ? Material.COOKED_BEEF : Material.GLASS_BOTTLE,
                "<yellow>Nourriture native : " + (on ? "<green>ON" : "<red>OFF"),
                "<dark_gray>clic = activer/désactiver",
                "<dark_gray>mangeable par le mécanisme vanilla"));

        if (on) {
            inv.setItem(12, ItemEditorMenu.btn(Material.POTATO, "<gold>Nutrition : <white>" + d.foodNutrition(),
                    "<gray>points de faim restaurés (0–20)", "<dark_gray>clic = +1 · clic droit = −1"));
            inv.setItem(13, ItemEditorMenu.btn(Material.COOKED_CHICKEN,
                    "<gold>Saturation : <white>" + fmt(d.foodSaturation()),
                    "<gray>saturation restaurée (0–20)", "<dark_gray>clic = +0.5 · clic droit = −0.5"));
            inv.setItem(14, ItemEditorMenu.btn(d.foodCanAlwaysEat() ? Material.GOLDEN_APPLE : Material.APPLE,
                    "<gold>Mangeable rassasié : " + (d.foodCanAlwaysEat() ? "<green>ON" : "<red>OFF"),
                    "<dark_gray>clic = basculer"));
            inv.setItem(15, ItemEditorMenu.btn(Material.CLOCK, "<gold>Durée : <white>" + fmt(d.foodEatSeconds()) + "s",
                    "<gray>durée de l'animation (0.1–60)", "<dark_gray>clic = +0.2 · clic droit = −0.2"));
        }
        inv.setItem(22, ItemEditorMenu.btn(Material.OAK_DOOR, "<yellow>← Retour à l'objet"));
    }

    public void click(Player p, int rawSlot, boolean right) {
        CustomItemDef d = module.rawDef(id);
        if (d == null) { p.closeInventory(); return; }
        switch (rawSlot) {
            case 10 -> {
                if (d.hasFood()) d.clearFood();
                else d.setFood(4, 2.4f, false, 1.6f);
                module.put(d); reopen(p);
            }
            case 12 -> {
                if (d.hasFood()) { d.setFood(d.foodNutrition() + (right ? -1 : 1),
                        d.foodSaturation(), d.foodCanAlwaysEat(), d.foodEatSeconds()); module.put(d); reopen(p); }
            }
            case 13 -> {
                if (d.hasFood()) { d.setFood(d.foodNutrition(),
                        d.foodSaturation() + (right ? -0.5f : 0.5f), d.foodCanAlwaysEat(), d.foodEatSeconds());
                        module.put(d); reopen(p); }
            }
            case 14 -> {
                if (d.hasFood()) { d.setFood(d.foodNutrition(), d.foodSaturation(),
                        !d.foodCanAlwaysEat(), d.foodEatSeconds()); module.put(d); reopen(p); }
            }
            case 15 -> {
                if (d.hasFood()) { d.setFood(d.foodNutrition(), d.foodSaturation(),
                        d.foodCanAlwaysEat(), d.foodEatSeconds() + (right ? -0.2f : 0.2f)); module.put(d); reopen(p); }
            }
            case 22 -> ItemEditorMenu.open(module, chat, p, id);
            default -> { }
        }
    }

    private void reopen(Player p) { if (p.isOnline()) open(module, chat, p, id); }

    private static String fmt(float v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    @Override
    public Inventory getInventory() { return inv; }
}
