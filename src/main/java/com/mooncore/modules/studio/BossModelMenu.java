package com.mooncore.modules.studio;

import com.mooncore.MoonCore;
import com.mooncore.modules.model.ModelEngineModule;
import com.mooncore.util.ChatInput;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * Choix du <b>MODÈLE 3D ANIMÉ</b> d'un boss (remplace l'ancienne « texture » plate, absurde
 * pour un boss). Le boss apparaît alors comme le rig choisi : l'entité vanilla est rendue
 * invisible et le modèle animé la suit (l'IA gère déplacement/combat). Rigs = {@code golem}
 * intégré + tout {@code .bbmodel} déposé dans {@code plugins/MoonCore/models/}.
 */
public final class BossModelMenu implements StudioMenu {

    private final MoonCore plugin;
    private final ChatInput chat;
    private final String bossId;
    private final List<String> rigs;
    private Inventory inv;

    private BossModelMenu(MoonCore plugin, ChatInput chat, String bossId) {
        this.plugin = plugin;
        this.chat = chat;
        this.bossId = bossId;
        ModelEngineModule me = engine();
        this.rigs = me == null ? List.of() : me.availableRigs();
    }

    public static void open(MoonCore plugin, ChatInput chat, Player p, String bossId) {
        BossModelMenu m = new BossModelMenu(plugin, chat, bossId);
        m.inv = Bukkit.createInventory(m, 54, Text.mm("<gradient:#8a2be2:#c77dff>Modèle 3D</gradient> <dark_gray>» <white>" + bossId));
        m.build();
        p.openInventory(m.inv);
    }

    private void build() {
        StudioItems.fill(inv);
        ModelEngineModule me = engine();
        if (me == null) {
            inv.setItem(22, StudioItems.btn(Material.BARRIER, "<red>Moteur de modèles inactif",
                    "<gray>(uniquement sur le build 1.21.11)"));
            inv.setItem(8, StudioItems.btn(Material.OAK_DOOR, "<yellow>Retour"));
            return;
        }
        String current = me.bossRigName(bossId);
        inv.setItem(4, StudioItems.btn(Material.ARMOR_STAND, "<red>Modèle de " + bossId,
                "<gray>actuel : <white>" + (current == null ? "aucun (apparence vanilla)" : current),
                "<gray>le boss devient invisible et CE modèle 3D animé le remplace"));
        inv.setItem(8, StudioItems.btn(Material.OAK_DOOR, "<yellow>Retour"));

        inv.setItem(45, StudioItems.btn(Material.BARRIER, "<red>Aucun (vanilla)",
                "<gray>retire le modèle : le boss reprend son apparence d'entité"));
        inv.setItem(49, StudioItems.btn(Material.SPAWNER, "<green>Spawn test",
                "<gray>fait apparaître le boss pour voir le modèle"));
        inv.setItem(53, StudioItems.btn(Material.BOOK, "<gray>Astuce",
                "<gray>conçois un modèle dans BlockBench,",
                "<gray>dépose le <white>.bbmodel</white> dans plugins/MoonCore/models/,",
                "<gray>il apparaîtra ici."));

        // Un bouton par rig disponible.
        for (int i = 0; i < rigs.size() && i < StudioItems.CONTENT_SLOTS.length; i++) {
            String rig = rigs.get(i);
            boolean sel = rig.equalsIgnoreCase(current);
            inv.setItem(StudioItems.CONTENT_SLOTS[i], StudioItems.btn(
                    sel ? Material.LIME_DYE : (rig.equals("golem") ? Material.IRON_BLOCK : Material.PAINTING),
                    (sel ? "<green>● " : "<white>") + rig,
                    sel ? "<gray>modèle actuel" : "<gray>clic = assigner ce modèle au boss"));
        }
    }

    @Override
    public void click(Player p, int slot, boolean rightClick, boolean shiftClick) {
        ModelEngineModule me = engine();
        if (slot == 8) { BossEditorMenu.open(plugin, chat, p, bossId); return; }
        if (me == null) return;
        if (slot == 45) {
            me.removeBossRig(bossId);
            p.sendMessage(Text.mm("<yellow>Modèle retiré : <white>" + bossId + "</white> reprendra son apparence vanilla au prochain spawn."));
            build(); return;
        }
        if (slot == 49) {
            var boss = plugin.moduleManager().get(com.mooncore.modules.boss.BossManagerModule.class);
            if (boss != null) boss.spawn(bossId, p.getLocation());
            return;
        }
        int idx = indexFor(slot);
        if (idx < 0 || idx >= rigs.size()) return;
        String rig = rigs.get(idx);
        if (me.resolveRig(rig) == null) { p.sendMessage(Text.mm("<red>Modèle illisible : " + rig)); return; }
        me.setBossRig(bossId, rig);
        p.sendMessage(Text.mm("<green>Boss <white>" + bossId + "</white> → modèle 3D <white>" + rig
                + "</white>. Spawn-le (slot vert) pour le voir animé."));
        build();
    }

    private static int indexFor(int slot) {
        for (int i = 0; i < StudioItems.CONTENT_SLOTS.length; i++) if (StudioItems.CONTENT_SLOTS[i] == slot) return i;
        return -1;
    }

    private ModelEngineModule engine() { return plugin.moduleManager().get(ModelEngineModule.class); }

    @Override
    public Inventory getInventory() { return inv; }
}
