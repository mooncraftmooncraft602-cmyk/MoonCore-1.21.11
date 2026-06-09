package com.mooncore.modules.studio;

import com.mooncore.MoonCore;
import com.mooncore.modules.customitem.CustomItemDef;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.util.ChatInput;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Choix des DROPS d'un boss : liste les objets custom ; clic = (dé)activer le drop pour ce
 * boss, clic droit = cycler la probabilité. Le drop est stocké sur l'objet sous la source
 * {@code boss:<id>} (consommé par CustomItemListener.onDeath).
 */
public final class BossDropMenu implements StudioMenu {

    private static final double[] CHANCES = {0.05, 0.10, 0.25, 0.50, 1.0};
    private static final int[] AMOUNTS = {1, 2, 3, 5, 8, 16};

    private final MoonCore plugin;
    private final ChatInput chat;
    private final String bossId;
    private final int page;
    private final List<String> ids;
    private Inventory inv;

    private BossDropMenu(MoonCore plugin, ChatInput chat, String bossId, int page) {
        this.plugin = plugin;
        this.chat = chat;
        this.bossId = bossId;
        this.page = Math.max(0, page);
        CustomItemManagerModule m = items();
        this.ids = m == null ? List.of() : new ArrayList<>(m.rawDefs().keySet());
    }

    public static void open(MoonCore plugin, ChatInput chat, Player p, String bossId, int page) {
        BossDropMenu menu = new BossDropMenu(plugin, chat, bossId, page);
        menu.inv = Bukkit.createInventory(menu, 54, Text.mm("<gradient:#8a2be2:#c77dff>Drops</gradient> <dark_gray>» <white>" + bossId));
        menu.build();
        p.openInventory(menu.inv);
    }

    private void build() {
        StudioItems.fill(inv);
        inv.setItem(4, StudioItems.btn(Material.WITHER_SKELETON_SKULL, "<red>Drops de " + bossId,
                "<gray>en bas : objets custom (clic = on/off, clic droit = chance)",
                "<gray>en haut : ajoute n'importe quel objet (même vanilla)"));
        // Ajout direct de l'objet tenu en main (vanilla OU custom) — résout « on ne peut pas ajouter de drop ».
        inv.setItem(2, StudioItems.btn(Material.HOPPER, "<green>➕ Ajouter l'objet en main",
                "<gray>tiens un objet (vanilla ou custom) et clique",
                "<gray>→ il sera lâché par le boss à sa mort"));
        var bm = boss();
        int vanilla = bm == null ? 0 : bm.vanillaDrops(bossId).size();
        inv.setItem(6, StudioItems.btn(Material.CHEST_MINECART, "<gold>Drops directs : <white>" + vanilla,
                "<gray>objets ajoutés via « objet en main »",
                "<dark_gray>clic droit = tout vider"));
        inv.setItem(8, StudioItems.btn(Material.OAK_DOOR, "<yellow>Retour"));
        CustomItemManagerModule module = items();
        if (module == null) { inv.setItem(22, StudioItems.btn(Material.BARRIER, "<red>custom-item inactif")); return; }
        if (ids.isEmpty()) inv.setItem(31, StudioItems.btn(Material.WRITABLE_BOOK, "<yellow>Aucun objet custom",
                "<gray>utilise « ➕ Ajouter l'objet en main » ci-dessus,",
                "<gray>ou crée un objet : <white>/moon item create <id></white>"));

        int per = StudioItems.CONTENT_SLOTS.length;
        int start = page * per;
        for (int i = 0; i < per && start + i < ids.size(); i++) {
            CustomItemDef def = module.rawDef(ids.get(start + i));
            if (def == null) continue;
            CustomItemDef.DropRule r = ruleOf(def);
            String state = r != null ? "<green>DROP " + Math.round(r.chance() * 100) + "% ×" + r.max() : "<dark_gray>non";
            inv.setItem(StudioItems.CONTENT_SLOTS[i], StudioItems.label(module.buildItem(def, 1).getType(),
                    "<aqua>" + def.id(), "<gray>statut: " + state,
                    "<dark_gray>clic = on/off · clic droit = chance · shift = quantité"));
        }
        if (page > 0) inv.setItem(45, StudioItems.btn(Material.ARROW, "<yellow>Page précédente"));
        if (start + per < ids.size()) inv.setItem(53, StudioItems.btn(Material.ARROW, "<yellow>Page suivante"));
    }

