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

import java.util.Locale;

/**
 * Sous-menu d'édition du composant outil NATIF ({@code minecraft:tool}) : active/désactive,
 * règle la vitesse de minage par défaut et la durabilité par bloc, et gère les règles de minage
 * (par tag {@code #minecraft:mineable/...} ou liste de matériaux). Sans règle explicite, l'item
 * déduit une règle de son {@code ToolKind}.
 */
public final class ToolRulesMenu implements InventoryHolder {

    private static final int RULES_START = 18; // première case d'affichage des règles

    private final CustomItemManagerModule module;
    private final ChatInput chat;
    private final String id;
    private Inventory inv;

    private ToolRulesMenu(CustomItemManagerModule module, ChatInput chat, String id) {
        this.module = module;
        this.chat = chat;
        this.id = id;
    }

    public static void open(CustomItemManagerModule module, ChatInput chat, Player p, String id) {
        ToolRulesMenu m = new ToolRulesMenu(module, chat, id);
        m.inv = Bukkit.createInventory(m, 45,
                Text.mm("<gradient:#8a2be2:#c77dff>Outil natif</gradient> <dark_gray>» <white>" + id));
        m.build();
        p.openInventory(m.inv);
    }

    private void build() {
        CustomItemDef d = module.rawDef(id);
        if (d == null) return;
        for (int i = 0; i < 45; i++) inv.setItem(i, ItemEditorMenu.btn(Material.GRAY_STAINED_GLASS_PANE, " "));

        boolean on = d.hasToolComponent();
        inv.setItem(10, ItemEditorMenu.btn(on ? Material.IRON_PICKAXE : Material.STICK,
                "<yellow>Outil natif : " + (on ? "<green>ON" : "<red>OFF"),
                "<dark_gray>clic = activer/désactiver",
                "<dark_gray>vitesse de minage réelle + durabilité/bloc"));

        if (on) {
            inv.setItem(12, ItemEditorMenu.btn(Material.GOLDEN_PICKAXE,
                    "<gold>Vitesse de minage : <white>" + fmt(d.toolMiningSpeed()),
                    "<gray>vitesse par défaut (hors règles)", "<dark_gray>clic = +0.5 · clic droit = −0.5"));
            inv.setItem(13, ItemEditorMenu.btn(Material.FLINT,
                    "<gold>Durabilité/bloc : <white>" + d.toolDamagePerBlock(),
                    "<gray>durabilité perdue par bloc miné", "<dark_gray>clic = +1 · clic droit = −1"));
            inv.setItem(14, ItemEditorMenu.btn(Material.WRITABLE_BOOK, "<green>+ Ajouter une règle",
                    "<gray>tag <white>#minecraft:mineable/pickaxe</white>",
                    "<gray>ou matériaux <white>STONE,DEEPSLATE</white>",
                    "<dark_gray>clic = saisir : <white>blocs [vitesse] [drops:true/false]</white>"));

            int slot = RULES_START;
            for (int i = 0; i < d.toolRules().size() && slot < 44; i++, slot++) {
                CustomItemDef.ToolRule r = d.toolRules().get(i);
                inv.setItem(slot, ItemEditorMenu.btn(Material.PAPER,
                        "<white>" + r.blocks(),
                        "<gray>vitesse <white>" + fmt(r.speed()) + "</white> · drops <white>" + r.correctForDrops(),
                        "<dark_gray>clic = supprimer"));
            }
            if (d.toolRules().isEmpty()) {
                inv.setItem(RULES_START, ItemEditorMenu.btn(Material.BARRIER,
                        "<gray>Aucune règle explicite",
                        "<dark_gray>règle auto déduite du type d'outil (" + d.toolKind().label() + ")"));
            }
        }
        inv.setItem(40, ItemEditorMenu.btn(Material.OAK_DOOR, "<yellow>← Retour à l'objet"));
    }

    public void click(Player p, int rawSlot, boolean right) {
        CustomItemDef d = module.rawDef(id);
        if (d == null) { p.closeInventory(); return; }
        switch (rawSlot) {
            case 10 -> {
                if (d.hasToolComponent()) d.clearToolComponent();
                else d.setToolComponent(1.0f, 1);
                module.put(d); reopen(p);
            }
            case 12 -> {
                if (d.hasToolComponent()) {
                    d.setToolComponent(d.toolMiningSpeed() + (right ? -0.5f : 0.5f), d.toolDamagePerBlock());
                    module.put(d); reopen(p);
                }
            }
            case 13 -> {
                if (d.hasToolComponent()) {
                    d.setToolComponent(d.toolMiningSpeed(), d.toolDamagePerBlock() + (right ? -1 : 1));
                    module.put(d); reopen(p);
                }
            }
            case 14 -> {
                if (d.hasToolComponent()) {
                    p.closeInventory();
                    chat.request(p, "<yellow>Règle — <white>blocs [vitesse] [drops:true/false]</white> "
                            + "(blocs = <white>#tag</white> ou <white>MAT1,MAT2</white>) :", in -> {
                        addRule(p, d, in);
                        module.put(d); reopen(p);
                    });
                }
            }
            case 40 -> ItemEditorMenu.open(module, chat, p, id);
            default -> {
                if (rawSlot >= RULES_START && rawSlot < 44 && d.hasToolComponent()) {
                    int index = rawSlot - RULES_START;
                    if (index >= 0 && index < d.toolRules().size()) {
                        d.toolRules().remove(index);
                        module.put(d); reopen(p);
                    }
                }
            }
        }
    }

    /** Parse « blocs [vitesse] [drops] » et ajoute la règle. */
    private void addRule(Player p, CustomItemDef d, String in) {
        String[] parts = in.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isBlank()) {
            p.sendMessage(Text.mm("<red>Règle vide ignorée."));
            return;
        }
        String blocks = parts[0];
        float speed = d.toolMiningSpeed();
        boolean correct = true;
        for (int i = 1; i < parts.length; i++) {
            String tok = parts[i].toLowerCase(Locale.ROOT);
            if (tok.equals("true") || tok.equals("drops") || tok.equals("drops:true")) correct = true;
            else if (tok.equals("false") || tok.equals("nodrops") || tok.equals("drops:false")) correct = false;
            else { try { speed = Float.parseFloat(tok); } catch (NumberFormatException ignored) { } }
        }
        d.addToolRule(blocks, speed, correct);
        p.sendMessage(Text.mm("<green>Règle ajoutée : <white>" + blocks + "</white> (vitesse "
                + fmt(speed) + ", drops " + correct + ")"));
    }

    private void reopen(Player p) { if (p.isOnline()) open(module, chat, p, id); }

    private static String fmt(float v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    @Override
    public Inventory getInventory() { return inv; }
}