    @Override
    public void click(Player p, int slot, boolean rightClick, boolean shiftClick) {
        if (slot == 8) { BossEditorMenu.open(plugin, chat, p, bossId); return; }
        if (slot == 2) { addHeld(p); return; }
        if (slot == 6) {
            var bm = boss();
            if (bm != null && rightClick) { bm.clearVanillaDrops(bossId); p.sendMessage(Text.mm("<yellow>Drops directs vidés.")); build(); }
            return;
        }
        if (slot == 45) { if (page > 0) open(plugin, chat, p, bossId, page - 1); return; }
        if (slot == 53) { open(plugin, chat, p, bossId, page + 1); return; }
        CustomItemManagerModule module = items();
        if (module == null) return;
        int idx = indexFor(slot);
        if (idx < 0) return;
        int itemIndex = page * StudioItems.CONTENT_SLOTS.length + idx;
        if (itemIndex >= ids.size()) return;
        CustomItemDef def = module.rawDef(ids.get(itemIndex));
        if (def == null) return;
        CustomItemDef.DropRule r = ruleOf(def);
        if (shiftClick) {
            if (r != null) setRule(module, def, r.chance(), nextAmount(r.max())); // cycle la quantité
        } else if (rightClick) {
            if (r == null) setRule(module, def, CHANCES[0], 1);                   // active à la 1re chance
            else setRule(module, def, nextChance(r.chance()), r.max());           // cycle la chance
        } else {
            if (r != null) setRule(module, def, 0, 1);                            // off
            else setRule(module, def, 0.25, 1);                                   // on (défaut 25% ×1)
        }
        build();
    }

    private CustomItemDef.DropRule ruleOf(CustomItemDef def) {
        for (CustomItemDef.DropRule d : def.drops())
            if (d.source().equalsIgnoreCase("boss:" + bossId)) return d;
        return null;
    }

    private void setRule(CustomItemManagerModule module, CustomItemDef def, double chance, int amount) {
        def.drops().removeIf(d -> d.source().equalsIgnoreCase("boss:" + bossId));
        if (chance > 0) {
            int a = Math.max(1, amount);
            def.drops().add(new CustomItemDef.DropRule("boss:" + bossId, chance, a, a));
        }
        module.put(def);
    }

    private static double nextChance(double cur) {
        for (double c : CHANCES) if (c > cur + 1e-6) return c;
        return CHANCES[0];
    }

    private static int nextAmount(int cur) {
        for (int a : AMOUNTS) if (a > cur) return a;
        return AMOUNTS[0];
    }

    private static int indexFor(int slot) {
        for (int i = 0; i < StudioItems.CONTENT_SLOTS.length; i++) if (StudioItems.CONTENT_SLOTS[i] == slot) return i;
        return -1;
    }

    /** Ajoute l'objet en main comme drop : item custom → règle boss:&lt;id&gt; ; sinon → drop direct (vanilla). */
    private void addHeld(Player p) {
        org.bukkit.inventory.ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) { p.sendMessage(Text.mm("<red>Tiens un objet en main.")); return; }
        CustomItemManagerModule ci = items();
        String customId = ci == null ? null : ci.factory().idOf(hand);
        if (customId != null && ci.rawDef(customId) != null) {
            setRule(ci, ci.rawDef(customId), 0.25, hand.getAmount()); // active le drop de l'objet custom
            p.sendMessage(Text.mm("<green>Objet custom <white>" + customId + "</white> ajouté aux drops (25% ×" + hand.getAmount() + ")."));
        } else {
            var bm = boss();
            if (bm == null) { p.sendMessage(Text.mm("<red>Module boss inactif.")); return; }
            bm.addVanillaDrop(bossId, hand);
            p.sendMessage(Text.mm("<green>Objet <white>" + hand.getType().name().toLowerCase(java.util.Locale.ROOT)
                    + "</white> ×" + hand.getAmount() + " ajouté aux drops directs du boss."));
        }
        build();
    }

    private com.mooncore.modules.boss.BossManagerModule boss() {
        return plugin.moduleManager().get(com.mooncore.modules.boss.BossManagerModule.class);
    }

    private CustomItemManagerModule items() { return plugin.moduleManager().get(CustomItemManagerModule.class); }

    @Override
    public Inventory getInventory() { return inv; }
}
